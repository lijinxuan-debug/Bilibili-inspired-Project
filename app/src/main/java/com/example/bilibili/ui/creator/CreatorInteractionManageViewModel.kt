package com.example.bilibili.ui.creator

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.bilibili.data.api.UcenterService
import com.example.bilibili.data.model.CreatorCommentItem
import com.example.bilibili.data.model.CreatorDanmuItem
import com.example.bilibili.data.model.CreatorVideoOption
import com.example.bilibili.util.ApiJson.errorMessage
import com.example.bilibili.util.ApiJson.isSuccess
import com.example.bilibili.util.PagingDefaults
import com.example.bilibili.util.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class CreatorInteractionManageViewModel : ViewModel() {

    companion object {
        private const val ALL_VIDEOS_LABEL = "全部视频"
    }

    private val service = RetrofitClient.create(UcenterService::class.java)

    private val _videoOptions = MutableLiveData<List<CreatorVideoOption>>(emptyList())
    val videoOptions: LiveData<List<CreatorVideoOption>> = _videoOptions

    private val selectedVideoId = MutableStateFlow<String?>(null)

    private val _selectedVideoOption = MutableLiveData<CreatorVideoOption>()
    val selectedVideoOption: LiveData<CreatorVideoOption> = _selectedVideoOption

    val comments: Flow<PagingData<CreatorCommentItem>> = selectedVideoId.flatMapLatest { videoId ->
        Pager(
            config = PagingDefaults.videoListConfig(),
            pagingSourceFactory = { CreatorCommentPagingSource(videoId) },
        ).flow
    }.cachedIn(viewModelScope)

    val danmus: Flow<PagingData<CreatorDanmuItem>> = selectedVideoId.flatMapLatest { videoId ->
        Pager(
            config = PagingDefaults.videoListConfig(),
            pagingSourceFactory = { CreatorDanmuPagingSource(videoId) },
        ).flow
    }.cachedIn(viewModelScope)

    fun loadVideoOptions() {
        viewModelScope.launch {
            try {
                val options = withContext(Dispatchers.IO) {
                    val response = JSONObject(service.loadAllVideo())
                    if (!response.isSuccess()) {
                        throw IllegalStateException(response.errorMessage())
                    }
                    val array = response.getJSONArray("data")
                    val list = mutableListOf(CreatorVideoOption(null, ALL_VIDEOS_LABEL))
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        list.add(
                            CreatorVideoOption(
                                videoId = item.optString("videoId"),
                                title = item.optString("videoName"),
                            ),
                        )
                    }
                    list
                }
                _videoOptions.value = options
                if (_selectedVideoOption.value == null) {
                    selectVideo(options.first())
                } else if (_selectedVideoOption.value?.videoId != null) {
                    val current = options.find { it.videoId == _selectedVideoOption.value?.videoId }
                    if (current != null) {
                        selectVideo(current)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _videoOptions.value = listOf(CreatorVideoOption(null, ALL_VIDEOS_LABEL))
            }
        }
    }

    fun selectVideo(option: CreatorVideoOption) {
        selectedVideoId.value = option.videoId
        _selectedVideoOption.value = option
    }

    fun selectVideo(videoId: String?) {
        val option = _videoOptions.value?.find { it.videoId == videoId }
            ?: CreatorVideoOption(videoId, ALL_VIDEOS_LABEL)
        selectVideo(option)
    }

    fun deleteComment(commentId: Int, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val response = JSONObject(service.delComment(commentId))
                if (response.isSuccess()) onResult(true, null)
                else onResult(false, response.errorMessage())
            } catch (e: Exception) {
                onResult(false, e.message)
            }
        }
    }

    fun deleteDanmu(danmuId: Int, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val response = JSONObject(service.delDanmu(danmuId))
                if (response.isSuccess()) onResult(true, null)
                else onResult(false, response.errorMessage())
            } catch (e: Exception) {
                onResult(false, e.message)
            }
        }
    }
}
