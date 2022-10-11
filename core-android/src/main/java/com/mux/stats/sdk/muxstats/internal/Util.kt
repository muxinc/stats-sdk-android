package com.mux.stats.sdk.muxstats.internal

@JvmSynthetic
internal fun <T : Any> T.oneOf(vararg these: T) = these.contains(this)

@JvmSynthetic
internal fun <T : Any> T.noneOf(vararg these: T) = !these.contains(this)
