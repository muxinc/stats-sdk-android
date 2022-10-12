package com.mux.stats.sdk.muxstats.internal

@JvmSynthetic
internal fun <T> T.oneOf(vararg these: T) = these.contains(this)

@JvmSynthetic
internal fun <T> T.noneOf(vararg these: T) = !these.contains(this)

/**
 * Gets a Log Tag from the name of the calling class. Can be used in any package that isn't
 * obfuscated (such as muxstats)
 */
@Suppress("unused") // T is used for its class
internal inline fun <reified T> T.logTag() = T::class.java.simpleName

