package com.mux.core_android

import com.mux.stats.sdk.muxstats.MuxNetwork
import io.mockk.every
import io.mockk.mockk
import org.junit.Before

class HttpClientTests {
  private lateinit var httpClient: MuxNetwork.HttpClient

  @Before
  fun setUp() {
    httpClient = MuxNetwork.HttpClient(mockk {
      every { networkConnectionType } returns "cellular"
    })
  }

  // TODO: Test Cases
  //  HTTP 500 (retries fail + retries succeed)
  //  Exception (retries fail + retries succeed)
  //  200 OK
  //  Offline (retries fail + retries succeed)
}