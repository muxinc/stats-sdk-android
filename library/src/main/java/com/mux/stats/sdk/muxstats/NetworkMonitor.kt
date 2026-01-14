package com.mux.stats.sdk.muxstats

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.os.Build
import androidx.annotation.RequiresApi
import com.mux.android.util.weak
import com.mux.stats.sdk.core.model.NetworkConnectionType
import com.mux.stats.sdk.muxstats.MuxDataSdk.AndroidDevice.Companion.CONNECTION_TYPE_CELLULAR
import com.mux.stats.sdk.muxstats.MuxDataSdk.AndroidDevice.Companion.CONNECTION_TYPE_OTHER
import com.mux.stats.sdk.muxstats.MuxDataSdk.AndroidDevice.Companion.CONNECTION_TYPE_WIFI
import com.mux.stats.sdk.muxstats.MuxDataSdk.AndroidDevice.Companion.CONNECTION_TYPE_WIRED

internal interface MuxNetworkMonitor {

  // TODO: Method for synchronously getting the network info (call on init)

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
    NetworkMonitorApi16(context.applicationContext)
  }
}
@JvmSynthetic
@RequiresApi(Build.VERSION_CODES.M)
internal fun NetworkCapabilities.toMuxConnectionType(): NetworkConnectionType {
  return when {
    hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
      -> NetworkConnectionType.WIRED
    hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
      -> NetworkConnectionType.WIRED
    hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
      -> NetworkConnectionType.CELLULAR

    else -> NetworkConnectionType.OTHER
  }
}

@Suppress("DEPRECATION")
@JvmSynthetic
internal fun NetworkInfo.toMuxConnectionType(): NetworkConnectionType {
  return when (type) {
    ConnectivityManager.TYPE_ETHERNET -> {
      NetworkConnectionType.WIRED
    }

    ConnectivityManager.TYPE_WIFI -> {
      NetworkConnectionType.WIRED
    }

    ConnectivityManager.TYPE_MOBILE,
    ConnectivityManager.TYPE_MOBILE_DUN,
    ConnectivityManager.TYPE_MOBILE_HIPRI,
    ConnectivityManager.TYPE_MOBILE_SUPL,
    ConnectivityManager.TYPE_WIMAX,
    ConnectivityManager.TYPE_MOBILE_MMS -> {
      NetworkConnectionType.CELLULAR
    }

    else -> {
      NetworkConnectionType.OTHER
    }
  }
}

private class NetworkMonitorApi16(
  appContext: Context
) : MuxNetworkMonitor {
  override fun release() {
    TODO("Not yet implemented")
  }

}

/*
 * Version Considerations:
 * * On Oreo and up: onCapabilitiesChanged is guaranteed to be called after onAvailable
 *    before this, we can't assume either way.
 *    How do we handle this? Keep the last-known network type and only call the listener when diff.
 *     .. and implement both onCapChanged and onAvailable
 * * On N and up, we can listen for the default network instead. Should we even do this?
 *   If so, we don't care if we go from eg, cellular to cellular due to dual-sim switches or '
 *    whatever (right?).. but our logic from the above point will take care of that
 *   I think we *do* want to listen for the default network tho, since that's almost definitely what
 *    the caller will be using.
 *   This case: Both wifi and cellular available. On N+, we can assume the default network is used
 *    so this case is covered by using the default network callback whatever guy
 *    On L and M, things are not as simple. We don't know which network is the default and can't
 *      ask ConnectivityManager from the callback. So I guess we can rely on the broadcast reciever
 *      there as well
 */

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
  // todo - Handler so we can report on the main thread (required)
) : MuxNetworkMonitor {

  private val defaultNetworkCallback: ConnectivityManager.NetworkCallback

  // todo - maybe we use the enum from muxcore?
  private var lastSeenNetworkType: String? = null

  override fun release() {
    val connectivityManager = getConnectivityManager(appContext)
    connectivityManager.unregisterNetworkCallback(defaultNetworkCallback)
  }

  private fun getConnectivityManager(context: Context): ConnectivityManager {
    return context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
  }

  init {
    val connectivityManager = getConnectivityManager(appContext)

    val networkCallback = object : ConnectivityManager.NetworkCallback() {
      override fun onCapabilitiesChanged(
        network: Network,
        networkCapabilities: NetworkCapabilities
      ) {
        val connType = networkCapabilities.toMuxConnectionType()
        val lowBandwidth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
          !networkCapabilities.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_NOT_BANDWIDTH_CONSTRAINED
          )
        } else {
          false
        }
        outsideListener.onNetworkChanged(connType, lowBandwidth)
      }

      override fun onLost(network: Network) {
        outsideListener.onNetworkChanged(null, false)
      }
    }

    connectivityManager.registerDefaultNetworkCallback(networkCallback)

    this.defaultNetworkCallback = networkCallback
  }
}
