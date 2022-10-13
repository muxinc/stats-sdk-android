package com.mux.core_android

import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.mux.core_android.testdoubles.mockConnectivityManager16
import com.mux.core_android.testdoubles.mockConnectivityManager23
import com.mux.stats.sdk.muxstats.IDevice
import com.mux.stats.sdk.muxstats.MuxDataSdk
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AndroidDeviceTests {

  @Test
  fun testConnectionTypeIsCellular() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      // Should be on Cellular
      testConnectionType23(
        "api 23: cellular network detected",
        ConnectivityManager.TYPE_MOBILE,
        MuxDataSdk.AndroidDevice.CONNECTION_TYPE_CELLULAR,
        true
      )
      // Should not be on these
      testConnectionType23(
        "api 23: cellular network detected",
        ConnectivityManager.TYPE_MOBILE,
        MuxDataSdk.AndroidDevice.CONNECTION_TYPE_CELLULAR,
        false
      )
      testConnectionType23(
        "api 23: cellular network detected",
        ConnectivityManager.TYPE_MOBILE,
        MuxDataSdk.AndroidDevice.CONNECTION_TYPE_CELLULAR,
        false
      )
      testConnectionType23(
        "api 23: cellular network detected",
        ConnectivityManager.TYPE_MOBILE,
        MuxDataSdk.AndroidDevice.CONNECTION_TYPE_CELLULAR,
        false
      )
    } else {
      // Should be on Cellular
      testConnectionType16(
        "api 16: cellular network detected",
        NetworkCapabilities.TRANSPORT_CELLULAR,
        MuxDataSdk.AndroidDevice.CONNECTION_TYPE_CELLULAR,
        true
      )
    }
  }

  @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
  private fun testConnectionType16(
    assertMsg: String,
    typeFromConnMgr:Int,
    expectedType: String,
    onThisNetwork: Boolean,
  ) {
    val ctx = mockk<Activity> {
      every { getSystemService(ConnectivityManager::class.java) } returns mockConnectivityManager16(
        typeFromConnMgr
      )
    }
    if (onThisNetwork) {
      assertEquals(assertMsg, expectedType, device(ctx).networkConnectionType)
    } else {
      assertNotEquals(assertMsg, expectedType, device(ctx).networkConnectionType)
    }
  }

  @TargetApi(Build.VERSION_CODES.M)
  private fun testConnectionType23(
    assertMsg: String,
    typeFromConnMgr:Int,
    expectedType: String,
    onThisNetwork: Boolean,
  ) {
    val ctx = mockk<Activity> {
      every { getSystemService(ConnectivityManager::class.java) } returns mockConnectivityManager23(
        typeFromConnMgr
      )
    }
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
