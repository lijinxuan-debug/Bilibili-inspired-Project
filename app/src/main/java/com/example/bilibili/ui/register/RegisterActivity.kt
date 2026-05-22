package com.example.bilibili.ui.register

import android.app.Dialog
import android.os.Bundle
import android.util.Base64
import android.util.Patterns
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.bilibili.data.api.AccountService
import com.example.bilibili.databinding.ActivityRegisterBinding
import com.example.bilibili.databinding.DialogAgreementBinding
import com.example.bilibili.util.AuthSessionHelper
import com.example.bilibili.util.PasswordToggleHelper
import com.example.bilibili.util.RetrofitClient
import com.example.bilibili.util.ToastUtils
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.lang.Exception

class RegisterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegisterBinding

    private var mCurrentCaptchaKey: String? = null

    private var isAllFilled: Boolean = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        PasswordToggleHelper.bind(binding.btnTogglePassword, binding.etPassword)
        PasswordToggleHelper.bind(binding.btnTogglePasswordConfirm, binding.etPasswordConfirm)

        // 处理输入框的软键盘
        initEditConfigs()

        // 监听每个输入框的输入状态
        initRegisterButtonState()

        // 初始化验证码
        loadCaptcha()

        // 点击验证码后可以刷新新的验证码
        binding.ivCaptchaCode.setOnClickListener {
            loadCaptcha()
        }

        // 监听注册按钮键
        binding.btnRegister.setOnClickListener {
            performRegister()
        }

        // 返回键
        binding.btnBack.setOnClickListener {
            finish()
        }

    }

    /**
     * 初始化验证码
     */
    private fun loadCaptcha() {
        lifecycleScope.launch {
            try {
                val accountService = RetrofitClient.create(AccountService::class.java)
                val responseString = accountService.getCaptcha()

                val root = JSONObject(responseString)
                val code = root.optInt("code")
                if (code == 200) {
                    val data = root.getJSONObject("data")
                    // 获取验证编码
                    val base64Raw = data.getString("checkCode")
                    // 存储当前验证啊key
                    mCurrentCaptchaKey = data.getString("checkCodeKey")

                    // 解析 Base64 图片字符串，过滤掉 "data:image/png;base64," 前缀
                    val base64Data = if (base64Raw.contains(",")) {
                        base64Raw.substring(base64Raw.indexOf(",") + 1)
                    } else {
                        base64Raw
                    }
                    // 对base64进行解码
                    val imageBytes = Base64.decode(base64Data, Base64.DEFAULT)
                    Glide.with(this@RegisterActivity).load(imageBytes).into(binding.ivCaptchaCode)
                    binding.etCaptcha.text?.clear()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@RegisterActivity, "网络异常，请重试", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 处理输入框的下一步
    private fun initEditConfigs() {
        // 账号的下一步
        binding.etAccount.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                binding.etNickname.requestFocus()
                true
            } else false
        }

        // 昵称下一步
        binding.etNickname.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                binding.etPassword.requestFocus()
                true
            } else false
        }

        // 密码下一步
        binding.etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                binding.etPasswordConfirm.requestFocus()
                true
            } else false
        }

        // 确认密码下一步
        binding.etPasswordConfirm.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                binding.etCaptcha.postDelayed({
                    binding.etCaptcha.requestFocus()
                    // 强行呼出键盘
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(binding.etCaptcha, InputMethodManager.SHOW_IMPLICIT)
                }, 50) // 给系统 50 毫秒处理布局切换
                true
            } else false
        }

        // 最后的验证码完成键
        binding.etCaptcha.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                // 隐藏键盘
                hideKeyboard()
                if (binding.btnRegister.isEnabled) {
                    performRegister()
                }
                true
            } else false
        }
    }

    // 隐藏软键盘的辅助函数
    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

    // 注册账号
    private fun performRegister() {
        if (!binding.cbAgreement.isChecked && isAllFilled) {
            // 没有勾选则需要弹窗提示用户勾选
            showAgreementDialog()
        } else if (binding.cbAgreement.isChecked && isAllFilled) {
            // 获取输入框内容
            val email = binding.etAccount.text.toString().trim()
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                ToastUtils.showLong(this,"请输入正确的邮箱格式")
                return
            }
            val nickName = binding.etNickname.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            if (password.length < 8) {
                ToastUtils.showLong(this,"密码长度必须在8-18位之间，同时必须包含字母数字两种")
                return
            }
            val confirmPassword = binding.etPasswordConfirm.text.toString().trim()
            if (password != confirmPassword) {
                ToastUtils.showLong(this,"两次输入的密码不一致")
                return
            }
            // 密码必须大于8位
            val captcha = binding.etCaptcha.text.toString().trim()
            val captchaKey = mCurrentCaptchaKey ?: ""

            lifecycleScope.launch {
                try {
                    val accountService = RetrofitClient.create(AccountService::class.java)
                    val responseString = accountService.register(
                        email = email,
                        nickName = nickName,
                        registerPassword = password,
                        checkCode = captcha,
                        checkCodeKey = captchaKey
                    )
                    val root = JSONObject(responseString)
                    if (root.optInt("code") == 200) {
                        ToastUtils.showShort(this@RegisterActivity, "注册成功")
                        val data = root.optJSONObject("data")
                        if (data != null) {
                            AuthSessionHelper.saveLoginData(data)
                            AuthSessionHelper.syncProfileFromServer(data.getString("userId"))
                            AuthSessionHelper.navigateToMainAndFinish(this@RegisterActivity)
                        } else {
                            finish()
                        }
                    } else {
                        ToastUtils.showShort(this@RegisterActivity,root.getString("message"))
                        // 重新加载验证码
                        loadCaptcha()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    ToastUtils.showShort(this@RegisterActivity,"网络出错")
                }
            }

        }
    }

    private fun initRegisterButtonState() {
        val editTexts = listOf(
            binding.etAccount,
            binding.etNickname,
            binding.etPassword,
            binding.etPasswordConfirm,
            binding.etCaptcha
        )

        // 为每个输入框添加监听
        for (editText in editTexts) {
            editText.addTextChangedListener {
                // 每次文字变化，都检查一遍是否所有框都填好了
                checkInputs(editTexts)
            }
        }
    }

    /**
     * 校验输入框内容
     */
    private fun checkInputs(editTexts: List<EditText>) {
        // 所有输入框都不为空
        isAllFilled = editTexts.all { it.text.toString().trim().isNotEmpty() }
        // 更新按钮状态
        binding.btnRegister.isEnabled = isAllFilled

    }

    /**
     * 显示用户协议弹窗
     */
    private fun showAgreementDialog() {
        val dialog = Dialog(this)
        val dialogBinding = DialogAgreementBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        dialog.show()

        // show 之后再调整窗口属性
        dialog.window?.apply {
            // 设置背景透明（防止出现系统默认的白边框）
            setBackgroundDrawableResource(android.R.color.transparent)

            // 获取当前窗口参数
            val params = attributes
            // 将宽度设置为屏幕宽度的 85%
            val displayMetrics = resources.displayMetrics
            params.width = (displayMetrics.widthPixels * 0.85).toInt()
            params.height = WindowManager.LayoutParams.WRAP_CONTENT

            // 重新赋值回窗口
            attributes = params
        }

        dialogBinding.btnDialogAgree.setOnClickListener {
            binding.cbAgreement.isChecked = true
            dialog.dismiss()
            performRegister()
        }

        dialogBinding.tvDialogDisagree.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.ivClose.setOnClickListener {
            dialog.dismiss()
        }

    }

}