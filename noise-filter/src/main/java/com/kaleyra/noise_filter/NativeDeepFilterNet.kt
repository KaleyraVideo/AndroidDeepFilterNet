package com.rikorose.deepfilternet

import android.content.Context
import android.util.Log
import com.kaleyra.noise_filter.DeepFilterNet
import com.kaleyra.noise_filter.R
import com.kaleyra.video_utils.dispatcher.DispatcherProvider
import com.kaleyra.video_utils.dispatcher.StandardDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference

/**
 * Manages the native DeepFilterNet library for audio noise reduction.
 *
 * This class handles the loading, processing, and releasing of the DeepFilterNet native library,
 * providing functionalities for noise reduction on audio buffers.
 */
class NativeDeepFilterNet(
    context: Context,
    attenuationLimit: Float = DEFAULT_ATTENUATION_LIMIT,
    private val dispatchers: DispatcherProvider = StandardDispatchers
) : DeepFilterNet {

    /** Pointer to the native DeepFilterNet state. */
    private var nativePointer: Long? = null

    /** Atomic reference for the callback to be invoked when the model is loaded. */
    private var onModelLoadedCallback: AtomicReference<((DeepFilterNet) -> Unit)?> = AtomicReference(null)

    init {
        CoroutineScope(dispatchers.main).launch {
            runCatching {
                val modelBytes = loadModelBytes(context)
                val nativePointer = withContext(dispatchers.io) { newNative(modelBytes, attenuationLimit) }
                nativePointer
            }.onSuccess {
                nativePointer = it
                Log.i(TAG, "DeepFilter model loaded successfully.")
                onModelLoadedCallback.get()?.invoke(this@NativeDeepFilterNet)
                onModelLoadedCallback.set(null)
            }.onFailure { ex ->
                Log.e(TAG, "Failed to initialize DeepFilter native state: ${ex.message}")
            }
        }
    }

    /**
     * Loads the DeepFilterNet model bytes from the raw resources.
     *
     * @param context The Android Context to access resources.
     * @return The model bytes as a ByteArray.
     */
    private suspend fun loadModelBytes(context: Context): ByteArray = withContext(dispatchers.io) {
        context.resources.openRawResource(R.raw.deep_filter_mobile_model).use { it.readBytes() }
    }

    /**
     * Gets the frame length required by the DeepFilterNet model.
     *
     * @return The frame length, or -1L if the model is not loaded.
     */
    override val frameLength: Long
        get() = nativePointer?.let { getFrameLengthNative(it) } ?: -1L

    /**
     * Sets the attenuation limit (threshold) for noise reduction.
     *
     * @param thresholdDb The noise attenuation limit in decibels (dB).
     */
    override fun setAttenuationLimit(thresholdDb: Float): Boolean {
        return nativePointer?.let {
            val result = setAttenLimNative(it, thresholdDb)
            Log.d(TAG, "Attenuation limit set to: $thresholdDb dB.")
            result
        } ?: run {
            Log.w(TAG, "DeepFilter model is not loaded yet. Cannot set attenuation limit.")
            false
        }
    }

    /**
     * Sets the post-filter beta value.
     *
     * @param beta The post-filter beta value.
     */
    override fun setPostFilterBeta(beta: Float): Boolean {
        return nativePointer?.let {
            val result = setPostFilterBetaNative(it, beta)
            Log.d(TAG, "Post-filter beta set to: $beta.")
            result
        } ?: run {
            Log.w(TAG, "DeepFilter model is not loaded yet. Cannot set post-filter beta.")
            false
        }
    }

    /**
     * Processes an audio frame using the DeepFilterNet model.
     *
     * @param inputFrame The input audio frame as a ByteBuffer.
     * @return The processed frame signal to noise ratio as a float, or -1f if the model is not loaded.
     */
    override fun processFrame(inputFrame: ByteBuffer): Float {
        return nativePointer?.let { processFrameNative(it, inputFrame) } ?: run {
            Log.w(TAG, "DeepFilter model is not loaded yet. Cannot process frame.")
            -1f
        }
    }

    /**
     * Releases the resources associated with the DeepFilterNet model.
     */
    override fun release() {
        nativePointer?.let {
            freeNative(it)
            nativePointer = null
            Log.i(TAG, "DeepFilter model resources released.")
        }
    }

    /**
     * Sets a callback to be invoked when the DeepFilterNet model is loaded.
     *
     * If the model is already loaded, the callback is invoked immediately.
     *
     * @param block The callback to be invoked.
     */
    override fun onModelLoaded(block: (DeepFilterNet) -> Unit) {
        nativePointer?.let {
            block(this)
            Log.d(TAG, "Model loaded callback invoked immediately.")
        } ?: run {
            onModelLoadedCallback.set(block)
            Log.d(TAG, "Model loaded callback set.")
        }
    }

    /** Native function to create a new DeepFilterNet state. */
    private external fun newNative(modelBytes: ByteArray, attenuationLimit: Float): Long

    /** Native function to get the frame length. */
    private external fun getFrameLengthNative(statePtr: Long): Long

    /** Native function to set the attenuation limit. */
    private external fun setAttenLimNative(statePtr: Long, limDb: Float): Boolean

    /** Native function to set the post-filter beta. */
    private external fun setPostFilterBetaNative(statePtr: Long, beta: Float): Boolean

    /** Native function to process an audio frame. */
    private external fun processFrameNative(statePtr: Long, inputFrame: ByteBuffer): Float

    /** Native function to release the DeepFilterNet state. */
    private external fun freeNative(statePtr: Long)

    companion object {
        private const val TAG = "NativeDeepFilterNet"

        /** Default attenuation limit for noise reduction. */
        const val DEFAULT_ATTENUATION_LIMIT = 50f

        init {
            System.loadLibrary("df")
        }
    }
}
