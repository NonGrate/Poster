package com.example.poster.util

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/**
 * Custom test runner that uses TestApplication for dependency injection
 * This ensures all instrumented tests use the proper Koin configuration
 */
class TestInstrumentationRunner : AndroidJUnitRunner() {

    override fun newApplication(cl: ClassLoader?, name: String?, context: Context?): Application {
        return super.newApplication(cl, TestApplication::class.java.name, context)
    }

    override fun onStart() {
        applyServerFixtures()
        super.onStart()
    }
}
