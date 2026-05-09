package com.newoether.agora.api

import android.util.Log
import java.io.File

object LlamaEngine {
    private const val TAG = "LlamaEngine"

    init {
        System.loadLibrary("c++_shared")
        System.loadLibrary("agora_llama")
    }

    private external fun nativeLoadModel(path: String): Long
    private external fun nativeFreeModel(handle: Long)
    private external fun nativeComputeEmbedding(handle: Long, text: String): FloatArray?
    private external fun nativeGetEmbeddingDim(handle: Long): Int

    fun isModelReady(modelPath: String): Boolean {
        return modelPath.isNotBlank() && File(modelPath).exists() && File(modelPath).length() > 0
    }

    fun computeEmbedding(text: String, modelPath: String): FloatArray? {
        val start = System.currentTimeMillis()
        val handle = nativeLoadModel(modelPath)
        if (handle == 0L) {
            Log.e(TAG, "Failed to load model (${System.currentTimeMillis() - start}ms): $modelPath")
            return null
        }
        Log.d(TAG, "Model loaded in ${System.currentTimeMillis() - start}ms, dim=${nativeGetEmbeddingDim(handle)}")
        return try {
            val embd = nativeComputeEmbedding(handle, text)
            if (embd == null) {
                Log.e(TAG, "nativeComputeEmbedding returned null for text len=${text.length}")
            } else {
                Log.d(TAG, "Embedding computed: dim=${embd.size}, elapsed=${System.currentTimeMillis() - start}ms")
            }
            embd
        } catch (e: Exception) {
            Log.e(TAG, "Embedding computation crashed", e)
            null
        } finally {
            nativeFreeModel(handle)
        }
    }

    fun computeEmbeddings(texts: List<String>, modelPath: String): List<FloatArray?> {
        if (texts.isEmpty()) return emptyList()
        val start = System.currentTimeMillis()
        val handle = nativeLoadModel(modelPath)
        if (handle == 0L) {
            Log.e(TAG, "Failed to load model (${System.currentTimeMillis() - start}ms): $modelPath")
            return texts.map { null }
        }
        Log.d(TAG, "Model loaded in ${System.currentTimeMillis() - start}ms, dim=${nativeGetEmbeddingDim(handle)}")
        return try {
            texts.map { text ->
                val embd = nativeComputeEmbedding(handle, text)
                if (embd == null) {
                    Log.e(TAG, "nativeComputeEmbedding returned null for text len=${text.length}")
                }
                embd
            }
        } catch (e: Exception) {
            Log.e(TAG, "Batch embedding crashed", e)
            texts.map { null }
        } finally {
            nativeFreeModel(handle)
            Log.d(TAG, "Batch: ${texts.size} embeddings in ${System.currentTimeMillis() - start}ms")
        }
    }
}
