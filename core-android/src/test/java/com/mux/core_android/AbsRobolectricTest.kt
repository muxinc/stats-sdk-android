package com.mux.core_android

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(
  manifest = Config.NONE,
  // Values chosen based on @TargetApi annotations in this lib
  sdk = [16, 23, 30, 32],
)
@RunWith(RobolectricTestRunner::class)
abstract class AbsRobolectricTest
