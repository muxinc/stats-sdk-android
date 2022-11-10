package com.mux.stats.sdk.muxstats.internal

import com.mux.stats.sdk.muxstats.INetworkRequest
import org.json.JSONObject
import java.net.URL
import java.util.*

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
}