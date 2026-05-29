package com.example.bilibili.ui.releaseVideo.bottomSheet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.bilibili.databinding.LayoutBottomSheetContainerBinding
import com.example.bilibili.ui.releaseVideo.ReleaseVideoViewModel
import com.example.bilibili.ui.releaseVideo.mainTag.MainTagFragment
import com.example.bilibili.ui.releaseVideo.partition.PartitionFragment
import com.example.bilibili.util.ToastUtils
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlin.getValue

class TagBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var _binding: LayoutBottomSheetContainerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ReleaseVideoViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = LayoutBottomSheetContainerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val pagerAdapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 2 // 两个页面：主页和分区页

            override fun createFragment(position: Int): Fragment {
                return when (position) {
                    0 -> MainTagFragment()   // 第 0 页：带 ChipGroup 的主交互页
                    else -> PartitionFragment() // 第 1 页：选分区的列表页
                }
            }
        }

        // 绑定适配器
        binding.viewPager.adapter = pagerAdapter

        // 禁止左右滑动
        binding.viewPager.isUserInputEnabled = false

        // 监听取消按钮
        binding.tvCancel.setOnClickListener {
            // 如果在第二页，点击取消应该是回到第一页
            if (binding.viewPager.currentItem > 0) {
                switchToPage(0)
            } else {
                dismiss()
            }
        }

        binding.tvConfirm.setOnClickListener {
            if (binding.viewPager.currentItem > 0) {
                // 说明是在选择分区，那么点击确定就意味着选择了对应的分区数据
                if (!viewModel.hasPartitionSelected()) {
                    ToastUtils.showShort(requireContext(), "请选择对应的分区")
                } else {
                    // 返回到首页即可
                    switchToPage(0)
                }
            } else {
                dismiss()
            }
        }
    }

    /**
     * 提供给子 Fragment 调用的方法：切换页面
     */
    fun switchToPage(position: Int) {
        binding.viewPager.setCurrentItem(position, true)

        // 动态修改顶部的标题
        binding.tvSheetTitle.text = if (position == 0) "选择分区及话题" else "选择分区"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}