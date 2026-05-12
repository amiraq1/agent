package com.newoether.agora.util

import android.content.Context

object DebugLog {
    @Volatile
    private var enabled = true

    fun init(context: Context) {
        enabled = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    fun d(tag: String, msg: String) { if (enabled) android.util.Log.d(tag, msg) }
    fun d(tag: String, msg: String, tr: Throwable) { if (enabled) android.util.Log.d(tag, msg, tr) }
    fun e(tag: String, msg: String) { if (enabled) android.util.Log.e(tag, msg) }
    fun e(tag: String, msg: String, tr: Throwable) { if (enabled) android.util.Log.e(tag, msg, tr) }
    fun w(tag: String, msg: String) { if (enabled) android.util.Log.w(tag, msg) }
    fun w(tag: String, msg: String, tr: Throwable) { if (enabled) android.util.Log.w(tag, msg, tr) }
}
