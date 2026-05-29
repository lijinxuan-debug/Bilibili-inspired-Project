package com.example.bilibili.data.api

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface MessageService {

    @POST("message/loadAllMessage")
    @FormUrlEncoded
    suspend fun loadAllMessage(
        @Field("pageNo") pageNo: Int,
    ): String

    @POST("message/loadMessage")
    @FormUrlEncoded
    suspend fun loadMessage(
        @Field("messageType") messageType: Int,
        @Field("pageNo") pageNo: Int,
        @Field("messageTypes") messageTypes: String? = null,
        @Field("atMeOnly") atMeOnly: Int? = null,
    ): String

    @POST("message/readAll")
    @FormUrlEncoded
    suspend fun readAll(
        @Field("messageType") messageType: Int,
    ): String

    @POST("message/getNoReadCountGroup")
    suspend fun getNoReadCountGroup(): String

    @POST("message/readMessage")
    @FormUrlEncoded
    suspend fun readMessage(
        @Field("messageId") messageId: Int,
    ): String

    @POST("message/delMessage")
    @FormUrlEncoded
    suspend fun delMessage(
        @Field("messageId") messageId: Int,
    ): String
}
