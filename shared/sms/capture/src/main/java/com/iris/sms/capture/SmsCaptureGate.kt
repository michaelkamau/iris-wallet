package com.iris.sms.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.iris.data.datastore.DatastoreKeys
import com.iris.domain.usecase.sms.SmsCaptureGate
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Android half of [SmsCaptureGate]: one preference and two runtime permissions.
 *
 * The permission is re-read on every call rather than cached, because the user can revoke it from
 * system settings while the app is alive and a cached "yes" would turn that revocation into a
 * crash on the next broadcast.
 */
@Singleton
class AndroidSmsCaptureGate @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
) : SmsCaptureGate {

    /**
     * An absent preference reads `false`. That single default is the whole of SC-010: nobody who
     * upgrades into this release has their messages read until they ask for it.
     */
    override suspend fun isEnabled(): Boolean =
        dataStore.data.first()[DatastoreKeys.SMS_CAPTURE_ENABLED] == true

    override fun hasReceivePermission(): Boolean = granted(Manifest.permission.RECEIVE_SMS)

    override fun canReadInbox(): Boolean = granted(Manifest.permission.READ_SMS)

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SmsCaptureGateModule {
    @Binds
    @Singleton
    abstract fun gate(impl: AndroidSmsCaptureGate): SmsCaptureGate
}
