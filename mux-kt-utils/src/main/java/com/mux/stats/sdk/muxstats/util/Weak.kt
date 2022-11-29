package com.mux.stats.sdk.muxstats.util

import java.lang.ref.WeakReference
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Property Delegate that makes the object referenced by the property weakly-reachable
 * Not thread-safe
 */
@Suppress("unused")
fun <T> weak(t: T?): ReadWriteProperty<Any, T?> = Weak(t)

/**
 * Property Delegate that makes the object referenced by the property weakly-reachable
 * Not thread-safe
 */
@Suppress("unused")
fun <T> weak(): ReadWriteProperty<Any, T?> = Weak(null)

/**
 * Property Delegate where the property's referent is not reachable
 * The implementation is private, but within this module you can use weak(...) to use this class
 *   (this prevents Weak from being instantiated in java with `new Weak$library()`)
 */
private class Weak<T>(referent: T?) : ReadWriteProperty<Any, T?> {
  private var weakT = WeakReference(referent)
  private var onSet: ((T?) -> Unit)? = null

  fun onSet(block: (T?) -> Unit): Weak<T> {
    onSet = block
    return this
  }

  override fun getValue(thisRef: Any, property: KProperty<*>): T? = weakT.get()

  override fun setValue(thisRef: Any, property: KProperty<*>, value: T?) {
    onSet?.invoke(value)
    weakT = WeakReference(value)
  }
}
