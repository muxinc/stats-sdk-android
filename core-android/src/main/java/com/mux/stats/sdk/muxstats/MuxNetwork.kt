package com.mux.stats.sdk.muxstats

import android.net.Uri
import com.mux.stats.sdk.core.util.MuxLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

private const val LOG_TAG = "MuxNetwork"

/**
 * Android implementation of [INetworkRequest] backed by a coroutine dispatcher
 *
 * @param device an [IDevice] for the host device
 * @param coroutineScope Optional [CoroutineScope] in which requests can run.
 * The default is [Dispatchers.Default]
 */
class MuxNetwork(
  private val device: IDevice,
  private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) : INetworkRequest {

  constructor(device: IDevice) : this(device, CoroutineScope(Dispatchers.Default))

  override fun get(url: URL?) {
    TODO("Not yet implemented")
  }

  override fun post(url: URL?, body: JSONObject?, requestHeaders: Hashtable<String, String>?) {
    TODO("Not yet implemented")
  }

  override fun postWithCompletion(
    domain: String?,
    envKey: String?,
    body: String?,
    requestHeaders: Hashtable<String, String>?,
    completion: INetworkRequest.IMuxNetworkRequestsCompletion?
  ) {
    TODO("Not yet implemented")
  }

  // -- HTTP Client below here. It's a nested class to keep it out of java callers' namespace

  /**
   * Small HTTP client with gzip, per-request exponential backoff, and GET and POST
   *
   * @param device provides access to the device
   * @param coroutineScope the coroutine scope for logic (IO is done on the IO dispatcher)
   */
  internal class HttpClient(
    val device: IDevice,
    val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
  ) {
    @Throws(IOException::class)
    private suspend fun doOneCall(request: Request): Response {
      // TODO: Do the call, suspending for backoff, suspending also to do IO on the IO Dispatcher
      //  This method rethrows exceptions, and doesn't handle retries
      //  This method *does* encode and stuff
      MuxLogger.d(LOG_TAG, "doOneCall: Sending $request")

      val gzip = request.headers["Content-Encoding"] == "gzip"
      val data = if (request.body != null && gzip) {
        @Suppress("BlockingMethodInNonBlockingContext") // no IO is really done, won't block
        request.body.gzip()
      } else {
        request.body
      }

      // Run the actual request on the IO dispatcher
      return withContext(Dispatchers.IO) {
        @Suppress("BlockingMethodInNonBlockingContext") // On the IO dispatcher as desired
        val hurlConn = request.url.openConnection().let { it as HttpURLConnection }.apply {
          // Basic options/config
          readTimeout = READ_TIMEOUT_MS.toInt()
          connectTimeout = CONNECTION_TIMEOUT_MS.toInt()
          requestMethod = request.method
        }

        Response(
          originalRequest = request,
          status = Response.StatusLine(0, ""), //TODO
          headers = mapOf(), //TODO
          body = null, // TODO
        )
      } // withContext(Dispatchers.IO)
    }

    /**
     * Represents the result of an HTTP call. This
     */
    data class CallResult(
      val response: Response? = null,
      val exception: Exception? = null,
      val retries: Int = 0
    ) {
      val successful = exception == null || (response?.successful ?: false)
    }
  }

  internal class GET(
    url: URL,
    headers: Map<String, String> = mapOf(),
  ) : Request("GET", url, headers, null)

  internal class POST(
    url: URL,
    headers: Map<String, String> = mapOf(),
    contentType: String? = null,
    body: ByteArray? = null
  ) : Request(
    method = "POST",
    url = url,
    headers = headers,
    body = body,
  ) {
    constructor(
      url: URL,
      headers: Map<String, String> = mapOf()
    ) : this(url = url, headers = headers, body = null)

    constructor(
      url: URL,
      headers: Map<String, String> = mapOf(),
      contentType: String?,
      body: String,
    ) : this(url = url, headers = headers, body = body.asRequestBody(), contentType = contentType)

    constructor(
      url: URL,
      headers: Map<String, String> = mapOf(),
      params: Map<String, String> = mapOf()
    ) : this(
      url = url,
      headers = headers,
      body = params.asPostBody(),
      contentType = "application/x-www-form-urlencoded"
    )

    constructor(
      url: URL,
      headers: Map<String, String> = mapOf(),
      body: JSONObject
    ) : this(
      url = url,
      headers = headers,
      body = body.asRequestBody(),
      contentType = "application/json"
    )
  }

  /**
   * Represents an HTTP request. Use subclasses like [GET] or [POST]
   */
  internal abstract class Request(
    val method: String,
    val url: URL,
    val headers: Map<String, String>,
    val body: ByteArray?,
  ) {
    override fun hashCode(): Int = toString().hashCode()
    override fun equals(other: Any?) = other is Request && hashCode() == other.hashCode()
    override fun toString(): String {
      return "Request(method='$method', url=$url, headers=$headers, body=${body?.contentToString()})"
    }
  }

  /**
   * A response from an HTTP request
   */
  internal class Response(
    val originalRequest: Request,
    val status: StatusLine,
    val headers: Map<String, String>,
    val body: ByteArray?,
  ) {
    data class StatusLine(val code: Int, val message: String)

    /**
     * True if the status code of this response is 20x (OK, ACCEPTED, etc)
     */
    val successful = status.code in 200..299

    /**
     * Parses the response body as a String, returning null if there was no response body
     */
    fun bodyAsString(): String? {
      return body?.let { return decodeBody(headers["Content-Encoding"], it) }
    }

    /**
     * Parse the response body as a [JSONObject]
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
              "body=${body?.contentToString()}, successful=$successful)"
    }
  } // class Response

  companion object {
    private val DEFAULT_CHARSET = Charsets.UTF_8
    private val CONNECTION_TIMEOUT_MS =
      TimeUnit.MILLISECONDS.convert(30, TimeUnit.SECONDS)
    private val READ_TIMEOUT_MS =
      TimeUnit.MILLISECONDS.convert(20, TimeUnit.SECONDS)
    private val MAX_REQUEST_RETRIES = 4
  }
} // class MuxNetwork

/**
 * Enqueues and sends requests
 */
private class RequestWorker(coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)) {

}

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
    byteStream
  }.toByteArray()

  return zippedBytes
}

/**
 * Un-Gzips an array of bytes, returning the unzipped result in a new ByteArray
 */
@Throws(IOException::class)
internal fun ByteArray.unGzip(): ByteArray {
  return GZIPInputStream(ByteArrayInputStream(this)).use { it.readBytes() }
}
