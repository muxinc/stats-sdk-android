package com.mux.core_android.testdoubles

import android.content.Context
import android.view.View
import com.mux.stats.sdk.core.CustomOptions
import com.mux.stats.sdk.core.model.CustomerData
import com.mux.stats.sdk.muxstats.*

/**
 * A Fake Mux Data SDK facade that can accept injected objects under test if required. All of the
 * ctor params have default values suitable as objects that are not under test, provided the test
 * doesn't require any special mocked behavior from them
 */
open class FakeMuxDataSdk<Player, ExtraPlayer, V : View>(
  context: Context = mockActivity(),
  envKey: String = "fake-env-key",
  customerData: CustomerData = CustomerData(),
  customOptions: CustomOptions? = null,
  playerAdapter: MuxPlayerAdapter<View, Player, ExtraPlayer> = mockPlayerAdapter(),
  playerListener: IPlayerListener = mockPlayerListener(),
  device: IDevice = FakeMuxDevice(),
  network: INetworkRequest = FakeNetwork(),
  verboseLogging: Boolean = false
) : MuxDataSdk<Player, ExtraPlayer, View>(
  context,
  envKey,
  customerData,
  customOptions,
  playerAdapter,
  playerListener,
  device,
  network,
  if (verboseLogging) LogcatLevel.VERBOSE else LogcatLevel.DEBUG
)