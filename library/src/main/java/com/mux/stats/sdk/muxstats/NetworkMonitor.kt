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
import androidx.annotation.RequiresApi
import com.mux.stats.sdk.core.model.NetworkConnectionType

interface MuxNetworkMonitor {

  fun release()

  interface NetworkChangedListener {
    fun onNetworkChanged(networkType: NetworkConnectionType?, restrictedData: Boolean)
  }
}

@JvmSynthetic
internal fun MuxNetworkMonitor(
  context: Context,
  listener: MuxNetworkMonitor.NetworkChangedListener
): MuxNetworkMonitor {
  return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    NetworkMonitorApi26(context.applicationContext, listener)
  } else {
    NetworkMonitorApi16(context.applicationContext, listener)
  }
}

@JvmSynthetic
@RequiresApi(Build.VERSION_CODES.M)
internal fun NetworkCapabilities.toMuxConnectionType(): NetworkConnectionType {
  return when {
    hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
      -> NetworkConnectionType.WIRED
    hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
      -> NetworkConnectionType.WIFI
    hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
      -> NetworkConnectionType.CELLULAR

    else -> NetworkConnectionType.OTHER
  }
}

@Suppress("DEPRECATION")
@JvmSynthetic
internal fun NetworkInfo.toMuxConnectionType(): NetworkConnectionType {
  return when (type) {
    ConnectivityManager.TYPE_ETHERNET -> NetworkConnectionType.WIRED

    ConnectivityManager.TYPE_WIFI -> NetworkConnectionType.WIFI

    ConnectivityManager.TYPE_MOBILE,
    ConnectivityManager.TYPE_MOBILE_DUN,
    ConnectivityManager.TYPE_MOBILE_HIPRI,
    ConnectivityManager.TYPE_MOBILE_SUPL,
    ConnectivityManager.TYPE_WIMAX,
    ConnectivityManager.TYPE_MOBILE_MMS -> {
      NetworkConnectionType.CELLULAR
    }

    else -> NetworkConnectionType.OTHER

  }
}

/**
 * Detects changes to network status using the old BroadcastReceiver method
 *
 * We use this all the way up to O, because NetworkCallback was not reliable before then
 */
@Suppress("DEPRECATION")
private class NetworkMonitorApi16(
  val appContext: Context,
  val outsideListener: MuxNetworkMonitor.NetworkChangedListener
) : MuxNetworkMonitor {

  private var connectivityReceiver: ConnectivityReceiver? = null
  private var lastSeenConnectionType: NetworkConnectionType? = null


  private fun getConnectivityManager(context: Context): ConnectivityManager {
    return context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
  }

  private fun currentConnectionType(): NetworkConnectionType? {
    val networkInfo = getConnectivityManager(appContext).activeNetworkInfo
    val connType = networkInfo?.toMuxConnectionType()
    return connType
  }

  override fun release() {
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
        outsideListener.onNetworkChanged(currentType, false)
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
private class NetworkMonitorApi26(
  val appContext: Context,
  val outsideListener: MuxNetworkMonitor.NetworkChangedListener
) : MuxNetworkMonitor {

  // todo - use me
  private val callbackHandler = Handler(Looper.getMainLooper())

  private var defaultNetworkCallback: ConnectivityManager.NetworkCallback? = null
  private var lastSeenNetworkType: NetworkConnectionType? = null

  override fun release() {
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
        false
      }

      lastSeenNetworkType = connType
      outsideListener.onNetworkChanged(connType, lowBandwidth)
    }
  }

  init {
    val networkCallback = object : ConnectivityManager.NetworkCallback() {
      override fun onCapabilitiesChanged(
        network: Network,
        networkCapabilities: NetworkCapabilities
      ) {
        handleNetworkCapabilities(networkCapabilities)
      }

      override fun onLost(network: Network) {
        lastSeenNetworkType = null
        outsideListener.onNetworkChanged(null, false)
      }
    }

    getConnectivityManager(appContext).registerDefaultNetworkCallback(networkCallback)
    this.defaultNetworkCallback = networkCallback
  }
}
