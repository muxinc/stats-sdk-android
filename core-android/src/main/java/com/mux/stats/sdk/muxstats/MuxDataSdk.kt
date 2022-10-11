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
import com.mux.stats.sdk.core.util.MuxLogger
import com.mux.stats.sdk.muxstats.internal.weak
import java.util.*

/**
 * Base class for Mux Data SDKs for Android. This class provides some structure to hold a
 * [MuxStateCollector], [PlayerAdapter], etc, allowing the SDKs themselves to mostly be player
 * interaction instead of this boilerplate
 *
 * This class also defines a minimal set of functionality for a Mux Data SDK, requiring Data SDKs
 * implementing this class to define functionality such as presentation/orientation tracking,
 * accepting CustomerVideoData/CustomerPlayerData/etc, video/program change, and so on
 */
abstract class MuxDataSdk {

  /**
   * Basic device details such as OS version, vendor name and etc. Instances of this class
   * are used by [MuxStats] to interface with the device.
   */
  protected open class MuxAndroidDevice(
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
    @Suppress("MemberVisibilityCanBePrivate")
    var overwrittenDeviceName: String? = null
    @Suppress("MemberVisibilityCanBePrivate")
    var overwrittenOsFamilyName: String? = null
    @Suppress("MemberVisibilityCanBePrivate")
    var overwrittenOsVersion: String? = null
    @Suppress("MemberVisibilityCanBePrivate")
    var overwrittenManufacturer: String? = null

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
        //  introduce a hang risk (from if the callback didn't fire)
        connectionTypeApi16()
      }

    @TargetApi(Build.VERSION_CODES.M)
    private fun connectionTypeApi23(): String? {
      contextRef?.let { context ->
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val nc: NetworkCapabilities? =
          connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)

        return if (nc == null) {
          MuxLogger.w(TAG, "Could not get network info")
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
      } ?: return null
    }

    @Suppress("DEPRECATION") // Uses deprecated APIs for backward compat
    private fun connectionTypeApi16(): String? {
      // use let so we get both a null-check and a hard ref
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
      } ?: return null
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
      val muxStatsInstance = MuxStats.getHostDevice() as? MuxAndroidDevice
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
    }
  }

}
