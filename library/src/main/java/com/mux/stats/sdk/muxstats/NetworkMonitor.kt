package com.mux.stats.sdk.muxstats

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import androidx.annotation.RequiresApi
import com.mux.android.util.weak

internal interface MuxNetworkMonitor {

  // TODO: Method for synchronously getting the network info (call on init)

  fun release()

  interface NetworkChangedListener {
    fun onNetworkChanged(networkType: String?, restrictedData: Boolean)
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
 * before API 26, but isn't generally considered reliable before then. The callback method we need
 * (onCapabilitiesChanged) isn't guaranteed to be called, and the callbacks may fire before
 * ConnectivityManager has updated capabilities info (so we can't ask it for info synchronously)
 */
@RequiresApi(Build.VERSION_CODES.O)
private class NetworkMonitorApi26(
  val appContext: Context,
  val outsideListener: MuxNetworkMonitor.NetworkChangedListener
  // todo - Handler so we can report on the main thread (required)
): MuxNetworkMonitor {

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

    val networkCallback = object: ConnectivityManager.NetworkCallback() {
      override fun onCapabilitiesChanged(
        network: Network,
        networkCapabilities: NetworkCapabilities
      ) {
        // TODO - Get connection type and report change if different
      }

      override fun onLost(network: Network) {
        // todo - network lost, report null network
      }
    }

    connectivityManager.registerDefaultNetworkCallback(networkCallback)

    this.defaultNetworkCallback = networkCallback
  }
}
