package com.mux.stats.sdk.muxstats

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.View
import com.mux.stats.sdk.core.Core
import com.mux.stats.sdk.core.CustomOptions
import com.mux.stats.sdk.core.MuxSDKViewOrientation
import com.mux.stats.sdk.core.events.EventBus
import com.mux.stats.sdk.core.events.IEvent
import com.mux.stats.sdk.core.model.CustomerData
import com.mux.stats.sdk.core.model.CustomerPlayerData
import com.mux.stats.sdk.core.model.CustomerVideoData
import com.mux.stats.sdk.core.util.MuxLogger
import com.mux.stats.sdk.muxstats.MuxDataSdk.AndroidDevice
import com.mux.stats.sdk.muxstats.internal.oneOf
import com.mux.stats.sdk.muxstats.internal.weak
import java.util.*
import kotlin.math.ceil

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
 */
@Suppress("unused")
abstract class MuxDataSdk<Player, ExtraPlayer, PlayerView : View> protected constructor(
  context: Context,
  envKey: String,
  customerData: CustomerData,
  customOptions: CustomOptions? = null,
  @Suppress("MemberVisibilityCanBePrivate")
  val playerAdapter: MuxPlayerAdapter<PlayerView, *, *>,
  playerListener: IPlayerListener,
  device: IDevice,
  network: INetworkRequest, /* TODO: Implement NetworkRequest as a protected static class here */
  logLevel: LogcatLevel = LogcatLevel.NONE,
) {

  // MuxCore Java Stuff
  @Suppress("MemberVisibilityCanBePrivate") protected val muxStats: MuxStats
  @Suppress("MemberVisibilityCanBePrivate") protected val eventBus = EventBus()
  @Suppress("MemberVisibilityCanBePrivate") protected lateinit var playerId: String

  private val uiDelegate by playerAdapter::uiDelegate
  private val basicPlayer by playerAdapter::basicPlayer
  private val extraPlayer by playerAdapter::extraPlayer
  private val collector by playerAdapter::collector

  private val displayDensity: Float

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
    muxStats.setPlayerSize(pxToDp(widthPx), pxToDp(heightPx))

  /**
   * Manually set the size of the screen. This overrides the normal auto-detection. The dimensions
   * should be in physical pixels
   */
  open fun setScreenSize(widthPx: Int, heightPx: Int) =
    muxStats.setScreenSize(pxToDp(widthPx), pxToDp(heightPx))

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

  /**
   * Convert physical pixels to device density independent pixels.
   *
   * @param px physical pixels to be converted.
   * @return number of density pixels calculated.
   */
  @Suppress("MemberVisibilityCanBePrivate")
  protected fun pxToDp(px: Int): Int {
    return ceil((px / displayDensity).toDouble()).toInt()
  }

  init {
    customerData.apply { if (customerPlayerData == null) customerPlayerData = CustomerPlayerData() }
    customerData.customerPlayerData.environmentKey = envKey

    // Just don't hold the context ref
    displayDensity = context.resources.displayMetrics.density

    // These must be statically set before creating our MuxStats
    //  TODO em - eventually these should probably just be instance vars, that is likely to be safer
    MuxStats.setHostDevice(device)
    MuxStats.setHostNetworkApi(network)
    if (!::playerId.isInitialized) {
      // playerId is for tracking static instances of CorePlayer in core
      val viewId = playerAdapter.uiDelegate.getViewId()
      if (viewId != View.NO_ID) {
        context.javaClass.canonicalName!! + playerAdapter.uiDelegate.getViewId()
      } else {
        playerId = context.javaClass.canonicalName!! + "audio"
      }
    }
    muxStats = MuxStats(playerListener, playerId, customerData, customOptions ?: CustomOptions())
      .also { eventBus.addListener(it) }
    Core.allowLogcatOutputForPlayer(
      playerId,
      logLevel.oneOf(LogcatLevel.DEBUG, LogcatLevel.VERBOSE),
      logLevel == LogcatLevel.VERBOSE
    )
  }

  /**
   * Values for the verbosity of [MuxLogger]'s output
   */
  protected enum class LogcatLevel { NONE, DEBUG, VERBOSE }

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

    // TODO: A new API is coming for these, using CustomerViewerData.
    @Suppress("MemberVisibilityCanBePrivate") var overwrittenDeviceName: String? = null
    @Suppress("MemberVisibilityCanBePrivate") var overwrittenOsFamilyName: String? = null
    @Suppress("MemberVisibilityCanBePrivate") var overwrittenOsVersion: String? = null
    @Suppress("MemberVisibilityCanBePrivate") var overwrittenManufacturer: String? = null

    override fun getHardwareArchitecture(): String? = Build.HARDWARE
    override fun getOSFamily() = "Android"
    override fun getMuxOSFamily(): String? = overwrittenOsFamilyName
    override fun getOSVersion() = Build.VERSION.RELEASE + " (" + Build.VERSION.SDK_INT + ")"
    override fun getMuxOSVersion(): String? = overwrittenOsVersion
    override fun getManufacturer(): String? = Build.MANUFACTURER
    override fun getMuxManufacturer(): String? = overwrittenManufacturer
    override fun getModelName(): String? = Build.MODEL
    override fun getMuxModelName(): String? = overwrittenDeviceName
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

        return if (nc == null) {
          MuxLogger.w(TAG, "Could not get network capabilities")
          null
        } else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
          CONNECTION_TYPE_WIRED
        } else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
          CONNECTION_TYPE_WIFI
        } else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
          CONNECTION_TYPE_CELLULAR
        } else {
          CONNECTION_TYPE_OTHER
        }
      } ?: return null // contextRef?.let {...
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
        } else if (activeNetwork.type == ConnectivityManager.TYPE_ETHERNET) {
          CONNECTION_TYPE_WIRED
        } else if (activeNetwork.type == ConnectivityManager.TYPE_WIFI) {
          CONNECTION_TYPE_WIFI
        } else if (activeNetwork.type == ConnectivityManager.TYPE_MOBILE) {
          CONNECTION_TYPE_CELLULAR
        } else {
          CONNECTION_TYPE_OTHER
        }
      } ?: return null // contextRef?.let {...
    }

    override fun getElapsedRealtime(): Long {
      return SystemClock.elapsedRealtime()
    }

    override fun outputLog(logPriority: LogPriority, tag: String, msg: String) {
      when (logPriority) {
        LogPriority.ERROR -> Log.e(tag, msg)
        LogPriority.WARN -> Log.w(tag, msg)
        LogPriority.INFO -> Log.i(tag, msg)
        LogPriority.DEBUG -> Log.d(tag, msg)
        LogPriority.VERBOSE -> Log.v(tag, msg)
        else -> Log.v(tag, msg)
      }
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
      val muxStatsInstance = MuxStats.getHostDevice() as? AndroidDevice
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
