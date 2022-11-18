package com.mux.stats.sdk.muxstats

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.View
import com.mux.stats.sdk.core.CustomOptions
import com.mux.stats.sdk.core.MuxSDKViewOrientation
import com.mux.stats.sdk.core.events.EventBus
import com.mux.stats.sdk.core.events.IEvent
import com.mux.stats.sdk.core.events.IEventDispatcher
import com.mux.stats.sdk.core.model.CustomerData
import com.mux.stats.sdk.core.model.CustomerPlayerData
import com.mux.stats.sdk.core.model.CustomerVideoData
import com.mux.stats.sdk.core.util.MuxLogger
import com.mux.stats.sdk.muxstats.MuxDataSdk.AndroidDevice
import com.mux.stats.sdk.muxstats.internal.convertPxToDp
import com.mux.stats.sdk.muxstats.internal.oneOf
import com.mux.stats.sdk.muxstats.internal.weak
import java.util.*

/**
 * Base class for Mux Data SDK facades for Android. This class provides some structure to hold a
 * [MuxStateCollector], [MuxPlayerAdapter], etc, allowing the SDKs themselves to mostly be player
 * interaction instead of this boilerplate
 *
 * This class also defines a minimal set of functionality for a Mux Data SDK, requiring Data SDKs
 * implementing this class to define functionality such as presentation/orientation tracking,
 * accepting CustomerVideoData/CustomerPlayerData/etc, video/program change, and so on
 *
 * This class also has two protected static classes, [AndroidDevice] and [MuxNetwork]. These
 * classes provide all platform and network interaction required for most SDKs. They are both open,
 * and so can be extended if additional functionality is required
 *
 * Using the class for its base features only requires extending it. If you need additional
 * features, you can inject your own instances of [IPlayerListener], [MuxStateCollector],
 * [MuxPlayerAdapter], and [MuxUiDelegate], that work with your [MuxPlayerAdapter.PlayerBinding] to
 * track the required data.
 */
@Suppress("unused")
abstract class MuxDataSdk<Player, ExtraPlayer, PlayerView : View> @JvmOverloads protected constructor(
  context: Context,
  envKey: String,
  player: Player,
  playerView: PlayerView?,
  customerData: CustomerData,
  device: IDevice,
  playerBinding: MuxPlayerAdapter.PlayerBinding<Player>,
  customOptions: CustomOptions = CustomOptions(),
  trackFirstFrame: Boolean = false,
  makePlayerId: (context: Context, view: View?) -> String = Companion::generatePlayerId,
  makePlayerListener: (
    of: MuxDataSdk<Player, ExtraPlayer, PlayerView>
  ) -> IPlayerListener = Companion::defaultPlayerListener,
  makeMuxStats: (
    playerListener: IPlayerListener,
    playerId: String,
    customerData: CustomerData,
    customOptions: CustomOptions
  ) -> MuxStats = Companion::defaultMuxStats,
  makeEventBus: () -> EventBus = { EventBus() },
  makePlayerAdapter: (
    player: Player,
    uiDelegate: MuxUiDelegate<PlayerView>,
    collector: MuxStateCollector,
    playerBinding: MuxPlayerAdapter.PlayerBinding<Player>
  ) -> MuxPlayerAdapter<PlayerView, Player, ExtraPlayer> = Companion::defaultPlayerAdapter,
  makeStateCollector: (
    muxStats: MuxStats,
    dispatcher: IEventDispatcher,
    trackFirstFrame: Boolean
  ) -> MuxStateCollector = Companion::defaultMuxStateCollector,
  makeUiDelegate: (
    context: Context, view: PlayerView?
  ) -> MuxUiDelegate<PlayerView> = Companion::defaultUiDelegate,
  network: INetworkRequest = MuxNetwork(device),
  logLevel: LogcatLevel = LogcatLevel.NONE,
) {

  @Suppress("MemberVisibilityCanBePrivate")
  protected val playerAdapter: MuxPlayerAdapter<PlayerView, Player, ExtraPlayer>
  protected val muxStats: MuxStats

  @Suppress("MemberVisibilityCanBePrivate")
  protected val eventBus: EventBus

  @Suppress("MemberVisibilityCanBePrivate")
  protected val player: Player
  protected val extraPlayer: ExtraPlayer? = null

  @Suppress("MemberVisibilityCanBePrivate")
  protected val uiDelegate: MuxUiDelegate<PlayerView>

  @Suppress("MemberVisibilityCanBePrivate")
  protected val collector: MuxStateCollector

  @Suppress("MemberVisibilityCanBePrivate")
  protected val displayDensity: Float

  /**
   * Update all Customer Data (custom player, video, and view data) with the data found here
   * Older values will not be cleared
   */
  open fun updateCustomerData(customerData: CustomerData) {
    muxStats.customerData = customerData
  }

  /**
   * Gets the [CustomerData] object containing the player, video, and view data you want to attach
   * to the current view
   */
  open fun getCustomerData(): CustomerData = muxStats.customerData

  /**
   * If true, this object will automatically track fatal playback errors, eventually showing the
   * errors on the dashboard. If false, only errors reported via [error] will show up on the
   * dashboard
   */
  open fun setAutomaticErrorTracking(enabled: Boolean) = muxStats.setAutomaticErrorTracking(enabled)

  /**
   * Report a fatal error to the dashboard for this view. This is normally tracked automatically,
   * but if you are reporting errors yourself, you can do so with this method
   */
  open fun error(exception: MuxErrorException) = muxStats.error(exception)

  /**
   * Change the player [View] this object observes.
   */
  open fun setPlayerView(view: PlayerView?) {
    uiDelegate.view = view
  }

  /**
   * Manually set the size of the player view. This overrides the normal auto-detection. The
   * dimensions should be in physical pixels
   */
  open fun setPlayerSize(widthPx: Int, heightPx: Int) =
    muxStats.setPlayerSize(
      convertPxToDp(widthPx, displayDensity),
      convertPxToDp(heightPx, displayDensity)
    )

  /**
   * Manually set the size of the screen. This overrides the normal auto-detection. The dimensions
   * should be in physical pixels
   */
  open fun setScreenSize(widthPx: Int, heightPx: Int) =
    muxStats.setScreenSize(
      convertPxToDp(widthPx, displayDensity),
      convertPxToDp(heightPx, displayDensity)
    )

  /**
   * Call when a new media item (video or audio) is being played in a player. This will start a new
   * View to represent the new video being consumed
   */
  open fun videoChange(videoData: CustomerVideoData) {
    collector.videoChange(videoData)
  }

  /**
   * Call when new content is being served over the same URL, such as during a live stream. This
   * method will start a new view to represent the new content being consumed
   */
  open fun programChange(videoData: CustomerVideoData) {
    collector.programChange(videoData)
  }

  /**
   * Call when the device changes physical orientation, such as moving from portrait to landscape
   */
  open fun orientationChange(orientation: MuxSDKViewOrientation) =
    muxStats.orientationChange(orientation)

  /**
   * Call when the presentation of the video changes, ie Fullscreen vs Normal, etc
   */
  open fun presentationChange(presentation: MuxSDKViewPresentation) =
    muxStats.presentationChange(presentation)

  /**
   * Dispatch a raw event to the View. Please use this method with caution, as unexpected events can
   * lead to broken views
   */
  open fun dispatch(event: IEvent?) = eventBus.dispatch(event)

  /**
   * Enables ADB logging for this SDK
   * @param enable If true, enables logging. If false, disables logging
   * @param verbose If true, enables verbose logging. If false, disables it
   */
  open fun enableMuxCoreDebug(enable: Boolean, verbose: Boolean) =
    muxStats.allowLogcatOutput(enable, verbose)

  /**
   * Tears down this object. After this, the object will no longer be usable
   */
  open fun release() {
    // NOTE: If you override this, you must call super()
    playerAdapter.unbindEverything()
    muxStats.release()
  }

  @Suppress("MemberVisibilityCanBePrivate")
  protected open inner class PlayerListenerBase : IPlayerListener {
    @Suppress("RedundantNullableReturnType")
    protected val collector: MuxStateCollector? get() = this@MuxDataSdk.collector

    /**
     * Convert physical pixels to device density independent pixels.
     *
     * @param px physical pixels to be converted.
     * @return number of density pixels calculated.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    protected fun pxToDp(px: Int): Int = convertPxToDp(px, uiDelegate.displayDensity())

    override fun getCurrentPosition(): Long = collector?.playbackPositionMills ?: 0L
    override fun getMimeType() = collector?.mimeType
    override fun getSourceWidth(): Int? = collector?.sourceWidth
    override fun getSourceHeight(): Int? = collector?.sourceHeight
    override fun getSourceAdvertisedBitrate(): Int? = collector?.sourceAdvertisedBitrate
    override fun getSourceAdvertisedFramerate(): Float? = collector?.sourceAdvertisedFrameRate
    override fun getSourceDuration() = collector?.sourceDurationMs
    override fun isPaused() = collector?.isPaused() ?: true
    override fun isBuffering(): Boolean = collector?.muxPlayerState == MuxPlayerState.BUFFERING
    override fun getPlayerProgramTime(): Long? = null
    override fun getPlayerManifestNewestTime(): Long? = null
    override fun getVideoHoldback(): Long? = null
    override fun getVideoPartHoldback(): Long? = null
    override fun getVideoPartTargetDuration(): Long? = null
    override fun getVideoTargetDuration(): Long? = null
    override fun getPlayerViewWidth() =
      convertPxToDp(uiDelegate.getPlayerViewSize().x, uiDelegate.displayDensity())

    override fun getPlayerViewHeight() =
      convertPxToDp(uiDelegate.getPlayerViewSize().y, uiDelegate.displayDensity())
  }

  init {
    this.player = player
    eventBus = makeEventBus()
    @Suppress("LeakingThis")
    uiDelegate = makeUiDelegate(context, playerView)
    muxStats = makeMuxStats(
      makePlayerListener(this),
      makePlayerId(context, playerView),
      customerData,
      customOptions
    )
    collector = makeStateCollector(muxStats, eventBus, trackFirstFrame)
    playerAdapter = makePlayerAdapter(
      player, uiDelegate, collector, playerBinding
    )

    customerData.apply { if (customerPlayerData == null) customerPlayerData = CustomerPlayerData() }
    customerData.customerPlayerData.environmentKey = envKey
    muxStats.customerData = customerData
    eventBus.addListener(muxStats)
    displayDensity = uiDelegate.displayDensity()

    // These must be statically set before creating our MuxStats
    //  TODO em - eventually these should probably just be instance vars, that is likely to be safer
    MuxStats.setHostDevice(device)
    MuxStats.setHostNetworkApi(network)
    muxStats.allowLogcatOutput(
      logLevel.oneOf(LogcatLevel.DEBUG, LogcatLevel.VERBOSE),
      logLevel == LogcatLevel.VERBOSE
    )
  }

  /**
   * Values for the verbosity of [MuxLogger]'s output
   */
  enum class LogcatLevel { NONE, DEBUG, VERBOSE }

  companion object {
    /**
     * Generates a player ID based off the containing context and the ID of the View being used for
     * playback
     */
    protected fun generatePlayerId(context: Context, view: View?) =
      context.javaClass.canonicalName!! + (view?.id ?: "audio")

    protected fun defaultPlayerListener(outerSdk: MuxDataSdk<*, *, *>): IPlayerListener =
      outerSdk.PlayerListenerBase()

    protected fun <V : View> defaultUiDelegate(
      context: Context,
      view: V?
    ) = view.muxUiDelegate(context as? Activity)

    protected fun defaultMuxStats(
      playerListener: IPlayerListener,
      playerId: String,
      customerData: CustomerData,
      customOptions: CustomOptions
    ): MuxStats = MuxStats(playerListener, playerId, customerData, customOptions)

    protected fun <Player, ExtraPlayer, PlayerView : View> defaultPlayerAdapter(
      player: Player,
      uiDelegate: MuxUiDelegate<PlayerView>,
      collector: MuxStateCollector,
      playerBinding: MuxPlayerAdapter.PlayerBinding<Player>,
    ) = MuxPlayerAdapter<PlayerView, Player, ExtraPlayer>(
      player,
      collector,
      uiDelegate,
      playerBinding
    )

    protected fun defaultMuxStateCollector(
      muxStats: MuxStats,
      dispatcher: IEventDispatcher,
      trackFirstFrame: Boolean = false
    ) = MuxStateCollector(muxStats, dispatcher, trackFirstFrame)

  }

  // ----------------------------------------------------------------------------------------------
  // Android platform interaction below.
  // These are nested classes in order to keep them out of customers' classpath
  // ----------------------------------------------------------------------------------------------

  /**
   * Android implementation of [IDevice]. Interacts with the Android platform for device state info,
   * such as network availability, device metadata, etc
   */
  open class AndroidDevice(
    ctx: Context,
    private val playerVersion: String,
    private val muxPluginName: String,
    private val muxPluginVersion: String,
    private val playerSoftware: String
  ) : IDevice {

    private val contextRef by weak(ctx)
    private val deviceId: String
    private var appName = ""
    private var appVersion = ""

    @Deprecated(
      message = "Mux core does not use this value anymore.",
      replaceWith = ReplaceWith("CustomerViewerData")
    )
    override fun getMuxManufacturer(): String? = ""

    @Deprecated(
      message = "Mux core does not use this value anymore.",
      replaceWith = ReplaceWith("CustomerViewerData")
    )
    override fun getMuxOSFamily(): String? = ""

    @Deprecated(
      message = "Mux core does not use this value anymore.",
      replaceWith = ReplaceWith("CustomerViewerData")
    )
    override fun getMuxOSVersion(): String? = ""

    @Deprecated(
      message = "Mux core does not use this value anymore.",
      replaceWith = ReplaceWith("CustomerViewerData")
    )
    override fun getMuxDeviceName(): String = ""

    @Deprecated(
      message = "Mux core does not use this value anymore.",
      replaceWith = ReplaceWith("CustomerViewerData")
    )
    override fun getMuxDeviceCategory(): String = ""

    @Deprecated(
      message = "Mux core does not use this value anymore.",
      replaceWith = ReplaceWith("CustomerViewerData")
    )
    override fun getMuxModelName(): String? = ""

    override fun getHardwareArchitecture(): String? = Build.HARDWARE
    override fun getOSFamily() = "Android"
    override fun getOSVersion() = Build.VERSION.RELEASE + " (" + Build.VERSION.SDK_INT + ")"
    override fun getDeviceName(): String = "" // Default gets the name from the server
    override fun getDeviceCategory(): String = "" // Default behavior gets name via server
    override fun getManufacturer(): String? = Build.MANUFACTURER
    override fun getModelName(): String? = Build.MODEL
    override fun getPlayerVersion() = playerVersion
    override fun getDeviceId() = deviceId
    override fun getAppName() = appName
    override fun getAppVersion() = appVersion
    override fun getPluginName() = muxPluginName
    override fun getPluginVersion() = muxPluginVersion
    override fun getPlayerSoftware() = playerSoftware

    /**
     * Determine the correct network connection type.
     *
     * @return the connection type name.
     */
    override fun getNetworkConnectionType(): String? =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        connectionTypeApi23()
      } else {
        // on API 21 and 22 deprecated APIs will be called, but there's no good synchronous way to get
        //  active network info until API 23. Making the async ones synchronous w/coroutines would
        //  be cool but might introduce a hang risk if the callback didn't fire
        connectionTypeApi16()
      }

    @TargetApi(Build.VERSION_CODES.M)
    private fun connectionTypeApi23(): String? {
      contextRef?.let { context ->
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val nc: NetworkCapabilities? =
          connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)

        return when {
          nc == null -> {
            MuxLogger.w(TAG, "Could not get network capabilities")
            null
          }
          nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
            CONNECTION_TYPE_WIRED
          }
          nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
            CONNECTION_TYPE_WIFI
          }
          nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
            CONNECTION_TYPE_CELLULAR
          }
          else -> {
            CONNECTION_TYPE_OTHER
          }
        }
      } ?: return null
    }

    @Suppress("DEPRECATION") // Uses deprecated APIs for backward compat
    private fun connectionTypeApi16(): String? {
      // use let{} so we get both a null-check and a hard ref for the check
      contextRef?.let { context ->
        val connectivityMgr = context
          .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork: NetworkInfo? = connectivityMgr.activeNetworkInfo

        return if (activeNetwork == null) {
          MuxLogger.w(TAG, "Couldn't obtain network info")
          null
        } else {
          when (activeNetwork.type) {
            ConnectivityManager.TYPE_ETHERNET -> {
              CONNECTION_TYPE_WIRED
            }
            ConnectivityManager.TYPE_WIFI -> {
              CONNECTION_TYPE_WIFI
            }
            ConnectivityManager.TYPE_MOBILE,
            ConnectivityManager.TYPE_MOBILE_DUN,
            ConnectivityManager.TYPE_MOBILE_HIPRI,
            ConnectivityManager.TYPE_MOBILE_SUPL,
            ConnectivityManager.TYPE_WIMAX,
            ConnectivityManager.TYPE_MOBILE_MMS -> {
              CONNECTION_TYPE_CELLULAR
            }
            else -> {
              CONNECTION_TYPE_OTHER
            }
          }
        }
      } ?: return null // contextRef?.let {...
    }

    override fun getElapsedRealtime(): Long {
      return SystemClock.elapsedRealtime()
    }

    override fun outputLog(logPriority: LogPriority?, tag: String?, msg: String?, t: Throwable?) {
      when (logPriority) {
        LogPriority.ERROR -> Log.e(tag, msg, t)
        LogPriority.WARN -> Log.w(tag, msg, t)
        LogPriority.INFO -> Log.i(tag, msg, t)
        LogPriority.DEBUG -> Log.d(tag, msg, t)
        LogPriority.VERBOSE -> Log.v(tag, msg, t)
        else -> Log.v(tag, msg, t)
      }
    }

    override fun outputLog(logPriority: LogPriority, tag: String, msg: String) {
      outputLog(logPriority, tag, msg)
    }

    /**
     * Print underlying [MuxStats] SDK messages on the logcat.
     *
     * @param tag tag to be used.
     * @param msg message to be printed.
     */
    override fun outputLog(tag: String, msg: String) {
      Log.v(tag, msg)
    }

    @SuppressLint("ApplySharedPref") // It's important that we not create multiple device ids
    @Synchronized
    private fun getOrCreateDeviceId(context: Context): String {
      val sharedPreferences = context.getSharedPreferences(MUX_DEVICE_ID, Context.MODE_PRIVATE)
      var deviceId = sharedPreferences.getString(MUX_DEVICE_ID, null)
      if (deviceId == null) {
        deviceId = UUID.randomUUID().toString()
        val editor = sharedPreferences.edit()
        editor.putString(MUX_DEVICE_ID, deviceId)
        editor.commit()
      }
      return deviceId
    }

    companion object {
      const val CONNECTION_TYPE_CELLULAR = "cellular"
      const val CONNECTION_TYPE_WIFI = "wifi"
      const val CONNECTION_TYPE_WIRED = "wired"
      const val CONNECTION_TYPE_OTHER = "other"

      private const val TAG = "MuxDevice"
      private const val MUX_DEVICE_ID = "MUX_DEVICE_ID"

      /**
       * Gets the singleton instance
       */
      val muxStatsInstance get() = MuxStats.getHostDevice() as? AndroidDevice
    }

    init {
      deviceId = getOrCreateDeviceId(ctx)
      try {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        appName = pi.packageName
        appVersion = pi.versionName
      } catch (e: PackageManager.NameNotFoundException) {
        MuxLogger.d(TAG, "could not get package info")
      }
    } // init
  } // protected open class ... : IDevice
} // abstract class MuxDataSdk
