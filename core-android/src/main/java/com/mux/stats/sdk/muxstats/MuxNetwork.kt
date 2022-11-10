package com.mux.stats.sdk.muxstats

import android.net.Uri
import com.mux.stats.sdk.core.util.MuxLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL
import java.nio.charset.Charset
import java.util.*
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

private const val LOG_TAG = "MuxNetwork"
private val DEFAULT_CHARSET = Charsets.UTF_8

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
   * Small HTTP client with SSL, gzip, per-request exponential backoff, and GET and POST
   */
  internal class HttpClient(
    val device: IDevice
  ) {
  }

  internal class GET(
    url: URL,
    headers: Map<String, String> = mapOf(),
  ) : Request("GET", url, headers, null)

  internal class POST(
    url: URL,
    headers: Map<String, String> = mapOf(),
    body: ByteArray?
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
      body: String
    ) : this(url = url, headers = headers, body = body.asRequestBody())

    constructor(
      url: URL,
      headers: Map<String, String> = mapOf(),
      params: Map<String, String> = mapOf()
    ) : this(url = url, headers = headers, body = params.asPostBody())

    constructor(
      url: URL,
      headers: Map<String, String> = mapOf(),
      body: JSONObject
    ) : this(url = url, headers = headers, body = body.asRequestBody())
  }

  /**
   * Represents an HTTP request. Use subclasses like [GET] or [POST]
   */
  internal abstract class Request(
    val method: String,
    val url: URL,
    val headers: Map<String, String>,
    val body: ByteArray?,
  )

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
  } // class Response

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
