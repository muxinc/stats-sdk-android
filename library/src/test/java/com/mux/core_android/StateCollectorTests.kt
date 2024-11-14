package com.mux.core_android

import com.mux.core_android.test.tools.AbsRobolectricTest
import com.mux.core_android.test.tools.testdoubles.FakeEventDispatcher
import com.mux.stats.sdk.core.events.EventBus
import com.mux.stats.sdk.core.events.IEvent
import com.mux.stats.sdk.core.events.playback.*
import com.mux.stats.sdk.muxstats.MuxPlayerState
import com.mux.stats.sdk.muxstats.MuxStateCollector
import com.mux.stats.sdk.muxstats.MuxStats
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test

class StateCollectorTests : AbsRobolectricTest() {

  private lateinit var stateCollector: MuxStateCollector
  private lateinit var eventDispatcher: FakeEventDispatcher

  @Before
  fun setUpCollector() {
    eventDispatcher = FakeEventDispatcher()
    val stats = mockk<MuxStats>(relaxed = true)
    val eventBus = mockk<EventBus>(relaxed = true) {
      val slot = slot<IEvent>()
      every { dispatch(capture(slot)) } answers { eventDispatcher.dispatch(slot.captured) }
    }
    stateCollector = MuxStateCollector(stats, eventBus)
  }

  @Test
  fun testBuffering() {
    stateCollector.buffering()
    stateCollector.buffering() // The buffering state is re-entrant

    assertEquals(
      "init -> buffering => buffering",
      MuxPlayerState.BUFFERING,
      stateCollector.muxPlayerState,
    )
    eventDispatcher.assertHasExactlyThese(listOf(TimeUpdateEvent(null)))
  }

  @Test
  fun testRebuffering() {
    stateCollector.playing() // initial condition: play then buffering

    stateCollector.buffering() // Buffering events after play are Rebuffering
    stateCollector.buffering() // Rebuffering/buffering state is re-entrant
    assertEquals(
      "playing -> buffering => rebuffering",
      MuxPlayerState.REBUFFERING,
      stateCollector.muxPlayerState,
    )
    eventDispatcher.assertHasOneOf(RebufferStartEvent(null))

    stateCollector.playing()
    eventDispatcher.assertHasOneOf(RebufferEndEvent(null))
  }

  @Test
  fun testPlay() {
    // Initial case: play() from any state
    stateCollector.play()
    assertEquals(
      "init -> play => play",
      MuxPlayerState.PLAY,
      stateCollector.muxPlayerState,
    )
    eventDispatcher.assertHasOneOf(PlayEvent(null))
  }

  @Test
  fun testFirstPlayWhileSeeking() {
    // The first play() while seeking should be recorded
    stateCollector.seeking()
    stateCollector.play()
    assertEquals(
      "the first play event should always be recorded",
      MuxPlayerState.PLAY,
      stateCollector.muxPlayerState,
    )
    eventDispatcher.assertHasOneOf(PlayEvent(null))
  }

  @Test
  fun testFirstPlayWhileSeeked() {
    // The first play() while seeked should be recorded
    stateCollector.seeking()
    stateCollector.seeked()
    stateCollector.play()
    assertEquals(
      "the first play event should always be recorded",
      MuxPlayerState.PLAY,
      stateCollector.muxPlayerState,
    )
    eventDispatcher.assertHasOneOf(PlayEvent(null))
  }

  @Test
  fun testPlayWhileRebuffering() {
    // subsequent play() while rebuffering should be ignored
    stateCollector.play()
    stateCollector.playing()
    stateCollector.buffering()
    stateCollector.play()
    assertNotEquals(
      "play -> buffering -> play => buffering",
      stateCollector.muxPlayerState,
      MuxPlayerState.PLAY,
    )
    eventDispatcher.assertHasOneOf(PlayEvent(null))
  }

  @Test
  fun testPlayWhileSeeked() {
    // subsequent play() while seeked should be ignored
    stateCollector.play()
    stateCollector.seeking()
    stateCollector.seeked()
    stateCollector.play()
    assertNotEquals(
      "play -> buffering -> play => buffering",
      stateCollector.muxPlayerState,
      MuxPlayerState.PLAY
    )
    eventDispatcher.assertHasOneOf(PlayEvent(null)) // Only the first play() is counted
  }

  @Test
  fun testPlayWhileSeeking() {
    // subsequent play() while seeking should be ignored
    stateCollector.play()
    stateCollector.seeking()
    stateCollector.play()
    assertNotEquals(
      "play -> buffering -> play => buffering",
      MuxPlayerState.PLAY,
      stateCollector.muxPlayerState,
    )
    eventDispatcher.assertHasOneOf(PlayEvent(null))
  }

  @Test
  fun testPlayingWhileSeeking() {
    stateCollector.play() // Seek events are ignored before play()
    stateCollector.seeking()
    // playing() ignored if seeking.
    stateCollector.playing()
    assertNotEquals(
      "seeking -> playing => seeking",
      MuxPlayerState.PLAYING,
      stateCollector.muxPlayerState,
    )
    eventDispatcher.assertHasNoneOf(PlayingEvent(null))
  }

  @Test
  fun testPlayingWhilePaused() {
    // if paused, should go through PLAY and into PLAYING
    stateCollector.pause()
    stateCollector.playing()
    assertEquals(
      "pause -> playing() => play -> playing",
      MuxPlayerState.PLAYING,
      stateCollector.muxPlayerState,
    )
    eventDispatcher.assertHasOneOf(PlayEvent(null))
    eventDispatcher.assertHasOneOf(PlayingEvent(null))
  }

  @Test
  fun testPlayingWhileRebuffering() {
    // if rebuffering, should signal rebuffering is ended and then go to PLAYING
    stateCollector.play()
    stateCollector.playing()
    stateCollector.buffering()
    stateCollector.playing()
    assertEquals(
      "play -> playing -> buffering -> playing => playing",
      MuxPlayerState.PLAYING,
      stateCollector.muxPlayerState,
    )
    eventDispatcher.assertHasOneOf(RebufferEndEvent(null))
    // should be 2, but only the second playing() call is under test
    eventDispatcher.assertHasOneOrMoreOf(PlayingEvent(null))
  }

  @Test
  fun testFirstPauseWhileSeeked() {
    // if seeked, and this is the first pause event, process as paused
    stateCollector.seeking()
    stateCollector.seeked()
    stateCollector.pause()
    assertEquals(
      "seeked -> pause => pause (if 1st pause)",
      MuxPlayerState.PAUSED,
      stateCollector.muxPlayerState,
    )
    eventDispatcher.assertHasOneOf(PauseEvent(null))
  }

  @Test
  fun testPauseWhileSeeked() {
    // SEEKED is the state of being paused after seeking, so ignore pause() in this state unless it's the 1st
    stateCollector.pause()
    stateCollector.play()
    stateCollector.seeking()
    stateCollector.seeked()
    stateCollector.pause()
    assertEquals(
      "seeking -> seeked -> pause => pause (if 1st pause)",
      MuxPlayerState.SEEKED,
      stateCollector.muxPlayerState,
    )
    // The 1 pause event comes from the first pause(). The second pause() should be ignored
    eventDispatcher.assertHasOneOf(PauseEvent(null))
  }

  @Test
  fun testPauseDuringRebuffering() {
    // if pause comes during rebuffering, then rebuffering is over
    stateCollector.playing()
    stateCollector.buffering()
    stateCollector.pause()
    assertEquals(
      "rebuffering -> pause => pause",
      MuxPlayerState.PAUSED,
      stateCollector.muxPlayerState,
    )
    eventDispatcher.assertHasOneOf(RebufferEndEvent(null))
    eventDispatcher.assertHasOneOf(PauseEvent(null))
  }

  @Test
  fun testPauseDuringSeeking() {
    // if pause comes during seeking, we are in SEEKED
    stateCollector.play() //seeks before play are ignored
    stateCollector.seeking()
    stateCollector.pause()
    assertEquals(
      "seeking -> pause => seeked",
      MuxPlayerState.SEEKED,
      stateCollector.muxPlayerState,
    )
    eventDispatcher.assertHasOneOf(SeekedEvent(null))
  }

  @Test
  fun testSeekedWhileNotSeeking() {
    // if not seeking, seeked should be skipped
    stateCollector.seeked()
    stateCollector.seeked()
    assertNotEquals(
      "seeked while not seeking should be ignored",
      MuxPlayerState.SEEKED,
      stateCollector.muxPlayerState,
    )
    eventDispatcher.assertHasNoneOf(SeekedEvent(null))
  }

  @Test
  fun testSeekedWhileSeeking() {
    // If seeking and not inferring state info, just go to seeked
    stateCollector.play() // Seek events are ignored before play()
    stateCollector.seeking()
    stateCollector.seeked()
    assertEquals(
      "seeking -> seeked() => seeked",
      MuxPlayerState.SEEKED,
      stateCollector.muxPlayerState,
    )
    eventDispatcher.assertHasNoneOf(PlayingEvent(null))
    eventDispatcher.assertHasOneOf(SeekedEvent(null))
  }

  @Test
  fun testSeekOutOfAd() {
    stateCollector.play()
    stateCollector.playing()
    stateCollector.playingAds()
    stateCollector.seeking()
    stateCollector.finishedPlayingAds()

    eventDispatcher.assertHasOneOf(SeekedEvent(null))
    assertEquals(
      "plaingAds() -> seeking() -> finishedPlayingAds() => seeked",
      MuxPlayerState.SEEKED,
      stateCollector.muxPlayerState
    )
  }

  @Test
  fun testIncrementDroppedFrames() {
    val incrementSlot = slot<Long>()
    val mockMuxStats = mockk<MuxStats> {
      // MuxStats has its own test for this so we can just mock it here
      every { setDroppedFramesCount(capture(incrementSlot)) } just Runs
    }
    stateCollector = MuxStateCollector(
      muxStats = mockMuxStats,
      dispatcher = FakeEventDispatcher(),
    )

    val randomFrames1 = (0..100).random()
    val randomFrames2 = (0..100).random()
    stateCollector.incrementDroppedFrames(randomFrames1)
    stateCollector.incrementDroppedFrames(randomFrames2)

    verify {
      mockMuxStats.setDroppedFramesCount(randomFrames1.toLong())
      mockMuxStats.setDroppedFramesCount(randomFrames2 + randomFrames1.toLong())
    }
    
    stateCollector.resetState()
    verify {
      mockMuxStats.setDroppedFramesCount(0)
    }
  }

  @Test
  fun testSetDroppedFrames() {
    val incrementSlot = slot<Long>()
    val mockMuxStats = mockk<MuxStats> {
      // MuxStats has its own test for this so we can just mock it here
      every { setDroppedFramesCount(capture(incrementSlot)) } just Runs
    }
    stateCollector = MuxStateCollector(
      muxStats = mockMuxStats,
      dispatcher = FakeEventDispatcher(),
    )

    val randomFrames1 = (0..100).random()
    val randomFrames2 = (0..100).random()
    stateCollector.droppedFrames = randomFrames1
    stateCollector.droppedFrames = randomFrames2

    verify {
      mockMuxStats.setDroppedFramesCount(randomFrames1.toLong())
      mockMuxStats.setDroppedFramesCount(randomFrames2.toLong())
    }

    stateCollector.resetState()
    verify {
      mockMuxStats.setDroppedFramesCount(0)
    }
  }
}
