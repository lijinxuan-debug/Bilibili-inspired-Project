package com.example.bilibili.ui.user

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.bilibili.R
import com.example.bilibili.data.api.PostService
import com.example.bilibili.databinding.ActivityEditProfileBinding
import com.example.bilibili.util.GlideEngine
import com.example.bilibili.util.PermissionHelper
import com.example.bilibili.util.RetrofitClient
import com.example.bilibili.util.SPUtils
import com.example.bilibili.util.ToastUtils
import com.example.bilibili.util.UserInfoText
import com.luck.picture.lib.basic.PictureSelector
import com.luck.picture.lib.config.SelectMimeType
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
import java.util.Calendar

/**
 * 编辑资料Activity
 */
class EditProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditProfileBinding
    private val postService = RetrofitClient.create(PostService::class.java)

    // 选中的头像文件
    private var selectedAvatarFile: File? = null
    private var uploadedAvatarUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initViews()
        loadUserInfo()
    }

    private fun initViews() {
        // 返回按钮
        binding.tvBack.setOnClickListener {
            finish()
        }

        // 保存按钮
        binding.tvSave.setOnClickListener {
            saveUserInfo()
        }

        // 头像点击选择图片
        binding.ivAvatar.setOnClickListener {
            selectAvatar()
        }

        // 生日选择
        binding.tvBirthday.setOnClickListener {
            showDatePicker()
        }
    }

    private fun loadUserInfo() {
        // 从SPUtils加载用户信息并填充到界面
        binding.etNickname.setText(SPUtils.getNickname())
        binding.etSchool.setText(UserInfoText.storageSchool(SPUtils.getSchool()))
        binding.etIntroduction.setText(
            UserInfoText.storageIntroduction(SPUtils.getPersonalIntroduction())
        )
        binding.etNotice.setText(SPUtils.getNoticeInfo())

        // 加载头像
        GlideEngine.loadUserAvatar(this, SPUtils.getAvatar(), binding.ivAvatar)

        // 设置性别
        val sex = SPUtils.getSex()
        if (sex == 0) {
            binding.rbMale.isChecked = true
        } else if (sex == 1) {
            binding.rbFemale.isChecked = true
        }

        // 设置生日
        binding.tvBirthday.text = UserInfoText.displayBirthday(SPUtils.getBirthday())
    }

    private fun selectAvatar() {
        PermissionHelper.requestGalleryImage(this) { openAvatarPicker() }
    }

    private fun openAvatarPicker() {
        PictureSelector.create(this)
            .openGallery(SelectMimeType.ofImage())
            .setImageEngine(GlideEngine.createGlideImageEngine())
            .setMaxSelectNum(1)
            .setMinSelectNum(1)
            .setImageSpanCount(4)
            .setSelectedData(emptyList<LocalMedia>())
            .forResult(object : OnResultCallbackListener<LocalMedia> {
                override fun onResult(result: ArrayList<LocalMedia>) {
                    if (result.isNotEmpty()) {
                        val media = result[0]
                        val filePath = resolvePickerImagePath(media) ?: return

                        selectedAvatarFile = File(filePath)
                        GlideEngine.loadUserAvatar(this@EditProfileActivity, filePath, binding.ivAvatar)
                    }
                }

                override fun onCancel() {
                    // 用户取消选择
                }
            })
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(
            this,
            { _, selectedYear, selectedMonth, selectedDay ->
                val birthday = "$selectedYear-${String.format("%02d", selectedMonth + 1)}-${String.format("%02d", selectedDay)}"
                binding.tvBirthday.text = birthday
            },
            year, month, day
        ).show()
    }

    private fun saveUserInfo() {
        // 验证必填字段
        val nickname = binding.etNickname.text.toString().trim()
        if (nickname.isEmpty()) {
            ToastUtils.showShort(this, "昵称不能为空")
            return
        }

        if (nickname.length > 20) {
            ToastUtils.showShort(this, "昵称不能超过20个字符")
            return
        }

        val school = binding.etSchool.text.toString().trim()
        if (school.length > 100) {
            ToastUtils.showShort(this, "学校名称不能超过100个字符")
            return
        }

        val introduction = binding.etIntroduction.text.toString().trim()
        if (introduction.length > 300) {
            ToastUtils.showShort(this, "个人简介不能超过300个字符")
            return
        }

        val notice = binding.etNotice.text.toString().trim()
        if (notice.length > 100) {
            ToastUtils.showShort(this, "通知信息不能超过100个字符")
            return
        }

        val birthday = binding.tvBirthday.text.toString()
        if (birthday.length > 100) {
            ToastUtils.showShort(this, "生日格式不正确")
            return
        }

        val sex = if (binding.rbMale.isChecked) 0 else 1

        // 显示加载提示
        binding.tvSave.isEnabled = false
        binding.tvSave.text = "保存中..."

        lifecycleScope.launch {
            try {
                // 如果选择了新头像，先上传
                var avatarUrl = SPUtils.getAvatar()
                if (selectedAvatarFile != null) {
                    val uploadedUrl = uploadAvatar(selectedAvatarFile!!)
                    if (uploadedUrl != null) {
                        avatarUrl = uploadedUrl
                    }
                }

                // 调用更新用户信息接口
                val response = postService.updateUserInfo(
                    avatar = avatarUrl,
                    nickName = nickname,
                    sex = sex,
                    birthday = birthday,
                    school = school,
                    noticeInfo = notice,
                    personalIntroduction = introduction
                )

                val jsonObject = JSONObject(response)
                if (jsonObject.optInt("code") == 200) {
                    // 保存到本地
                    SPUtils.saveAvatar(avatarUrl)
                    SPUtils.saveNickname(nickname)
                    SPUtils.saveSex(sex)
                    SPUtils.saveBirthday(birthday)
                    SPUtils.saveSchool(school)
                    SPUtils.savePersonalIntroduction(introduction)
                    SPUtils.saveNoticeInfo(notice)

                    ToastUtils.showShort(this@EditProfileActivity, "保存成功")
                    finish()
                } else {
                    val errorMsg = jsonObject.optString("message", "保存失败")
                    ToastUtils.showShort(this@EditProfileActivity, errorMsg)
                    binding.tvSave.isEnabled = true
                    binding.tvSave.text = "保存"
                }

            } catch (e: Exception) {
                e.printStackTrace()
                ToastUtils.showShort(this@EditProfileActivity, "保存失败")
                binding.tvSave.isEnabled = true
                binding.tvSave.text = "保存"
            }
        }
    }

    private suspend fun uploadAvatar(file: File): String? {
        return withContext(Dispatchers.IO) {
            try {
                val requestBody = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                val filePart = MultipartBody.Part.createFormData("file", file.name, requestBody)
                val createThumbnailBody = "false".toRequestBody("text/plain".toMediaTypeOrNull())

                val fileService = RetrofitClient.create(com.example.bilibili.data.api.FileService::class.java)
                val result = fileService.postImage(filePart, createThumbnailBody)
                val jsonObject = JSONObject(result)

                if (jsonObject.optInt("code") == 200) {
                    jsonObject.optString("data")
                } else {
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private fun resolvePickerImagePath(media: LocalMedia): String? {
        val candidates = listOfNotNull(
            media.sandboxPath,
            media.compressPath,
            media.realPath,
            media.path,
            media.availablePath
        )
        return candidates.firstOrNull { it.isNotBlank() && File(it).exists() }
    }
}