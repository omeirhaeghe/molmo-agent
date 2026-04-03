package com.example.molmoagent.inference.local

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlamaCppBridge @Inject constructor() {

    companion object {
        private const val TAG = "LlamaCppBridge"

        var isAvailable: Boolean = false
            private set

        init {
            try {
                System.loadLibrary("llama_jni")
                isAvailable = true
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load llama_jni native library", e)
                isAvailable = false
            }
        }
    }

    external fun loadModel(
        modelPath: String,
        mmprojPath: String,
        nCtx: Int = 2048,
        nThreads: Int = 4
    ): Boolean

    external fun runVisionInference(
        imageBytes: ByteArray,
        prompt: String,
        maxTokens: Int = 512,
        temperature: Float = 0.1f
    ): String

    external fun unloadModel()

    external fun isModelLoaded(): Boolean
}
