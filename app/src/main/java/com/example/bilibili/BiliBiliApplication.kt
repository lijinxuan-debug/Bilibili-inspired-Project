package com.example.bilibili

import android.app.Application
import com.example.bilibili.util.SPUtils

class BiliBiliApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SPUtils.init(this)
    }
}