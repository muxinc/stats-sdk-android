package com.mux.core_android.testdoubles

import com.mux.core_android.log
import com.mux.stats.sdk.muxstats.IDevice
import com.mux.stats.sdk.muxstats.LogPriority

/**
 * Fake IDevice that returns some dummy values, and logs to stdout so we see it on the text console
 */
class FakeMuxDevice : IDevice {

  override fun getHardwareArchitecture(): String = "a-test-jvm"
  override fun getOSFamily(): String = "java"
  override fun getMuxOSFamily(): String = "java(overridden)"
  override fun getOSVersion(): String = "1.2.3"
  override fun getMuxOSVersion(): String = "1.2.3(overridden)"
  override fun getManufacturer(): String = "Oracle-or-maybe-amazon-or-maybe-the-openjdk-people"
  override fun getMuxManufacturer(): String =
    "oracle-or-maybe-amazon-or-maybe-the-openjdk-people(overridden)"
  override fun getModelName(): String = "the-jvm"
  override fun getMuxModelName(): String = "the-jvm(overridden)"
  override fun getPlayerVersion(): String = "4.5.6"
  override fun getDeviceId(): String = "device-id-uniquely-generated"
  override fun getAppName(): String = "the-unit-tests"
  override fun getAppVersion(): String = "7.8.9"
  override fun getPluginName(): String = "fake-mux-sdk"
  override fun getPluginVersion(): String = "10.11.12"
  override fun getPlayerSoftware(): String = "13.14.15"
  override fun getNetworkConnectionType(): String = "cellular"
  override fun getElapsedRealtime(): Long = 1000L

  override fun outputLog(priority: LogPriority?, tag: String?, msg: String?) {
    log(tag ?: "\t", msg ?: "null", null)
  }

  override fun outputLog(tag: String?, msg: String?) {
    log(tag ?: "\t", msg ?: "null", null)
  }
}
