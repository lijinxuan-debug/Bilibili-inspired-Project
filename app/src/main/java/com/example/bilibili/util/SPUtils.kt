package com.example.bilibili.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SPUtils {
    private const val SP_NAME = "secure_bili_prefs"
    private const val KEY_TOKEN = "webToken"
    private const val USER_ID = "userId"
    private const val CURRENT_COIN_COUNT = "currentCoinCount"
    private const val NICKNAME = "nickname"
    private const val AVATAR = "avatar"
    private const val SEX = "sex"
    private const val BIRTHDAY = "birthday"
    private const val SCHOOL = "school"
    private const val PERSONAL_INTRODUCTION = "personalIntroduction"
    private const val NOTICE_INFO = "noticeInfo"
    private const val KEY_SEARCH_HISTORY = "search_history_json"
    private const val KEY_DEVICE_ID = "app_device_id"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        try {
            val mainKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            prefs = EncryptedSharedPreferences.create(
                context,
                SP_NAME,
                mainKey, // 传入新的 MasterKey 对象
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            // 兜底方案
            prefs = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        }
    }

    /**
     * 保存token
     */
    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    /**
     * 获取token
     */
    fun getToken(): String {
        return prefs.getString(KEY_TOKEN, "") ?: ""
    }

    /**
     * 清理token（一般退出登录使用）
     */
    fun cleanToken() {
        prefs.edit().remove(KEY_TOKEN).remove(USER_ID).remove(NICKNAME).remove(AVATAR).remove(CURRENT_COIN_COUNT).apply()
    }

    /**
     * 保存用户ID
     */
    fun saveUserId(userId: String) {
        prefs.edit().putString(USER_ID, userId).apply()
    }

    /**
     * 获取用户ID
     */
    fun getUserId(): String {
        return prefs.getString(USER_ID, "") ?: ""
    }

    fun saveDeviceId(deviceId: String) {
        prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
    }

    fun getDeviceId(): String {
        return prefs.getString(KEY_DEVICE_ID, "") ?: ""
    }

    /**
     * 保存当前硬币数量
     */
    fun saveCurrentCoinCount(count: Int) {
        prefs.edit().putInt(CURRENT_COIN_COUNT, count).apply()
    }

    /**
     * 获取当前硬币数量
     */
    fun getCurrentCoinCount(): Int {
        return prefs.getInt(CURRENT_COIN_COUNT, 0)
    }

    /**
     * 保存昵称
     */
    fun saveNickname(nickname: String) {
        prefs.edit().putString(NICKNAME, nickname).apply()
    }

    /**
     * 获取昵称
     */
    fun getNickname(): String {
        return prefs.getString(NICKNAME, "") ?: ""
    }

    /**
     * 保存头像
     */
    fun saveAvatar(avatar: String) {
        prefs.edit().putString(AVATAR, avatar).apply()
    }

    /**
     * 获取头像
     */
    fun getAvatar(): String {
        return prefs.getString(AVATAR, "") ?: ""
    }

    /**
     * 保存单条搜索词
     * 逻辑：去重、置顶、限额
     */
    fun saveSearchHistory(keyword: String) {
        if (keyword.isBlank()) return

        val historyList = getSearchHistory().toMutableList()

        // 1. 去重（如果存在则删除，后续加到 0 位）
        historyList.remove(keyword)

        // 2. 置顶
        historyList.add(0, keyword)

        // 3. 限制最大存储数量（ 12 条）
        val resultList = if (historyList.size > 12) historyList.subList(0, 12) else historyList

        // 4. 使用 org.json 序列化
        val jsonArray = org.json.JSONArray()
        resultList.forEach { jsonArray.put(it) }

        prefs.edit().putString(KEY_SEARCH_HISTORY, jsonArray.toString()).apply()
    }

    /**
     * 获取所有搜索历史
     */
    fun getSearchHistory(): List<String> {
        val historyJson = prefs.getString(KEY_SEARCH_HISTORY, "[]") ?: "[]"
        val list = mutableListOf<String>()
        try {
            val jsonArray = org.json.JSONArray(historyJson)
            for (i in 0 until jsonArray.length()) {
                list.add(jsonArray.getString(i))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    /**
     * 清空搜索历史
     */
    fun clearSearchHistory() {
        prefs.edit().remove(KEY_SEARCH_HISTORY).apply()
    }

    /**
     * 保存性别
     */
    fun saveSex(sex: Int) {
        prefs.edit().putInt(SEX, sex).apply()
    }

    /**
     * 获取性别
     */
    fun getSex(): Int {
        return prefs.getInt(SEX, 0)
    }

    /**
     * 保存生日
     */
    fun saveBirthday(birthday: String) {
        prefs.edit().putString(BIRTHDAY, birthday).apply()
    }

    /**
     * 获取生日
     */
    fun getBirthday(): String {
        return UserInfoText.normalize(prefs.getString(BIRTHDAY, ""))
    }

    /**
     * 保存学校
     */
    fun saveSchool(school: String) {
        prefs.edit().putString(SCHOOL, school).apply()
    }

    /**
     * 获取学校
     */
    fun getSchool(): String {
        return UserInfoText.normalize(prefs.getString(SCHOOL, ""))
    }

    /**
     * 保存个人简介
     */
    fun savePersonalIntroduction(introduction: String) {
        prefs.edit().putString(PERSONAL_INTRODUCTION, introduction).apply()
    }

    /**
     * 获取个人简介
     */
    fun getPersonalIntroduction(): String {
        return UserInfoText.normalize(prefs.getString(PERSONAL_INTRODUCTION, ""))
    }

    /**
     * 保存通知信息
     */
    fun saveNoticeInfo(noticeInfo: String) {
        prefs.edit().putString(NOTICE_INFO, noticeInfo).apply()
    }

    /**
     * 获取通知信息
     */
    fun getNoticeInfo(): String {
        return prefs.getString(NOTICE_INFO, "") ?: ""
    }

}