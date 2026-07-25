package com.iris.wallet.di

import com.iris.domain.AppStarter
import com.iris.wallet.IrisAppStarter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindingsModule {
    @Binds
    abstract fun appStarter(appStarter: IrisAppStarter): AppStarter
}
