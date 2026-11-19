package com.naatdelay.dd7.di

import android.content.Context
import androidx.room.Room
import com.naatdelay.dd7.data.local.AppDatabase
import com.naatdelay.dd7.data.local.dao.ExportDao
import com.naatdelay.dd7.data.local.dao.LicenseDao
import com.naatdelay.dd7.data.local.dao.PresetDao
import com.naatdelay.dd7.data.local.dao.ProjectDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "dd7x_vocal_delay.db")
            .addCallback(AppDatabase.seedCallback())
            .build()

    @Provides fun providePresetDao(db: AppDatabase): PresetDao = db.presetDao()
    @Provides fun provideProjectDao(db: AppDatabase): ProjectDao = db.projectDao()
    @Provides fun provideExportDao(db: AppDatabase): ExportDao = db.exportDao()
    @Provides fun provideLicenseDao(db: AppDatabase): LicenseDao = db.licenseDao()
}
