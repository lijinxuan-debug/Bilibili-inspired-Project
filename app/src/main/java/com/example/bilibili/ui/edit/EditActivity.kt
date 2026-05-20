package com.example.bilibili.ui.edit

import android.app.DatePickerDialog
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.DatePicker
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.TextViewCompat
import androidx.lifecycle.lifecycleScope
import com.example.bilibili.R
import com.example.bilibili.data.api.PostService
import com.example.bilibili.databinding.ActivityEditBinding
import com.example.bilibili.databinding.DialogEditTextBinding
import com.example.bilibili.databinding.DialogGenderSelectBinding
import com.example.bilibili.util.GlideEngine
import com.example.bilibili.util.RetrofitClient
import com.example.bilibili.util.SPUtils
import com.example.bilibili.util.ToastUtils
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.luck.picture.lib.basic.PictureSelector
import com.luck.picture.lib.config.PictureConfig
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
import java.util.Calendar

class EditActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEditBinding
    private val postService = RetrofitClient.create(PostService::class.java)

    // 用户数据
    private var userId: String = ""
    private var avatar: String = ""
    private var nickname: String = ""
    private var sex: Int = 2 // 0=男，1=女，2=保密
    private var birthday: String = ""
    private var signature: String = ""
    private var school: String = ""

    // 临时数据
    private var tempAvatarFile: File? = null
    private var uploadedAvatarUrl: String? = null
    private var tempNickname: String = ""
    private var tempSex: Int = 2
    private var tempBirthday: String = ""
    private var tempSignature: String = ""
    private var tempSchool: String = ""

    // 是否有未保存的修改
    private var hasUnsavedChanges: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupClickListeners()
        setupBackPressedCallback()
        loadUserInfo()
    }

    private fun setupBackPressedCallback() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackPress()
            }
        })
    }

    private fun setEditTextSelectionHandlesColor(editText: EditText) {
        // 通过反射设置文本选择手柄的颜色
        try {
            val editorField = TextViewCompat::class.java.getDeclaredField("mEditor")
            editorField.isAccessible = true
            val editor = editorField.get(editText)

            val editorClass = editor?.javaClass
            val selectHandleLeftField = editorClass?.getDeclaredField("mSelectHandleLeft")
            val selectHandleRightField = editorClass?.getDeclaredField("mSelectHandleRight")
            val selectHandleCenterField = editorClass?.getDeclaredField("mSelectHandleCenter")

            selectHandleLeftField?.isAccessible = true
            selectHandleRightField?.isAccessible = true
            selectHandleCenterField?.isAccessible = true

            val drawableLeft = resources.getDrawable(R.drawable.text_cursor_handle_left, null)
            val drawableRight = resources.getDrawable(R.drawable.text_cursor_handle_right, null)
            val drawableCenter = resources.getDrawable(R.drawable.text_cursor_handle_middle, null)

            selectHandleLeftField?.set(editor, drawableLeft)
            selectHandleRightField?.set(editor, drawableRight)
            selectHandleCenterField?.set(editor, drawableCenter)

            drawableLeft?.setTint(resources.getColor(R.color.bilibili_pink, null))
            drawableRight?.setTint(resources.getColor(R.color.bilibili_pink, null))
            drawableCenter?.setTint(resources.getColor(R.color.bilibili_pink, null))
        } catch (e: Exception) {
            // 反射失败，忽略错误
            e.printStackTrace()
        }
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupClickListeners() {
        // 返回按钮
        binding.ivBack.setOnClickListener {
            handleBackPress()
        }

        // 保存按钮
        binding.tvSave.setOnClickListener {
            saveUserInfo()
        }

        // 头像点击
        binding.clAvatar.setOnClickListener {
            selectAvatar()
        }

        // 昵称点击
        binding.clNickname.setOnClickListener {
            showNicknameDialog()
        }

        // 性别点击
        binding.clGender.setOnClickListener {
            showGenderDialog()
        }

        // 出生日期点击
        binding.clBirthday.setOnClickListener {
            showDatePicker()
        }

        // 个性签名点击
        binding.clSignature.setOnClickListener {
            showSignatureDialog()
        }

        // 学校点击
        binding.clSchool.setOnClickListener {
            showSchoolDialog()
        }

        // UID点击
        binding.clUid.setOnClickListener {
            copyUidToClipboard()
        }
    }

    private fun loadUserInfo() {
        lifecycleScope.launch {
            try {
                val responseString = withContext(Dispatchers.IO) {
                    val postService = RetrofitClient.create(PostService::class.java)
                    postService.getUserInfo(SPUtils.getUserId())
                }

                val userInfo = JSONObject(responseString)
                if (userInfo.optInt("code") == 200) {
                    val data = userInfo.getJSONObject("data")
                    bindUserData(data)
                } else {
                    ToastUtils.showShort(this@EditActivity, "获取用户信息失败")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                ToastUtils.showShort(this@EditActivity, "网络请求失败")
            }
        }
    }

    private fun bindUserData(data: JSONObject) {
        // 保存原始数据
        userId = data.optString("userId", "")
        avatar = data.optString("avatar", "")
        nickname = data.optString("nickName", "")
        sex = data.optInt("sex", 2)
        birthday = data.optString("birthday", "")
        signature = data.optString("personalIntroduction", "")
        school = data.optString("school", "")

        // 初始化临时数据
        tempNickname = nickname
        tempSex = sex
        tempBirthday = birthday
        tempSignature = signature
        tempSchool = school

        // 更新UI
        GlideEngine.loadUserAvatar(this, avatar, binding.ivAvatar)
        binding.tvNickname.text = tempNickname.ifEmpty { "未设置" }

        val sexText = when (tempSex) {
            0 -> "男"
            1 -> "女"
            else -> "保密"
        }
        binding.tvGender.text = sexText

        binding.tvBirthday.text = tempBirthday.ifEmpty { "未设置" }
        binding.tvSignature.text = tempSignature.ifEmpty { "未设置" }
        binding.tvSchool.text = tempSchool.ifEmpty { "未设置" }
        binding.tvUid.text = userId
    }

    private fun selectAvatar() {
        PictureSelector.create(this)
            .openGallery(SelectMimeType.ofImage())
            .setImageEngine(GlideEngine.createGlideImageEngine())
            .setSelectionMode(SelectModeConfig.SINGLE) // ⭐ 关键核心：强制设置为单选模式，点击直接切换
            .setMaxSelectNum(1)
            .setMinSelectNum(1)
            .setImageSpanCount(4)
            .forResult(object : OnResultCallbackListener<LocalMedia> {
                override fun onResult(result: ArrayList<LocalMedia>) {
                    if (result.isNotEmpty()) {
                        val media = result[0]
                        val filePath = media.realPath  // 使用realPath获取真实路径

                        if (filePath != null) {
                            tempAvatarFile = File(filePath)
                            GlideEngine.loadUserAvatar(this@EditActivity, filePath, binding.ivAvatar)
                            hasUnsavedChanges = true
                        }
                    }
                }

                override fun onCancel() {
                    // 用户取消选择
                }
            })
    }

    private fun showNicknameDialog() {
        showEditTextBottomSheet(
            currentText = tempNickname,
            hint = "请输入昵称",
            onConfirmed = { newName ->
                if (newName.isNotEmpty() && newName != tempNickname) {
                    tempNickname = newName
                    binding.tvNickname.text = newName
                    hasUnsavedChanges = true
                }
            }
        )
    }

    /**
     * 弹出编辑 BottomSheet：自动聚焦、弹出软键盘、全选已有内容
     */
    private fun showEditTextBottomSheet(
        currentText: String,
        hint: String,
        maxLines: Int = 1,
        onConfirmed: (String) -> Unit
    ) {
        val dialogBinding = DialogEditTextBinding.inflate(LayoutInflater.from(this))
        val editText = dialogBinding.etInput
        editText.hint = hint
        editText.maxLines = maxLines
        editText.imeOptions = EditorInfo.IME_ACTION_DONE
        setEditTextSelectionHandlesColor(editText)

        editText.setText(currentText)
        editText.selectAll()

        val dialog = BottomSheetDialog(this)
        dialog.setContentView(dialogBinding.root)
        dialog.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        )

        dialog.setOnShowListener {
            editText.requestFocus()
            editText.postDelayed({
                editText.selectAll()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
            }, 100)
        }

        editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                onConfirmed(editText.text.toString().trim())
                dialog.dismiss()
                true
            } else {
                false
            }
        }

        dialog.show()
    }

    private fun showGenderDialog() {
        val dialogBinding = DialogGenderSelectBinding.inflate(LayoutInflater.from(this))

        when (tempSex) {
            0 -> dialogBinding.rbMale.isChecked = true
            1 -> dialogBinding.rbFemale.isChecked = true
            2 -> dialogBinding.rbSecret.isChecked = true
        }

        val dialog = BottomSheetDialog(this)
        dialog.setContentView(dialogBinding.root)
        dialog.show()

        // 监听RadioButton的点击事件
        dialogBinding.rbMale.setOnClickListener {
            if (tempSex != 0) {
                tempSex = 0
                binding.tvGender.text = "男"
                hasUnsavedChanges = true
            }
            dialog.dismiss()
        }

        dialogBinding.rbFemale.setOnClickListener {
            if (tempSex != 1) {
                tempSex = 1
                binding.tvGender.text = "女"
                hasUnsavedChanges = true
            }
            dialog.dismiss()
        }

        dialogBinding.rbSecret.setOnClickListener {
            if (tempSex != 2) {
                tempSex = 2
                binding.tvGender.text = "保密"
                hasUnsavedChanges = true
            }
            dialog.dismiss()
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        var year = calendar.get(Calendar.YEAR)
        var month = calendar.get(Calendar.MONTH)
        var day = calendar.get(Calendar.DAY_OF_MONTH)

        // 如果已有生日，解析日期
        if (tempBirthday.isNotEmpty()) {
            try {
                val parts = tempBirthday.split("-")
                if (parts.size == 3) {
                    year = parts[0].toInt()
                    month = parts[1].toInt() - 1
                    day = parts[2].toInt()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        DatePickerDialog(
            this,
            R.style.BilibiliDatePickerTheme,
            { _: DatePicker, selectedYear: Int, selectedMonth: Int, selectedDay: Int ->
                val newBirthday = "$selectedYear-${String.format("%02d", selectedMonth + 1)}-${String.format("%02d", selectedDay)}"
                if (newBirthday != tempBirthday) {
                    tempBirthday = newBirthday
                    binding.tvBirthday.text = newBirthday
                    hasUnsavedChanges = true
                }
            },
            year, month, day
        ).show()
    }

    private fun showSignatureDialog() {
        showEditTextBottomSheet(
            currentText = tempSignature,
            hint = "请输入个性签名",
            maxLines = 3,
            onConfirmed = { newSignature ->
                if (newSignature != tempSignature) {
                    tempSignature = newSignature
                    binding.tvSignature.text = newSignature.ifEmpty { "未设置" }
                    hasUnsavedChanges = true
                }
            }
        )
    }

    private fun showSchoolDialog() {
        showEditTextBottomSheet(
            currentText = tempSchool,
            hint = "请输入学校名称",
            onConfirmed = { newSchool ->
                if (newSchool != tempSchool) {
                    tempSchool = newSchool
                    binding.tvSchool.text = newSchool.ifEmpty { "未设置" }
                    hasUnsavedChanges = true
                }
            }
        )
    }

    private fun copyUidToClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = android.content.ClipData.newPlainText("UID", userId)
        clipboard.setPrimaryClip(clip)
        ToastUtils.showShort(this, "UID已复制到剪贴板")
    }

    private fun saveUserInfo() {
        // 验证昵称
        val finalNickname = tempNickname.trim()
        if (finalNickname.isEmpty()) {
            ToastUtils.showShort(this, "昵称不能为空")
            return
        }

        if (finalNickname.length > 20) {
            ToastUtils.showShort(this, "昵称不能超过20个字符")
            return
        }

        val finalSchool = tempSchool.trim()
        if (finalSchool.length > 100) {
            ToastUtils.showShort(this, "学校名称不能超过100个字符")
            return
        }

        val finalSignature = tempSignature.trim()
        if (finalSignature.length > 300) {
            ToastUtils.showShort(this, "个性签名不能超过300个字符")
            return
        }

        // 显示加载提示
        binding.tvSave.isEnabled = false
        binding.tvSave.text = "保存中..."

        lifecycleScope.launch {
            try {
                // 如果选择了新头像，先上传
                var finalAvatar = avatar
                if (tempAvatarFile != null) {
                    val uploadedUrl = uploadAvatar(tempAvatarFile!!)
                    if (uploadedUrl != null) {
                        finalAvatar = uploadedUrl
                    }
                }

                // 调用更新用户信息接口
                val response = postService.updateUserInfo(
                    avatar = finalAvatar,
                    nickName = finalNickname,
                    sex = tempSex,
                    birthday = tempBirthday,
                    school = finalSchool,
                    noticeInfo = "", // 这个字段在当前界面不编辑
                    personalIntroduction = finalSignature
                )

                val jsonObject = JSONObject(response)
                if (jsonObject.optInt("code") == 200) {
                    // 更新本地存储
                    SPUtils.saveAvatar(finalAvatar)
                    SPUtils.saveNickname(finalNickname)
                    SPUtils.saveSex(tempSex)
                    SPUtils.saveBirthday(tempBirthday)
                    SPUtils.saveSchool(finalSchool)
                    SPUtils.savePersonalIntroduction(finalSignature)

                    ToastUtils.showShort(this@EditActivity, "保存成功")
                    hasUnsavedChanges = false
                    finish()
                } else {
                    val errorMsg = jsonObject.optString("message", "保存失败")
                    ToastUtils.showShort(this@EditActivity, errorMsg)
                    binding.tvSave.isEnabled = true
                    binding.tvSave.text = "保存"
                }

            } catch (e: Exception) {
                e.printStackTrace()
                ToastUtils.showShort(this@EditActivity, "保存失败")
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

    private fun handleBackPress() {
        if (hasUnsavedChanges) {
            val dialog = AlertDialog.Builder(this)
                .setTitle("退出编辑")
                .setMessage("您有未保存的修改，确定要退出吗？")
                .setPositiveButton("退出") { _, _ ->
                    finish()
                }
                .setNegativeButton("继续编辑", null)
                .create()

            dialog.show()

            // 设置按钮颜色为粉色
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(resources.getColor(R.color.bilibili_pink, null))
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(resources.getColor(R.color.bilibili_pink, null))
        } else {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 清理临时文件
        tempAvatarFile?.delete()
    }
}