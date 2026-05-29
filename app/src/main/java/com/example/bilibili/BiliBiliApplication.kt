package com.example.bilibili

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.example.bilibili.ui.playVideo.BiliExo2PlayerManager
import com.example.bilibili.util.PlayerContainerSizer
import com.example.bilibili.util.SPUtils
import com.example.bilibili.util.TextSelectHandleHelper
import com.shuyu.gsyvideoplayer.player.PlayerFactory

class BiliBiliApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SPUtils.init(this)
        PlayerContainerSizer.ensureDefaultShowType()
        // m3u8 使用 ExoPlayer（Media3），HLS seek/时长比默认 IJK 更稳
        PlayerFactory.setPlayManager(BiliExo2PlayerManager::class.java)
        registerActivityLifecycleCallbacks(EditTextHandleLifecycleCallbacks())
    }

    private class EditTextHandleLifecycleCallbacks : ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) {
            TextSelectHandleHelper.applyPinkHandlesIn(activity)
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}
