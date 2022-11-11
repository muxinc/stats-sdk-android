package com.mux.core_android

import com.mux.core_android.testdoubles.mockURL
import com.mux.stats.sdk.muxstats.MuxNetwork
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection

class HttpClientTests : AbsRobolectricTest() {
  private lateinit var httpClient: MuxNetwork.HttpClient

  companion object {
    const val TEST_URL = "https://docs.mux.com"
  }

  @Before
  fun setUp() {
    httpClient = MuxNetwork.HttpClient(
      device = mockk {
        every { networkConnectionType } returns "cellular"
      },
      backoffBaseTimeMs = 10
    )
  }

  // --------------------------------------------------

  @Test
  fun testNetErrorsDoesntRecover() = testNetErrors(false)

  @Test
  fun testNetErrorsRecovers() = testNetErrors(true);

  private fun testNetErrors(recovers: Boolean) {
    testNetErrors(recovers, true)
    testNetErrors(recovers, false)
  }

  private fun testNetErrors(recovers: Boolean, failsOnConnect: Boolean) {
    val hurlConn = mockk<HttpURLConnection>(relaxed = true) {
      if (recovers) {
        if (failsOnConnect) {
          every { connect() } throws IOException("connect threw") andThenJust runs
        }
        every { inputStream } throws IOException("stream threw") andThen ByteArrayInputStream(
          ByteArray(0)
        )
      } else {
        if (failsOnConnect) {
          every { connect() } throws IOException("connect threw")
          every { inputStream } returns ByteArrayInputStream(ByteArray(0))
        } else {
          every { connect() } just runs
          every { inputStream } throws IOException("stream threw")
        }
      }
    }

    val request = MuxNetwork.GET(url = mockURL("https://docs.mux.com", hurlConn))
    val result = runInBg { httpClient.doCall(request) }

    if (recovers) {
      assertTrue("Final Result is successful", result.successful)
      assertEquals(
        "Final Result code is OK",
        HttpURLConnection.HTTP_OK,
        result.response?.status?.code
      )
      assertTrue(
        "1 Retries should be made",
        result.retries == 1
      )
      assertNull("No exception should be reported", result.exception)
      assertFalse("Device should have been online for request", result.offlineForCall)
    } else {
      assertFalse("Final Result is not successful", result.successful)
      assertTrue(
        "Final Result was an IOException",
        result.exception is IOException
      )
      if (failsOnConnect) {
        assertEquals(
          "exceptions caught from connect()",
          "connect threw", result.exception?.message
        )
      } else {
        assertEquals(
          "exceptions caught from inputStream()",
          "stream threw", result.exception?.message
        )
      }
      assertTrue(
        "4 Retries should be made",
        result.retries == 4
      )
      assertNull("No HTTP response should be recorded", result.response)
      assertFalse("Device should have been online for request", result.offlineForCall)
    }
  }

  // --------------------------------------------------

  @Test
  fun testServerIssueDoesntRecover() = testServerIssues(false)

  @Test
  fun testServerIssueRecovers() = testServerIssues(true)

  private fun testServerIssues(recovers: Boolean) {
    val hurlConn = mockk<HttpURLConnection>(relaxed = true) {
      every { inputStream } returns ByteArrayInputStream(ByteArray(0))
      every { headerFields } returns mapOf()
      if (recovers) {
        every { responseCode } returnsMany listOf(503, 503, 200)
        every { responseMessage } returnsMany listOf("err", "err", "ok")
      } else {
        every { responseCode } returns HttpURLConnection.HTTP_BAD_GATEWAY
        every { responseMessage } returns "bad gateway"
      }
    }

    val request = MuxNetwork.GET(url = mockURL("https://docs.mux.com", hurlConn))
    val result = runInBg { httpClient.doCall(request) }

    if (recovers) {
      assertTrue("Final Result is successful", result.successful)
      assertEquals(
        "Final Result code is OK",
        HttpURLConnection.HTTP_OK,
        result.response?.status?.code
      )
      assertTrue(
        "2 Retries should be made",
        result.retries == 2
      )
      assertNull("No exception should be reported", result.exception)
      assertFalse("Device should have been online for request", result.offlineForCall)
    } else {
      assertFalse("Final Result is not successful", result.successful)
      assertEquals(
        "Final Result code is 503",
        HttpURLConnection.HTTP_BAD_GATEWAY,
        result.response?.status?.code
      )
      assertTrue(
        "4 Retries should be made",
        result.retries == 4
      )
      assertNull("No exception should be reported", result.exception)
      assertFalse("Device should have been online for request", result.offlineForCall)
    }
  }

  private fun <R> runInBg(block: suspend () -> R): R {
    return runBlocking(Dispatchers.Unconfined) { block() }
  }

  // TODO: Test Cases
  //  HTTP 500 (retries fail + retries succeed)
  //  Exception (retries fail + retries succeed)
  //  200 OK
  //  Offline (retries fail + retries succeed)
}