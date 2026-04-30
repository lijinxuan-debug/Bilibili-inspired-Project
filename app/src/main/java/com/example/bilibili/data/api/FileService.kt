package com.example.bilibili.data.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.*

interface FileService {
    // 预上传接口 - 获取uploadId
    @POST("file/preUploadVideo")
    @FormUrlEncoded
    suspend fun preUploadVideo(
        @Field("fileName") fileName: String,
        @Field("chunks") chunks: Int
    ): String // 返回 uploadId

    // 分片上传接口
    @Multipart
    @POST("file/uploadVideo")
    suspend fun uploadVideo(
        @Part chunkFile: MultipartBody.Part,
        @Part("chunkIndex") chunkIndex: RequestBody,
        @Part("uploadId") uploadId: RequestBody
    ): String

    // 删除上传接口
    @POST("file/delUploadVideo")
    @FormUrlEncoded
    suspend fun deleteUpload(
        @Field("uploadId") uploadId: String
    ): String

    // 上传图片接口
    @POST("file/uploadImage")
    @Multipart
    suspend fun postImage(
        @Part file: MultipartBody.Part,
        @Part("createThumbnail") createThumbnail: RequestBody
    ): String
}