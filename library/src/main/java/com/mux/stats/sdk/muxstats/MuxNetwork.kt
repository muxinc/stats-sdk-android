package com.mux.stats.sdk.muxstats

import android.net.Uri
import android.util.Log
import com.mux.android.http.*
import com.mux.android.util.oneOf
import com.mux.stats.sdk.core.model.VideoData
import com.mux.stats.sdk.core.model.ViewData
import com.mux.stats.sdk.core.util.MuxLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URL
import java.util.*

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

  private val httpClient = HttpClient(device.networkDevice())
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
        body?.keys()?.forEach { key ->
          if (key.oneOf(VideoData.VIDEO_SOURCE_WIDTH, VideoData.VIDEO_SOURCE_HEIGHT)) {
            val value = body.get(key)
            MuxLogger.d("RENDITIONCHANGE", "Reporting metadata key $key -> $value")
          }
        }

        httpClient.call(
          POST(
            url = url,
            json = body,
            headers = headers
          )
        )
      }
    }
  }

  override fun postWithCompletion(
    domain: String?,
    envKey: String?,
    body: String?,
    requestHeaders: Hashtable<String, String>?,
    completion: INetworkRequest.IMuxNetworkRequestsCompletion2?
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
        val result = httpClient.call(
          POST(
            url = url,
            headers = headers,
            bodyStr = body
          )
        )
        // Dispatch the result back on the main thread
        coroutineScope.launch(Dispatchers.Main) {
          completion?.onComplete(result.successful, result.response?.headers)
        }
      }
    } else {
      // If no envKey was supplied from java core, that's an error
      coroutineScope.launch(Dispatchers.Main) {
        completion?.onComplete(false, null)
      }
    }}

  override fun postWithCompletion(
    domain: String?,
    envKey: String?,
    body: String?,
    requestHeaders: Hashtable<String, String>?,
    completion: INetworkRequest.IMuxNetworkRequestsCompletion?
  ) {
    postWithCompletion(domain, envKey, body, requestHeaders) { success, _ ->
      completion?.onComplete(success)
    }
  }

  /**
   * Shuts down this [MuxNetwork] immediately, canceling any running requests.
   * This is not needed for normal operation, but is available if required
   */
  fun shutdown() {
    coroutineScope.cancel("shutdown requested")
  }

}

private fun IDevice.networkDevice() = object : HttpClient.DeviceNetwork {
  override fun isOnline(): Boolean   {
    return networkConnectionType != null
  }
}
