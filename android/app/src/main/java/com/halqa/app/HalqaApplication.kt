package com.halqa.app

import android.app.Application
import com.google.firebase.FirebaseApp
import com.halqa.app.data.AuthPrefs
import com.halqa.app.data.AuthRepository
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HalqaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        AuthPrefs.init(this)
        AuthRepository.bootstrap()
    }
}
