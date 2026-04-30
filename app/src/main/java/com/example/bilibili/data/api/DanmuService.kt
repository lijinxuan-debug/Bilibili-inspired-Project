package com.example.bilibili.data.api

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface DanmuService {
    // 获取弹幕列表信息
    @POST("danmu/loadDanmu")
    @FormUrlEncoded
    suspend fun loadDanmu(
        @Field("fileId") fileId: String,
        @Field("videoId") videoId: String
    ): String

    // 发送弹幕
    @POST("danmu/postDanmu")
    @FormUrlEncoded
    suspend fun postDanmu(
        @Field("text") text: String,
        @Field("mode") mode: Int,
        @Field("color") color: String,
        @Field("time") time: Int,
        @Field("field") field: String,
        @Field("videoId") videoId: String,
    ): String
}