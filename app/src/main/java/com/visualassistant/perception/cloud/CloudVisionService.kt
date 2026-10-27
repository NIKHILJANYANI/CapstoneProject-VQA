package com.visualassistant.perception.cloud

import android.graphics.Bitmap
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * CloudVisionService handles communication with the Gemini API.
 * It is used for rich scene descriptions when the user asks for high-level semantic info.
 */
class CloudVisionService(apiKey: String) {

    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = apiKey
    )

    /**
     * Send a bitmap to Gemini for detailed description.
     */
    suspend fun describeImage(bitmap: Bitmap, prompt: String): String? = withContext(Dispatchers.IO) {
        try {
            val response = generativeModel.generateContent(
                content {
                    image(bitmap)
                    text(prompt)
                }
            )
            response.text
        } catch (e: Exception) {
            Log.e(TAG, "Gemini API error: ${e.message}")
            null
        }
    }

    companion object {
        private const val TAG = "CloudVisionService"
    }
}
