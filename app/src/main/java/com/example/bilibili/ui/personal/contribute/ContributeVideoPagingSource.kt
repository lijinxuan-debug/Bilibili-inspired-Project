package com.example.bilibili.ui.personal.contribute

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.bilibili.data.api.PostService
import com.example.bilibili.data.model.VideoItem
import com.example.bilibili.util.RetrofitClient
import org.json.JSONObject

class ContributeVideoPagingSource(
    private val userId: String,
    private val orderType: Int = 0
) : PagingSource<Int, VideoItem>() {
    private val service = RetrofitClient.create(PostService::class.java)

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, VideoItem> {
        return try {
            // 获取页码，默认从第1页开始
            val page = params.key ?: 1

            // 调用接口获取数据
            val response = service.loadVideoList(userId = userId, orderType = orderType, pageNo = page)
            val jsonObject = JSONObject(response)

            if (jsonObject.optString("status") == "success") {
                val list = mutableListOf<VideoItem>()
                val jsonArray = jsonObject.getJSONObject("data").getJSONArray("list")

                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    list.add(VideoItem(
                        videoId = item.getString("videoId"),
                        videoName = item.getString("videoName"),
                        videoCover = item.getString("videoCover"),
                        playCount = item.optInt("playCount"),
                        commentCount = item.optInt("commentCount"),
                        danmuCount = item.optInt("danmuCount"),
                        duration = item.optInt("duration"),
                        createTime = item.getString("createTime"),
                        nickName = if (item.isNull("nickName")) "未知UP" else item.getString("nickName")
                    ))
                }

                // 判断是否还有下一页
                val hasMore = list.size >= params.loadSize
                val nextPage = if (hasMore) page + 1 else null

                LoadResult.Page(
                    data = list,
                    prevKey = if (page == 1) null else page - 1,
                    nextKey = nextPage
                )
            } else {
                LoadResult.Error(Exception(jsonObject.optString("message", "加载失败")))
            }
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, VideoItem>): Int? {
        // 返回刷新时的页码
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }
}