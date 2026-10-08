package com.boom.harmix

import android.app.Application
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HarmixApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        isReady = true
    }

    companion object {
        @Volatile
        var isReady: Boolean = false
            private set
    }
}
