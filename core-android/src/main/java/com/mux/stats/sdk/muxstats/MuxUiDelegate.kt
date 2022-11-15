package com.mux.stats.sdk.muxstats

import android.annotation.TargetApi
import android.app.Activity
import android.graphics.Point
import android.os.Build
import android.view.View
import android.view.WindowInsets
import com.mux.stats.sdk.core.util.MuxLogger
import com.mux.stats.sdk.muxstats.internal.weak

/**
 * Allows implementers to supply data about the view and screen being used for playback
 */
abstract class MuxUiDelegate<PlayerView>(view: PlayerView?) {
  var view by weak(view)

  /**
   * Gets the size of the player view in px as a pair of (width, height)
   * If {@link #view} is non-null returns the size of the player view
   * It (@link #view} is null, returns size of 0
   */
  abstract fun getPlayerViewSize(): Point

  /**
   * Gets the sie of the entire screen in px, not including nav/status bar/window insets
   * If the View is null, returns a size of 0
   */
  abstract fun getScreenSize(): Point

  /**
   * Gets the ID of the View being used for displaying video content. This can be [View.NO_ID] if
   * there is no view
   */
  abstract fun getViewId(): Int

  /**
   * Returns the overall density of the host display, if applicable
   */
  abstract fun displayDensity(): Float
}

/**
 * MuxViewDelegate for an Android View.
 */
private class AndroidUiDelegate<PlayerView : View>(activity: Activity?, view: PlayerView?) :
  MuxUiDelegate<PlayerView>(view) {

  private val _screenSize: Point = activity?.let { screenSize(it) } ?: Point()
  private val displayDensity = activity?.resources?.displayMetrics?.density ?: 0F

  override fun getPlayerViewSize(): Point = view?.let { view ->
    Point().apply {
      x = view.width
      y = view.height
    }
  } ?: Point()

  override fun getScreenSize(): Point = _screenSize

  private fun screenSize(activity: Activity): Point {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      screenSizeApiR(activity)
    } else {
      screenSizeLegacy(activity)
    }
  }

  @TargetApi(Build.VERSION_CODES.R)
  private fun screenSizeApiR(activity: Activity): Point {
    val windowBounds = activity.windowManager.currentWindowMetrics.bounds
      .let { Point(it.width(), it.height()) }
    val windowInsets = activity.windowManager.currentWindowMetrics.windowInsets
      .getInsetsIgnoringVisibility(
        WindowInsets.Type.navigationBars() or WindowInsets.Type.displayCutout()
      )

    // Return a minimum-size for fullscreen, as not all apps hide system UI
    return Point().apply {
      x = windowBounds.x - (windowInsets.right + windowInsets.left)
      y = windowBounds.y - (windowInsets.top + windowInsets.bottom)
    }
  }

  private fun screenSizeLegacy(activity: Activity): Point {
    return Point().let { size ->
      @Suppress("DEPRECATION") // bounds - insets method is used on API 30+
      activity.windowManager?.defaultDisplay?.getSize(size)
      size
    }.also { size ->
      MuxLogger.d(
        javaClass.simpleName,
        "displayStuffLegacy: Legacy Screen Size Size: $size"
      )
    }
  }

  override fun getViewId(): Int {
    return view?.id ?: View.NO_ID
  }

  override fun displayDensity(): Float = displayDensity
}

/**
 * Create a MuxUiDelegate based on a View
 */
@Suppress("unused")
@JvmSynthetic
internal fun <V : View> V?.muxUiDelegate(activity: Activity?)
        : MuxUiDelegate<View> = AndroidUiDelegate(activity, this)

/**
 * Create a MuxUiDelegate for a view-less playback experience. Returns 0 for all sizes, as we are
 * not able to get a Display from a non-activity context
 */
@Suppress("unused")
@JvmSynthetic
internal fun noUiDelegate(): MuxUiDelegate<View> = AndroidUiDelegate(null, null)
