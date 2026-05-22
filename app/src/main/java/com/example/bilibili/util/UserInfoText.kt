package com.example.bilibili.util

import org.json.JSONObject

/**
 * 用户信息文案：过滤后端 null / "null"，展示用默认占位。
 */
object UserInfoText {

    fun normalize(raw: String?): String {
        val s = raw?.trim().orEmpty()
        return if (s.isEmpty() || s.equals("null", ignoreCase = true)) "" else s
    }

    fun displayIntroduction(raw: String?): String =
        normalize(raw).ifEmpty { DEFAULT_INTRO }

    fun displaySchool(raw: String?): String =
        normalize(raw).ifEmpty { DEFAULT_SCHOOL }

    fun displayBirthday(raw: String?): String =
        normalize(raw).ifEmpty { DEFAULT_BIRTHDAY }

    /** 写入本地缓存或提交前：空值用注册默认文案 */
    fun storageIntroduction(raw: String?): String =
        normalize(raw).ifEmpty { DEFAULT_INTRO }

    fun storageSchool(raw: String?): String =
        normalize(raw).ifEmpty { DEFAULT_SCHOOL }

    const val DEFAULT_INTRO = "这个家伙很懒，什么也没留下"
    const val DEFAULT_SCHOOL = "门头沟学院"
    const val DEFAULT_BIRTHDAY = "未设置"
}

fun JSONObject.optNormalizedString(key: String): String =
    UserInfoText.normalize(optString(key, ""))
