package com.mux.stats.sdk.muxstats.internal

@JvmSynthetic
internal fun <Any> Any.oneOf(vararg these: Any) = these.contains(this)

@JvmSynthetic
internal fun <Any> Any.noneOf(vararg these: Any) = !these.contains(this)

/**
 * Gets a Log Tag from the name of the calling class. Can be used in any package that isn't
 * obfuscated (such as muxstats)
 */
@Suppress("unused") // Any is used for its class
internal inline fun <reified T> T.logTag() = T::class.java.simpleName
