package com.mux.android.http

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.util.concurrent.TimeUnit
import kotlin.math.pow

/**
 * Small HTTP client with gzip, per-request exponential backoff, and GET and POST
 *
 * @param network An object that can access the state of the network on this client's behalf
 */
class HttpClient(
  private val network: DeviceNetwork,
  private val backoffBaseTimeMs: Long = RETRY_DELAY_BASE_MS
) {

  companion object {
    val CONNECTION_TIMEOUT_MS =
      TimeUnit.MILLISECONDS.convert(30, TimeUnit.SECONDS)
    val READ_TIMEOUT_MS =
      TimeUnit.MILLISECONDS.convert(20, TimeUnit.SECONDS)
    const val MAX_REQUEST_RETRIES = 4
    val RETRY_DELAY_BASE_MS =
      TimeUnit.MILLISECONDS.convert(5, TimeUnit.SECONDS)
  }

  /**
   * Sends the given HTTP requests, suspending for I/O and/or exponential backoff
   * @param request the [Request] to send
   * @return the result of the HTTP call. If there were retries,
   */
  suspend fun call(request: Request): CallResult {
    return callWithBackoff(request)
  } // doCall

  private suspend fun callWithBackoff(request: Request, retries: Int = 0): CallResult {
    println("ReqLoss callWithBackoff: called with $retries retries")
    suspend fun maybeRetry(result: CallResult): CallResult {
      val moreRetries = result.retries < MAX_REQUEST_RETRIES
      return if (moreRetries) {
        callWithBackoff(request, result.retries + 1)
      } else {
        System.out.println("ReqLoss callWithBackoff: called with $retries retries")
        result
      }
    }

    maybeBackoff(retries)

    return if (!network.isOnline()) {
      println("ReqLoss"+ " Network offline, backing off")
      maybeRetry(CallResult(offlineForCall = true, retries = retries))
    } else {
      try {
        val response = callOnce(request)
        if (response.status.code in 500..599) {
          maybeRetry(CallResult(response = response, retries = retries))
        } else {
          // Done! The request may have been rejected, but not for a retry-able reason
          CallResult(response = response, retries = retries)
        }
      } catch (e: Exception) {
        maybeRetry(CallResult(exception = e, retries = retries))
      }
    }
  }

  private suspend fun maybeBackoff(retries: Int) {
    if (retries > 0) {
      // Random backoff within an increasing time period
      val factor = (2.0.pow((retries - 1).toDouble())) * Math.random()
      val backoffDelay = ((1 + factor) * backoffBaseTimeMs).toLong()
      delay(backoffDelay)
    }
  }

  @Throws(Exception::class)
  @Suppress("BlockingMethodInNonBlockingContext") // already on the IO dispatcher here
  private suspend fun callOnce(request: Request): Response =
    withContext(Dispatchers.IO) {
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
          doOutput = bodyData != null
          doInput = true
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
          status = Response.StatusLine(
            hurlConn.responseCode,
            hurlConn.responseMessage
          ),
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

  /**
   * Provides interaction with the device's network connection
   */
  interface DeviceNetwork {
    fun isOnline(): Boolean
  }
}
