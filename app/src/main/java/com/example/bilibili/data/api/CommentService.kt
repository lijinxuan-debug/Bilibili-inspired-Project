package com.example.bilibili.data.api

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface CommentService {
    // 加载单个视频下的所有评论
    @POST("comment/loadComment")
    @FormUrlEncoded
    suspend fun loadComment(
        @Field("videoId") videoId: String,
        @Field("pageNo") pageNo: Int? = null,
        @Field("orderType") orderType: Int,
    ): String

    // 发送评论
    @POST("comment/postComment")
    @FormUrlEncoded
    suspend fun postComment(
        @Field("content") content: String,
        @Field("imgPath") imgPath: Int? = null,
        @Field("videoId") videoId: String,
    ): String
}