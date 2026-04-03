package com.example.molmoagent.inference.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    sealed class DownloadState {
        data object NotDownloaded : DownloadState()
        data class Downloading(val progressPercent: Int, val description: String = "") : DownloadState()
        data object Downloaded : DownloadState()
        data class Error(val message: String) : DownloadState()
    }

    companion object {
        const val MODEL_FILENAME = "SmolVLM2-2.2B-Instruct-Q4_K_M.gguf"
        const val MMPROJ_FILENAME = "mmproj-SmolVLM2-2.2B-Instruct-f16.gguf"

        private const val BASE_URL = "https://huggingface.co/ggml-org/SmolVLM2-2.2B-Instruct-GGUF/resolve/main"
        const val MODEL_URL = "$BASE_URL/$MODEL_FILENAME"
        const val MMPROJ_URL = "$BASE_URL/$MMPROJ_FILENAME"
    }

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.NotDownloaded)
    val downloadState: StateFlow<DownloadState> = _downloadState

    @Volatile
    private var cancelRequested = false

    private val modelsDir: File
        get() = File(context.filesDir, "models")

    val modelPath: String
        get() = File(modelsDir, MODEL_FILENAME).absolutePath

    val mmprojPath: String
        get() = File(modelsDir, MMPROJ_FILENAME).absolutePath

    val isDownloaded: Boolean
        get() {
            val modelFile = File(modelsDir, MODEL_FILENAME)
            val mmprojFile = File(modelsDir, MMPROJ_FILENAME)
            // Require files to exist and have non-trivial size (> 1MB)
            return modelFile.exists() && modelFile.length() > 1_000_000 &&
                    mmprojFile.exists() && mmprojFile.length() > 1_000_000
        }

    fun checkDownloadStatus(): DownloadState {
        val state = if (isDownloaded) {
            DownloadState.Downloaded
        } else {
            DownloadState.NotDownloaded
        }
        _downloadState.value = state
        return state
    }

    /**
     * Get the total size of downloaded model files in bytes.
     */
    fun getDownloadedSizeBytes(): Long {
        val modelFile = File(modelsDir, MODEL_FILENAME)
        val mmprojFile = File(modelsDir, MMPROJ_FILENAME)
        return (if (modelFile.exists()) modelFile.length() else 0L) +
                (if (mmprojFile.exists()) mmprojFile.length() else 0L)
    }

    suspend fun downloadModels() = withContext(Dispatchers.IO) {
        cancelRequested = false
        modelsDir.mkdirs()

        try {
            // Download main model (~85% of total size)
            _downloadState.value = DownloadState.Downloading(0, "Downloading model...")
            downloadFile(MODEL_URL, File(modelsDir, MODEL_FILENAME)) { progress ->
                if (cancelRequested) throw CancellationException("Download cancelled")
                _downloadState.value = DownloadState.Downloading(
                    (progress * 0.85).toInt(),
                    "Downloading model... ${(progress * 0.85).toInt()}%"
                )
            }

            // Download mmproj (~15% of total size)
            _downloadState.value = DownloadState.Downloading(85, "Downloading vision projector...")
            downloadFile(MMPROJ_URL, File(modelsDir, MMPROJ_FILENAME)) { progress ->
                if (cancelRequested) throw CancellationException("Download cancelled")
                _downloadState.value = DownloadState.Downloading(
                    85 + (progress * 0.15).toInt(),
                    "Downloading vision projector... ${85 + (progress * 0.15).toInt()}%"
                )
            }

            _downloadState.value = DownloadState.Downloaded
        } catch (e: CancellationException) {
            // Clean up partial files
            File(modelsDir, "$MODEL_FILENAME.tmp").delete()
            File(modelsDir, "$MMPROJ_FILENAME.tmp").delete()
            _downloadState.value = DownloadState.NotDownloaded
        } catch (e: Exception) {
            _downloadState.value = DownloadState.Error(e.message ?: "Download failed")
        }
    }

    fun cancelDownload() {
        cancelRequested = true
    }

    suspend fun deleteModels() = withContext(Dispatchers.IO) {
        File(modelsDir, MODEL_FILENAME).delete()
        File(modelsDir, MMPROJ_FILENAME).delete()
        File(modelsDir, "$MODEL_FILENAME.tmp").delete()
        File(modelsDir, "$MMPROJ_FILENAME.tmp").delete()
        _downloadState.value = DownloadState.NotDownloaded
    }

    private fun downloadFile(
        url: String,
        destination: File,
        onProgress: (Int) -> Unit
    ) {
        // Skip if file already exists with non-trivial size
        if (destination.exists() && destination.length() > 1_000_000) {
            onProgress(100)
            return
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url(url)
            .build()

        val tmpFile = File(destination.parent, "${destination.name}.tmp")

        val response = try {
            client.newCall(request).execute()
        } catch (e: Exception) {
            tmpFile.delete()
            throw RuntimeException("Download failed: ${e.message}")
        }

        if (!response.isSuccessful) {
            tmpFile.delete()
            throw RuntimeException("Download failed: HTTP ${response.code}")
        }

        val body = response.body ?: throw RuntimeException("Empty response body")
        val contentLength = body.contentLength()

        try {
            body.byteStream().use { input ->
                FileOutputStream(tmpFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Long = 0

                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        bytesRead += read

                        if (contentLength > 0) {
                            val progress = ((bytesRead * 100) / contentLength).toInt()
                            onProgress(progress.coerceIn(0, 100))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            tmpFile.delete()
            throw e
        }

        // Atomic rename — fall back to copy+delete if rename fails
        if (!tmpFile.renameTo(destination)) {
            tmpFile.copyTo(destination, overwrite = true)
            tmpFile.delete()
        }
        onProgress(100)
    }

    private class CancellationException(message: String) : Exception(message)
}
