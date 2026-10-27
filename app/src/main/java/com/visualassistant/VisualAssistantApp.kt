package com.visualassistant

import android.app.Application
import android.util.Log

/**
 * Application class for Visual Assistant.
 * Initializes global singletons and handles application-level configuration.
 */
class VisualAssistantApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "VisualAssistantApp started")
    }

    companion object {
        private const val TAG = "VisualAssistantApp"
    }
}
