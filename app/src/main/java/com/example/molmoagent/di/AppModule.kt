package com.example.molmoagent.di

import android.content.Context
import androidx.room.Room
import com.example.molmoagent.data.AppDatabase
import com.example.molmoagent.data.StepDao
import com.example.molmoagent.data.TaskDao
import com.example.molmoagent.inference.InferenceClient
import com.example.molmoagent.inference.InferenceClientManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "molmo_agent_db"
        ).build()
    }

    @Provides
    fun provideTaskDao(database: AppDatabase): TaskDao = database.taskDao()

    @Provides
    fun provideStepDao(database: AppDatabase): StepDao = database.stepDao()
}

@Module
@InstallIn(SingletonComponent::class)
object InferenceModule {

    @Provides
    @Singleton
    fun provideInferenceClient(manager: InferenceClientManager): InferenceClient = manager
}
