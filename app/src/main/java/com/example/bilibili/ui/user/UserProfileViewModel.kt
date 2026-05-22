package com.example.bilibili.ui.user

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bilibili.data.api.PostService
import com.example.bilibili.util.RetrofitClient
import com.example.bilibili.util.UserInfoText
import com.example.bilibili.util.optNormalizedString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class UserInfo(
    val userId: String,
    val avatar: String,
    val nickName: String,
    val email: String,
    val sex: Int,
    val birthday: String,
    val school: String,
    val personalIntroduction: String,
    val noticeInfo: String,
    val theme: Int,
    val fansCount: Int,
    val focusCount: Int,
    val likeCount: Int,
    val playCount: Int,
    val haveFocus: Boolean
)

class UserProfileViewModel : ViewModel() {

    private val postService = RetrofitClient.create(PostService::class.java)

    private val _userInfo = MutableLiveData<UserInfo>()
    val userInfo: LiveData<UserInfo> = _userInfo

    private val _focusState = MutableLiveData<Boolean>()
    val focusState: LiveData<Boolean> = _focusState

    fun setUserInfo(data: JSONObject) {
        _userInfo.value = UserInfo(
            userId = data.optString("userId"),
            avatar = data.optString("avatar"),
            nickName = data.optString("nickName"),
            email = data.optString("email"),
            sex = data.optInt("sex"),
            birthday = data.optNormalizedString("birthday"),
            school = data.optNormalizedString("school"),
            personalIntroduction = data.optNormalizedString("personalIntroduction"),
            noticeInfo = data.optNormalizedString("noticeInfo"),
            theme = data.optInt("theme"),
            fansCount = data.optInt("fansCount"),
            focusCount = data.optInt("focusCount"),
            likeCount = data.optInt("likeCount"),
            playCount = data.optInt("playCount"),
            haveFocus = data.optBoolean("haveFocus")
        )

        _focusState.value = data.optBoolean("haveFocus")
    }

    fun setFocused(focused: Boolean) {
        _focusState.value = focused
    }

    fun toggleFocus(targetUserId: String) {
        viewModelScope.launch {
            try {
                val currentState = _focusState.value ?: false
                val newState = !currentState

                // 乐观更新 UI
                _focusState.value = newState

                // 调用 API
                val result = withContext(Dispatchers.IO) {
                    if (newState) {
                        postService.focus(targetUserId)
                    } else {
                        postService.cancelFocus(targetUserId)
                    }
                }

                val jsonObject = JSONObject(result)
                if (jsonObject.optInt("code") != 200) {
                    // 失败回滚
                    _focusState.value = currentState
                }
            } catch (e: Exception) {
                // 异常回滚
                _focusState.value = (_focusState.value == false)
            }
        }
    }
}