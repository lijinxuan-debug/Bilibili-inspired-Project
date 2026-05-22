package com.example.bilibili

import android.app.Application
import com.example.bilibili.util.SPUtils
import com.example.bilibili.util.PlayerContainerSizer
import com.shuyu.gsyvideoplayer.player.PlayerFactory
import com.example.bilibili.ui.playVideo.BiliExo2PlayerManager

class BiliBiliApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SPUtils.init(this)
        PlayerContainerSizer.ensureDefaultShowType()
        // m3u8 使用 ExoPlayer（Media3），HLS seek/时长比默认 IJK 更稳
        PlayerFactory.setPlayManager(BiliExo2PlayerManager::class.java)
    }
}
