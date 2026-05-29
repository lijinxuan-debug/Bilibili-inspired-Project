package com.example.bilibili.ui.login

import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.util.Patterns
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.bilibili.data.api.AccountService
import com.example.bilibili.databinding.ActivityLoginBinding
import com.example.bilibili.ui.register.RegisterActivity
import com.example.bilibili.util.AuthSessionHelper
import com.example.bilibili.util.MD5Utils
import com.example.bilibili.util.PasswordToggleHelper
import com.example.bilibili.util.RetrofitClient
import com.example.bilibili.util.ToastUtils
import kotlinx.coroutines.launch
import org.json.JSONObject

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding

    private var mCurrentCaptchaKey: String? = null

    private var isAllFilled: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        PasswordToggleHelper.bind(binding.btnTogglePassword, binding.etPassword)

        binding.jumpToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        loadCaptcha()
        binding.ivCaptchaCode.setOnClickListener { loadCaptcha() }

        initRegisterButtonState()

        binding.btnLogin.setOnClickListener {
            performLogin()
        }
    }

    private fun performLogin() {
        if (!isAllFilled) return

        val email = binding.etAccount.text.toString().trim()
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            ToastUtils.showLong(this, "请输入正确的邮箱格式")
            return
        }
        val password = binding.etPassword.text.toString().trim()
        if (password.length < 8) {
            ToastUtils.showLong(this, "密码长度必须在8-18位之间，同时必须包含字母数字两种")
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
                    checkCodeKey = captchaKey,
                )
                val root = JSONObject(responseString)
                if (root.optInt("code") == 200) {
                    ToastUtils.showShort(this@LoginActivity, "登录成功")
                    val data = root.getJSONObject("data")
                    AuthSessionHelper.saveLoginData(data)
                    AuthSessionHelper.syncProfileFromServer(data.getString("userId"))
                    AuthSessionHelper.navigateToMainAndFinish(this@LoginActivity)
                } else {
                    ToastUtils.showShort(this@LoginActivity, root.getString("message"))
                    loadCaptcha()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                ToastUtils.showShort(this@LoginActivity, "网络出错")
            }
        }
    }

    private fun loadCaptcha() {
        lifecycleScope.launch {
            try {
                val accountService = RetrofitClient.create(AccountService::class.java)
                val responseString = accountService.getCaptcha()

                val root = JSONObject(responseString)
                val code = root.optInt("code")
                if (code == 200) {
                    val data = root.getJSONObject("data")
                    val base64Raw = data.getString("checkCode")
                    mCurrentCaptchaKey = data.getString("checkCodeKey")

                    val base64Data = if (base64Raw.contains(",")) {
                        base64Raw.substring(base64Raw.indexOf(",") + 1)
                    } else {
                        base64Raw
                    }
                    val imageBytes = Base64.decode(base64Data, Base64.DEFAULT)
                    Glide.with(this@LoginActivity).load(imageBytes).into(binding.ivCaptchaCode)
                    binding.etCaptcha.text?.clear()
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
            binding.etCaptcha,
        )

        for (editText in editTexts) {
            editText.addTextChangedListener {
                checkInputs(editTexts)
            }
        }

        binding.etAccount.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_NEXT ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                binding.etPassword.requestFocus()
                return@setOnEditorActionListener true
            }
            false
        }

        binding.etPassword.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_NEXT ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                binding.etCaptcha.requestFocus()
                binding.etCaptcha.post {
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.showSoftInput(binding.etCaptcha, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                }
                return@setOnEditorActionListener true
            }
            false
        }
    }

    private fun checkInputs(editTexts: List<EditText>) {
        isAllFilled = editTexts.all { it.text.toString().trim().isNotEmpty() }
        binding.btnLogin.isEnabled = isAllFilled
    }
}
