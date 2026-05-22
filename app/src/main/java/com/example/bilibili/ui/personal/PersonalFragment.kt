package com.example.bilibili.ui.personal

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.bilibili.R
import com.example.bilibili.data.api.PostService
import com.example.bilibili.databinding.FragmentPersonalBinding
import com.example.bilibili.ui.edit.EditActivity
import com.example.bilibili.ui.login.LoginActivity
import com.example.bilibili.ui.personal.collect.CollectFragment
import com.example.bilibili.ui.personal.contribute.ContributeFragment
import com.example.bilibili.ui.personal.home.HomeFragment
import com.example.bilibili.util.GlideEngine
import com.example.bilibili.util.UserInfoText
import com.example.bilibili.util.optNormalizedString
import com.example.bilibili.util.RetrofitClient
import com.example.bilibili.util.SPUtils
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class PersonalFragment : Fragment() {
    private var _binding: FragmentPersonalBinding? = null
    private val binding get() = _binding!!

    private val tabTitles = listOf("主页", "投稿", "收藏")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPersonalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 添加状态栏适配
        setupStatusBarPadding()

        setupViewPagerAndTabs()

        // 先用本地缓存头像，避免等接口期间一直显示默认图
        GlideEngine.loadUserAvatar(requireContext(), SPUtils.getAvatar(), binding.ivAvatar)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val responseString = withContext(Dispatchers.IO) {
                    // 获取个人信息
                    val postService = RetrofitClient.create(PostService::class.java)
                    postService.getUserInfo(SPUtils.getUserId())
                }
                val userInfo = JSONObject(responseString)
                if (userInfo.optInt("code") == 200) {
                    val data = userInfo.getJSONObject("data")
                    // 更新个人信息
                    binding.apply {
                        // 获取并更新头像
                        val avatar = data.optString("avatar", "")
                        SPUtils.saveAvatar(avatar) // 更新本地存储的头像
                        GlideEngine.loadUserAvatar(requireContext(), avatar, ivAvatar)
                        // 粉丝数量
                        tvFansCount.text = data.optInt("fansCount").toString()
                        // 关注数量
                        tvFollowCount.text = data.optInt("focusCount").toString()
                        // 获赞数量
                        tvLikeCount.text = data.optInt("likeCount").toString()
                        // 昵称
                        tvNickname.text = data.optNormalizedString("nickName")
                            .ifEmpty { "用户" }
                        tvDescription.text = UserInfoText.displayIntroduction(
                            data.optNormalizedString("personalIntroduction")
                        )
                        SPUtils.savePersonalIntroduction(
                            UserInfoText.storageIntroduction(
                                data.optNormalizedString("personalIntroduction")
                            )
                        )
                        SPUtils.saveSchool(
                            UserInfoText.storageSchool(data.optNormalizedString("school"))
                        )
                        SPUtils.saveBirthday(data.optNormalizedString("birthday"))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                binding.loadingView.visibility = View.GONE
            }
        }

        binding.btnLogout.setOnClickListener {
            // 跳转到编辑资料页面
            val intent = Intent(requireContext(), EditActivity::class.java)
            startActivity(intent)
        }

        // 设置退出登录按钮
        setupLogoutButton()
    } // 🔥 修复点1：在这里加上右大括号，正确结束 onViewCreated 方法

    private fun setupStatusBarPadding() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top

            val bannerParams = binding.ivBanner.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            bannerParams.topMargin = statusBarHeight
            binding.ivBanner.layoutParams = bannerParams

            val logoutParams = binding.ivLogout.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            val logoutDesignTop = resources.getDimensionPixelSize(R.dimen.personal_logout_margin_top)
            logoutParams.topMargin = statusBarHeight + logoutDesignTop
            binding.ivLogout.layoutParams = logoutParams

            insets
        }
    }

    private fun setupLogoutButton() {
        val isLoggedIn = SPUtils.getToken().isNotEmpty() && SPUtils.getUserId().isNotEmpty()
        if (isLoggedIn) {
            binding.ivLogout.visibility = View.VISIBLE
            binding.ivLogout.setOnClickListener { showLogoutDialog() }
        } else {
            binding.ivLogout.visibility = View.GONE
        }
    }

    private fun showLogoutDialog() {
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("退出登录")
            .setMessage("确定要退出登录吗？")
            .setPositiveButton("确定") { _, _ ->
                // 退出登录逻辑
                SPUtils.cleanToken()
                val intent = Intent(requireContext(), LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                requireActivity().finish()
            }
            .setNegativeButton("取消", null)
            .create()

        dialog.show()

        // 设置按钮颜色为粉色
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(resources.getColor(R.color.bilibili_pink, null))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(resources.getColor(R.color.bilibili_pink, null))
    }

    private fun setupViewPagerAndTabs() {
        binding.viewPager.adapter = UserProfilePagerAdapter(requireActivity())
        binding.viewPager.offscreenPageLimit = 3

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = tabTitles[position]
        }.attach()
    }

    override fun onResume() {
        super.onResume()
        // 从编辑资料页返回后刷新头像（不必重启 App）
        if (_binding != null) {
            GlideEngine.loadUserAvatar(requireContext(), SPUtils.getAvatar(), binding.ivAvatar)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        @JvmStatic
        fun newInstance() = PersonalFragment()
    }

    fun switchToContributeTab() {
        // 切换到投稿tab（position 1）
        binding.viewPager.setCurrentItem(1, true)
    }

    inner class UserProfilePagerAdapter(fragmentActivity: FragmentActivity) :
        FragmentStateAdapter(fragmentActivity) {
        override fun getItemCount(): Int = tabTitles.size

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> HomeFragment.newInstance()
                1 -> ContributeFragment.newInstance()
                2 -> CollectFragment.newInstance()
                else -> HomeFragment.newInstance()
            }
        }
    }
}