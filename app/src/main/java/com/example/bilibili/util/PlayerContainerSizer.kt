package com.example.bilibili.util

import com.shuyu.gsyvideoplayer.utils.GSYVideoType

/** 播放器展示：固定高度由布局 270dp 决定，此处仅保证按比例适配不拉伸。 */
object PlayerContainerSizer {

    fun ensureDefaultShowType() {
        GSYVideoType.setShowType(GSYVideoType.SCREEN_TYPE_DEFAULT)
    }
}
