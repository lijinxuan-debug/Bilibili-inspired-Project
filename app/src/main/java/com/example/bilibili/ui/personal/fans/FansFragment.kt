package com.example.bilibili.ui.personal.fans

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.bilibili.R
import com.example.bilibili.databinding.FragmentFansBinding
import com.example.bilibili.ui.user.UserProfileActivity
import com.example.bilibili.util.ToastUtils

class FansFragment : Fragment() {
    private var _binding: FragmentFansBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FansViewModel by viewModels()
    private lateinit var fansAdapter: FansAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFansBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. 初始化适配器
        fansAdapter = FansAdapter(
            list = emptyList(),
            onBtnClick = { user ->
                androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.PinkDialogTheme)
                    .setTitle("回关用户")
                    .setMessage("确定要回关 ${user.otherNickName} 吗？")
                    .setPositiveButton("确定") { _, _ ->
                        viewModel.followBack(user.otherUserId)
                    }
                    .setNegativeButton("取消", null)
                    .show()
            },
            onUserClick = { user -> openUserProfile(user.otherUserId) }
        )

        // 2. 设置 RecyclerView
        binding.rvFans.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFans.adapter = fansAdapter

        // 3. 观察数据
        viewModel.fanList.observe(viewLifecycleOwner) { list ->
            fansAdapter.updateData(list)
            // 更新顶部人数显示
            binding.tvCountNumber.text = "${list.size}人"
        }

        // 4. 观察Toast消息
        viewModel.toastMessage.observe(viewLifecycleOwner) { message ->
            ToastUtils.showShort(requireContext(), message)
        }

        // 5. 加载数据
        viewModel.loadData()
    }

    private fun openUserProfile(userId: String) {
        if (userId.isEmpty()) return
        startActivity(
            Intent(requireContext(), UserProfileActivity::class.java).apply {
                putExtra("user_id", userId)
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        @JvmStatic
        fun newInstance() = FansFragment()
    }
}