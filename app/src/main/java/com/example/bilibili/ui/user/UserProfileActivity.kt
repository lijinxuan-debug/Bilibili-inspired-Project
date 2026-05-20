package com.example.bilibili.ui.user

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.bilibili.R
import com.example.bilibili.data.api.PostService
import com.example.bilibili.databinding.ActivityUserProfileBinding
import com.example.bilibili.ui.edit.EditActivity
import com.example.bilibili.ui.personal.contribute.ContributeFragment
import com.example.bilibili.ui.personal.home.HomeFragment
import com.example.bilibili.ui.login.LoginActivity
import com.example.bilibili.util.GlideEngine
import com.example.bilibili.util.RetrofitClient
import com.example.bilibili.util.SPUtils
import com.example.bilibili.util.ToastUtils
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class UserProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserProfileBinding
    private val viewModel: UserProfileViewModel by viewModels()

    private val postService = RetrofitClient.create(PostService::class.java)

    // 只保留主页和投稿两个 tab
    private val tabTitles = listOf("主页", "投稿")

    // 目标用户ID
    private var targetUserId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 获取目标用户ID
        targetUserId = intent.getStringExtra("user_id")
        if (targetUserId.isNullOrEmpty()) {
            ToastUtils.showShort(this, "用户信息加载失败")
            finish()
            return
        }

        // 设置状态栏适配
        setupStatusBarPadding()

        // 初始化布局
        setupViewPagerAndTabs()

        // 设置退出登录按钮（右上角）
        setupLogoutButton()

        // 关注 / 编辑资料按钮
        setupProfileActionButton()

        // 加载用户信息
        loadUserInfo()

        // 观察数据变化
        observeData()

        // 返回按钮
        binding.ivBack.setOnClickListener { finish() }
    }

    private fun isCurrentUser(): Boolean {
        val currentUserId = SPUtils.getUserId()
        return currentUserId.isNotEmpty() && targetUserId == currentUserId
    }

    private fun setupProfileActionButton() {
        if (isCurrentUser()) {
            binding.btnFocus.visibility = View.VISIBLE
            binding.btnFocus.text = "编辑资料"
            binding.btnFocus.setBackgroundResource(R.drawable.bg_pink_border)
            binding.btnFocus.setTextColor(resources.getColor(R.color.bilibili_pink, null))
            binding.btnFocus.setOnClickListener {
                startActivity(Intent(this, EditActivity::class.java))
            }
        } else {
            binding.btnFocus.visibility = View.VISIBLE
            binding.btnFocus.setOnClickListener {
                viewModel.toggleFocus(targetUserId!!)
            }
        }
    }

    private fun setupLogoutButton() {
        // 检查是否是当前用户
        val currentUserId = SPUtils.getUserId()
        val isCurrentUser = (targetUserId == currentUserId)

        if (isCurrentUser) {
            // 显示退出登录按钮
            binding.btnLogout.visibility = View.VISIBLE
            binding.btnLogout.setOnClickListener {
                showLogoutDialog()
            }
        } else {
            // 隐藏退出登录按钮
            binding.btnLogout.visibility = View.GONE
        }
    }

    private fun showLogoutDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("退出登录")
            .setMessage("确定要退出登录吗？")
            .setPositiveButton("确定") { _, _ ->
                // 退出登录逻辑
                SPUtils.cleanToken()
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton("取消", null)
            .create()

        dialog.show()

        // 设置按钮颜色为粉色
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(resources.getColor(R.color.bilibili_pink, null))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(resources.getColor(R.color.bilibili_pink, null))
    }

    private fun setupStatusBarPadding() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top

            // 只给顶部banner添加状态栏padding
            val params = binding.ivBanner.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            params.topMargin = statusBarHeight
            binding.ivBanner.layoutParams = params

            // 给返回按钮也添加顶部padding
            val backParams = binding.ivBack.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            backParams.topMargin = statusBarHeight
            binding.ivBack.layoutParams = backParams

            insets
        }
    }

    private fun setupViewPagerAndTabs() {
        binding.viewPager.adapter = UserProfilePagerAdapter(this)
        binding.viewPager.offscreenPageLimit = 2

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = tabTitles[position]
        }.attach()
    }

    private fun loadUserInfo() {
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    postService.getUserInfo(targetUserId!!)
                }

                val jsonObject = JSONObject(result)
                if (jsonObject.optInt("code") == 200) {
                    val userData = jsonObject.getJSONObject("data")
                    viewModel.setUserInfo(userData)
                } else {
                    val errorMsg = jsonObject.optString("message", "加载用户信息失败")
                    ToastUtils.showShort(this@UserProfileActivity, errorMsg)
                }
            } catch (e: Exception) {
                ToastUtils.showShort(this@UserProfileActivity, "网络错误，请重试")
                e.printStackTrace()
            }
        }
    }

    private fun observeData() {
        viewModel.userInfo.observe(this) { userInfo ->
            // 更新UI
            userInfo?.let { updateUI(it) }
        }

        viewModel.focusState.observe(this) { isFocused ->
            if (!isCurrentUser()) {
                updateFocusButton(isFocused)
            }
        }
    }

    private fun updateUI(userInfo: UserInfo) {
        // 加载头像
        GlideEngine.loadUserAvatar(this, userInfo.avatar, binding.ivAvatar)

        // 设置昵称
        binding.tvNickname.text = userInfo.nickName

        // 设置简介
        binding.tvDescription.text = if (userInfo.personalIntroduction.isNullOrEmpty()) {
            "这个人很懒，什么都没留下"
        } else {
            userInfo.personalIntroduction
        }

        // 设置统计数据
        binding.tvFansCount.text = userInfo.fansCount.toString()
        binding.tvFollowCount.text = userInfo.focusCount.toString()
        binding.tvLikeCount.text = userInfo.likeCount.toString()

        // 他人主页才更新关注状态
        if (!isCurrentUser()) {
            viewModel.setFocused(userInfo.haveFocus)
        }

        // 显示内容，隐藏加载遮罩
        binding.loadingView.visibility = View.GONE
    }

    private fun updateFocusButton(isFocused: Boolean) {
        if (isCurrentUser()) return
        if (isFocused) {
            binding.btnFocus.text = "已关注"
            binding.btnFocus.setBackgroundColor(resources.getColor(R.color.sl_divider_color))
            binding.btnFocus.setTextColor(resources.getColor(R.color.sl_comment_item_color_default))
        } else {
            binding.btnFocus.text = "关注"
            binding.btnFocus.setBackgroundColor(resources.getColor(R.color.bilibili_pink))
            binding.btnFocus.setTextColor(resources.getColor(R.color.white))
        }
    }

    inner class UserProfilePagerAdapter(fragmentActivity: FragmentActivity) :
        FragmentStateAdapter(fragmentActivity) {
        override fun getItemCount(): Int = tabTitles.size

        override fun createFragment(position: Int): androidx.fragment.app.Fragment {
            return when (position) {
                0 -> {
                    // 主页 Fragment，传递用户ID
                    val fragment = HomeFragment()
                    fragment.arguments = Bundle().apply {
                        putString("user_id", targetUserId)
                    }
                    fragment
                }
                1 -> {
                    // 投稿 Fragment，传递用户ID
                    val fragment = ContributeFragment()
                    fragment.arguments = Bundle().apply {
                        putString("user_id", targetUserId)
                    }
                    fragment
                }
                else -> throw IllegalArgumentException("Invalid position")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 不需要清除 binding，因为这是一个 Activity
    }
}