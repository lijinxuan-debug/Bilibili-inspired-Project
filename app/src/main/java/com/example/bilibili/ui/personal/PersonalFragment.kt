package com.example.bilibili.ui.personal

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import com.example.bilibili.ui.friends.MyFriendsActivity
import com.example.bilibili.ui.login.LoginActivity
import com.example.bilibili.ui.personal.collect.CollectFragment
import com.example.bilibili.ui.personal.contribute.ContributeFragment
import com.example.bilibili.ui.personal.home.HomeFragment
import com.example.bilibili.util.AvatarUpdateHelper
import com.example.bilibili.util.GlideEngine
import com.example.bilibili.util.ToastUtils
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
    private var currentUserId: String = ""

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
                        val school = data.optNormalizedString("school")
                        tvSchool.text = UserInfoText.displaySchool(school)
                        currentUserId = data.optString("userId", SPUtils.getUserId())
                        tvUid.text = currentUserId
                        SPUtils.savePersonalIntroduction(
                            UserInfoText.storageIntroduction(
                                data.optNormalizedString("personalIntroduction")
                            )
                        )
                        SPUtils.saveSchool(UserInfoText.storageSchool(school))
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
            startActivity(Intent(requireContext(), EditActivity::class.java))
        }

        binding.layoutFansStat.setOnClickListener {
            MyFriendsActivity.start(requireContext(), MyFriendsActivity.TAB_FANS)
        }
        binding.layoutFollowStat.setOnClickListener {
            MyFriendsActivity.start(requireContext(), MyFriendsActivity.TAB_FOLLOWING)
        }
        binding.layoutLikeStat.setOnClickListener {
            showLikeSummaryDialog()
        }
        binding.rowUid.setOnClickListener {
            copyUidToClipboard()
        }

        binding.ivAvatar.setOnClickListener {
            changeAvatarFromGallery()
        }

        setupLogoutButton()
    } // 🔥 修复点1：在这里加上右大括号，正确结束 onViewCreated 方法

    private fun changeAvatarFromGallery() {
        if (SPUtils.getToken().isEmpty() || SPUtils.getUserId().isEmpty()) {
            ToastUtils.showShort(requireContext(), "请先登录")
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            return
        }
        AvatarUpdateHelper.pickAndUpdate(
            activity = requireActivity(),
            scope = viewLifecycleOwner.lifecycleScope,
            onPreview = { localPath ->
                ToastUtils.showShort(requireContext(), "正在更新头像...")
                GlideEngine.loadUserAvatar(requireContext(), localPath, binding.ivAvatar)
            },
            onSuccess = { avatarUrl ->
                GlideEngine.loadUserAvatar(requireContext(), avatarUrl, binding.ivAvatar)
                ToastUtils.showShort(requireContext(), "头像已更新")
            },
            onError = { message ->
                ToastUtils.showShort(requireContext(), message)
                GlideEngine.loadUserAvatar(requireContext(), SPUtils.getAvatar(), binding.ivAvatar)
            },
        )
    }

    private fun copyUidToClipboard() {
        val uid = currentUserId.ifEmpty { SPUtils.getUserId() }
        if (uid.isEmpty()) {
            ToastUtils.showShort(requireContext(), "UID 暂不可用")
            return
        }
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("UID", uid))
        ToastUtils.showShort(requireContext(), "UID已复制到剪贴板")
    }

    private fun showLikeSummaryDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_like_summary, null)
        dialogView.findViewById<android.widget.TextView>(R.id.tv_nickname).text =
            binding.tvNickname.text
        dialogView.findViewById<android.widget.TextView>(R.id.tv_like_count).text =
            binding.tvLikeCount.text

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialogView.findViewById<View>(R.id.btn_confirm).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

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
            binding.tvSchool.text = UserInfoText.displaySchool(SPUtils.getSchool())
            val uid = currentUserId.ifEmpty { SPUtils.getUserId() }
            if (uid.isNotEmpty()) {
                binding.tvUid.text = uid
            }
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