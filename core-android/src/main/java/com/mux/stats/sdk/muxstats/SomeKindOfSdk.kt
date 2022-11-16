package com.mux.stats.sdk.muxstats

import android.content.Context
import android.view.View
import com.mux.core_android.BuildConfig
import com.mux.stats.sdk.core.model.CustomerData

class SomeKindOfSdk(
  context: Context,
  envKey: String,
  player: Any,
  playerView: View,
  customerData: CustomerData
  ) : MuxDataSdk<Any, Any, View>(
  context = context,
  envKey = envKey,
  customerData = customerData,
  player = player,
  playerView = playerView,
  playerBinding = AnyPlayerBinding(),
  device = AndroidDevice(
    ctx = context,
    playerSoftware = "someplayer",
    playerVersion = "1.1.1",
    muxPluginName = "plugin",
    muxPluginVersion = BuildConfig.LIB_VERSION
  )
) {
}

private class AnyPlayerBinding : MuxPlayerAdapter.PlayerBinding<Any> {
  override fun bindPlayer(player: Any, collector: MuxStateCollector) {}
  override fun unbindPlayer(player: Any, collector: MuxStateCollector) {}
}

/*
  Ugh fuck me. This sucks so much ass. How do I deal with this?
  So, the player listener impl requires a collector for state and a uidelegate for other state
  But also it's supposed to be passed as a ctor param

  MuxDataSdk could implement IPlayerListener and we still get that cool :MuxDataSdk that happens
  'cause like it's all coming out of the collector anyway. The player binding can take care of the
  rest.

  Ok so we don't know where to do muxStats. Ideally we'd do it in the superclass but the Collector
  needs a muxStats right now. SDKs are supposed to be able to bring their own collector. We can make
  the input for the Collector a factory but it's kind of annoying. Could do a muxStats factory too
  I guess. I mean this is really just poor person's dagger I guess so fuck it, factories it is
  Factories for what?

  PlayerAdapter
  MuxStats
  playerId
  MuxStateCollector
  IPlayerListener

  ok you've now reduced the problem down as clear as possible. will need collector factory after muxstats,
  PlayerListener before muxstats, need PlayerListener before MuxStats
  OK maybe its fine (no, need playerListener before collector, but need need collector before playerlistener):
  uiDelegate -> muxStats -> collector ->

  Now we need the following primary ctor properties:
  uiDelegate
  MuxStats
  playerAdapter
  stateCollector
 */