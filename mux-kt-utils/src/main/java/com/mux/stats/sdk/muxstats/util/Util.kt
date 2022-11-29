package com.mux.stats.sdk.muxstats.util

import kotlin.math.ceil

fun <Any> Any.oneOf(vararg these: Any) = these.contains(this)

fun <Any> Any.noneOf(vararg these: Any) = !these.contains(this)

fun <Any> allEqual(these: List<Any>): Boolean {
  if (these.isEmpty()) {
    return true
  } else {
    val head = these.first()
    these.slice(1..these.lastIndex).onEach { if (head != it) return false }
    return true
  }
}

fun convertPxToDp(px: Int, displayDensity: Float): Int {
  return ceil((px / displayDensity).toDouble()).toInt()
}

/**
 * Gets a Log Tag from the name of the calling class. Can be used in any package that isn't
 * obfuscated (such as muxstats)
 */
@Suppress("unused") // T is used for its class
inline fun <reified T> T.logTag() = T::class.java.simpleName
