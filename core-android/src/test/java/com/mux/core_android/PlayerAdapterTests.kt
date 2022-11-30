package com.mux.core_android

import android.view.View
import com.mux.core_android.testdoubles.FakePlayerBinding
import com.mux.core_android.testdoubles.mockActivity
import com.mux.core_android.testdoubles.mockView
import com.mux.stats.sdk.muxstats.MuxPlayerAdapter
import com.mux.stats.sdk.muxstats.MuxStateCollector
import com.mux.stats.sdk.muxstats.MuxUiDelegate
import com.mux.stats.sdk.muxstats.muxUiDelegate
import io.mockk.*
import org.junit.Test

class PlayerAdapterTests : AbsRobolectricTest() {

  @Test
  fun testBindOnCreate() {
    val basicBinding = mockk<MuxPlayerAdapter.PlayerBinding<Any>> {
      every { bindPlayer(any(), any()) } just runs
      every { unbindPlayer(any(), any()) } throws AssertionError("unbind shouldn't be called")
    }

    @Suppress("UNUSED_VARIABLE")
    val playerAdapter = playerAdapter(basicBinding)
    // ctors should invoke the bindings
    verify {
      basicBinding.bindPlayer(any(), any())
    }
  }

  @Test
  fun testBindAndUnbind() {
    val basicBinding = mockk<MuxPlayerAdapter.PlayerBinding<Any>> {
      every { bindPlayer(any(), any()) } just runs
      every { unbindPlayer(any(), any()) } just runs
    }
    val playerAdapter = playerAdapter(basicBinding)
    val basicPlayer1 = playerAdapter.basicPlayer
    val basicPlayer2: Any = Object()

    playerAdapter.changeBasicPlayer(basicPlayer2)
    verifySequence {
      basicBinding.bindPlayer(any(), any())
      basicBinding.unbindPlayer(eq(basicPlayer1!!), any())
      basicBinding.bindPlayer(eq(basicPlayer2), any())
    }
  }

  @Test
  fun testUnbindEverything() {
    val basicBinding = mockk<MuxPlayerAdapter.PlayerBinding<Any>> {
      every { bindPlayer(any(), any()) } just runs
      every { unbindPlayer(any(), any()) } just runs
    }
    val playerAdapter = playerAdapter(basicBinding)
    playerAdapter.unbindEverything()

    verify {
      basicBinding.unbindPlayer(any(), any())
    }
  }

  private fun playerAdapter(
    basicMetrics: MuxPlayerAdapter.PlayerBinding<Any> = FakePlayerBinding("basic metrics"),
  ): MuxPlayerAdapter<View, Any> {
    val fakePlayer: Any = Object()
    val mockUiDelegate: MuxUiDelegate<View> =
      mockView().muxUiDelegate()
    val mockCollector = mockStateCollector()

    return MuxPlayerAdapter(
      player = fakePlayer,
      uiDelegate = mockUiDelegate,
      basicMetrics = basicMetrics,
      collector = mockCollector
    )
  }

  private fun mockStateCollector() = mockk<MuxStateCollector>()

}
