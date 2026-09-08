package com.yourdomain.scheduleapp

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ScheduleApplication : Application() {
    override fun onCreate() { super.onCreate(); PDFBoxResourceLoader.init(applicationContext) }
}
