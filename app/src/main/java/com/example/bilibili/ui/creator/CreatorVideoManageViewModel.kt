package com.example.bilibili.ui.creator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.bilibili.data.api.UcenterService
import com.example.bilibili.data.model.CreatorVideoPost
import com.example.bilibili.util.ApiJson.errorMessage
import com.example.bilibili.util.ApiJson.isSuccess
import com.example.bilibili.util.PagingDefaults
import com.example.bilibili.util.RetrofitClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import org.json.JSONObject

class CreatorVideoManageViewModel : ViewModel() {

    private val service = RetrofitClient.create(UcenterService::class.java)
    private val searchKeyword = MutableStateFlow("")

    val videos: Flow<PagingData<CreatorVideoPost>> = searchKeyword
        .debounce(350)
        .flatMapLatest { keyword ->
            Pager(
                config = PagingDefaults.videoListConfig(),
                pagingSourceFactory = { CreatorVideoPagingSource(keyword) },
            ).flow
        }
        .cachedIn(viewModelScope)

    fun setSearchKeyword(keyword: String) {
        searchKeyword.value = keyword
    }

    fun deleteVideo(videoId: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val response = JSONObject(service.delVideo(videoId))
                if (response.isSuccess()) {
                    onResult(true, null)
                } else {
                    onResult(false, response.errorMessage())
                }
            } catch (e: Exception) {
                onResult(false, e.message)
            }
        }
    }
}
