package com.yourdomain.scheduleapp

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton fun db(@ApplicationContext c:Context)=Room.databaseBuilder(c,AppDatabase::class.java,"schedule.db").build()
    @Provides fun scheduleDao(db:AppDatabase)=db.scheduleDao()
    @Provides fun imageDao(db:AppDatabase)=db.vkImageDao()
    @Provides @Singleton fun vkApi():VkApiService=Retrofit.Builder().baseUrl("https://api.vk.com/method/").addConverterFactory(GsonConverterFactory.create()).build().create(VkApiService::class.java)
}
