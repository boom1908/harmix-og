package com.boom.harmix.ui.components

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/**
 * While this composable is on screen the display will not switch off by itself.
 * Used for the lyrics view so people can read along without tapping the screen.
 */
@Composable
fun KeepScreenOn(enabled: Boolean = true) {
    val context = LocalContext.current
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window
        ?: (context as? Activity)?.window
    DisposableEffect(enabled, window) {
        if (enabled) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}