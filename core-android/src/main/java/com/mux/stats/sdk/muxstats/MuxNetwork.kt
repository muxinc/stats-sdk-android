package com.mux.stats.sdk.muxstats

import android.net.Uri
import com.mux.stats.sdk.core.util.MuxLogger
import com.mux.stats.sdk.muxstats.internal.beaconAuthority
import kotlinx.coroutines.*
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.math.pow

private const val LOG_TAG = "MuxNetwork"

/**
 * Android implementation of [INetworkRequest] backed by a coroutine dispatcher
 *
 * @param device an [IDevice] for the host device
 * @param coroutineScope Optional [CoroutineScope] in which requests can run.
 * The default is [Dispatchers.Default]
 */
class MuxNetwork @JvmOverloads constructor(
  private val device: IDevice,
  coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) : INetworkRequest {

  private val httpClient = HttpClient(device)
  private val coroutineScope = CoroutineScope(coroutineScope.coroutineContext)

  override fun get(url: URL?) {
    if (url != null) {
      coroutineScope.launch { httpClient.call(GET(url = url)) }
    }
  }

  override fun post(url: URL?, body: JSONObject?, requestHeaders: Hashtable<String, String>?) {
    if (url != null) {
      // By the standard, you can have multiple headers with the same key
      val headers = requestHeaders?.mapValues { listOf(it.value) } ?: mapOf()
      coroutineScope.launch {
        httpClient.call(POST(url = url, json = body, headers = headers))
      }
    }
  }

  override fun postWithCompletion(
    domain: String?,
    envKey: String?,
    body: String?,
    requestHeaders: Hashtable<String, String>?,
    completion: INetworkRequest.IMuxNetworkRequestsCompletion?
  ) {
    if (envKey != null) {
      val url = Uri.Builder()
        .scheme("https")
        .authority(beaconAuthority(envKey = envKey, domain = domain ?: ""))
        .path("android")
        .build().toURL()
      // By the standard, you can have multiple headers with the same key
      val headers = requestHeaders?.mapValues { listOf(it.value) } ?: mapOf()

      coroutineScope.launch {
        val result = httpClient.call(POST(url = url, headers = headers, bodyStr = body))
        // Dispatch the result back on the main thread
        coroutineScope.launch(Dispatchers.Main) {
          completion?.onComplete(result.successful)
        }
      }
    }
  }

  /**
   * Shuts down this [MuxNetwork] immediately, canceling any running requests.
   * This is not needed for normal operation, but is available if required
   */
  fun shutdown() {
    coroutineScope.cancel("shutdown requested")
  }

  // -- HTTP Client below here. It's a nested class to keep it out of java callers' namespace

  /**
   * Small HTTP client with gzip, per-request exponential backoff, and GET and POST
   *
   * @param device provides access to the device
   */
  class HttpClient(
    private val device: IDevice,
    private val backoffBaseTimeMs: Long = RETRY_DELAY_BASE_MS
  ) {

    /**
     * Sends the given HTTP requests, suspending for I/O and/or exponential backoff
     * @param request the [Request] to send
     * @return the result of the HTTP call. If there were retries,
     */
    suspend fun call(request: Request): CallResult {
      MuxLogger.d(LOG_TAG, "doCall: Enqueue $request")
      return callWithBackoff(request).also {
        MuxLogger.d(LOG_TAG, "doCall: Final Result for ${request.url}:\n$it")
      }
    } // doCall

    private suspend fun callWithBackoff(request: Request, retries: Int = 0): CallResult {
      suspend fun maybeRetry(result: CallResult): CallResult {
        val moreRetries = result.retries < MAX_REQUEST_RETRIES
        return if (moreRetries) {
          callWithBackoff(request, result.retries + 1)
        } else {
          result
        }
      }

      maybeBackoff(request, retries)

      return if (!device.isOnline()) {
        maybeRetry(CallResult(offlineForCall = true, retries = retries))
      } else {
        try {
          val response = callOnce(request)
          MuxLogger.d(LOG_TAG, "HTTP call completed:\n$request \n\t$response")
          if (response.status.code in 500..599) {
            MuxLogger.d(LOG_TAG, "Server needs a break. Backing off")
            maybeRetry(CallResult(response = response, retries = retries))
          } else {
            // Done! The request may have been rejected, but not for a retry-able reason
            CallResult(response = response, retries = retries)
          }
        } catch (e: Exception) {
          MuxLogger.exception(e, LOG_TAG, "doCall: I/O error for $request")
          maybeRetry(CallResult(exception = e, retries = retries))
        }
      }
    }

    private suspend fun maybeBackoff(request: Request, retries: Int) {
      if (retries > 0) {
        // Random backoff within an increasing time period
        val factor = (2.0.pow((retries - 1).toDouble())) * Math.random()
        val backoffDelay = ((1 + factor) * backoffBaseTimeMs).toLong()

        MuxLogger.d(LOG_TAG, "Retrying in ${backoffDelay}ms: $request")
        delay(backoffDelay)
      }
    }

    @Throws(Exception::class)
    @Suppress("BlockingMethodInNonBlockingContext") // already on the IO dispatcher here
    private suspend fun callOnce(request: Request): Response = withContext(Dispatchers.IO) {
      MuxLogger.d(LOG_TAG, "doOneCall: Sending $request")
      var hurlConn: HttpURLConnection? = null
      try {
        val gzip = request.headers["Content-Encoding"]?.last() == "gzip"
        val bodyData = if (request.body != null && gzip) {
          request.body.gzip()
        } else {
          request.body
        }

        hurlConn = request.url.openConnection().let { it as HttpURLConnection }.apply {
          // Basic options/config
          readTimeout = READ_TIMEOUT_MS.toInt()
          connectTimeout = CONNECTION_TIMEOUT_MS.toInt()
          requestMethod = request.method
          //Headers
          request.headers.onEach { header ->
            header.value.onEach { setRequestProperty(header.key, it) }
          }
          request.contentType?.let { contentType ->
            if (contentType.isNotEmpty()) {
              setRequestProperty("Content-Type", contentType)
            }
          }
        }
        // Add Body
        bodyData?.let { dataBytes -> hurlConn.outputStream.use { it.write(dataBytes) } }

        // Connect!
        hurlConn.connect()
        val responseBytes = hurlConn.inputStream?.use { it.readBytes() }

        Response(
          originalRequest = request,
          status = Response.StatusLine(hurlConn.responseCode, hurlConn.responseMessage),
          headers = hurlConn.headerFields,
          body = responseBytes,
        )
      } finally {
        hurlConn?.disconnect()
      } // try {} finally {}
    } // fun callOnce = (...) = withContext(Dispatchers.IO)

    /**
     * Represents the result of an HTTP call. This
     */
    data class CallResult(
      val response: Response? = null,
      val exception: Exception? = null,
      val offlineForCall: Boolean = false,
      val retries: Int = 0
    ) {
      val successful
        get() = exception == null && (response?.successful ?: false) && (!offlineForCall)
    }
  }

  class GET(
    url: URL,
    headers: Map<String, List<String>> = mapOf(),
  ) : Request("GET", url, headers, null, null)

  class POST(
    url: URL,
    headers: Map<String, List<String>> = mapOf(),
    contentType: String? = null,
    body: ByteArray? = null
  ) : Request(
    method = "POST",
    contentType = contentType,
    url = url,
    headers = headers,
    body = body,
  ) {

    constructor(
      url: URL,
      headers: Map<String, List<String>> = mapOf(),
      contentType: String? = "application/json", //default is based on existing usage
      bodyStr: String?,
    ) : this(
      url = url,
      headers = headers,
      body = bodyStr?.asRequestBody(),
      contentType = contentType
    )

    constructor(
      url: URL,
      headers: Map<String, List<String>> = mapOf(),
      params: Map<String, String>? = mapOf()
    ) : this(
      url = url,
      headers = headers,
      body = params?.asPostBody(),
      contentType = "application/x-www-form-urlencoded"
    )

    constructor(
      url: URL,
      headers: Map<String, List<String>> = mapOf(),
      json: JSONObject?
    ) : this(
      url = url,
      headers = headers,
      body = json?.asRequestBody(),
      contentType = "application/json"
    )
  }

  /**
   * Represents an HTTP request. Use subclasses like [GET] or [POST]
   */
  abstract class Request(
    val method: String,
    val url: URL,
    val headers: Map<String, List<String>>,
    val contentType: String? = null,
    val body: ByteArray?,
  ) {
    override fun hashCode(): Int = toString().hashCode()
    override fun equals(other: Any?) =
      other?.let { it::class == this::class && hashCode() == it.hashCode() } ?: false

    override fun toString(): String {
      return "Request(method='$method', url=$url, headers=$headers, contentType=$contentType, " +
              "body=${body?.contentToString()?.take(80)})"
    }
  }

  /**
   * A response from an HTTP request
   */
  class Response(
    val originalRequest: Request,
    val status: StatusLine,
    val headers: Map<String, List<String>>,
    val body: ByteArray?,
  ) {
    data class StatusLine(val code: Int, val message: String?)

    /**
     * True if the status code of this response is 20x (OK, ACCEPTED, etc)
     */
    val successful = status.code in 200..299

    /**
     * Parses the response body as a String, returning null if there was no response body
     */
    fun bodyAsString(): String? {
      return body?.let { decodeBody(headers["Content-Encoding"]?.last(), it) }
    }

    /**
     * Parse the response body as a [JSONObject]. Doesn't care if the response has a json MIME type
     */
    @Throws(JSONException::class)
    fun bodyAsJSONObject(): JSONObject? {
      return bodyAsString()?.let { JSONObject(it) }
    }

    private fun decodeBody(bodyEncoding: String?, bodyBytes: ByteArray): String {
      val expandedBody: ByteArray = if (bodyEncoding == "gzip") {
        bodyBytes.unGzip()
      } else {
        bodyBytes
      }
      val charset = try {
        bodyEncoding?.let { Charset.forName(it) } ?: DEFAULT_CHARSET
      } catch (e: Exception) {
        MuxLogger.exception(e, LOG_TAG, "bad encoding $bodyEncoding")
        DEFAULT_CHARSET
      }

      return String(expandedBody, charset)
    } // private fun decodeBody

    override fun hashCode(): Int = toString().hashCode()
    override fun equals(other: Any?) = other is Response && hashCode() == other.hashCode()
    override fun toString(): String {
      return "Response(originalRequest=$originalRequest, status=$status, headers=$headers, " +
              "body=${body?.contentToString()?.take(80)}, successful=$successful)"
    }
  } // class Response

  companion object {
    private val DEFAULT_CHARSET = Charsets.UTF_8
    private val CONNECTION_TIMEOUT_MS =
      TimeUnit.MILLISECONDS.convert(30, TimeUnit.SECONDS)
    private val READ_TIMEOUT_MS =
      TimeUnit.MILLISECONDS.convert(20, TimeUnit.SECONDS)
    private const val MAX_REQUEST_RETRIES = 4
    private val RETRY_DELAY_BASE_MS =
      TimeUnit.MILLISECONDS.convert(5, TimeUnit.SECONDS)
  }
} // class MuxNetwork

internal fun IDevice.isOnline() = networkConnectionType != null

/**
 * Convert from android [Uri] to [URL]
 */
internal fun Uri.toURL() = URL(toString())

/**
 * Convert from [URL] to android [Uri]
 */
internal fun URL.toUri() = Uri.parse(toString())

/**
 * Encodes a String as a request body with UTF-8 encoding
 */
internal fun String.asRequestBody() = toByteArray(Charsets.UTF_8)

/**
 * Encodes a [JSONObject] as a UTF-8 JSON string
 */
internal fun JSONObject.asRequestBody() = toString().asRequestBody()

/**
 * Encodes a [Map] of strings to strings as a string of POST params with UTF-8 encoding
 */
internal fun Map<String, String>.asPostBody(): ByteArray {
  return map { entry -> "${entry.key}=${entry.value}" }
    .joinToString(separator = "&")
    .asRequestBody()
}

/**
 * Gzips an array of bytes, returning the zipped result in a new ByteArray
 */
@Throws(IOException::class)
internal fun ByteArray.gzip(): ByteArray {
  val zippedBytes = ByteArrayOutputStream().use { byteStream ->
    GZIPOutputStream(byteStream).use { stream ->
      stream.write(this)
      stream.flush()
    }
    byteStream.toByteArray()
  }

  return zippedBytes
}

/**
 * Un-Gzips an array of bytes, returning the unzipped result in a new ByteArray
 */
@Throws(IOException::class)
internal fun ByteArray.unGzip(): ByteArray {
  return GZIPInputStream(ByteArrayInputStream(this)).use { it.readBytes() }
}
