package com.mux.stats.sdk.muxstats.internal

import java.util.regex.Pattern
import kotlin.math.ceil

@JvmSynthetic
internal fun <Any> Any.oneOf(vararg these: Any) = these.contains(this)

@JvmSynthetic
internal fun <Any> Any.noneOf(vararg these: Any) = !these.contains(this)

@JvmSynthetic
internal fun <Any> allEqual(these: List<Any>): Boolean {
  if (these.isEmpty()) {
    return true
  } else {
    val head = these.first()
    these.slice(1..these.lastIndex).onEach { if (head != it) return false }
    return true
  }
}

@JvmSynthetic
internal fun convertPxToDp(px: Int, displayDensity: Float): Int {
  return ceil((px / displayDensity).toDouble()).toInt()
}

/**
 * Gets a Log Tag from the name of the calling class. Can be used in any package that isn't
 * obfuscated (such as muxstats)
 */
@Suppress("unused") // T is used for its class
internal inline fun <reified T> T.logTag() = T::class.java.simpleName

/**
 * Gets the URI Authority used for POSTing beacons to the backend, provided a domain and env key
 */
internal fun beaconAuthority(envKey: String, domain: String): String {
  return if (Pattern.matches("^[a-z0-9]+$", envKey)) {
    "$envKey$domain"
  } else {
    "img$domain"
  }
}
