package com.mux.core_android.testdoubles

import android.app.Activity
import android.graphics.Point
import android.net.ConnectivityManager
import android.view.View
import com.mux.stats.sdk.muxstats.*
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
   * Mocks an [INetworkRequest] with no mocked methods
   */
  fun mockNetworkRequest() = mockk<INetworkRequest> {}

  /**
   * Mocks a [MuxPlayerAdapter] with (almost) no mocked methods
   */
  fun <Player, ExtraPlayer> mockPlayerAdapter(): MuxPlayerAdapter<View, Player, ExtraPlayer> =
    mockk {
      every { uiDelegate } returns mockView().muxUiDelegate(mockActivity())
    }


  /**
   * Mocks a View of constant size
   */
  fun mockView() = mockk<View> {
    every { width } returns MOCK_PLAYER_WIDTH
    every { height } returns MOCK_PLAYER_HEIGHT
    every { id } returns 1
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

  /**
   * Mocks a [ConnectivityManager] with methods for getting the active network type using modern SDK
   * APIs
   */
  fun mockConnectivityManager23(transportType: Int) = mockk<ConnectivityManager> {
    every { activeNetwork } returns mockk {}
    every { getNetworkCapabilities(any()) } returns mockk {
      every { hasTransport(any()) } answers { firstArg<Int>() == transportType }
    }
  }

  /**
   * Mocks [ConnectivityManager] with methods for getting the active network type using the older
   * [NetworkInfo]-based APIs
   */
  @Suppress("DEPRECATION") // some legacy methods are used for backward compat
  fun mockConnectivityManager16(netType: Int) = mockk<ConnectivityManager> {
    every { activeNetworkInfo } returns mockk {
      every { type } answers { netType }
    }
  }
}
