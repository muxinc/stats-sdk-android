# stats-sdk-android

Core library for our Data SDKs for Android. It's a small layer over our Core Java lib that provides
an android-specific SDK facade, code for ensuring that contracts around event ordering are
obeyed,and implementations of some objects that Core Java leaves to the integrator.

This SDK is meant to be a base for other Mux Data SDKs, such as
our [ExoPlayer SDK](https://github.com/muxinc/mux-stats-sdk-exoplayer).

## Usage

Add the dependency to your SDK.

```groovy
api 'com.mux.stats.sdk.muxstats:core-android:0.1.0'
```

Implement `PlayerBinding<>` to listen for changes from your Player

```kotlin
// PlayerBinding for a player called ExamplePlayer
internal class ExamplePlayerBinding : PlayerBinding<ExamplePlayer> {
  private val playerEventListener: MyEventListener? = null
  override fun bindPlayer(player: ExamplePlayer, collector: MuxStateCollector) {
    playerEventListener = object : MyEventListener {
      override fun onPlay() = collector.play()
    }
    // ... And so on
  }
  override fun unbindPlayer(player: ExamplePlayer, collector: MuxStateCollector) {
    playerEventListener?.let { player.removeListener(it) }
  }
}
```

Extend the `MuxDataSdk` facade

```kotlin
class MuxStatsExamplePlayer(
  context: Context,
  envKey: envKey,
  customerData: CustomerData,
  player: ExamplePlayer,
  playerView: ExamplePlayerView? = null,
  customOptions: CustomOptions? = null,
  /* Plus whatever other inputs are required*/
) : MuxDataSdk<ExamplePlayer, ExamplePlayer, ExamplePlayerView>(
  context = context,
  envKey = envKey,
  customerData = customerData,
  playerAdapter = MuxPlayerAdapter(
    player = player,
    collector = MuxStateCollector(
      muxStats = MuxStats(
        null,
        MuxDataSdk.generatePlayerId(context, view),
        customerData,
        CustomOptions()
      ),
      dispatcher = EventBus(),
      trackFirstFrameRendered = false // Only set to `true` if the player can give this info!
    ),
    uiDelegate = noUiDelegate(),
    basicMetrics = ExamplePlayerBinding(),
  ),
  device = AndroidDevice(
    ctx = context,
    playerVersion = player.getVersion(),
    muxPluginName = "example-sdk",
    muxPluginVersion = BuildConfig.LIB_VERSION,
    playerSoftware = "example-player"
  ),
) {
  // The base class provides a lot of simple functionality but you can add additional capabilities,
  //  and all the public functions are open in case their implementation doesn't work for your SDK
  
  override fun videoChange(video: CustomerVideoData) {
    super.videoChange(video.map { /* mutate the video data somehow */})
  }
}
```

## Contributing

The code in this repo conforms to
the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html). Please reformat
before submission

The code was formatted in Android Studio/IntelliJ using
the [Google Java Style for IntelliJ](https://github.com/google/styleguide/blob/gh-pages/intellij-java-google-style.xml)
. The style can be installed via the Java-style section of the IDE
preferences (`Editor -> Code Style - >Java`).
