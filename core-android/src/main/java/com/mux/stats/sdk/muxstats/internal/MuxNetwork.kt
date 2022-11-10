package com.mux.stats.sdk.muxstats.internal

import android.content.Context
import com.mux.stats.sdk.core.util.MuxLogger
import com.mux.stats.sdk.muxstats.INetworkRequest
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

class MuxNetwork : INetworkRequest {

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

  /**
   * Small HTTP client with SSL, gzip, per-request exponential backoff, and support for GET and POST
   */
  internal class HttpClient(context: Context) {

    private val appContext = context.applicationContext

  }

  abstract class Request(
    val method: String,
    val url: URL,
    val headers: Map<String, String>,
    val body: ByteArray?,
  )

  class Response(
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
    } //private fun decodeBody
  } // class Response

  private companion object {
    const val LOG_TAG = "MuxNetwork"
    val DEFAULT_CHARSET = Charsets.UTF_8
  }
} // class MuxNetwork

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
