package com.halqa.app

import android.app.Application
import com.google.firebase.FirebaseApp
import com.halqa.app.data.Analytics
import com.halqa.app.data.AuthPrefs
import com.halqa.app.data.AuthRepository
import com.halqa.app.data.OnboardingPrefs
import com.halqa.app.data.SettingsPrefs

class HalqaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        Analytics.init(this)
        AuthPrefs.init(this)
        SettingsPrefs.init(this)
        // Lina — founder banner / first-event flags need to be readable
        // from the moment Main is first composed; initialise before
        // AuthRepository.bootstrap() so any return sign-in (which can
        // happen inside the bootstrap) finds the prefs already open.
        OnboardingPrefs.init(this)
        AuthRepository.bootstrap()
    }
}
