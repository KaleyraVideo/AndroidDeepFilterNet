package com.kaleyra.noise_filter

import android.content.Context
import android.util.Log
import com.kaleyra.noise_filter.model_loader.DeepFilterModelLoader
import fetcher.S3ModelFetcher
import com.kaleyra.video_utils.dispatcher.DispatcherProvider
import com.kaleyra.video_utils.dispatcher.StandardDispatchers
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Default implementation of [DeepFilterModelLoader] that loads the deep filter model
 * from the application's files directory. If the model is not present, it is
 * downloaded from an S3 bucket.
 */
class DefaultDeepFilterModelLoader(
    private val dispatchers: DispatcherProvider = StandardDispatchers
) : DeepFilterModelLoader {

    override suspend fun load(context: Context): ByteArray? = withContext(dispatchers.io) {
        var httpClient: HttpClient? = null
        var modelFile: File? = null

        return@withContext try {
            httpClient = HttpClient(Android)
            modelFile = File(context.filesDir, MODEL_NAME).apply { createNewFile() }

            val modelFetcher = S3ModelFetcher(httpClient)

            if (isModelAvailable(modelFile) || modelFetcher.fetchModel(modelFile)) {
                Log.i(
                    TAG,
                    "Model file is available (cached or successfully downloaded). Reading bytes."
                )
                modelFile.readBytes()
            } else {
                if (modelFile.exists()) modelFile.delete()
                Log.i(TAG, "Failed to make model file available (not cached and download failed).")
                null
            }
        } catch (e: IOException) {
            modelFile?.delete()
            Log.e(TAG, "IOException while loading model file: ${e.message}", e)
            null
        } catch (e: Throwable) {
            modelFile?.delete()
            Log.e(TAG, "Unexpected error while loading model file: ${e.message}", e)
            null
        } finally {
            httpClient?.close()
        }
    }

    /**
     * Checks if a model file is available.
     *
     * @param file The [File] object representing the model file.
     * @return `true` if the model file is available, `false` otherwise.
     */
    private fun isModelAvailable(file: File): Boolean = file.exists() && file.length() > 0

    companion object {
        private const val TAG = "DeepFilterModelProvider"

        private const val MODEL_NAME = "deep-filter-mobile-model"
    }
}
