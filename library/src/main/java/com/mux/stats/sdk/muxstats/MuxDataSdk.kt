package com.mux.stats.sdk.muxstats

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.View
import androidx.annotation.RequiresApi
import com.mux.android.util.convertPxToDp
import com.mux.android.util.oneOf
import com.mux.android.util.weak
import com.mux.stats.sdk.core.CustomOptions
import com.mux.stats.sdk.core.MuxSDKViewOrientation
import com.mux.stats.sdk.core.events.EventBus
import com.mux.stats.sdk.core.events.IEvent
import com.mux.stats.sdk.core.events.IEventDispatcher
import com.mux.stats.sdk.core.events.playback.ErrorEvent
import com.mux.stats.sdk.core.events.playback.ErrorEvent.ErrorSeverity
import com.mux.stats.sdk.core.model.*
import com.mux.stats.sdk.core.util.MuxLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

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
abstract class MuxDataSdk<Player, PlayerView : View> @JvmOverloads protected constructor(
  context: Context,
  envKey: String,
  player: Player,
  playerView: PlayerView?,
  customerData: CustomerData,
  device: IDevice,
  playerBinding: MuxPlayerAdapter.PlayerBinding<Player>,
  customOptions: CustomOptions = CustomOptions(),
  trackFirstFrame: Boolean = false,
  logLevel: LogcatLevel = LogcatLevel.NONE,
  networkChangeMonitor: NetworkChangeMonitor = NetworkChangeMonitor(context),
  makePlayerId: (context: Context, view: View?) -> String = Factory::generatePlayerId,
  makePlayerListener: (
    of: MuxDataSdk<Player, PlayerView>
  ) -> IPlayerListener = Factory::defaultPlayerListener,
  makeMuxStats: (
    playerListener: IPlayerListener,
    playerId: String,
    customerData: CustomerData,
    customOptions: CustomOptions
  ) -> MuxStats = Factory::defaultMuxStats,
  makeEventBus: () -> EventBus = { EventBus() },
  makePlayerAdapter: (
    player: Player,
    uiDelegate: MuxUiDelegate<PlayerView>,
    collector: MuxStateCollector,
    playerBinding: MuxPlayerAdapter.PlayerBinding<Player>
  ) -> MuxPlayerAdapter<PlayerView, Player> = Factory::defaultPlayerAdapter,
  makeStateCollector: (
    muxStats: MuxStats,
    dispatcher: IEventDispatcher,
    trackFirstFrame: Boolean
  ) -> MuxStateCollector = Factory::defaultMuxStateCollector,
  makeUiDelegate: (
    context: Context,
    view: PlayerView?
  ) -> MuxUiDelegate<PlayerView> = Factory::defaultUiDelegate,
  makeNetworkRequest: (
    device: IDevice,
  ) -> INetworkRequest = Factory::defaultNetworkRequest,
) {

  @Suppress("MemberVisibilityCanBePrivate")
  protected val playerAdapter: MuxPlayerAdapter<PlayerView, Player>
  protected val muxStats: MuxStats

  @Suppress("MemberVisibilityCanBePrivate")
  protected val eventBus: EventBus

  @Suppress("MemberVisibilityCanBePrivate")
  protected val player: Player

  @Suppress("MemberVisibilityCanBePrivate")
  protected val uiDelegate: MuxUiDelegate<PlayerView>

  @Suppress("MemberVisibilityCanBePrivate")
  protected val collector: MuxStateCollector

  @Suppress("MemberVisibilityCanBePrivate")
  protected val networkChangeMonitor: NetworkChangeMonitor

  @Suppress("MemberVisibilityCanBePrivate")
  protected val displayDensity: Float get() = uiDelegate.displayDensity()

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
   * Report an error to the dashboard for this view. If the error is recoverable, you can set its
   * severity to [ErrorEvent.ErrorSeverity.WARNING]. If it's related to your application logic
   * and not playback, you can flag that as well
   *
   * @param code An error code from your player.
   * @param message a message describing the error
   * @param errorContext additional context, such as a stack trace, related to this error
   */
  open fun error(code: String,
                 message: String?,
                 errorContext: String) =
    muxStats.error(code, message , errorContext)

  /**
   * Report an error to the dashboard for this view. If the error is recoverable, you can set its
   * severity to [ErrorEvent.ErrorSeverity.WARNING]. If it's related to your application logic
   * and not playback, you can flag that as well
   *
   * @param code An error code from your player.
   * @param message a message describing the error
   * @param errorContext additional context, such as a stack trace, related to this error
   * @param errorSeverity The severity of the error. Errors can be fatal or warnings
   * @param isBusinessException True if the error is related to app logic (eg, expired subscription)
   */
  
  open fun error(code: String,
                 message: String?,
                 errorContext: String,
                 errorSeverity: ErrorSeverity,
                 isBusinessException: Boolean) =
    muxStats.error(code, message , errorContext, errorSeverity, isBusinessException)

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
   * Call when the video is presented in a particular way, eg, fullscreen, or picture-in-picture
   *
   * You can call this method immediately after starting a view, or wait until a relevant change.
   * If you do the latter, the mode will be [PlaybackMode.STANDARD] until you change it.
   *
   * @param mode The playback mode being entered
   */
  open fun playbackModeChange(mode: PlaybackMode) {
    muxStats.playbackModeChange(mode, null as JSONObject?)
  }

  /**
   * Call when the video is presented in a particular way, eg, fullscreen, or picture-in-picture
   *
   * You can call this method immediately after starting a view, or wait until a relevant change.
   * If you do the latter, the mode will be [PlaybackMode.STANDARD] until you change it.
   *
   * @param mode The playback mode being entered
   * @param extraData Extra data to accompany this event. Will appear in the event timeline for your view
   */
  open fun playbackModeChange(mode: PlaybackMode, extraData: JSONObject) {
    muxStats.playbackModeChange(mode, extraData)
  }

  /**
   * Call when the video is presented in a particular way, eg, fullscreen, or picture-in-picture
   *
   * You can call this method immediately after starting a view, or wait until a relevant change.
   * If you do the latter, the mode will be [PlaybackMode.STANDARD] until you change it.
   *
   * @param customMode The playback mode being entered
   */
  open fun playbackModeChange(customMode: String) {
    muxStats.playbackModeChange(customMode, null as JSONObject?)
  }

  /**
   * Call when the video is presented in a particular way, eg, fullscreen, or picture-in-picture
   *
   * You can call this method immediately after starting a view, or wait until a relevant change.
   * If you do the latter, the mode will be [PlaybackMode.STANDARD] until you change it.
   *
   * @param customMode The playback mode being entered
   * @param extraData Extra data to accompany this event. Will appear in the event timeline for your view
   */
  open fun playbackModeChange(customMode: String, extraData: JSONObject) {
    muxStats.playbackModeChange(customMode, extraData)
  }

  /**
   * Call when the presentation of the video changes, ie Fullscreen vs Normal, etc
   */
  open fun presentationChange(presentation: MuxSDKViewPresentation) =
    muxStats.presentationChange(presentation)

  /**
   * Disables event collection for this MuxDataSdk. The current view will be ended, and any pending
   * events will be sent to the dashboard
   */
  open fun disable() = muxStats.disable()

  /**
   * Enables event collection after previously disabling it. A new view will be started with the
   * provided [CustomerData]
   *
   * @param customerData [CustomerData] with metadata about the new view
   * @see CustomerData
   */
  open fun enable(customerData: CustomerData) {
    collector.resetState()
    muxStats.enable(customerData)
  }

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
    networkChangeMonitor.release()
    playerAdapter.unbindEverything()
    muxStats.release()
  }

  @Suppress("MemberVisibilityCanBePrivate")
  protected open inner class PlayerListenerBase : IPlayerListener {
    @Suppress("RedundantNullableReturnType")
    protected val collector: MuxStateCollector?
      get() = this@MuxDataSdk.collector

    /**
     * Convert physical pixels to device density independent pixels.
     *
     * @param px physical pixels to be converted.
     * @return number of density pixels calculated.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    protected fun pxToDp(px: Int): Int = convertPxToDp(px, uiDelegate.displayDensity())

    // Mux's SDKs set this dimension only during renditionchange so our impl here can return null
    override fun getSourceCodec(): String? = null
    override fun getCurrentPosition(): Long = collector?.playbackPositionMills ?: 0L
    override fun getMimeType() = collector?.mimeType
    override fun getSourceWidth(): Int? = collector?.sourceWidth
    override fun getSourceHeight(): Int? = collector?.sourceHeight
    override fun getSourceAdvertisedBitrate(): Int? = collector?.sourceAdvertisedBitrate
    override fun getSourceAdvertisedFramerate(): Float? = collector?.sourceAdvertisedFrameRate
    override fun getSourceDuration() = collector?.sourceDurationMs
    override fun isPaused() = collector?.isPaused() ?: true
    override fun isBuffering(): Boolean = collector?.muxPlayerState == MuxPlayerState.BUFFERING
    override fun getPlayerProgramTime(): Long? = collector?.hlsPlayerProgramTime
    override fun getPlayerManifestNewestTime(): Long? = collector?.hlsManifestNewestTime
    override fun getVideoHoldback(): Long? = collector?.hlsHoldBack
    override fun getVideoPartHoldback(): Long? = collector?.hlsPartHoldBack
    override fun getVideoPartTargetDuration(): Long? = collector?.hlsPartTargetDuration
    override fun getVideoTargetDuration(): Long? = collector?.hlsTargetDuration
    override fun getPlayerViewWidth() =
      convertPxToDp(uiDelegate.getPlayerViewSize().x, uiDelegate.displayDensity())

    override fun getPlayerViewHeight() =
      convertPxToDp(uiDelegate.getPlayerViewSize().y, uiDelegate.displayDensity())
  }

  init {
    this.player = player
    // These must be statically set before creating our MuxStats
    //  TODO em - eventually these should probably just be instance vars, that is likely to be safer
    MuxStats.setHostDevice(device)
    MuxStats.setHostNetworkApi(makeNetworkRequest(device))

    // CustomerData fields are non-nullable internally in Core
    customerData.apply {
      if (customerPlayerData == null) {
        customerPlayerData = CustomerPlayerData()
      }
      if (customerVideoData == null) {
        customerVideoData = CustomerVideoData()
      }
      if (customerViewData == null) {
        customerViewData = CustomerViewData()
      }
      if (customerViewerData == null) {
        customerViewerData = CustomerViewerData()
      }
      if (customData == null) {
        customData = CustomData()
      }
    }
    // Core wants the env key on CustomerPlayerData
    customerData.apply { if (customerPlayerData == null) customerPlayerData = CustomerPlayerData() }
    customerData.customerPlayerData.environmentKey = envKey

    eventBus = makeEventBus()
    uiDelegate = makeUiDelegate(context, playerView)
    @Suppress("LeakingThis")
    muxStats = makeMuxStats(
      makePlayerListener(this),
      makePlayerId(context, playerView),
      customerData,
      customOptions
    )
    collector = makeStateCollector(muxStats, eventBus, trackFirstFrame)
    eventBus.addListener(muxStats)
    muxStats.customerData = customerData
    playerAdapter = makePlayerAdapter(
      player, uiDelegate, collector, playerBinding
    )
    networkChangeMonitor.setListener { networkType, lowData ->
      muxStats.networkChange(networkType, lowData)
    }
    this.networkChangeMonitor = networkChangeMonitor

    muxStats.allowLogcatOutput(
      logLevel.oneOf(LogcatLevel.DEBUG, LogcatLevel.VERBOSE),
      logLevel == LogcatLevel.VERBOSE
    )
    if (logLevel == LogcatLevel.VERBOSE) {
      MuxLogger.enable("all", muxStats)
    }
  }

  /**
   * Values for the verbosity of [MuxLogger]'s output
   */
  enum class LogcatLevel { NONE, DEBUG, VERBOSE }

  protected companion object Factory {

    private val nextPlayerId = AtomicInteger(0)

    /**
     * Generates a player ID based off the containing context and the ID of the View being used for
     * playback
     */
    fun generatePlayerId(context: Context, view: View?) =
      context.javaClass.canonicalName!! + "-" + (view?.id ?: "no-view") +
          "-" + nextPlayerId.getAndIncrement()

    fun defaultPlayerListener(outerSdk: MuxDataSdk<*, *>): IPlayerListener =
      outerSdk.PlayerListenerBase()

    fun <V : View> defaultUiDelegate(context: Context, view: V?): MuxUiDelegate<V> =
      view?.muxUiDelegate(context) ?: noUiDelegate(context)

    fun defaultMuxStats(
      playerListener: IPlayerListener,
      playerId: String,
      customerData: CustomerData,
      customOptions: CustomOptions
    ): MuxStats = MuxStats(playerListener, playerId, customerData, customOptions)

    fun <Player, PlayerView : View> defaultPlayerAdapter(
      player: Player,
      uiDelegate: MuxUiDelegate<PlayerView>,
      collector: MuxStateCollector,
      playerBinding: MuxPlayerAdapter.PlayerBinding<Player>,
    ) = MuxPlayerAdapter(player, collector, uiDelegate, playerBinding)

    fun defaultMuxStateCollector(
      muxStats: MuxStats,
      dispatcher: IEventDispatcher,
      trackFirstFrame: Boolean = false
    ) = MuxStateCollector(muxStats, dispatcher, trackFirstFrame)

    fun defaultNetworkRequest(device: IDevice) = MuxNetwork(device, CoroutineScope(Dispatchers.IO))

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

    override fun getIsNetworkInLowDataMode(): Boolean? {
      if (Build.VERSION.SDK_INT > Build.VERSION_CODES.BAKLAVA) {
        return contextRef?.let { context ->
          val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
          val nc: NetworkCapabilities? =
            connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
          return nc
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_BANDWIDTH_CONSTRAINED)
            ?.not()
        }
      } else {
        return null
      }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun connectionTypeApi23(): String? {
      // use let{} so we get both a null-check and a hard ref
      return contextRef?.let { context ->
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val nc: NetworkCapabilities? =
          connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)

        /*return*/ nc?.toMuxConnectionType() ?: NetworkChangeMonitor.CONNECTION_TYPE_NONE
      }
    }

    @Suppress("DEPRECATION") // Uses deprecated APIs for backward compat
    private fun connectionTypeApi16(): String? {
      // use let{} so we get both a null-check and a hard ref
      return contextRef?.let { context ->
        val connectivityMgr = context
          .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork: NetworkInfo? = connectivityMgr.activeNetworkInfo
        activeNetwork?.toMuxConnectionType()
      } // contextRef?.let {...
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
      outputLog(logPriority, tag, msg, null)
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

    @Suppress("DEPRECATION")
    private fun getPackageInfoLegacy(ctx: Context): PackageInfo =
      ctx.packageManager.getPackageInfo(ctx.packageName, 0)

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun getPackageInfoApi33(ctx: Context): PackageInfo =
      ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.PackageInfoFlags.of(0))

    companion object {
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
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          getPackageInfoApi33(ctx)
        } else {
          getPackageInfoLegacy(ctx)
        }
        @Suppress("USELESS_ELVIS")  // i dunno man i have a weird feeling about this one
        appName = packageInfo.packageName ?: ""
        appVersion = packageInfo.versionName ?: ""
      } catch (e: PackageManager.NameNotFoundException) {
        MuxLogger.d(TAG, "could not get package info")
      }
    } // init
  } // protected open class ... : IDevice
} // abstract class MuxDataSdk
