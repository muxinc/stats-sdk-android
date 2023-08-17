package com.mux.stats.sdk.muxstats

import com.mux.android.util.logTag
import com.mux.android.util.noneOf
import com.mux.android.util.oneOf
import com.mux.android.util.weak
import com.mux.stats.sdk.core.events.IEvent
import com.mux.stats.sdk.core.events.IEventDispatcher
import com.mux.stats.sdk.core.events.InternalErrorEvent
import com.mux.stats.sdk.core.events.playback.*
import com.mux.stats.sdk.core.model.BandwidthMetricData
import com.mux.stats.sdk.core.model.CustomerVideoData
import com.mux.stats.sdk.core.model.SessionTag
import com.mux.stats.sdk.core.util.MuxLogger
import kotlinx.coroutines.*
import java.util.*
import kotlin.properties.Delegates

/**
 * Collects events from a player and delivers them into a MuxStats instance.
 * As a player's state model may differ from that used by Mux Data products, the state as understood
 * by Mux Data is tracked here.
 *
 * SDK creators should supply one of these to [MuxPlayerAdapter], and call is methods from your
 * [MuxPlayerAdapter.PlayerBinding] when the player state changes
 */
open class MuxStateCollector(
  val muxStats: MuxStats,
  val dispatcher: IEventDispatcher,
  private val trackFirstFrameRendered: Boolean = true,
) {

  companion object {
    @Suppress("unused")
    const val TIME_UNKNOWN = -1L

    @Suppress("unused")
    const val ERROR_UNKNOWN = -1

    @Suppress("unused")
    const val ERROR_DRM = -2

    @Suppress("unused")
    const val ERROR_IO = -3

    // For firstFrameRenderedAtMilliseconds, indicates that the first video frame hasn't rendered
    private const val FIRST_FRAME_NOT_RENDERED: Long = -1

    // Wait this long after the first frame was rendered before logic considers it rendered
    private const val FIRST_FRAME_WAIT_MILLIS = 50L
  }

  /**
   * This is the time to wait in ms that needs to pass after the player has seeked in
   * order for us to conclude that playback has actually started. This is to ensure that callback
   * ordering doesn't cause state issues during sometimes-chaotic player startup
   * */
  @Suppress("unused")
  var timeToWaitAfterFirstFrameReceived: Long = 50

  /**
   * The current state of the player, as represented by Mux Data. Only mutable from the inside
   */
  @Suppress("MemberVisibilityCanBePrivate")
  val muxPlayerState by ::_playerState
  private var _playerState = MuxPlayerState.INIT

  /** Event counter. This is useful to know when the view have started.  */
  @Suppress("unused")
  var detectMimeType = true

  /**
   * Detected MIME type of the playing media, if applicable
   */
  @Suppress("MemberVisibilityCanBePrivate")
  var mimeType: String? = null

  /**
   * True if the media being played has a video stream, false if not
   * This is used to decide how to handle position discontinuities for audio-only streams
   * The default value is true, which might be fine to keep, depending on your player
   */
  @Suppress("MemberVisibilityCanBePrivate")
  var mediaHasVideoTrack: Boolean? = true

  /**
   * Total duration of the media being played, in milliseconds
   */
  @Suppress("unused")
  var sourceDurationMs: Long = TIME_UNKNOWN

  /**
   * The current playback position of the player
   */
  var playbackPositionMills: Long = TIME_UNKNOWN

  /**
   * The media bitrate advertised by the current media item
   */
  @Suppress("MemberVisibilityCanBePrivate")
  var sourceAdvertisedBitrate: Int = 0

  /**
   * The frame rate advertised by the current media item
   */
  @Suppress("MemberVisibilityCanBePrivate")
  var sourceAdvertisedFrameRate: Float = 0F

  /**
   * Width of the current media item in pixels. 0 for non-video media
   */
  @Suppress("MemberVisibilityCanBePrivate")
  var sourceWidth: Int = 0

  /**
   * Width of the current media item in pixels. 0 for non-video media
   */
  @Suppress("MemberVisibilityCanBePrivate")
  var sourceHeight: Int = 0

  /**
   * The number of frames dropped during this view, or 0 if not tracked
   */
  @Suppress("MemberVisibilityCanBePrivate")
  var droppedFrames = 0

  /**
   * The list of renditions currently available as part of an HLS, DASH, etc stream
   */
  @Suppress("MemberVisibilityCanBePrivate")
  var renditionList: List<BandwidthMetricData.Rendition>? = null

  /**
   * For HLS live streams, the PROGRAM-DATE-TIME or an approximation
   */
  @Suppress("MemberVisibilityCanBePrivate")
  val hlsPlayerProgramTime: Long?
    get() {
      return hlsManifestNewestTime?.let { it + playbackPositionMills }
    }

  /**
   * For HLS streams, the newest timestamp received (for live, this ~= the time we started watching)
   */
  @Suppress("MemberVisibilityCanBePrivate")
  var hlsManifestNewestTime: Long? = null

  /**
   * For HLS live streams, the value of the HOLD-BACK tag
   */
  @Suppress("MemberVisibilityCanBePrivate")
  var hlsHoldBack: Long? = null

  /**
   * For HLS live streams, the value of the PART-HOLD-BACK tag
   */
  @Suppress("MemberVisibilityCanBePrivate")
  var hlsPartHoldBack: Long? = null

  /**
   * For HLS live streams, the value of the PART-TARGET tag
   */
  @Suppress("MemberVisibilityCanBePrivate")
  var hlsPartTargetDuration: Long? = null

  /**
   * For HLS live streams, the value of the EXT-X-TARGETDURATION tag
   */
  @Suppress("MemberVisibilityCanBePrivate")
  var hlsTargetDuration: Long? = null

  /**
   * An asynchronous watcher for playback position. It waits for the given update interval, and
   * then sets the [playbackPositionMills] property on this object. It can be stopped by
   * calling [PlayerWatcher.stop], and will automatically stop if it can no longer
   * access play state info
   */
  @Suppress("MemberVisibilityCanBePrivate")
  var playerWatcher: PlayerWatcher<*>?
          by Delegates.observable(null) @Synchronized { _, old, _ ->
            old?.stop("watcher replaced")
          }

  private var sessionTags: List<SessionTag> = Collections.emptyList()

  private var firstFrameRenderedAtMillis = FIRST_FRAME_NOT_RENDERED // Based on system time
  private var seekingInProgress = false // TODO: em - We have a SEEKING state so why do we have this
  private var firstFrameReceived = false

  private var pauseEventsSent = 0
  private var playEventsSent = 0
  private var totalEventsSent = 0
  private var seekingEventsSent = 0
  private var seekedEventsSent = 0

  private var dead = false

  /**
   *  List of string patterns that will be used to determine if certain HTTP header will be
   *  reported to the backend.
   *  */
  @Suppress("MemberVisibilityCanBePrivate")
  var allowedHeaders = ArrayList<String>()

  /**
   * Call when the player starts buffering. Buffering events after the player began playing are
   * reported as rebuffering events
   */
  @Suppress("unused")
  fun buffering() {
    // Only process buffering if we are not already buffering or seeking
    if (_playerState.noneOf(
        MuxPlayerState.BUFFERING,
        MuxPlayerState.REBUFFERING,
        MuxPlayerState.SEEKED
      ) && !seekingInProgress
    ) {
      if (_playerState == MuxPlayerState.PLAYING) {
        // If we were playing then the player buffers, that's re-buffering instead
        //  Buffering can also happen because of seeking
        rebufferingStarted()
      } else {
        _playerState = MuxPlayerState.BUFFERING
        dispatch(TimeUpdateEvent(null))
      }
    }
  }

  /**
   * Call when the player prepares to play. That is, during the initialization and buffering, while
   * the caller intends for the video to play
   */
  @Suppress("unused")
  fun play() {
    if (playEventsSent <= 0
      || (!seekingInProgress
              && _playerState.noneOf(MuxPlayerState.REBUFFERING, MuxPlayerState.SEEKED))
    ) {
      _playerState = MuxPlayerState.PLAY
      dispatch(PlayEvent(null))
    }
  }

  /**
   * Call when the player begins playing. Note that this state is distinct from [.play],
   * which is the state of preparing with intent to play
   * If seeking is in progress, this is ignored, when on seeked,state transitions to either playing,
   * or seeked from there
   * If rebuffering was in progress,
   */
  @Suppress("unused")
  fun playing() {
    // Negative Logic Version
    if (seekingInProgress) {
      // We will dispatch playing event after seeked event
      MuxLogger.d("MuxStats", "Ignoring playing event, seeking in progress !!!")
      return
    }
    if (_playerState.oneOf(MuxPlayerState.PAUSED, MuxPlayerState.FINISHED_PLAYING_ADS)) {
      play()
    } else if (_playerState == MuxPlayerState.REBUFFERING) {
      rebufferingEnded()
    } else if (_playerState == MuxPlayerState.PLAYING) {
      // No need to re-enter the playing state
      return
    }

    _playerState = MuxPlayerState.PLAYING
    dispatch(PlayingEvent(null))
  }

  /**
   * Call when the player becomes paused.
   * If the player was rebuffering, then then an event will be sent to report that
   * If we were seeking, and the player becomes paused, the callers requested a pause during sync,
   *  which is already reported. Instead, we will move to the SEEKED state
   * Otherwise, we move to the PAUSED state and send a PauseEvent
   */
  @Suppress("unused")
  fun pause() {
    // Process unless we're already paused
    if (_playerState == MuxPlayerState.SEEKED && pauseEventsSent > 0) {
      // No pause event after seeked
      return
    }
    if (_playerState == MuxPlayerState.REBUFFERING) {
      rebufferingEnded()
    }
    if (seekingInProgress) {
      seeked()
      return
    }
    _playerState = MuxPlayerState.PAUSED
    dispatch(PauseEvent(null))
  }

  /**
   * Call when the player has stopped seeking. This is normally handled automatically, but may need
   * to be called if there was an surprise position discontinuity in some cases
   */
  fun seeked() {
    // Only handle if we were previously seeking
    if (seekingInProgress) {
      dispatch(SeekedEvent(null))
      seekingInProgress = false
      _playerState = MuxPlayerState.SEEKED
    }

    if (seekingEventsSent == 0) {
      seekingInProgress = false
    }
  }

  /**
   * Called when the player starts seeking, or encounters a discontinuity.
   * If the player was playing, a PauseEvent will be dispatched.
   * In all cases, the state will move to SEEKING, and frame rendering data will be reset
   */
  @Suppress("unused")
  fun seeking() {
    // TODO: We are getting a seeking() before the start of the view for some reason.
    //  This (I think?) causes state handling for another event to call seeked()
    //  We should eliminate the improper seeking() call, or find good criteria to ignore it,

    if (playEventsSent == 0) {
      // Ignore this, we have received a seek event before we have received playerready event,
      // so this event should be ignored.
      return
    }
    if (muxPlayerState == MuxPlayerState.PLAYING) {
      dispatch(PauseEvent(null))
    }
    _playerState = MuxPlayerState.SEEKING
    seekingInProgress = true
    firstFrameRenderedAtMillis = FIRST_FRAME_NOT_RENDERED
    dispatch(SeekingEvent(null))
    firstFrameReceived = false
  }

  /**
   * Increment the number of frames dropped during this view by the given amount
   */
  @Suppress("unused")
  fun incrementDroppedFrames(droppedFrames: Int) {
    this.droppedFrames += droppedFrames
  }

  /**
   * Call when the end of playback was reached.
   * A PauseEvent and EndedEvent will both be sent, and the state will be set to ENDED
   */
  @Suppress("unused")
  fun ended() {
    dispatch(PauseEvent(null))
    dispatch(EndedEvent(null))
    _playerState = MuxPlayerState.ENDED
  }

  @Suppress("unused")
  fun isPaused(): Boolean {
    return _playerState == MuxPlayerState.PAUSED
            || _playerState == MuxPlayerState.ENDED
            || _playerState == MuxPlayerState.ERROR
            || _playerState == MuxPlayerState.INIT
  }

  @Suppress("unused")
  fun onFirstFrameRendered() {
    firstFrameRenderedAtMillis = System.currentTimeMillis()
    firstFrameReceived = true
  }

  @Suppress("unused")
  fun internalError(error: Exception) {
    if (error is MuxErrorException) {
      dispatch(InternalErrorEvent(error.code, error.message))
    } else {
      dispatch(
        InternalErrorEvent(
          ERROR_UNKNOWN,
          "${error.javaClass.canonicalName} - ${error.message}"
        )
      )
    }
  }

  /**
   * Call when the media content was changed within the same stream, ie, the stream URL remained the
   * same but the content within changed, ie during a livestream. Does anyone remember shoutcast?
   *
   * This method will start a new Video View on Mux Data's backend
   */
  @Suppress("unused")
  fun programChange(customerVideoData: CustomerVideoData) {
    reset()
    muxStats.programChange(customerVideoData)
  }

  /**
   * Call when the media stream (by URL) was changed.
   *
   * This method will start a new Video View on Mux Data's backend
   */
  @Suppress("unused")
  fun videoChange(customerVideoData: CustomerVideoData) {
    _playerState = MuxPlayerState.INIT
    reset()
    muxStats.videoChange(customerVideoData)
  }

  @Suppress("unused")
  fun renditionChange(
    advertisedBitrate: Int,
    advertisedFrameRate: Float,
    sourceWidth: Int,
    sourceHeight: Int
  ) {
    sourceAdvertisedBitrate = advertisedBitrate
    sourceAdvertisedFrameRate = advertisedFrameRate
    this.sourceWidth = sourceWidth
    this.sourceHeight = sourceHeight

    dispatch(RenditionChangeEvent(null))
  }

  /**
   * Call when an Ad begins playing
   */
  @Suppress("unused")
  fun playingAds() {
    _playerState = MuxPlayerState.PLAYING_ADS
  }

  /**
   * Call when all ads are finished being played
   */
  @Suppress("unused")
  fun finishedPlayingAds() {
    _playerState = MuxPlayerState.FINISHED_PLAYING_ADS
  }

  @Suppress("unused")
  fun onMainPlaylistTags(tags: List<SessionTag>) {
    // dispatch new session data on change only
    if (sessionTags != tags) {
      sessionTags = tags
      muxStats.setSessionData(tags)
    }
  }

  /**
   * Kills this object. After being killed, this object will no longer report metrics to Mux Data
   */
  @Suppress("unused")
  fun release() {
    playerWatcher?.stop("tracker released")
    dead = true
  }

  /**
   * Returns true if a frame was rendered by the player, or if frame rendering is not being tracked
   * @see #trackFirstFrameRendered
   */
  @Suppress("unused")
  private fun firstFrameRendered(): Boolean = !trackFirstFrameRendered
          || (firstFrameReceived && (System.currentTimeMillis() - firstFrameRenderedAtMillis > FIRST_FRAME_WAIT_MILLIS))

  /**
   * Called internally when the player starts rebuffering. Rebuffering is buffering after the
   * initial content had started playing
   */
  private fun rebufferingStarted() {
    _playerState = MuxPlayerState.REBUFFERING
    dispatch(RebufferStartEvent(null))
  }

  /**
   * Called internally when the player finishes rebuffering. Callers are responsible for setting the
   * new appropriate player state
   */
  private fun rebufferingEnded() {
    dispatch(RebufferEndEvent(null))
  }

  private fun reset() {
    mimeType = null
    playEventsSent = 0
    pauseEventsSent = 0
    totalEventsSent = 0
    seekingEventsSent = 0
    seekedEventsSent = 0
    firstFrameReceived = false
    firstFrameRenderedAtMillis = FIRST_FRAME_NOT_RENDERED
    allowedHeaders.clear()
  }

  @JvmSynthetic
  internal fun dispatch(event: IEvent) {
    totalEventsSent++
    when (event.type) {
      PlayEvent.TYPE -> {
        playEventsSent++
      }

      PauseEvent.TYPE -> {
        pauseEventsSent++
      }

      SeekingEvent.TYPE -> {
        seekingEventsSent++
      }
    }
    dispatcher.dispatch(event)
  }

  /**
   * Manages a timer loop in a coroutine scope that periodically polls the player for its current
   * playback position. The polling is done from the main thread.
   *
   * To create this object you must provide a function that can read the state info from your
   * player. This function takes both a [Player] and a [MuxStateCollector], allowing you to update
   * any information you need to poll for. This function must return the reported playback position
   * of the player, or [TIME_UNKNOWN] if the time isn't known
   *
   * This object should be stopped when no longer needed. To handle cases where users forget to
   * release our SDK, implementations should not hold strong references to big objects like context
   * or a player. If [checkPositionMillis] returns null, this object will automatically stop
   *
   * Stops if:
   *  the caller calls [stop]
   *  the superclass starts returning null from [getTimeMillis]
   *  the [Player] referenced by the given WeakReferences is garbage collected
   *
   *  @param updateIntervalMillis The time interval, in milliseconds, between position updates
   *  @param stateCollector The [MuxStateCollector] that should track the playback position
   *  @param player The [Player] object that returns playback position info
   *  @param checkPositionMillis A block that is run every [updateIntervalMillis] that returns the
   *                             [player]'s current playback position, or [TIME_UNKNOWN]
   *
   *  @param Player The type of the Player object. Should be something that returns playback pos
   */
  class PlayerWatcher<Player>(
    @Suppress("MemberVisibilityCanBePrivate") val updateIntervalMillis: Long,
    @Suppress("MemberVisibilityCanBePrivate") val stateCollector: MuxStateCollector,
    player: Player,
    val checkPositionMillis: (Player, MuxStateCollector) -> Long?
  ) {
    private val timerScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
    private val player by weak(player)

    private fun getTimeMillis(): Long? = player?.let { checkPositionMillis(it, stateCollector) }

    fun stop(message: String) {
      timerScope.cancel(message)
    }

    @Suppress("unused")
    fun start() {
      timerScope.launch {
        while (true) {
          updateOnMain(this)
          delay(updateIntervalMillis)
        }
      }
    }

    private fun updateOnMain(coroutineScope: CoroutineScope) {
      coroutineScope.launch(Dispatchers.Main) {
        val position = getTimeMillis()
        if (position != null) {
          stateCollector.playbackPositionMills = position
        } else {
          // If the data source is returning null, assume caller cleaned up the player
          MuxLogger.d(logTag(), "PlaybackPositionWatcher: Player lost. Stopping")
          stop("player lost")
        }
      }
    }
  }
}
