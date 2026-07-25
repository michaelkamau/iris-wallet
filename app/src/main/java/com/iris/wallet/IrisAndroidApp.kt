package com.iris.wallet

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.iris.base.legacy.appContext
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import timber.log.Timber.DebugTree
import javax.inject.Inject

/**
 * Created by iliyan on 24.02.18.
 */
@HiltAndroidApp
class IrisAndroidApp : Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        appContext = this

        if (BuildConfig.DEBUG) {
            Timber.plant(DebugTree())
        }
    }
}
