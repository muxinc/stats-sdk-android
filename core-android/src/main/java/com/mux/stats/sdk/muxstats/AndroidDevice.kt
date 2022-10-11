package com.mux.stats.sdk.muxstats

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
 * Basic device details such as OS version, vendor name and etc. Instances of this class
 * are used by [MuxStats] to interface with the device.
 */
// TODO: consider making this a protected static class inside MuxSdk when it's made (to hide the symbol)
class MuxAndroidDevice(
  ctx: Context,
  private val playerVersion: String,
  private val muxPluginName: String,
  private val muxPluginVersion: String,
  private val playerSoftware: String
) : IDevice {

  private var contextRef by weak(ctx)
  private var deviceId: String?
  private var appName = ""
  private var appVersion = ""

  var overwrittenDeviceName: String? = null
  var overwrittenOsFamilyName: String? = null
  var overwrittenOsVersion: String? = null
  var overwrittenManufacturer: String? = null

  override fun getHardwareArchitecture(): String {
    return Build.HARDWARE
  }

  override fun getOSFamily(): String {
    return "Android"
  }

  override fun getMuxOSFamily(): String? = overwrittenOsFamilyName

  override fun getOSVersion(): String {
    return Build.VERSION.RELEASE + " (" + Build.VERSION.SDK_INT + ")"
  }

  override fun getMuxOSVersion(): String? = overwrittenOsVersion

  override fun getManufacturer(): String {
    return Build.MANUFACTURER
  }

  override fun getMuxManufacturer(): String? = overwrittenManufacturer

  override fun getModelName(): String {
    return Build.MODEL
  }

  override fun getMuxModelName(): String? = overwrittenDeviceName

  override fun getPlayerVersion(): String = playerVersion

  override fun getDeviceId(): String {
    return deviceId ?: "unknown"
  }

  override fun getAppName(): String {
    return appName
  }

  override fun getAppVersion(): String {
    return appVersion
  }

  override fun getPluginName(): String {
    return muxPluginName
  }

  override fun getPluginVersion(): String {
    return muxPluginVersion
  }

  override fun getPlayerSoftware(): String {
    return playerSoftware
  }

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
      //  active network info until API 23
      connectionTypeApi16()
    }

  @TargetApi(Build.VERSION_CODES.M)
  private fun connectionTypeApi23(): String? {
    contextRef?.let { context ->

      return "REMOVE THIS LINE"
    } ?: return null
  }

  @Suppress("DEPRECATION") // Uses deprecated APIs for backward compat
  private fun connectionTypeApi16(): String? {
    // use let so we get both a null-check and a hard ref
    contextRef?.let { context ->
      val connectivityMgr = context
        .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
      val activeNetwork: NetworkInfo? = connectivityMgr.activeNetworkInfo
      return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val nc = connectivityMgr
          .getNetworkCapabilities(connectivityMgr.activeNetwork)
        if (nc == null) {
          MuxLogger.d(
            TAG,
            "ERROR: Failed to obtain NetworkCapabilities manager !!!"
          )
          return null
        }
        if (nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
          CONNECTION_TYPE_WIRED
        } else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
          CONNECTION_TYPE_WIFI
        } else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
          CONNECTION_TYPE_CELLULAR
        } else {
          CONNECTION_TYPE_OTHER
        }
      } else {
        if (activeNetwork!!.type == ConnectivityManager.TYPE_ETHERNET) {
          CONNECTION_TYPE_WIRED
        } else if (activeNetwork.type == ConnectivityManager.TYPE_WIFI) {
          CONNECTION_TYPE_WIFI
        } else if (activeNetwork.type == ConnectivityManager.TYPE_MOBILE) {
          CONNECTION_TYPE_CELLULAR
        } else {
          CONNECTION_TYPE_OTHER
        }
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

  @Synchronized
  private fun getOrCreateDeviceId(context: Context): String {
    val sharedPreferences = context.getSharedPreferences(MUX_DEVICE_ID, Context.MODE_PRIVATE)
    var deviceId = sharedPreferences.getString(MUX_DEVICE_ID, null)
    if (deviceId == null) {
      deviceId = UUID.randomUUID().toString()
      val editor = sharedPreferences.edit()
      editor.putString(MUX_DEVICE_ID, deviceId)
      editor.apply()
    }
    return deviceId
  }

  companion object {
    const val CONNECTION_TYPE_CELLULAR = "cellular"
    const val CONNECTION_TYPE_WIFI = "wifi"
    const val CONNECTION_TYPE_WIRED = "wired"
    const val CONNECTION_TYPE_OTHER = "other"

    private const val TAG = "MuxDevice"
    private const val EXO_SOFTWARE = "ExoPlayer"
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
