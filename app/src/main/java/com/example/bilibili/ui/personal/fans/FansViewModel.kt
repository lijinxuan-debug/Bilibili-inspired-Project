package com.example.bilibili.ui.personal.fans

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bilibili.data.api.PostService
import com.example.bilibili.data.model.UserFriend
import com.example.bilibili.util.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class FansViewModel : ViewModel() {
    val fanList = MutableLiveData<List<UserFriend>>()
    val toastMessage = MutableLiveData<String>()
    private val service = RetrofitClient.create(PostService::class.java)

    fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 传 pageNo = 1
                val response = service.loadFansList(1)
                val jsonObject = JSONObject(response)

                if (jsonObject.optString("status") == "success") {
                    val dataObj = jsonObject.getJSONObject("data")
                    val jsonArray = dataObj.getJSONArray("list")
                    val tempList = mutableListOf<UserFriend>()

                    for (i in 0 until jsonArray.length()) {
                        val item = jsonArray.getJSONObject(i)
                        // 使用 optString 替代 getString，防止字段缺失报错
                        tempList.add(UserFriend(
                            userId = item.optString("userId"),
                            otherUserId = item.optString("otherUserId"),
                            otherNickName = item.optString("otherNickName", "未知用户"),
                            otherAvatar = item.optString("otherAvatar"),
                            otherPersonalIntroduction = item.optString("otherPersonalIntroduction"),
                            focusType = item.optInt("focusType"),
                            focusTime = item.optString("focusTime")
                        ))
                    }
                    fanList.postValue(tempList)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // 回关用户
    fun followBack(userId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = service.focus(userId)
                if (JSONObject(response).optString("status") == "success") {
                    toastMessage.postValue("回关成功")
                    loadData() // 刷新列表
                } else {
                    toastMessage.postValue("回关失败，请重试")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                toastMessage.postValue("网络错误，请重试")
            }
        }
    }
}