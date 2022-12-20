package com.mux.stats.sdk.muxstats.testdoubles

import io.mockk.*
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Mocks [URL], returning the given string as toString and a mocked [HttpURLConnection]
 */
fun mockURL(url: String, conn: HttpURLConnection = mockHttpUrlConnection()): URL = mockk {
  //every { toString() } returns url
  every { openConnection() } returns conn
}

/**
 * Mocks [OutputStream], capturing written data
 */
fun mockOutputStream(byteArraySlot: CapturingSlot<ByteArray> = slot()): OutputStream =
  mockk(relaxed = true) {
    every { write(capture(byteArraySlot)) } just runs
  }

/**
 * Mocks [HttpURLConnection], providing basic response data and input/output streams
 */
fun mockHttpUrlConnection(
  code: Int = 200,
  message: String? = "OK",
  responseHeaders: Map<String, List<String>> = mapOf(),
  input: InputStream = ByteArrayInputStream("hello world".encodeToByteArray()),
  output: OutputStream = mockOutputStream()
): HttpURLConnection =
  mockk(relaxed = true) {
    every { responseCode } returns code
    every { responseMessage } returns message
    every { headerFields } returns responseHeaders
    every { inputStream } returns input
    every { outputStream } returns output
    every { setRequestProperty(any(), any()) } just runs
  }
