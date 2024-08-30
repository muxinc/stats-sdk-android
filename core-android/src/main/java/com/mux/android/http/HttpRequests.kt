package com.mux.android.http

import android.net.Uri
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL
import java.nio.charset.Charset
import java.util.regex.Pattern
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

private val DEFAULT_CHARSET = Charsets.UTF_8
private const val LOG_TAG = "MuxHttp"

/**
 * A GET Request
 */
class GET(
  url: URL,
  headers: Map<String, List<String>> = mapOf(),
) : Request("GET", url, headers, null, null)

/**
 * A POST Request
 */
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
 * Represents an HTTP request. Use subclasses like [GET] or [POST], or make your own.
 */
abstract class Request(
  val method: String,
  val url: URL,
  val headers: Map<String, List<String>>,
  val contentType: String? = null,
  val body: ByteArray?,
) {
  override fun hashCode(): Int = System.identityHashCode(this)
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
      DEFAULT_CHARSET
    }

    return String(expandedBody, charset)
  } // private fun decodeBody

  override fun hashCode(): Int = System.identityHashCode(this)
  override fun equals(other: Any?) = other is Response && hashCode() == other.hashCode()
  override fun toString(): String {
    return "Response(originalRequest=$originalRequest, status=$status, headers=$headers, " +
            "body=${body?.contentToString()?.take(80)}, successful=$successful)"
  }
} // class Response

/**
 * Convert from android [Uri] to [URL]
 */
fun Uri.toURL() = URL(toString())

/**
 * Convert from [URL] to android [Uri]
 */
fun URL.toUri() = Uri.parse(toString())

/**
 * Encodes a String as a request body with UTF-8 encoding
 */
fun String.asRequestBody() = toByteArray(Charsets.UTF_8)

/**
 * Encodes a [JSONObject] as a UTF-8 JSON string
 */
fun JSONObject.asRequestBody() = toString().asRequestBody()

/**
 * Encodes a [Map] of strings to strings as a string of POST params with UTF-8 encoding
 */
fun Map<String, String>.asPostBody(): ByteArray {
  return map { entry -> "${entry.key}=${entry.value}" }
    .joinToString(separator = "&")
    .asRequestBody()
}

/**
 * Gzips an array of bytes, returning the zipped result in a new ByteArray
 */
@Throws(IOException::class)
fun ByteArray.gzip(): ByteArray {
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
fun ByteArray.unGzip(): ByteArray {
  return GZIPInputStream(ByteArrayInputStream(this)).use { it.readBytes() }
}

/**
 * Gets the URI Authority used for POSTing beacons to the backend, provided a domain and env key
 * @param envKey An env key for an environment, or blank to infer the key server-side
 * @param domain The domain, prepended with a '.'
 */
fun beaconAuthority(envKey: String, domain: String): String {
  return if (!domain.startsWith(".")) {
    domain
  } else if (Pattern.matches("^[a-z0-9]+$", envKey)) {
    "$envKey$domain"
  } else {
    "img$domain"
  }
}
