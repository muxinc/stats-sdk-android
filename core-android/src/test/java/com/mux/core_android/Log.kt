package com.mux.core_android

import java.lang.Exception

fun log(tag: String = "\t", message: String, ex: Throwable? = null) {
  println("$tag :: $message")
  ex?.let {
    print(it)
    println()
  }
}

