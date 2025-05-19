package com.kaleyra.noise_filter

import android.content.Context
import android.util.Log
import com.kaleyra.noise_filter.model_loader.DeepFilterModelLoader
import com.kaleyra.video_utils.dispatcher.DispatcherProvider
import com.kaleyra.video_utils.dispatcher.StandardDispatchers
import kotlinx.coroutines.withContext

class DefaultDeepFilterModelLoader(
    private val dispatchers: DispatcherProvider = StandardDispatchers
) : DeepFilterModelLoader {

    override suspend fun load(context: Context): ByteArray? = withContext(dispatchers.io) {
        return@withContext try {
            context.resources.openRawResource(R.raw.deep_filter_mobile_model).use { it.readBytes() }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading deep filter model raw resource", e)
           null
        }
    }

    companion object {
        private const val TAG = "DeepFilterModelProvider"
    }
}