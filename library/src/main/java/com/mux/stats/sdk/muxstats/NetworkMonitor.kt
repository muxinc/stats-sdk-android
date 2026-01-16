package com.mux.stats.sdk.muxstats

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * Listens for changes to network connection status from the system.
 *
 * The implementation may differ depending on android version
 */
interface NetworkChangeMonitor {
  companion object {
    const val CONNECTION_TYPE_CELLULAR = "cellular"
    const val CONNECTION_TYPE_WIFI = "wifi"
    const val CONNECTION_TYPE_WIRED = "wired"
    const val CONNECTION_TYPE_OTHER = "other"
  }

  fun setListener(listener: NetworkChangedListener?)

  fun release()

  fun interface NetworkChangedListener {
    fun onNetworkChanged(networkType: String?, restrictedData: Boolean?)
  }
}

@JvmSynthetic
internal fun NetworkChangeMonitor(
  context: Context,
): NetworkChangeMonitor {
  return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    NetworkChangeMonitorApi26(context.applicationContext)
  } else {
    NetworkChangeMonitorApi16(context.applicationContext)
  }
}

@JvmSynthetic
@RequiresApi(Build.VERSION_CODES.M)
internal fun NetworkCapabilities.toMuxConnectionType(): String {
  return when {
    hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
      -> NetworkChangeMonitor.CONNECTION_TYPE_WIRED
    hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
      -> NetworkChangeMonitor.CONNECTION_TYPE_WIFI
    hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
      -> NetworkChangeMonitor.CONNECTION_TYPE_CELLULAR

    else -> NetworkChangeMonitor.CONNECTION_TYPE_OTHER
  }
}

@Suppress("DEPRECATION")
@JvmSynthetic
internal fun NetworkInfo.toMuxConnectionType(): String {
  return when (type) {
    ConnectivityManager.TYPE_ETHERNET -> NetworkChangeMonitor.CONNECTION_TYPE_WIRED

    ConnectivityManager.TYPE_WIFI -> NetworkChangeMonitor.CONNECTION_TYPE_WIFI

    ConnectivityManager.TYPE_MOBILE,
    ConnectivityManager.TYPE_MOBILE_DUN,
    ConnectivityManager.TYPE_MOBILE_HIPRI,
    ConnectivityManager.TYPE_MOBILE_SUPL,
    ConnectivityManager.TYPE_WIMAX,
    ConnectivityManager.TYPE_MOBILE_MMS -> {
      NetworkChangeMonitor.CONNECTION_TYPE_CELLULAR
    }

    else -> NetworkChangeMonitor.CONNECTION_TYPE_OTHER

  }
}

/**
 * Detects changes to network status using the old BroadcastReceiver method
 *
 * We use this all the way up to O, because NetworkCallback was not reliable before then
 */
@Suppress("DEPRECATION")
private class NetworkChangeMonitorApi16(
  val appContext: Context,
) : NetworkChangeMonitor {

  private var connectivityReceiver: ConnectivityReceiver? = null
  private var lastSeenConnectionType: String? = null
  private var outsideListener: NetworkChangeMonitor.NetworkChangedListener? = null


  private fun getConnectivityManager(context: Context): ConnectivityManager {
    return context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
  }

  private fun currentConnectionType(): String? {
    val networkInfo = getConnectivityManager(appContext).activeNetworkInfo
    val connType = networkInfo?.toMuxConnectionType()
    return connType
  }

  override fun setListener(listener: NetworkChangeMonitor.NetworkChangedListener?) {
    this.outsideListener = listener
  }

  override fun release() {
    outsideListener = null
    connectivityReceiver?.let { appContext.unregisterReceiver(it) }
    connectivityReceiver = null
  }

  init {
    this.connectivityReceiver = ConnectivityReceiver()
    val intentFilter = IntentFilter()
    intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION)
    appContext.registerReceiver(this.connectivityReceiver, intentFilter)
  }

  inner class ConnectivityReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      // when using the system broadcasts, we can safely query ConnectivityManager synchronously
      val currentType = currentConnectionType()
      if (currentType != lastSeenConnectionType) {
        outsideListener?.onNetworkChanged(currentType, false)
        lastSeenConnectionType = currentType
      }
    }
  }
}

/**
 * Detects Network changes using [ConnectivityManager.NetworkCallback]. The API is available way
 * before O, but isn't generally considered reliable before then. The callback method we need
 * (onCapabilitiesChanged) isn't guaranteed to be called, and asking for net capabilities
 * synchronously from onActive is a no-no because it is a source of data races. So we fall back to
 * the intent broadcasts L, M, and N still
 */
@RequiresApi(Build.VERSION_CODES.O)
private class NetworkChangeMonitorApi26(
  val appContext: Context,
) : NetworkChangeMonitor {

  // todo - use me
  private val callbackHandler = Handler(Looper.getMainLooper())

  private var outsideListener: NetworkChangeMonitor.NetworkChangedListener? = null
  private var defaultNetworkCallback: ConnectivityManager.NetworkCallback? = null
  private var lastSeenNetworkType: String? = null

  override fun setListener(listener: NetworkChangeMonitor.NetworkChangedListener?) {
    this.outsideListener = listener
  }

  override fun release() {
    outsideListener = null
    defaultNetworkCallback?.let {
      val connectivityManager = getConnectivityManager(appContext)
      connectivityManager.unregisterNetworkCallback(it)
    }
    defaultNetworkCallback = null
  }

  private fun getConnectivityManager(context: Context): ConnectivityManager {
    return context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
  }

  private fun handleNetworkCapabilities(networkCapabilities: NetworkCapabilities) {
    val connType = networkCapabilities.toMuxConnectionType()
    if (connType != lastSeenNetworkType) {
      val lowBandwidth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
        !networkCapabilities.hasCapability(
          NetworkCapabilities.NET_CAPABILITY_NOT_BANDWIDTH_CONSTRAINED
        )
      } else {
        null
      }

      lastSeenNetworkType = connType
      outsideListener?.onNetworkChanged(connType, lowBandwidth)
    }
  }

  init {
    val networkCallback = object : ConnectivityManager.NetworkCallback() {
      override fun onCapabilitiesChanged(
        network: Network,
        networkCapabilities: NetworkCapabilities
      ) {
        Log.d("NetworkMonitor", "onCapChanged: Current Thread: ${Thread.currentThread().name}")
        // todo - do we need a new thing
        handleNetworkCapabilities(networkCapabilities)
      }

      override fun onLost(network: Network) {
        Log.d("NetworkMonitor", "onLost: Current Thread: ${Thread.currentThread().name}")
        lastSeenNetworkType = null
        outsideListener?.onNetworkChanged(null, null)
      }
    }

    getConnectivityManager(appContext).registerDefaultNetworkCallback(networkCallback)
    this.defaultNetworkCallback = networkCallback
  }
}
