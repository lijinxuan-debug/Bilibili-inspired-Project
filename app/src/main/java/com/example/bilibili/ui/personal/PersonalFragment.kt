package com.example.bilibili.ui.personal

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.bilibili.R
import com.example.bilibili.data.api.PostService
import com.example.bilibili.databinding.FragmentPersonalBinding
import com.example.bilibili.ui.login.LoginActivity
import com.example.bilibili.ui.personal.collect.CollectFragment
import com.example.bilibili.ui.personal.contribute.ContributeFragment
import com.example.bilibili.ui.personal.home.HomeFragment
import com.example.bilibili.util.GlideEngine
import com.example.bilibili.util.RetrofitClient
import com.example.bilibili.util.SPUtils
import com.example.bilibili.util.ToastUtils
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
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPersonalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViewPagerAndTabs()

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
                        // 加载个人头像
                        GlideEngine.loadUserAvatar(requireContext(), SPUtils.getAvatar(), ivAvatar)
                        // 粉丝数量
                        tvFansCount.text = data.optInt("fansCount").toString()
                        // 关注数量
                        tvFollowCount.text = data.optInt("focusCount").toString()
                        // 获赞数量
                        tvLikeCount.text = data.optInt("likeCount").toString()
                        // 昵称
                        tvNickname.text = data.getString("nickName")
                        // 个人描述
                        tvDescription.text = data.getString("personalIntroduction")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                binding.loadingView.visibility = View.GONE
            }
        }

        binding.btnLogout.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.PinkDialogTheme)
                .setTitle("提示")
                .setMessage("确定要退出登录吗？")
                .setPositiveButton("确定") { _, _ ->
                    SPUtils.cleanToken()
                    ToastUtils.showShort(requireContext(), "已退出登录")

                    val intent = Intent(requireContext(), LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)

                    requireActivity().finish()
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun setupViewPagerAndTabs() {
        val adapter = PersonalPagerAdapter(requireActivity())
        binding.viewPager.adapter = adapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = tabTitles[position]
        }.attach()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class PersonalPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {
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