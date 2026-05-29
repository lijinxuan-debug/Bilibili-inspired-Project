package com.example.bilibili.ui.creator

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.bilibili.data.api.UcenterService
import com.example.bilibili.data.model.CreatorVideoPost
import com.example.bilibili.util.ApiJson.errorMessage
import com.example.bilibili.util.ApiJson.isSuccess
import com.example.bilibili.util.CreatorStatusText
import com.example.bilibili.util.PagingDefaults
import com.example.bilibili.util.RetrofitClient
import org.json.JSONObject

class CreatorVideoPagingSource(
    private val searchKeyword: String?,
) : PagingSource<Int, CreatorVideoPost>() {

    private val service = RetrofitClient.create(UcenterService::class.java)

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, CreatorVideoPost> {
        return try {
            val page = params.key ?: 1
            val keyword = searchKeyword?.trim().takeIf { !it.isNullOrEmpty() }
            val response = JSONObject(
                service.loadPostVideoList(
                    status = null,
                    pageNo = page,
                    videoNameFuzzy = keyword,
                ),
            )
            if (!response.isSuccess()) {
                return LoadResult.Error(Exception(response.errorMessage()))
            }
            val data = response.getJSONObject("data")
            val listArray = data.getJSONArray("list")
            val list = mutableListOf<CreatorVideoPost>()
            for (i in 0 until listArray.length()) {
                val item = listArray.getJSONObject(i)
                val statusValue = item.optInt("status")
                list.add(
                    CreatorVideoPost(
                        videoId = item.optString("videoId"),
                        videoName = item.optString("videoName"),
                        videoCover = item.optString("videoCover"),
                        status = statusValue,
                        statusLabel = CreatorStatusText.videoStatusLabel(statusValue),
                        playCount = item.optInt("playCount"),
                        likeCount = item.optInt("likeCount"),
                        commentCount = item.optInt("commentCount"),
                        danmuCount = item.optInt("danmuCount"),
                        duration = item.optInt("duration"),
                        createTime = item.optString("createTime"),
                    ),
                )
            }
            LoadResult.Page(
                data = list,
                prevKey = if (page == 1) null else page - 1,
                nextKey = PagingDefaults.nextPageKey(data, page, list.size),
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, CreatorVideoPost>): Int? =
        state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
        }
}
