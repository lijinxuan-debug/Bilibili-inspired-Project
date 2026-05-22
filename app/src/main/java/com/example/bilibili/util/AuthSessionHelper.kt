package com.example.bilibili.util

import android.app.Activity
import android.content.Intent
import com.example.bilibili.MainActivity
import com.example.bilibili.data.api.PostService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object AuthSessionHelper {

    fun saveLoginData(data: JSONObject) {
        SPUtils.saveToken(data.getString("token"))
        SPUtils.saveCurrentCoinCount(data.optInt("currentCoinCount"))
        SPUtils.saveUserId(data.getString("userId"))
        SPUtils.saveAvatar(data.optString("avatar"))
        SPUtils.saveNickname(data.getString("nickName"))
    }

    /** 登录/注册后拉取资料；若库里仍是空，则写入默认简介/学校 */
    suspend fun syncProfileFromServer(userId: String) {
        try {
            val postService = RetrofitClient.create(PostService::class.java)
            val responseString = withContext(Dispatchers.IO) {
                postService.getUserInfo(userId)
            }
            val root = JSONObject(responseString)
            if (root.optInt("code") != 200) {
                applyDefaultProfileToSp()
                return
            }
            val data = root.getJSONObject("data")
            val intro = data.optNormalizedString("personalIntroduction")
            val school = data.optNormalizedString("school")
            if (intro.isEmpty() || school.isEmpty()) {
                withContext(Dispatchers.IO) {
                    postService.updateUserInfo(
                        avatar = SPUtils.getAvatar(),
                        nickName = SPUtils.getNickname(),
                        sex = SPUtils.getSex(),
                        birthday = "",
                        school = UserInfoText.DEFAULT_SCHOOL,
                        noticeInfo = SPUtils.getNoticeInfo(),
                        personalIntroduction = UserInfoText.DEFAULT_INTRO
                    )
                }
                applyDefaultProfileToSp()
            } else {
                saveProfileFromApi(data)
            }
        } catch (_: Exception) {
            applyDefaultProfileToSp()
        }
    }

    fun saveProfileFromApi(data: JSONObject) {
        SPUtils.savePersonalIntroduction(
            UserInfoText.storageIntroduction(data.optNormalizedString("personalIntroduction"))
        )
        SPUtils.saveSchool(UserInfoText.storageSchool(data.optNormalizedString("school")))
        SPUtils.saveBirthday(data.optNormalizedString("birthday"))
        data.optInt("sex", 2).let { SPUtils.saveSex(it) }
    }

    fun applyDefaultProfileToSp() {
        SPUtils.savePersonalIntroduction(UserInfoText.DEFAULT_INTRO)
        SPUtils.saveSchool(UserInfoText.DEFAULT_SCHOOL)
        SPUtils.saveBirthday("")
    }

    fun navigateToMainAndFinish(activity: Activity) {
        val intent = Intent(activity, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        activity.startActivity(intent)
        activity.finishAffinity()
    }
}
