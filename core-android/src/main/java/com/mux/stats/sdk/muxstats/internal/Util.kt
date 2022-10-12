package com.mux.stats.sdk.muxstats.internal

@JvmSynthetic
internal fun <T> T.oneOf(vararg these: T) = these.contains(this)

@JvmSynthetic
internal fun <T> T.noneOf(vararg these: T) = !these.contains(this)
