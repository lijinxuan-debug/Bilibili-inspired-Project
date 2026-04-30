package com.example.bilibili.ui.front

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.bilibili.data.api.VideoService
import com.example.bilibili.data.model.VideoItem
import com.example.bilibili.util.RetrofitClient
import org.json.JSONObject

class FrontPagePagingSource(private val pCategoryId: Int = 0) : PagingSource<Int, VideoItem>() {
    private val service = RetrofitClient.create(VideoService::class.java)

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, VideoItem> {
        return try {
            // 获取页码，默认从第1页开始
            val page = params.key ?: 1

            // 调用接口获取数据
            // pCategoryId为-1时表示"全部"，不传递分类参数
            val response = service.loadVideo(
                pageNo = page,
                pCategoryId = if (pCategoryId == -1) null else pCategoryId
            )
            val jsonObject = JSONObject(response)

            if (jsonObject.optInt("code") == 200) {
                val list = mutableListOf<VideoItem>()
                val dataArray = jsonObject.getJSONObject("data").optJSONArray("list")

                if (dataArray != null) {
                    for (i in 0 until dataArray.length()) {
                        val item = dataArray.getJSONObject(i)
                        list.add(VideoItem(
                            videoId = item.optString("videoId"),
                            videoName = item.optString("videoName"),
                            videoCover = item.optString("videoCover"),
                            playCount = item.optInt("playCount", 0),
                            commentCount = item.optInt("commentCount", 0),
                            duration = item.optInt("duration", 0),
                            createTime = item.optString("createTime"),
                            danmuCount = item.optInt("danmuCount", 0),
                            nickName = item.optString("nickName")
                        ))
                    }
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