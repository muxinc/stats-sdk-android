package com.mux.core_android

import android.annotation.TargetApi
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.mux.core_android.testdoubles.mockActivity
import com.mux.core_android.testdoubles.mockConnectivityManager16
import com.mux.core_android.testdoubles.mockConnectivityManager23
import com.mux.core_android.testdoubles.mockSharedPrefs
import com.mux.stats.sdk.muxstats.MuxDataSdk
import com.mux.stats.sdk.muxstats.util.allEqual
import org.junit.Assert.*
import org.junit.Test
import org.robolectric.annotation.Config

@Config(
  sdk = [Build.VERSION_CODES.JELLY_BEAN, Build.VERSION_CODES.M]
)
class AndroidDeviceTests : AbsRobolectricTest() {

  @Test
  fun testDeviceId() {
    val sharedPrefs = mockSharedPrefs()
    val devicesId = listOf(
      device(mockActivity(prefs = sharedPrefs)),
      device(mockActivity(prefs = sharedPrefs)),
      device(mockActivity(prefs = sharedPrefs)),
      device(mockActivity(prefs = sharedPrefs)),
      device(mockActivity(prefs = sharedPrefs)),
    ).map { it.deviceId }

    assertTrue(
      "Devices should have a consistent ID",
      allEqual(devicesId)
    )
  }

  @Test
  fun testConnectivityCases() {
    // Cellular Networks
    testConnectivityCase(
      connMgrReturns16 = listOf(ConnectivityManager.TYPE_MOBILE),
      connMgrReturns23 = listOf(NetworkCapabilities.TRANSPORT_CELLULAR),
      correctNetwork = MuxDataSdk.AndroidDevice.CONNECTION_TYPE_CELLULAR,
      incorrectNetworks = listOf(
        MuxDataSdk.AndroidDevice.CONNECTION_TYPE_OTHER,
        MuxDataSdk.AndroidDevice.CONNECTION_TYPE_WIFI,
        MuxDataSdk.AndroidDevice.CONNECTION_TYPE_WIRED,
      )
    )
    // WiFi Networks
    testConnectivityCase(
      connMgrReturns16 = listOf(ConnectivityManager.TYPE_WIFI),
      connMgrReturns23 = listOf(NetworkCapabilities.TRANSPORT_WIFI),
      correctNetwork = MuxDataSdk.AndroidDevice.CONNECTION_TYPE_WIFI,
      incorrectNetworks = listOf(
        MuxDataSdk.AndroidDevice.CONNECTION_TYPE_OTHER,
        MuxDataSdk.AndroidDevice.CONNECTION_TYPE_CELLULAR,
        MuxDataSdk.AndroidDevice.CONNECTION_TYPE_WIRED,
      )
    )
    // Wired Networks
    testConnectivityCase(
      connMgrReturns16 = listOf(ConnectivityManager.TYPE_ETHERNET),
      connMgrReturns23 = listOf(NetworkCapabilities.TRANSPORT_ETHERNET),
      correctNetwork = MuxDataSdk.AndroidDevice.CONNECTION_TYPE_WIRED,
      incorrectNetworks = listOf(
        MuxDataSdk.AndroidDevice.CONNECTION_TYPE_OTHER,
        MuxDataSdk.AndroidDevice.CONNECTION_TYPE_CELLULAR,
        MuxDataSdk.AndroidDevice.CONNECTION_TYPE_WIFI,
      )
    )
    // Misc Networks
    testConnectivityCase(
      connMgrReturns16 = listOf(
        ConnectivityManager.TYPE_BLUETOOTH,
        ConnectivityManager.TYPE_DUMMY,
        ConnectivityManager.TYPE_VPN,
      ),
      connMgrReturns23 = listOf(
        8, /*not in api23, NetworkCapabilities.TRANSPORT_USB,*/
        6, /*not in api23, NetworkCapabilities.TRANSPORT_LOWPAN*/
        5, /*not in api23, NetworkCapabilities.TRANSPORT_WIFI_AWARE*/
        NetworkCapabilities.TRANSPORT_VPN,
      ),
      correctNetwork = MuxDataSdk.AndroidDevice.CONNECTION_TYPE_OTHER,
      incorrectNetworks = listOf(
        MuxDataSdk.AndroidDevice.CONNECTION_TYPE_WIRED,
        MuxDataSdk.AndroidDevice.CONNECTION_TYPE_CELLULAR,
        MuxDataSdk.AndroidDevice.CONNECTION_TYPE_WIFI,
      )
    )
  }

  private fun testConnectivityCase(
    connMgrReturns16: Collection<Int>,
    connMgrReturns23: Collection<Int>,
    correctNetwork: String,
    incorrectNetworks: List<String>
  ) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      // Correct case
      connMgrReturns23.onEach { transport ->
        testConnectionType23(
          assertMsg = "API 23: network type should be $correctNetwork",
          mockTypeFromConnMgr = transport,
          expectedType = correctNetwork,
          onThisNetwork = true
        )
      }
      // Incorrect cases
      incorrectNetworks.onEach { incorrectNet ->
        connMgrReturns23.onEach { connType ->
          testConnectionType23(
            assertMsg = "API 23: network type should not be $correctNetwork",
            mockTypeFromConnMgr = connType,
            expectedType = incorrectNet,
            onThisNetwork = false
          )
        }
      }
    } else {
      // Correct case
      connMgrReturns16.onEach { transport ->
        testConnectionType16(
          assertMsg = "API 16: network type should be $correctNetwork",
          mockTypeFromConnMgr = transport,
          expectedType = correctNetwork,
          onThisNetwork = true
        )
      }
      // Incorrect cases
      incorrectNetworks.onEach { incorrectNet ->
        connMgrReturns16.onEach { connType ->
          testConnectionType16(
            assertMsg = "API 16: network type should not be $correctNetwork",
            mockTypeFromConnMgr = connType,
            expectedType = incorrectNet,
            onThisNetwork = false
          )
        }
      }
    }
  }

  @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
  private fun testConnectionType16(
    assertMsg: String,
    mockTypeFromConnMgr: Int,
    expectedType: String,
    onThisNetwork: Boolean,
  ) {
    val ctx = mockActivity(connMgr = mockConnectivityManager16(mockTypeFromConnMgr))
    if (onThisNetwork) {
      assertEquals(assertMsg, expectedType, device(ctx).networkConnectionType)
    } else {
      assertNotEquals(assertMsg, expectedType, device(ctx).networkConnectionType)
    }
  }

  @TargetApi(Build.VERSION_CODES.M)
  private fun testConnectionType23(
    assertMsg: String,
    mockTypeFromConnMgr: Int,
    expectedType: String,
    onThisNetwork: Boolean,
  ) {
    val ctx = mockActivity(connMgr = mockConnectivityManager23(mockTypeFromConnMgr))
    if (onThisNetwork) {
      assertEquals(assertMsg, expectedType, device(ctx).networkConnectionType)
    } else {
      assertNotEquals(assertMsg, expectedType, device(ctx).networkConnectionType)
    }
  }

  private fun device(context: Context) = MuxDataSdk.AndroidDevice(
    ctx = context,
    playerVersion = "1.2.3-playerV",
    muxPluginName = "unit-tests",
    muxPluginVersion = "4.5.6-pluginV",
    playerSoftware = "any-player"
  )
}
