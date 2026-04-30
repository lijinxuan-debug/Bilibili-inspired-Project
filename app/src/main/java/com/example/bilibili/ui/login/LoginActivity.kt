package com.example.bilibili.ui.login

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.util.Patterns
import android.view.KeyEvent
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.bilibili.MainActivity
import com.example.bilibili.data.api.AccountService
import com.example.bilibili.databinding.ActivityLoginBinding
import com.example.bilibili.databinding.DialogAgreementBinding
import com.example.bilibili.ui.register.RegisterActivity
import com.example.bilibili.util.MD5Utils
import com.example.bilibili.util.RetrofitClient
import com.example.bilibili.util.SPUtils
import com.example.bilibili.util.ToastUtils
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.lang.Exception

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding

    private var mCurrentCaptchaKey: String? = null

    private var isAllFilled: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 调用自动登录网络请求
        autologin()

        // 跳转到注册按钮监听
        binding.jumpToRegister.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }

        // 初始化验证码
        loadCaptcha()

        // 监听输入框输入
        initRegisterButtonState()

        // 监听登录按钮
        binding.btnLogin.setOnClickListener {
            performLogin()
        }

    }

    /**
     * 登录功能
     */
    private fun performLogin() {
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
            val password = binding.etPassword.text.toString().trim()
            if (password.length < 8) {
                ToastUtils.showLong(this,"密码长度必须在8-18位之间，同时必须包含字母数字两种")
                return
            }
            val captcha = binding.etCaptcha.text.toString().trim()
            val captchaKey = mCurrentCaptchaKey ?: ""

            lifecycleScope.launch {
                try {
                    val accountService = RetrofitClient.create(AccountService::class.java)
                    val responseString = accountService.login(
                        email = email,
                        password = MD5Utils.encrypt(password),
                        checkCode = captcha,
                        checkCodeKey = captchaKey
                    )
                    val root = JSONObject(responseString)
                    if (root.optInt("code") == 200) {
                        ToastUtils.showShort(this@LoginActivity,"登录成功")
                        val data = root.getJSONObject("data")
                        // 保存token到SP
                        SPUtils.saveToken(data.getString("token"))
                        // 保存硬币
                        SPUtils.saveCurrentCoinCount(data.optInt("currentCoinCount"))
                        // 保存用户id
                        SPUtils.saveUserId(data.getString("userId"))
                        // 保存头像
                        SPUtils.saveAvatar(data.getString("avatar"))
                        // 保存昵称
                        SPUtils.saveNickname(data.getString("nickName"))

                        // 跳转到主页
                        val intent = Intent(this@LoginActivity, MainActivity::class.java)
                        startActivity(intent)
                        finish()

                    } else {
                        ToastUtils.showShort(this@LoginActivity,root.getString("message"))
                        // 重新加载验证码
                        loadCaptcha()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    ToastUtils.showShort(this@LoginActivity,"网络出错")
                }
            }

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
                    Glide.with(this@LoginActivity).load(imageBytes).into(binding.ivCaptchaCode)

                }

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@LoginActivity, "网络异常，请重试", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun initRegisterButtonState() {
        val editTexts = listOf(
            binding.etAccount,
            binding.etPassword,
            binding.etCaptcha
        )

        // 为每个输入框添加监听
        for (editText in editTexts) {
            editText.addTextChangedListener {
                // 每次文字变化，都检查一遍是否所有框都填好了
                checkInputs(editTexts)
            }
        }

        // 监听邮箱输入框的IME动作（回车键）
        binding.etAccount.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_NEXT ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                // 跳转到密码输入框
                binding.etPassword.requestFocus()
                return@setOnEditorActionListener true
            }
            false
        }

        // 监听密码输入框的IME动作（回车键）
        binding.etPassword.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_NEXT ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                // 跳转到验证码输入框
                binding.etCaptcha.requestFocus()
                // 显示软键盘
                binding.etCaptcha.post {
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.showSoftInput(binding.etCaptcha, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                }
                return@setOnEditorActionListener true
            }
            false
        }
    }

    /**
     * 校验输入框内容
     */
    private fun checkInputs(editTexts: List<EditText>) {
        // 所有输入框都不为空
        isAllFilled = editTexts.all { it.text.toString().trim().isNotEmpty() }
        // 更新按钮状态
        binding.btnLogin.isEnabled = isAllFilled

    }

    /**
     * 自动登录
     */
    private fun autologin() {
        // 如果token为空说明未登录
        val token = SPUtils.getToken()
        if (token.isEmpty()) {
            return
        }
        lifecycleScope.launch {
            try {
                val accountService = RetrofitClient.create(AccountService::class.java)
                val response = JSONObject(accountService.autoLogin(token))

                val resToken = response.getJSONObject("data").getString("token")
                if (resToken != token) {
                    // 说明token快过期了，先存放到本地sp
                    SPUtils.saveToken(resToken)
                }

                // 同时直接跳转到主页
                val intent = Intent(this@LoginActivity, MainActivity::class.java)
                startActivity(intent)
                finish()

            } catch (e: Exception) {
                e.printStackTrace()
                // 自动登录失败，清除无效的token
                SPUtils.cleanToken()
                ToastUtils.showShort(this@LoginActivity,"登录已过期，请重新登录")
            }
        }
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
            performLogin()
        }

        dialogBinding.tvDialogDisagree.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.ivClose.setOnClickListener {
            dialog.dismiss()
        }

    }

}