package com.iris.ui.di

import com.iris.ui.time.DevicePreferences
import com.iris.ui.time.TimeFormatter
import com.iris.ui.time.impl.AndroidDateTimePicker
import com.iris.ui.time.impl.AndroidDevicePreferences
import com.iris.ui.time.impl.DateTimePicker
import com.iris.ui.time.impl.IrisTimeFormatter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface IrisUiBindings {
    @Binds
    fun timeFormatter(impl: IrisTimeFormatter): TimeFormatter

    @Binds
    fun deviceTimePreferences(impl: AndroidDevicePreferences): DevicePreferences

    @Binds
    fun dateTimePicker(impl: AndroidDateTimePicker): DateTimePicker
}