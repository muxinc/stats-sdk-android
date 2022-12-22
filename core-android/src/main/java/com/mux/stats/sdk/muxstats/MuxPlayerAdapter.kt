package com.mux.stats.sdk.muxstats

import android.view.View
import com.mux.android.util.weak
import com.mux.stats.sdk.muxstats.MuxPlayerAdapter.PlayerBinding

/**
 * Adapts a player framework to a [MuxStateCollector], passing events between them using
 * [PlayerBinding] implementations provided by the player SDK
 *
 * TODO: [MuxUiDelegate] will need to be further generified to support Compose, probably
 *
 * @param player the Player object that should be tracked, such as MediaPlayer or ExoPlayer
 * @param collector The [MuxStateCollector] used to track state & dispatch events
 * @param uiDelegate The [MuxUiDelegate] used for gathering UI-related metrics
 * @param basicMetrics A [PlayerBinding] that listens to state from a player of type [MainPlayer]
 *
 * @param PlayerView The View used to show Player content and (possibly) player chrome.
 *                   When in doubt (or for player SDKs with multiple player views), use View
 * @param MainPlayer The type of the main Player, such as ExoPlayer or MediaPlayer
 */
class MuxPlayerAdapter<PlayerView : View, MainPlayer>(
  val player: MainPlayer,
  @Suppress("MemberVisibilityCanBePrivate") val collector: MuxStateCollector,
  @Suppress("MemberVisibilityCanBePrivate") val uiDelegate: MuxUiDelegate<PlayerView>,
  @Suppress("MemberVisibilityCanBePrivate") val basicMetrics: PlayerBinding<MainPlayer>,
) {

  /**
   * The View being used to collect data related to the player view. This is the View being managed
   * by the [uiDelegate]
   */
  @Suppress("unused")
  var playerView by uiDelegate::view

  init {
    basicMetrics.bindPlayer(player, collector)
  }

  /**
   * Unbinds all bindings. After this no strong references will be held to the player
   */
  @Suppress("unused")
  fun unbindEverything() {
    player?.let { player ->
      basicMetrics.unbindPlayer(player, collector)
    }
  }

  /**
   * Change the Player bound to this adapter
   */
  @Suppress("unused")
  fun changePlayer(player: MainPlayer?) {
    this.player?.let { oldPlayer -> basicMetrics.unbindPlayer(oldPlayer, collector) }
    player?.let { newPlayer -> basicMetrics.bindPlayer(newPlayer, collector) }
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
}
