package com.example.bilibili.ui.register

import android.os.Bundle
import android.util.Base64
import android.util.Patterns
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
import com.example.bilibili.util.AuthSessionHelper
import com.example.bilibili.util.PasswordToggleHelper
import com.example.bilibili.util.RetrofitClient
import com.example.bilibili.util.ToastUtils
import kotlinx.coroutines.launch
import org.json.JSONObject

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

        initEditConfigs()
        initRegisterButtonState()

        loadCaptcha()
        binding.ivCaptchaCode.setOnClickListener { loadCaptcha() }

        binding.btnRegister.setOnClickListener {
            performRegister()
        }

        binding.btnBack.setOnClickListener {
            finish()
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
                    Glide.with(this@RegisterActivity).load(imageBytes).into(binding.ivCaptchaCode)
                    binding.etCaptcha.text?.clear()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@RegisterActivity, "网络异常，请重试", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun initEditConfigs() {
        binding.etAccount.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                binding.etNickname.requestFocus()
                true
            } else false
        }

        binding.etNickname.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                binding.etPassword.requestFocus()
                true
            } else false
        }

        binding.etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                binding.etPasswordConfirm.requestFocus()
                true
            } else false
        }

        binding.etPasswordConfirm.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                binding.etCaptcha.postDelayed({
                    binding.etCaptcha.requestFocus()
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(binding.etCaptcha, InputMethodManager.SHOW_IMPLICIT)
                }, 50)
                true
            } else false
        }

        binding.etCaptcha.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard()
                if (binding.btnRegister.isEnabled) {
                    performRegister()
                }
                true
            } else false
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

    private fun performRegister() {
        if (!isAllFilled) return

        val email = binding.etAccount.text.toString().trim()
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            ToastUtils.showLong(this, "请输入正确的邮箱格式")
            return
        }
        val nickName = binding.etNickname.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        if (password.length < 8) {
            ToastUtils.showLong(this, "密码长度必须在8-18位之间，同时必须包含字母数字两种")
            return
        }
        val confirmPassword = binding.etPasswordConfirm.text.toString().trim()
        if (password != confirmPassword) {
            ToastUtils.showLong(this, "两次输入的密码不一致")
            return
        }
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
                    checkCodeKey = captchaKey,
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
                    ToastUtils.showShort(this@RegisterActivity, root.getString("message"))
                    loadCaptcha()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                ToastUtils.showShort(this@RegisterActivity, "网络出错")
            }
        }
    }

    private fun initRegisterButtonState() {
        val editTexts = listOf(
            binding.etAccount,
            binding.etNickname,
            binding.etPassword,
            binding.etPasswordConfirm,
            binding.etCaptcha,
        )

        for (editText in editTexts) {
            editText.addTextChangedListener {
                checkInputs(editTexts)
            }
        }
    }

    private fun checkInputs(editTexts: List<EditText>) {
        isAllFilled = editTexts.all { it.text.toString().trim().isNotEmpty() }
        binding.btnRegister.isEnabled = isAllFilled
    }
}
