package com.mux.core_android

fun assertContentEquals(message: String = "", expected: ByteArray, actual: ByteArray?) {
  if (!expected.contentEquals(actual)) {
    throw AssertionError(message)
  }
}