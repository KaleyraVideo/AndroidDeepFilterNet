package com.kaleyra.noise_filter.model_loader

import android.content.Context

/**
 * Interface for loading the deep filter model data.
 *
 * Implementations of this interface are responsible for providing the byte array
 * containing the deep filter model. This allows for flexibility in how the model
 * is sourced (e.g., from a file, network, or assets).
 */
interface DeepFilterModelLoader {
    suspend fun load(context: Context): ByteArray?
}
