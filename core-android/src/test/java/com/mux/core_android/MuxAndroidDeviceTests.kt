package com.mux.core_android

import android.content.Context
import android.view.View
import com.mux.core_android.testdoubles.FakeMuxDataSdk
import com.mux.core_android.testdoubles.FakeNetwork
import com.mux.core_android.testdoubles.UiDelegateMocks.mockActivity
import com.mux.core_android.testdoubles.UiDelegateMocks.mockPlayerAdapter
import com.mux.core_android.testdoubles.UiDelegateMocks.mockPlayerListener
import com.mux.stats.sdk.core.CustomOptions
import com.mux.stats.sdk.core.model.CustomerData
import com.mux.stats.sdk.muxstats.IDevice
import com.mux.stats.sdk.muxstats.INetworkRequest
import com.mux.stats.sdk.muxstats.IPlayerListener
import com.mux.stats.sdk.muxstats.MuxPlayerAdapter
import org.junit.Before
import org.junit.Test

class MuxAndroidDeviceTests {
  private lateinit var fakeSdk: FakeMuxDataSdk<*, *, *>

  @Before
  fun setUp() {
    fakeSdk = FakeMuxSdkExt<Any, Any>()
  }

  @Test
  fun testConnectionTypeIsCellular() {

  }
}

/**
 * Provides interaction with the object under test, which is nested w/protected access for the sake
 * of keeping it out of the customers' namespace
 */
private class FakeMuxSdkExt<P, EP>(
  context: Context = mockActivity(),
  envKey: String = "fake-env-key",
  customerData: CustomerData = CustomerData(),
  customOptions: CustomOptions? = null,
  playerAdapter: MuxPlayerAdapter<View, P, EP> = mockPlayerAdapter(),
  playerListener: IPlayerListener = mockPlayerListener(),
  val device: IDevice = MuxAndroidDevice(
    ctx = mockActivity(),
    playerVersion = "playerVer",
    muxPluginName = "unitTests",
    muxPluginVersion = "pluginVer",
    playerSoftware = "playerSoftware"
  ), // no default: This is the object under test
  network: INetworkRequest = FakeNetwork(),
  verboseLogging: Boolean = false
) : FakeMuxDataSdk<P, EP, View>(
  context,
  envKey,
  customerData,
  customOptions,
  playerAdapter,
  playerListener,
  device,
  network,
  verboseLogging
) {

  fun networkConnectionType() = device.networkConnectionType
}
