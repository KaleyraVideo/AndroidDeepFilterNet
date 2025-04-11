package com.kaleyra.noise_filter

import java.nio.ByteBuffer

/**
 * Interface for managing the DeepFilterNet audio noise reduction library.
 *
 * This interface defines the contract for classes that handle the loading,
 * processing, and releasing of the DeepFilterNet library. It provides
 * functionalities for noise reduction on audio buffers.
 */
interface DeepFilterNet {

    /**
     * Gets the frame length required by the DeepFilterNet model.
     *
     * @return The frame length, or -1L if the model is not loaded.
     */
    val frameLength: Long

    /**
     * Sets the attenuation limit (threshold) for noise reduction.
     *
     * @param thresholdDb The noise attenuation limit in decibels (dB).
     */
    fun setAttenuationLimit(thresholdDb: Float): Boolean

    /**
     * Sets the post-filter beta value.
     *
     * @param beta The post-filter beta value.
     */
    fun setPostFilterBeta(beta: Float): Boolean

    /**
     * Processes an audio frame using the DeepFilterNet model.
     *
     * @param inputFrame The input audio frame as a ByteBuffer.
     * @return The processed frame signal to noise ratio as a float, or -1f if the model is not loaded.
     */
    fun processFrame(inputFrame: ByteBuffer): Float

    /**
     * Releases the resources associated with the DeepFilterNet model.
     */
    fun release()

    /**
     * Sets a callback to be invoked when the DeepFilterNet model is loaded.
     *
     * If the model is already loaded, the callback is invoked immediately.
     *
     * @param block The callback to be invoked.
     */
    fun onModelLoaded(block: (DeepFilterNet) -> Unit)
}
