package com.example.bilibili.util

import java.util.Locale

/**
 * 视频数据展示工具类
 * 统一处理播放量、时长、日期的格式化逻辑
 */
object VideoDataUtils {

    /**
     * 格式化数字（播放量）
     * 规则：超过一万显示 1.x万，超过一亿显示 1.x亿
     */
    fun formatCount(count: Int): String {
        return when {
            count >= 100000000 -> {
                String.format(Locale.getDefault(), "%.1f亿", count / 100000000.0)
            }
            count >= 10000 -> {
                String.format(Locale.getDefault(), "%.1f万", count / 10000.0)
            }
            else -> count.toString()
        }
    }

    /**
     * 格式化视频时长
     * 规则：超过1小时显示 00:00:00，不满1小时显示 00:00
     */
    fun formatDuration(seconds: Int?): String {
        if (seconds == null || seconds <= 0) return "00:00"
        
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        
        return if (h > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", m, s)
        }
    }

    /**
     * 格式化日期（针对中文字符串格式）
     * 输入示例: "2026年4月21日" 或 "2026年04月21日 18:00"
     * 输出示例: "4月21日"
     */
    fun formatDate(timeStr: String?): String {
        if (timeStr.isNullOrBlank()) return ""
        
        return try {
            // 逻辑：找到“年”字的位置，取其后面的所有内容
            if (timeStr.contains("年")) {
                val result = timeStr.substringAfter("年")
                // 如果后面带了具体时间（比如有空格），只取日期部分
                if (result.contains(" ")) {
                    result.substringBefore(" ")
                } else {
                    result
                }
            } else {
                // 如果不含“年”，尝试按常规横杠格式截取
                if (timeStr.length >= 10 && timeStr.contains("-")) {
                    timeStr.substring(5, 10)
                } else {
                    timeStr
                }
            }
        } catch (e: Exception) {
            timeStr ?: ""
        }
    }
}