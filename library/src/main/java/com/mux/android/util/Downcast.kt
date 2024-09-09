package com.mux.android.util

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty

/**
 * Property Delegate that down-casts the value of another property. Another Property must be
 * supplied to back and downcast from
 * The Property must be nullable.
 *
 * Example usage:
 *  // where aView is a View or other superclass of ExoPlayerView:
 *  var exoPlayerView: ExoPlayerView by downcast<View, ExoPlayerView>(anObject::aView)
 *
 * @param Upper The type of the field being casted from. Has to be a superclass of [Lower]
 * @param Lower The type that should be casted to. Has to be a subclass of [Upper]
 */
@Suppress("unused")
fun <Upper, Lower : Upper> downcast(delegatedProperty: KMutableProperty0<Upper?>)
        : ReadWriteProperty<Any, Lower?> = Downcast(delegatedProperty)

/**
 * Property Delegate that down-casts a value from a delegated property
 * The Parameters are the upper & lower bounds
 * Access this class via the helper functions below
 */
private class Downcast<Upper, Lower : Upper>(var t: KMutableProperty0<Upper?>) :
  ReadWriteProperty<Any, Lower?> {

  override fun getValue(thisRef: Any, property: KProperty<*>): Lower? {
    @Suppress("UNCHECKED_CAST") // Safety guaranteed by type bounds
    return t.get() as? Lower
  }

  override fun setValue(thisRef: Any, property: KProperty<*>, value: Lower?) {
    t.set(value)
  }
}
