package com.mux.core_android

import com.mux.core_android.testdoubles.FakeMuxDevice
import com.mux.stats.sdk.muxstats.MuxStats
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(
  manifest = Config.NONE,
  // Values chosen based on @TargetApi annotations in this lib
  sdk = [16, 23, 30, 32],
  //sdk = [32]
)
@RunWith(RobolectricTestRunner::class)
abstract class AbsRobolectricTest {

  @Before
  fun setUpLogging() {
    // Subclasses may set this to something else as part of setup or testing, this is a good default
    MuxStats.setHostDevice(FakeMuxDevice())
  }
}
