package io.ciphertun.ghi.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. Hilt-annotated so the dependency graph
 * (network client, database, repositories — wired up per-module as each
 * core module gains real implementations) is available app-wide.
 */
@HiltAndroidApp
class GhiApplication : Application()
