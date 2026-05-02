package com.halqa.app

import android.app.Application
import com.halqa.app.data.AuthPrefs
import com.halqa.app.data.AuthRepository
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HalqaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialise the staff-session prefs file *before* the repository
        // tries to rehydrate from disk; the order matters because
        // AuthRepository.bootstrap() is otherwise a no-op when prefs is null.
        AuthPrefs.init(this)
        AuthRepository.bootstrap()
    }
}
