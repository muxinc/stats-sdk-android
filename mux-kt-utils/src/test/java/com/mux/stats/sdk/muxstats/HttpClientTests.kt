package com.mux.stats.sdk.muxstats

import com.mux.android.http.*
import com.mux.stats.sdk.muxstats.testdoubles.mockHttpUrlConnection
import com.mux.stats.sdk.muxstats.testdoubles.mockOutputStream
import com.mux.stats.sdk.muxstats.testdoubles.mockURL
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.UnknownHostException
import javax.net.ssl.HttpsURLConnection

class HttpClientTests : AbsRobolectricTest() {
  private lateinit var httpClient: HttpClient

  companion object {
    const val TEST_URL = "https://docs.mux.com"
  }

  @Before
  fun setUp() {
    httpClient = HttpClient(mockk { every { isOnline() } returns true }, 5)
  }

  @Test
  fun testGzip() {
    // apparently gzip inflates really small sets of data, so make a big set
    val inputBytes =
      "Hello I am a string that is probably compressible".repeat(10 * 1024).toByteArray()
    val outputBytes = inputBytes.gzip()
    assertTrue("gzipped data is smaller", inputBytes.size > outputBytes.size)
  }

  @Test
  fun testUnGzip() {
    // apparently gzip inflates really small sets of data, so make a big set
    val inputBytes =
      "Hello I am a string that is probably compressible".repeat(10 * 1024).toByteArray()
    val outputBytes = inputBytes.gzip().unGzip()
    assertTrue("Unzipped data is the same", inputBytes.contentEquals(outputBytes))
  }

  @Test
  fun testClientGzips() {
    val originalData =
      "Hello I am a string that is probably compressible".repeat(10 * 1024).toByteArray()
    val gzippedData = originalData.gzip()

    val requestBodySlot = slot<ByteArray>()
    val request = POST(
      url = mockURL(TEST_URL, mockHttpUrlConnection(output = mockOutputStream(requestBodySlot))),
      headers = mapOf("Content-Encoding" to listOf("gzip")),
      body = originalData
    )
    val result = runInBg { httpClient.call(request) }
    assertNull("no exception thrown by encoding/zip", result.exception)
    assertTrue("data was written to the outputStream", requestBodySlot.isCaptured)
    assertContentEquals(
      "HttpClient should obey Content-Encoding = gzip",
      gzippedData,
      requestBodySlot.captured
    )
  }

  @Test
  fun testSuccessfulRequest() {
    val hurlConn = mockk<HttpURLConnection>(relaxed = true) {
      every { inputStream } returns ByteArrayInputStream(ByteArray(0))
      every { headerFields } returns mapOf()
      every { responseCode } returns HttpURLConnection.HTTP_OK
      every { responseMessage } returns "OK"
    }

    val request = GET(mockURL("https://docs.mux.com", hurlConn))
    val result = runBlocking { httpClient.call(request) }

    assertTrue("Result is successful", result.successful)
    assertEquals(
      "Result success code is 200",
      HttpsURLConnection.HTTP_OK,
      result.response?.status?.code
    )
    assertNull("No Exception recorded", result.exception)
    assertFalse("Reported online", result.offlineForCall)
    assertEquals("No retries", 0, result.retries)
  }

  // --------------------------------------------------

  @Test
  fun testOfflineRecovers() = testOffline(true)

  @Test
  fun testOfflineDoesntRecover() = testOffline(false)

  private fun testOffline(recovers: Boolean) {
    val hurlConn = mockk<HttpURLConnection>(relaxed = true) {
      if (recovers) {
        every { connect() } just runs
        every { inputStream } returns ByteArrayInputStream(ByteArray(0))
        every { responseCode } returns HttpURLConnection.HTTP_OK
      } else {
        every { connect() } just runs
        every { inputStream } throws UnknownHostException("stream threw")
      }
    }
    val offlineClient = HttpClient(
      mockk {
        if (recovers) {
          every { isOnline() } returns false andThen true
        } else {
          every { isOnline() } returns false
        }
      },
      backoffBaseTimeMs = 5
    )

    val request = GET(url = mockURL("https://docs.mux.com", hurlConn))
    val result = runInBg { offlineClient.call(request) }

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
        "Final Result was offline",
        result.offlineForCall
      )
      assertTrue(
        "4 Retries should be made",
        result.retries == 4
      )
      assertNull("No HTTP response should be recorded", result.response)
      assertNull("No Exception recorded for request", result.exception)
    }
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
          every { inputStream } returns ByteArrayInputStream(ByteArray(0))
        } else {
          every { connect() } just runs
          every { inputStream } throws IOException("stream threw") andThen ByteArrayInputStream(
            ByteArray(0)
          )
        }
        every { responseCode } returns HttpURLConnection.HTTP_OK
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

    val request = GET(url = mockURL("https://docs.mux.com", hurlConn))
    val result = runInBg { httpClient.call(request) }

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

    val request = GET(url = mockURL("https://docs.mux.com", hurlConn))
    val result = runInBg { httpClient.call(request) }

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

  private fun assertContentEquals(message: String = "", expected: ByteArray, actual: ByteArray?) {
    if (!expected.contentEquals(actual)) {
      throw AssertionError(message)
    }
  }
}
