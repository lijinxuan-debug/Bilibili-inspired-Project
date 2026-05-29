package com.example.bilibili.util

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleCoroutineScope
import com.example.bilibili.data.api.FileService
import com.example.bilibili.data.api.PostService
import com.luck.picture.lib.basic.PictureSelector
import com.luck.picture.lib.config.SelectMimeType
import com.luck.picture.lib.config.SelectModeConfig
import com.luck.picture.lib.entity.LocalMedia
import com.luck.picture.lib.interfaces.OnResultCallbackListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File

object AvatarUpdateHelper {

    fun pickAndUpdate(
        activity: FragmentActivity,
        scope: LifecycleCoroutineScope,
        onPreview: (localPath: String) -> Unit,
        onSuccess: (avatarUrl: String) -> Unit,
        onError: (message: String) -> Unit,
    ) {
        PermissionHelper.requestGalleryImage(activity) {
            openPicker(activity, scope, onPreview, onSuccess, onError)
        }
    }

    private fun openPicker(
        activity: FragmentActivity,
        scope: LifecycleCoroutineScope,
        onPreview: (String) -> Unit,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        PictureSelector.create(activity)
            .openGallery(SelectMimeType.ofImage())
            .setImageEngine(GlideEngine.createGlideImageEngine())
            .setSelectionMode(SelectModeConfig.SINGLE)
            .setMaxSelectNum(1)
            .setMinSelectNum(1)
            .setImageSpanCount(4)
            .forResult(object : OnResultCallbackListener<LocalMedia> {
                override fun onResult(result: ArrayList<LocalMedia>) {
                    val media = result.firstOrNull() ?: return
                    val filePath = resolvePickerImagePath(media)
                    if (filePath == null) {
                        onError("无法读取所选图片")
                        return
                    }
                    onPreview(filePath)
                    scope.launch {
                        val uploadedUrl = uploadAvatarFile(File(filePath))
                        if (uploadedUrl.isNullOrBlank()) {
                            onError("头像上传失败")
                            return@launch
                        }
                        val ok = applyAvatarUrl(uploadedUrl)
                        if (ok) {
                            onSuccess(uploadedUrl)
                        } else {
                            onError("头像更新失败")
                        }
                    }
                }

                override fun onCancel() = Unit
            })
    }

    suspend fun uploadAvatarFile(file: File): String? = withContext(Dispatchers.IO) {
        runCatching {
            val requestBody = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData("file", file.name, requestBody)
            val createThumbnailBody = "false".toRequestBody("text/plain".toMediaTypeOrNull())
            val fileService = RetrofitClient.create(FileService::class.java)
            val result = fileService.postImage(filePart, createThumbnailBody)
            val jsonObject = JSONObject(result)
            if (jsonObject.optInt("code") == 200) {
                jsonObject.optString("data")
            } else {
                null
            }
        }.getOrNull()
    }

    suspend fun applyAvatarUrl(avatarUrl: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val postService = RetrofitClient.create(PostService::class.java)
            val response = postService.updateUserInfo(
                avatar = avatarUrl,
                nickName = SPUtils.getNickname(),
                sex = SPUtils.getSex(),
                birthday = SPUtils.getBirthday(),
                school = SPUtils.getSchool(),
                noticeInfo = SPUtils.getNoticeInfo(),
                personalIntroduction = SPUtils.getPersonalIntroduction(),
            )
            val jsonObject = JSONObject(response)
            if (jsonObject.optInt("code") == 200) {
                SPUtils.saveAvatar(avatarUrl)
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }

    private fun resolvePickerImagePath(media: LocalMedia): String? {
        val candidates = listOfNotNull(
            media.sandboxPath,
            media.compressPath,
            media.realPath,
            media.path,
            media.availablePath,
        )
        return candidates.firstOrNull { it.isNotBlank() && File(it).exists() }
    }
}
