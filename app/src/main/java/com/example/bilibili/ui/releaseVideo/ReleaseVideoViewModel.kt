package com.example.bilibili.ui.releaseVideo

import android.app.Application
import android.util.Log
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.bilibili.R
import com.example.bilibili.data.api.FileService
import com.example.bilibili.data.api.PostService
import com.example.bilibili.data.api.UcenterService
import com.example.bilibili.data.api.VideoService
import com.example.bilibili.data.repository.CategoryTreeLoader
import com.example.bilibili.util.CategoryPartitionHelper
import com.example.bilibili.data.model.CategoryInfo
import com.example.bilibili.data.model.ReleaseVideoPart
import com.example.bilibili.util.ApiResponseHelper
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

class ReleaseVideoViewModel(application: Application) : AndroidViewModel(application) {

    val selectedTags = MutableLiveData<MutableList<String>>(mutableListOf())
    val selectedPartition = MutableLiveData<CategoryInfo>()
    val uploadProgress = MutableLiveData<Int>()
    val uploadStatus = MutableLiveData<String>()
    val videoTitle = MutableLiveData("")
    val videoCoverUrl = MutableLiveData("default_cover.jpg")
    val introduction = MutableLiveData("")
    val statementType = MutableLiveData(StatementType.ORIGINAL)
    val repostSource = MutableLiveData("")
    val postStatus = MutableLiveData("")

    val videoParts = MutableLiveData<List<ReleaseVideoPart>>(emptyList())
    val selectedPartId = MutableLiveData<String?>()
    val canPublish = MutableLiveData(false)
    val uploadToast = MutableLiveData<String?>()

    /** 顶部进度条跟随的分 P（最近一次上报进度的正在上传分 P） */
    private var activeTopBarPartId: String? = null
    private val partsLock = Any()
    /** 分 P 列表权威数据源（IO 线程 upload 时不能用 videoParts.value，postValue 不会立刻反映到后台） */
    private var partsSnapshot: List<ReleaseVideoPart> = emptyList()
    /** 服务端已确认全部分片上传成功的分 P */
    private val uploadFinishedPartIds = mutableSetOf<String>()
    @Volatile
    private var isPosting = false
    /** 投稿已提交：不可再调 delUploadVideo，避免后端转码读不到 Redis */
    @Volatile
    private var postSubmitted = false

    /** 编辑模式：非空时表示更新已有稿件 */
    private var editingVideoId: String? = null

    /** 稿件状态：0 转码中 2 待审核 3 通过 4 未通过 */
    private var editingVideoStatus: Int? = null

    val editLoadedLive = MutableLiveData(false)

    enum class StatementType {
        ORIGINAL,
        REPOST,
    }

    fun shouldRetainUploadsOnServer(): Boolean = isPosting || postSubmitted

    fun isEditMode(): Boolean = !editingVideoId.isNullOrBlank()

    fun getEditingVideoId(): String? = editingVideoId

    fun updatePartition(categoryInfo: CategoryInfo) {
        selectedPartition.value = categoryInfo
    }

    fun formatPartitionDisplay(info: CategoryInfo? = selectedPartition.value): String {
        return CategoryPartitionHelper.displayName(info)
    }

    fun hasPartitionSelected(): Boolean {
        return (selectedPartition.value?.categoryId ?: 0) > 0
    }

    fun addTag(name: String) {
        val current = selectedTags.value ?: mutableListOf()
        if (current.size < 10 && !current.contains(name)) {
            current.add(name)
            selectedTags.value = current
        }
    }

    fun removeTag(name: String) {
        val current = selectedTags.value ?: mutableListOf()
        current.remove(name)
        selectedTags.value = current
    }

    fun setIntroduction(text: String) {
        introduction.value = text
    }

    fun setStatementType(type: StatementType) {
        statementType.value = type
    }

    fun setRepostSource(source: String) {
        repostSource.value = source
    }

    fun setVideoTitle(title: String) {
        videoTitle.value = title
    }

    fun setVideoCoverUrl(url: String) {
        videoCoverUrl.value = url
    }

    private suspend fun readCategoryIdsForEdit(videoId: String, videoInfo: JSONObject): Pair<Int, Int> {
        var (pCategoryId, categoryId) = CategoryPartitionHelper.readVideoCategoryIds(videoInfo)
        if (pCategoryId > 0 || categoryId > 0) {
            return pCategoryId to categoryId
        }
        return try {
            val response = RetrofitClient.create(VideoService::class.java).getVideoInfo(videoId)
            if (!ApiResponseHelper.isSuccess(response)) {
                return 0 to 0
            }
            val published = JSONObject(response).getJSONObject("data").getJSONObject("videoInfo")
            CategoryPartitionHelper.readVideoCategoryIds(published)
        } catch (e: Exception) {
            Log.w("ReleaseVideo", "read category from video/getVideoInfo failed", e)
            0 to 0
        }
    }

    private suspend fun resolvePartitionForEdit(pCategoryId: Int, categoryId: Int): CategoryInfo? {
        if (pCategoryId <= 0 && categoryId <= 0) return null
        return try {
            CategoryTreeLoader.resolvePartition(getApplication(), pCategoryId, categoryId)
        } catch (e: Exception) {
            Log.w("ReleaseVideo", "resolve partition names failed", e)
            null
        }
    }

    fun loadVideoForEdit(videoId: String) {
        editingVideoId = videoId
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val service = RetrofitClient.create(UcenterService::class.java)
                val response = service.getVideoInfoByVideoId(videoId)
                if (!ApiResponseHelper.isSuccess(response)) {
                    postStatus.postValue("加载稿件失败: ${ApiResponseHelper.errorMessage(response)}")
                    return@launch
                }
                val data = JSONObject(response).getJSONObject("data")
                val videoInfo = data.getJSONObject("videoInfo")
                val fileArray = data.optJSONArray("videoInfoFileList") ?: JSONArray()
                editingVideoStatus = videoInfo.optInt("status", -1).takeIf { it >= 0 }

                videoTitle.postValue(videoInfo.optString("videoName"))
                videoCoverUrl.postValue(videoInfo.optString("videoCover"))
                introduction.postValue(videoInfo.optString("introduction"))

                val tagList = videoInfo.optString("tags")
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toMutableList()
                selectedTags.postValue(tagList)

                val postType = videoInfo.optInt("postType", 0)
                statementType.postValue(
                    if (postType == 1) StatementType.REPOST else StatementType.ORIGINAL,
                )
                repostSource.postValue(videoInfo.optString("originInfo"))

                val (pCategoryId, categoryId) = readCategoryIdsForEdit(videoId, videoInfo)
                val partition = resolvePartitionForEdit(pCategoryId, categoryId)
                if (partition != null) {
                    selectedPartition.postValue(partition)
                } else if (pCategoryId > 0 || categoryId > 0) {
                    Log.w(
                        "ReleaseVideo",
                        "partition ids present but names missing: p=$pCategoryId c=$categoryId",
                    )
                }

                val parts = mutableListOf<ReleaseVideoPart>()
                synchronized(partsLock) { uploadFinishedPartIds.clear() }
                for (i in 0 until fileArray.length()) {
                    val file = fileArray.getJSONObject(i)
                    val uploadId = file.optString("uploadId")
                    if (uploadId.isBlank()) continue
                    val serverFileId = file.optString("fileId")
                    val fileName = file.optString("fileName")
                    val durationSec = file.optInt("duration", 0)
                    val transferResult = file.optInt("transferResult", 1)
                    val part = ReleaseVideoPart(
                        filePath = "",
                        fileName = fileName,
                        duration = durationSec * 1000L,
                        displayTitle = fileName,
                        uploadId = uploadId,
                        uploadStatus = uploadStatusForTransfer(transferResult),
                        uploadProgress = 100,
                        serverFileId = serverFileId,
                        persistedFileSize = file.optLong("fileSize", 0L),
                        transferResult = transferResult,
                    )
                    synchronized(partsLock) {
                        uploadFinishedPartIds.add(part.id)
                    }
                    parts.add(part)
                }
                publishParts(parts, selectId = parts.firstOrNull()?.id)
                editLoadedLive.postValue(true)
            } catch (e: Exception) {
                Log.e("ReleaseVideo", "loadVideoForEdit failed", e)
                postStatus.postValue("加载稿件失败: ${e.message}")
            }
        }
    }

    fun addPart(filePath: String, duration: Long): ReleaseVideoPart {
        val file = File(filePath)
        val part = ReleaseVideoPart(
            filePath = filePath,
            fileName = file.name,
            duration = duration,
            displayTitle = file.nameWithoutExtension.ifBlank { file.name },
        )
        val newList = currentParts() + part
        publishParts(newList, selectId = part.id)
        startUploadForPart(part.id)
        return part
    }

    fun replacePart(partId: String, filePath: String, duration: Long) {
        val file = File(filePath)
        val oldPart = currentParts().find { it.id == partId } ?: return
        synchronized(partsLock) {
            uploadFinishedPartIds.remove(partId)
        }
        if (!oldPart.isPersistedOnServer && oldPart.uploadId.isNotEmpty() && !postSubmitted) {
            deleteUpload(oldPart.uploadId)
        }
        val updated = ReleaseVideoPart(
            id = partId,
            filePath = filePath,
            fileName = file.name,
            duration = duration,
            displayTitle = file.nameWithoutExtension.ifBlank { file.name },
            serverFileId = "",
            persistedFileSize = 0L,
            transferResult = -1,
        )
        val newList = currentParts().map { if (it.id == partId) updated else it }
        publishParts(newList, selectId = partId)
        startUploadForPart(partId)
    }

    fun removePart(partId: String): Boolean {
        val current = currentParts()
        if (current.size <= 1) return false
        val removed = current.find { it.id == partId } ?: return false
        synchronized(partsLock) {
            uploadFinishedPartIds.remove(partId)
        }
        if (!removed.isPersistedOnServer && removed.uploadId.isNotEmpty() && !postSubmitted) {
            deleteUpload(removed.uploadId)
        }
        val newList = current.filter { it.id != partId }
        val nextSelected = if (selectedPartId.value == partId) {
            newList.firstOrNull()?.id
        } else {
            selectedPartId.value
        }
        publishParts(newList, selectId = nextSelected)
        return true
    }

    fun movePart(from: Int, to: Int) {
        val list = currentParts().toMutableList()
        if (from !in list.indices || to !in list.indices || from == to) return
        val item = list.removeAt(from)
        list.add(to, item)
        publishParts(list, selectId = selectedPartId.value)
    }

    fun updatePartTitle(partId: String, title: String) {
        val newList = currentParts().map { part ->
            if (part.id == partId) part.copy(displayTitle = title) else part
        }
        publishParts(newList, selectId = partId)
    }

    fun selectPart(partId: String) {
        selectedPartId.value = partId
    }

    fun getSelectedPart(): ReleaseVideoPart? {
        val id = selectedPartId.value ?: return videoParts.value?.firstOrNull()
        return videoParts.value?.find { it.id == id } ?: videoParts.value?.firstOrNull()
    }

    private fun currentParts(): List<ReleaseVideoPart> = synchronized(partsLock) {
        partsSnapshot.ifEmpty { videoParts.value.orEmpty() }
    }

    fun allPartsUploaded(): Boolean {
        val parts = currentParts()
        return parts.isNotEmpty() && parts.all { isPartUploadComplete(it) }
    }

    private fun isPartUploadComplete(part: ReleaseVideoPart): Boolean {
        if (part.uploadId.isEmpty()) return false
        if (part.transferResult == 2) return false
        if (part.isPersistedOnServer && part.transferResult == 0) return false
        if (part.uploadStatus.contains("失败") || part.uploadStatus.contains("预上传失败")) return false
        if (part.uploadStatus.startsWith("正在")) return false
        if (part.isPersistedOnServer && part.transferResult == 1) return true
        if (synchronized(partsLock) { uploadFinishedPartIds.contains(part.id) }) return true
        if (part.uploadStatus == "上传完成") return true
        return part.uploadProgress >= 100
    }

    fun isPublishReady(): Boolean = !postSubmitted && !isPosting && allPartsUploaded()

    private fun reconcileUploadFinished(parts: List<ReleaseVideoPart>) {
        synchronized(partsLock) {
            parts.forEach { part ->
                if (part.uploadStatus == "上传完成" &&
                    part.uploadId.isNotEmpty() &&
                    !part.uploadStatus.contains("失败")
                ) {
                    uploadFinishedPartIds.add(part.id)
                }
            }
        }
    }

    fun publishBlockReason(): String {
        if (postSubmitted) return "视频已投稿"
        if (isPosting) return "正在投稿，请稍候"
        validateBeforeSubmit(currentParts())?.let { return it }
        val parts = currentParts()
        if (parts.isEmpty()) return "请先添加分P视频"
        parts.firstOrNull { it.uploadStatus.contains("失败") || it.uploadStatus.contains("预上传失败") }
            ?.let { return it.uploadStatus }
        if (parts.any { it.uploadStatus.startsWith("正在") }) return "正在上传，请稍候"
        parts.firstOrNull { !isPartUploadComplete(it) }?.let { part ->
            val status = part.uploadStatus.ifBlank { "未开始" }
            if (part.uploadId.isEmpty()) {
                return if (status != "未开始") status else "「${part.shortFileName()}」上传未就绪，请稍候或重新添加"
            }
            return "「${part.shortFileName()}」未完成上传（$status）"
        }
        return "可以发布"
    }

    fun isAnyPartUploading(): Boolean {
        return videoParts.value.orEmpty().any { part ->
            part.uploadStatus.startsWith("正在")
        }
    }

    fun getTopBarUploadPart(): ReleaseVideoPart? {
        val parts = videoParts.value.orEmpty()
        val active = activeTopBarPartId?.let { id -> parts.find { it.id == id } }
        if (active != null && isPartUploading(active)) return active
        return parts.lastOrNull { isPartUploading(it) }
    }

    private fun isPartUploading(part: ReleaseVideoPart): Boolean {
        if (isPartUploadComplete(part)) return false
        if (part.uploadStatus.contains("失败")) return false
        return part.uploadStatus.startsWith("正在")
    }

    private fun refreshActiveTopBarPartId(partId: String, updated: ReleaseVideoPart) {
        if (isPartUploading(updated)) {
            activeTopBarPartId = partId
            return
        }
        if (activeTopBarPartId == partId) {
            activeTopBarPartId = videoParts.value.orEmpty().lastOrNull { isPartUploading(it) }?.id
        }
    }

    fun postVideo() {
        if (isPosting) return
        val parts = currentParts()
        val tags = selectedTags.value ?: mutableListOf()

        validateBeforeSubmit(parts)?.let { reason ->
            postStatus.value = reason
            return
        }

        if (parts.isEmpty()) {
            postStatus.postValue("请先添加分P视频")
            return
        }
        if (!allPartsUploaded()) {
            postStatus.postValue("请等待所有分P上传完成")
            return
        }
        if (!hasPartitionSelected()) {
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

        isPosting = true
        canPublish.postValue(false)

        val partition = selectedPartition.value!!
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val postService = RetrofitClient.create(PostService::class.java)
                val jsonArray = JSONArray()
                parts.forEach { part ->
                    if (!isPartUploadComplete(part)) {
                        throw IllegalStateException("分P「${part.displayTitle}」未完成上传")
                    }
                    jsonArray.put(buildUploadFileJson(part))
                }
                val response = postService.postVideo(
                    videoId = editingVideoId,
                    videoCover = videoCoverUrl.value ?: "",
                    videoName = videoTitle.value ?: "",
                    pCategoryId = partition.categoryId,
                    categoryId = partition.subCategoryId,
                    postType = if (statementType.value == StatementType.ORIGINAL) 0 else 1,
                    tags = tags.joinToString(","),
                    introduction = introduction.value ?: "",
                    interaction = "",
                    uploadFileList = jsonArray.toString(),
                )
                if (ApiResponseHelper.isSuccess(response)) {
                    postSubmitted = true
                    postStatus.postValue(
                        if (editingVideoId != null) "保存成功" else "投稿成功",
                    )
                } else {
                    postStatus.postValue("投稿失败: ${ApiResponseHelper.errorMessage(response)}")
                }
            } catch (e: Exception) {
                Log.e("ReleaseVideo", "投稿失败", e)
                postStatus.postValue("投稿失败: ${e.message}")
            } finally {
                isPosting = false
                canPublish.postValue(allPartsUploaded() && !postSubmitted)
            }
        }
    }

    @Deprecated("Use addPart")
    fun setVideoInfo(filePath: String, duration: Long) {
        addPart(filePath, duration)
    }

    @Deprecated("Upload starts in addPart")
    fun startVideoUpload() {
        videoParts.value?.firstOrNull()?.let { startUploadForPart(it.id) }
    }

    fun startUploadForPart(partId: String) {
        val part = currentParts().find { it.id == partId } ?: return
        if (part.filePath.isBlank()) {
            if (part.uploadId.isNotEmpty()) return
            failPartUpload(partId, "未选择视频文件")
            return
        }
        synchronized(partsLock) {
            uploadFinishedPartIds.remove(partId)
        }
        activeTopBarPartId = partId
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fileService = RetrofitClient.create(FileService::class.java)
                val file = File(part.filePath)
                if (!file.exists()) {
                    failPartUpload(partId, "视频文件不存在")
                    return@launch
                }
                val totalBytes = file.length().coerceAtLeast(1L)
                val targetChunks = 12L
                val minChunkSize = 256 * 1024L
                val maxChunkSize = 2 * 1024 * 1024L
                val chunkSize = ((totalBytes + targetChunks - 1) / targetChunks)
                    .coerceIn(minChunkSize, maxChunkSize)
                val chunks = ((totalBytes + chunkSize - 1) / chunkSize).toInt().coerceAtLeast(1)

                updatePart(partId) { it.copy(uploadStatus = "正在初始化上传...", uploadProgress = 0) }
                syncGlobalUploadUi(partId)

                val preUploadResponse = fileService.preUploadVideo(part.fileName, chunks)
                if (!ApiResponseHelper.isSuccess(preUploadResponse)) {
                    failPartUpload(partId, "预上传失败: ${ApiResponseHelper.errorMessage(preUploadResponse)}")
                    return@launch
                }
                val uploadId = ApiResponseHelper.successData(preUploadResponse)
                if (uploadId.isBlank()) {
                    failPartUpload(partId, "预上传失败: 未返回 uploadId")
                    return@launch
                }
                updatePart(partId) { it.copy(uploadId = uploadId) }
                Log.d("ReleaseVideo", "preUpload ok partId=$partId uploadId=$uploadId")

                for (chunkIndex in 0 until chunks) {
                    updatePart(partId) {
                        it.copy(uploadStatus = "正在上传分片 ${chunkIndex + 1}/$chunks")
                    }
                    syncGlobalUploadUi(partId)

                    val startPos = chunkIndex * chunkSize
                    val endPos = (startPos + chunkSize).coerceAtMost(file.length())
                    val chunkLength = (endPos - startPos).toInt()
                    val chunkData = ByteArray(chunkLength)
                    file.inputStream().use { input ->
                        input.skip(startPos)
                        input.read(chunkData)
                    }
                    val chunkFile = File.createTempFile("chunk_$chunkIndex", ".tmp", null)
                    try {
                        chunkFile.writeBytes(chunkData)
                        val requestBody = chunkFile.asRequestBody("video/mp4".toMediaTypeOrNull())
                        val chunkPart = MultipartBody.Part.createFormData(
                            "chunkFile",
                            chunkFile.name,
                            requestBody,
                        )
                        val chunkResponse = fileService.uploadVideo(
                            chunkPart,
                            chunkIndex.toString().toRequestBody("text/plain".toMediaTypeOrNull()),
                            uploadId.toRequestBody("text/plain".toMediaTypeOrNull()),
                        )
                        if (!ApiResponseHelper.isSuccess(chunkResponse)) {
                            failPartUpload(
                                partId,
                                "分片 ${chunkIndex + 1} 失败: ${ApiResponseHelper.errorMessage(chunkResponse)}",
                            )
                            return@launch
                        }
                    } finally {
                        chunkFile.delete()
                    }

                    val progress = ((chunkIndex + 1) * 100) / chunks
                    updatePart(partId) { it.copy(uploadProgress = progress) }
                    syncGlobalUploadUi(partId)
                }

                synchronized(partsLock) {
                    uploadFinishedPartIds.add(partId)
                }
                updatePart(partId) { it.copy(uploadStatus = "上传完成", uploadProgress = 100) }
                syncGlobalUploadUi(partId)
            } catch (e: Exception) {
                Log.e("VideoUpload", "分P上传失败", e)
                failPartUpload(partId, "上传失败: ${e.message}")
            }
        }
    }

    private fun failPartUpload(partId: String, message: String) {
        synchronized(partsLock) {
            uploadFinishedPartIds.remove(partId)
        }
        updatePart(partId) {
            it.copy(uploadId = "", uploadStatus = message, uploadProgress = 0)
        }
        syncGlobalUploadUi(partId)
        uploadToast.postValue(message)
    }

    fun deleteAllUploads() {
        if (shouldRetainUploadsOnServer()) {
            Log.w("ReleaseVideo", "跳过删除上传：投稿进行中或已提交")
            return
        }
        currentParts().forEach { part ->
            if (part.uploadId.isNotEmpty()) {
                deleteUpload(part.uploadId)
            }
        }
    }

    fun deleteUpload(uploadId: String) {
        if (uploadId.isEmpty() || postSubmitted) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fileService = RetrofitClient.create(FileService::class.java)
                val response = fileService.deleteUpload(uploadId)
                if (!ApiResponseHelper.isSuccess(response)) {
                    Log.w("ReleaseVideo", "删除上传失败: ${ApiResponseHelper.errorMessage(response)}")
                }
            } catch (e: Exception) {
                Log.e("VideoUpload", "删除上传失败", e)
            }
        }
    }

    private fun publishParts(parts: List<ReleaseVideoPart>, selectId: String?) {
        val indexed = parts.mapIndexed { index, part ->
            part.copy().apply { displayIndex = index + 1 }
        }
        emitParts(indexed, selectId)
    }

    private fun updatePart(partId: String, block: (ReleaseVideoPart) -> ReleaseVideoPart) {
        val indexed = synchronized(partsLock) {
            val currentList = partsSnapshot.ifEmpty { videoParts.value.orEmpty() }
            val current = currentList.find { it.id == partId } ?: return@synchronized null
            val updated = block(current)
            if (updated.uploadProgress < current.uploadProgress &&
                updated.uploadStatus == current.uploadStatus &&
                updated.uploadId == current.uploadId
            ) {
                return@synchronized null
            }
            refreshActiveTopBarPartId(partId, updated)
            currentList.map { part ->
                if (part.id == partId) updated else part
            }.mapIndexed { index, part ->
                part.copy().apply { displayIndex = index + 1 }
            }
        } ?: return
        emitParts(indexed, selectId = null)
    }

    private fun emitParts(indexed: List<ReleaseVideoPart>, selectId: String?) {
        synchronized(partsLock) {
            partsSnapshot = indexed
            reconcileUploadFinished(indexed)
        }
        val ready = !postSubmitted && indexed.isNotEmpty() && indexed.all { isPartUploadComplete(it) }
        val onMain = Looper.myLooper() == Looper.getMainLooper()
        if (onMain) {
            videoParts.value = indexed
            canPublish.value = ready && !isPosting
            if (selectId != null) {
                selectedPartId.value = selectId
            } else if (selectedPartId.value == null && indexed.isNotEmpty()) {
                selectedPartId.value = indexed.first().id
            }
        } else {
            videoParts.postValue(indexed)
            canPublish.postValue(ready && !isPosting)
            if (selectId != null) {
                selectedPartId.postValue(selectId)
            } else if (selectedPartId.value == null && indexed.isNotEmpty()) {
                selectedPartId.postValue(indexed.first().id)
            }
        }
    }

    private fun syncGlobalUploadUi(partId: String) {
        if (selectedPartId.value != partId && currentParts().firstOrNull()?.id != partId) return
        val part = currentParts().find { it.id == partId } ?: return
        uploadProgress.postValue(part.uploadProgress)
        uploadStatus.postValue(part.uploadStatus)
    }

    private fun validateBeforeSubmit(parts: List<ReleaseVideoPart>): String? {
        if (editingVideoId == null) return null
        when (editingVideoStatus) {
            0 -> return getApplication<Application>().getString(R.string.release_edit_transcoding)
            2 -> return getApplication<Application>().getString(R.string.release_edit_pending_audit)
        }
        val app = getApplication<Application>()
        parts.firstOrNull { it.transferResult == 2 }?.let { part ->
            return app.getString(R.string.release_part_transfer_failed, part.shortFileName())
        }
        parts.firstOrNull { it.isPersistedOnServer && it.transferResult == 0 }?.let { part ->
            return app.getString(R.string.release_part_transferring, part.shortFileName())
        }
        return null
    }

    private fun uploadStatusForTransfer(transferResult: Int): String = when (transferResult) {
        0 -> "转码中"
        2 -> "转码失败"
        else -> "上传完成"
    }

    private fun buildUploadFileJson(part: ReleaseVideoPart): JSONObject {
        val title = part.displayTitle.trim().ifBlank { part.fileName }
        val fileSize = when {
            part.filePath.isNotBlank() -> File(part.filePath).length()
            part.persistedFileSize > 0 -> part.persistedFileSize
            else -> 0L
        }
        return JSONObject().apply {
            if (part.serverFileId.isNotBlank()) {
                put("fileId", part.serverFileId)
            }
            put("uploadId", part.uploadId)
            put("fileName", title)
            put("fileSize", fileSize)
            put("duration", (part.duration / 1000).toInt())
        }
    }
}
