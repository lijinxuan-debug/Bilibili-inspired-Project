package com.example.bilibili.ui.releaseVideo

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.bilibili.data.api.FileService
import com.example.bilibili.data.api.PostService
import com.example.bilibili.data.model.CategoryInfo
import com.example.bilibili.data.model.VideoInfoFilePost
import com.example.bilibili.data.model.VideoUploadInfo
import com.example.bilibili.util.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ReleaseVideoViewModel(application: Application): AndroidViewModel(application) {

    // 使用 MutableLiveData 存储标签列表
    val selectedTags = MutableLiveData<MutableList<String>>(mutableListOf())

    // 存储选择的分区（给个初始默认值，比如图片里显示的"动物"）
    val selectedPartition = MutableLiveData<CategoryInfo>()

    // 视频上传信息
    val videoUploadInfo = MutableLiveData<VideoUploadInfo>()

    // 上传进度
    val uploadProgress = MutableLiveData<Int>()

    // 上传状态
    val uploadStatus = MutableLiveData<String>()

    // 视频标题
    val videoTitle = MutableLiveData("")

    // 视频封面URL
    val videoCoverUrl = MutableLiveData("default_cover.jpg") // 暂时使用默认封面

    // 简介
    val introduction = MutableLiveData("")

    // 创作声明类型
    enum class StatementType {
        ORIGINAL,  // 自制
        REPOST     // 转载
    }

    // 创作声明类型
    val statementType = MutableLiveData<StatementType>(StatementType.ORIGINAL)

    // 转载来源
    val repostSource = MutableLiveData("")

    // 投稿状态
    val postStatus = MutableLiveData("")

    // 更新分区的方法
    fun updatePartition(categoryInfo: CategoryInfo) {
        selectedPartition.value = categoryInfo
    }

    // 添加标签的方法
    fun addTag(name: String) {
        val current = selectedTags.value ?: mutableListOf()
        // 限制最多 10 个，且不重复
        if (current.size < 10 && !current.contains(name)) {
            current.add(name)
            selectedTags.value = current // 重新赋值以触发 LiveData 观察者
        }
    }

    // 移除标签的方法
    fun removeTag(name: String) {
        val current = selectedTags.value ?: mutableListOf()
        current.remove(name)
        selectedTags.value = current
    }

    // 设置简介
    fun setIntroduction(text: String) {
        introduction.value = text
    }

    // 设置创作声明类型
    fun setStatementType(type: StatementType) {
        statementType.value = type
    }

    // 设置转载来源
    fun setRepostSource(source: String) {
        repostSource.value = source
    }

    // 设置视频标题
    fun setVideoTitle(title: String) {
        videoTitle.value = title
    }

    // 设置视频封面URL
    fun setVideoCoverUrl(url: String) {
        videoCoverUrl.value = url
    }

    /**
     * 投稿视频
     */
    fun postVideo() {
        val uploadInfo = videoUploadInfo.value
        val partition = selectedPartition.value
        val tags = selectedTags.value ?: mutableListOf()

        // 验证必要数据
        if (uploadInfo == null) {
            Log.e("PostVideo", "上传信息为空")
            postStatus.postValue("视频未上传完成")
            return
        }

        if (uploadInfo.uploadId.isEmpty()) {
            Log.e("PostVideo", "uploadId 为空，视频可能未成功上传")
            postStatus.postValue("视频上传未完成，请等待上传完成后再投稿")
            return
        }

        if (partition == null) {
            postStatus.postValue("请选择分区")
            return
        }

        if (videoTitle.value.isNullOrEmpty()) {
            postStatus.postValue("请输入视频标题")
            return
        }

        if (tags.isEmpty()) {
            postStatus.postValue("请至少添加一个标签")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val postService = RetrofitClient.create(PostService::class.java)

                Log.d("PostVideo", "开始投稿视频")
                Log.d("PostVideo", "上传信息 - uploadId: ${uploadInfo.uploadId}, fileName: ${uploadInfo.fileName}")

                // 构建视频文件信息列表
                val videoFileList = listOf(
                    VideoInfoFilePost(
                        uploadId = uploadInfo.uploadId,
                        fileName = uploadInfo.fileName,
                        fileSize = uploadInfo.fileSize,
                        duration = (uploadInfo.duration / 1000).toInt() // 转换为秒
                    )
                )

                // 使用 org.json 转换为JSON字符串
                val jsonArray = JSONArray()

                videoFileList.forEach { file ->
                    val fileJson = JSONObject().apply {
                        put("uploadId", file.uploadId)
                        put("fileName", file.fileName)
                        put("fileSize", file.fileSize)
                        put("duration", file.duration)
                        put("fileMd5", file.fileMd5)
                    }
                    jsonArray.put(fileJson)
                }
                val uploadFileListJson = jsonArray.toString()

                Log.d("PostVideo", "uploadFileList JSON: $uploadFileListJson")

                // 转换标签为逗号分隔的字符串
                val tagsString = tags.joinToString(",")

                // 确定投稿类型：0-自制，1-转载
                val postType = if (statementType.value == StatementType.ORIGINAL) 0 else 1

                Log.d("PostVideo", "投稿参数 - 标题: ${videoTitle.value}, 分区: ${partition.categoryName}, 标签: $tagsString")

                // 调用投稿接口
                val response = postService.postVideo(
                    videoId = null, // 新投稿不传videoId
                    videoCover = videoCoverUrl.value ?: "",
                    videoName = videoTitle.value ?: "",
                    pCategoryId = partition.categoryId, // 使用一级分类ID
                    categoryId = partition.subCategoryId,
                    postType = postType,
                    tags = tagsString,
                    introduction = introduction.value ?: "",
                    interaction = "", // 互动设置暂时为空
                    uploadFileList = uploadFileListJson
                )

                Log.d("PostVideo", "投稿结果: $response")

                // 解析响应
                val responseJson = JSONObject(response)
                if (responseJson.optString("status") == "success") {
                    postStatus.postValue("投稿成功")
                    // 可以在这里清空上传信息或进行其他后续处理
                } else {
                    postStatus.postValue("投稿失败: ${responseJson.optString("message")}")
                }

            } catch (e: Exception) {
                Log.e("PostVideo", "投稿失败", e)
                postStatus.postValue("投稿失败: ${e.message}")
            }
        }
    }

    // 设置视频文件信息
    fun setVideoInfo(filePath: String, duration: Long) {
        val file = File(filePath)
        val fileName = file.name
        val fileSize = file.length()

        // 计算分片数量（每个分片 5MB）
        val chunkSize = 5 * 1024 * 1024L // 5MB
        val chunks = ((fileSize + chunkSize - 1) / chunkSize).toInt()

        val uploadInfo = VideoUploadInfo(
            uploadId = "",
            fileName = fileName,
            filePath = filePath,
            fileSize = fileSize,
            duration = duration,
            chunks = chunks,
            currentChunk = 0
        )

        videoUploadInfo.value = uploadInfo
    }

    // 开始上传视频
    fun startVideoUpload() {
        val uploadInfo = videoUploadInfo.value ?: return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fileService = RetrofitClient.create(FileService::class.java)

                // 1. 预上传，获取 uploadId
                Log.d("VideoUpload", "开始预上传: ${uploadInfo.fileName}")
                uploadStatus.postValue("正在初始化上传...")
                val preUploadResponse = fileService.preUploadVideo(uploadInfo.fileName, uploadInfo.chunks)
                val preUploadJson = JSONObject(preUploadResponse)

                if (preUploadJson.optString("status") == "success") {
                    val uploadId = preUploadJson.optString("data")
                    Log.d("VideoUpload", "获取到 uploadId: $uploadId")

                    // 更新 uploadId
                    uploadInfo.uploadId = uploadId
                    videoUploadInfo.postValue(uploadInfo)

                    // 2. 分片上传
                    uploadVideoChunks(fileService, uploadInfo)

                } else {
                    Log.e("VideoUpload", "预上传失败: $preUploadResponse")
                    // 清空 uploadId 防止提交空值
                    uploadInfo.uploadId = ""
                    videoUploadInfo.postValue(uploadInfo)
                    uploadStatus.postValue("预上传失败")
                }

            } catch (e: Exception) {
                Log.e("VideoUpload", "上传失败", e)
                // 清空 uploadId 防止提交空值
                uploadInfo.uploadId = ""
                videoUploadInfo.postValue(uploadInfo)
                uploadStatus.postValue("上传失败: ${e.message}")
            }
        }
    }

    // 分片上传
    private suspend fun uploadVideoChunks(fileService: FileService, uploadInfo: VideoUploadInfo) {
        val file = File(uploadInfo.filePath)
        val chunkSize = 5 * 1024 * 1024L // 5MB

        for (chunkIndex in 0 until uploadInfo.chunks) {
            try {
                // 读取分片数据
                val startPos = chunkIndex * chunkSize
                val endPos = (startPos + chunkSize).coerceAtMost(file.length())
                val chunkLength = (endPos - startPos).toInt()

                val chunkData = ByteArray(chunkLength)
                file.inputStream().use { inputStream ->
                    inputStream.skip(startPos)
                    inputStream.read(chunkData)
                }

                // 创建分片文件
                val chunkFile = File.createTempFile("chunk_$chunkIndex", ".tmp", null)
                // 将分片数据(chunkData)写入到临时文件(chunkFile)中
                chunkFile.writeBytes(chunkData)

                // 准备上传参数
                val requestBody = chunkFile.asRequestBody("video/mp4".toMediaTypeOrNull())
                val chunkPart = MultipartBody.Part.createFormData("chunkFile", chunkFile.name, requestBody)

                val chunkIndexBody = chunkIndex.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                val uploadIdBody = uploadInfo.uploadId.toRequestBody("text/plain".toMediaTypeOrNull())

                Log.d("VideoUpload", "上传分片 $chunkIndex/${uploadInfo.chunks}")
                uploadStatus.postValue("正在上传分片 $chunkIndex/${uploadInfo.chunks}")

                // 上传分片
                val response = fileService.uploadVideo(chunkPart, chunkIndexBody, uploadIdBody)
                Log.d("VideoUpload", "分片 $chunkIndex 上传结果: $response")

                // 更新进度
                val progress = ((chunkIndex + 1) * 100) / uploadInfo.chunks
                uploadProgress.postValue(progress)

                // 清理临时文件
                chunkFile.delete()

            } catch (e: Exception) {
                Log.e("VideoUpload", "分片 $chunkIndex 上传失败", e)
                // 清空 uploadId 防止提交空值
                uploadInfo.uploadId = ""
                videoUploadInfo.postValue(uploadInfo)
                uploadStatus.postValue("分片 $chunkIndex 上传失败")
                // 停止继续上传其他分片
                break
            }
        }

        Log.d("VideoUpload", "所有分片上传完成")
        uploadStatus.postValue("上传完成")
    }

    // 删除上传
    fun deleteUpload(uploadId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fileService = RetrofitClient.create(FileService::class.java)
                val response = fileService.deleteUpload(uploadId)
                Log.d("VideoUpload", "删除上传结果: $response")
            } catch (e: Exception) {
                Log.e("VideoUpload", "删除上传失败", e)
            }
        }
    }
}