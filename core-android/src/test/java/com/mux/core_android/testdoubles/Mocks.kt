package com.mux.core_android.testdoubles

import android.app.Activity
import android.graphics.Point
import android.view.View
import com.mux.stats.sdk.muxstats.IDevice
import com.mux.stats.sdk.muxstats.INetworkRequest
import com.mux.stats.sdk.muxstats.IPlayerListener
import io.mockk.every
import io.mockk.mockk

object UiDelegateMocks {

  const val MOCK_SCREEN_WIDTH = 2400
  const val MOCK_SCREEN_HEIGHT = 1080
  const val MOCK_INSET_X = 100
  const val MOCK_INSET_Y = 100

  const val MOCK_PLAYER_WIDTH = 1080
  const val MOCK_PLAYER_HEIGHT = 700

  /**
   * Mocks an [IPlayerListener], with no mocked methods
   */
  fun mockPlayerListener() = mockk<IPlayerListener> {}

  /**
   * Mocks an [IDevice] with no mocked methods
   */
  fun mockDevice() = mockk<IDevice> {}

  /**
   *
   */
  fun mockNetworkRequest() = mockk<INetworkRequest> {}

  /**
   * Mocks a View of constant size
   */
  fun mockView() = mockk<View> {
    every { width } returns MOCK_PLAYER_WIDTH
    every { height } returns MOCK_PLAYER_HEIGHT
  }

  /**
   * Mocks the path we call to get the size of the screen
   */
  @Suppress("DEPRECATION") // Backward-compatible APIs are mocked intentionally
  fun mockActivity() = mockk<Activity> {
    every { windowManager } returns mockk {
      every { defaultDisplay } returns mockk {
        every { getSize(Point()) } answers {
          arg<Point>(0).apply {
            x = MOCK_SCREEN_WIDTH
            y = MOCK_SCREEN_HEIGHT
          }
        }
      }
    }
  }
}
