# stats-sdk-android

Core library for our Data SDKs for Android. It's a small layer over our Core Java lib that provides
an android-specific SDK facade, code for ensuring that contracts around event ordering are
obeyed,and implementations of some objects that Core Java leaves to the integrator.

This SDK is meant to be a base for other Mux Data SDKs, such as
our [ExoPlayer SDK](https://github.com/muxinc/mux-stats-sdk-exoplayer).

## Usage

Add the dependency to your SDK.

```groovy
api 'com.mux.stats.sdk.muxstats:android:0.2.0'
```

Implement `PlayerBinding<>` to listen for changes from your Player

```kotlin
// PlayerBinding for a player called ExamplePlayer
internal class ExamplePlayerBinding : PlayerBinding<ExamplePlayer> {
  private val playerEventListener: MyEventListener? = null
  override fun bindPlayer(player: ExamplePlayer, collector: MuxStateCollector) {
    playerEventListener = object : MyEventListener {
      override fun onPlay() = collector.play()
    }.also { player.addListener(it) }
    // ... And so on
  }
  override fun unbindPlayer(player: ExamplePlayer, collector: MuxStateCollector) {
    playerEventListener?.let { player.removeListener(it) }
  }
}
```

Extend the `MuxDataSdk` facade. Don't make the user have to create a `PlayerAdapter` or a `MuxStats`
or any other "plumbing"-level class.

```kotlin
class MuxStatsExamplePlayer(
  context: Context,
  envKey: envKey,
  customerData: CustomerData,
  player: ExamplePlayer,
  playerView: ExamplePlayerView? = null,
  customOptions: CustomOptions? = null,
  /* Plus whatever other inputs are required for your particular SDK*/
) : MuxDataSdk<ExamplePlayer, ExamplePlayer, ExamplePlayerView>(
  context = context,
  envKey = envKey,
  customerData = customerData,
  customOptions = customOptions,
  player = player,
  playerView = playerView,
  playerBinding = ExamplePlayerBinding(), // ExamplePlayerBinding provided by you
  trackFirstFrame = false, // set to `true` only if your player can provide this information
  device = AndroidDevice(
    ctx = context,
    playerSoftware = "someplayer",
    playerVersion = "1.1.1",
    muxPluginName = "plugin",
    muxPluginVersion = BuildConfig.LIB_VERSION
  )
) {
  // The base class provides a lot of simple functionality but you can add additional capabilities,
  //  and all the public functions are open in case their implementation doesn't work for your SDK

  /**
   * For example, do some special logic when the video is changed
   */
  override fun videoChange(video: CustomerVideoData) {
    super.videoChange(video.map { /* mutate the video data somehow */ })
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
