package com.nabd.app.service

object AppForegroundTracker {
    @Volatile
    var isInForeground: Boolean = false
}
