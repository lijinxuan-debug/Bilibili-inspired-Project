package com.example.bilibili.data.api

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface UserActionService {
    /**
     * 对视频的交互操作
     * - 0 - 评论点赞 (可取消)
     * - 1 - 评论点踩 (可取消)
     * - 2 - 视频点赞 (可取消)
     * - 3 - 视频收藏 (可取消)
     * - 4 - 视频投币 (不可取消，每视频最多2次)
     */

    @POST("userAction/doAction")
    @FormUrlEncoded
    suspend fun doAction(
        @Field("videoId") videoId: String,
        @Field("actionType") actionType: Int,
        @Field("actionCount") actionCount: Int? = null
    ): String
}