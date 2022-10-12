package com.mux.stats.sdk.muxstats

import android.view.View
import com.mux.stats.sdk.muxstats.MuxPlayerAdapter.PlayerBinding
import com.mux.stats.sdk.muxstats.internal.observableWeak
import com.mux.stats.sdk.muxstats.internal.weak

/**
 * Adapts a player framework to a [MuxStateCollector], passing events between them using
 * [PlayerBinding] implementations provided by the player SDK
 *
 * TODO: [PlayerView] will need to be further generified to support Compose, probably
 *
 * @param player the Player object that should be tracked, such as MediaPlayer or ExoPlayer
 * @param collector The [MuxStateCollector] used to track state & dispatch events
 * @param uiDelegate The [MuxUiDelegate] used for gathering UI-related metrics
 * @param basicMetrics A [PlayerBinding] that listens to state from a player of type [MainPlayer]
 * @param extraMetrics A list of [PlayerBinding]s for advanced metrics, which may come from a
 *                     different Player object, such as bandwidth metrics in Media3 or JWPlayer
 *
 * @param PlayerView The View used to show Player content and (possibly) player chrome.
 *                   When in doubt (or for player SDKs with multiple player views), use View
 * @param MainPlayer The type of the main Player, such as ExoPlayer or MediaPlayer
 * @param ExtraPlayer The type of the extra player, such as ExoPlayer in the Media3,Kaltura,THEO,
 *                    etc SDKs
 */
class MuxPlayerAdapter<PlayerView : View, MainPlayer, ExtraPlayer>(
  player: MainPlayer,
  @Suppress("MemberVisibilityCanBePrivate") val collector: MuxStateCollector,
  @Suppress("MemberVisibilityCanBePrivate") val uiDelegate: MuxUiDelegate<PlayerView>,
  @Suppress("MemberVisibilityCanBePrivate") val basicMetrics: PlayerBinding<MainPlayer>,
  @Suppress("MemberVisibilityCanBePrivate")
  val extraMetrics: ExtraPlayerBindings<ExtraPlayer>? = null,
) {

  /**
   * The main Player being observed by this Adapter. When changed, the old player will be unbound
   * and the new player will be bound
   * This is the Player that belongs to [basicMetrics]
   */
  @Suppress("MemberVisibilityCanBePrivate")
  var basicPlayer by observableWeak(player) { changeBasicPlayer(it, collector) }

  /**
   * The Player responsible for gathering extra metrics that might not be available from all players
   * such as Bandwidth or Live Latency
   */
  @Suppress("MemberVisibilityCanBePrivate")
  var extraPlayer by observableWeak(extraMetrics?.player) { changeExtraPlayer(it, collector) }

  /**
   * The View being used to collect data related to the player view. This is the View being managed
   * by the [uiDelegate]
   */
  @Suppress("unused")
  var playerView by uiDelegate::view

  init {
    basicMetrics.bindPlayer(player, collector)
    extraMetrics?.bindings?.onEach { it.bindPlayer(extraMetrics.player!!, collector) }
  }

  /**
   * Unbinds all bindings. After this no strong references will be held to the player
   */
  @Suppress("unused")
  fun unbindEverything() {
    basicPlayer?.let { player ->
      basicMetrics.unbindPlayer(player, collector)
    }
    extraPlayer?.let { player ->
      extraMetrics?.bindings?.onEach { binding -> binding.unbindPlayer(player, collector) }
    }
  }

  private fun changeBasicPlayer(player: MainPlayer?, collector: MuxStateCollector) {
    basicPlayer?.let { oldPlayer -> basicMetrics.unbindPlayer(oldPlayer, collector) }
    player?.let { newPlayer -> basicMetrics.bindPlayer(newPlayer, collector) }
  }

  private fun changeExtraPlayer(player: ExtraPlayer?, collector: MuxStateCollector) {
    if (extraMetrics != null) {
      extraPlayer?.let { oldPlayer ->
        extraMetrics.bindings.onEach { it.unbindPlayer(oldPlayer, collector) }
      }
      player?.let { newPlayer ->
        extraMetrics.bindings.onEach { it.bindPlayer(newPlayer, collector) }
      }
      extraMetrics.player = player
    }
  }

  /**
   * A Binding between some Player object and a MuxDataCollector
   *
   * @param Player A type of Player, such as ExoPlayer or MediaPlayer
   */
  interface PlayerBinding<Player> {

    /**
     * Binds a player to a MuxDataCollector, setting listeners or whatever is required to observe
     * state, and calling hooks on MuxDataCollector
     */
    fun bindPlayer(player: Player, collector: MuxStateCollector)

    /**
     * Unbinds a player from a MuxDataCollector, removing listeners and cleaning up
     */
    fun unbindPlayer(player: Player, collector: MuxStateCollector)
  }

  /**
   * Container for bindings that gather metrics that may not be available from all players, such as
   * session data, or bandwidth metrics. These may come from a different source object than the main
   * player (ie Kaltura or Bitmovin's internal ExoPlayer, the player backing Media3, etc)
   */
  class ExtraPlayerBindings<ExtraPlayer>(
    player: ExtraPlayer,
    val bindings: List<PlayerBinding<ExtraPlayer>>
  ) {
    var player by weak(player)
  }
}
