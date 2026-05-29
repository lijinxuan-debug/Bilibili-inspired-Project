package com.example.bilibili.ui.releaseVideo.bottomSheet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.core.widget.doOnTextChanged
import com.example.bilibili.databinding.LayoutBottomSheetIntroductionBinding
import com.example.bilibili.ui.releaseVideo.ReleaseVideoViewModel
import com.example.bilibili.util.TextSelectHandleHelper
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class IntroductionBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var _binding: LayoutBottomSheetIntroductionBinding? = null
    private val binding get() = _binding!!

    // 直接访问 ViewModel
    private val viewModel: ReleaseVideoViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = LayoutBottomSheetIntroductionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        TextSelectHandleHelper.applyPinkHandlesIn(view)
        setupIntroductionInput()
        setupButtons()
    }

    private fun setupIntroductionInput() {
        // 从 ViewModel 获取当前的简介
        val currentIntroduction = viewModel.introduction.value ?: ""

        // 设置初始文本
        binding.etIntroductionInput.setText(currentIntroduction)
        binding.tvWordCount.text = "${currentIntroduction.length}/2000"

        // 监听文本变化
        binding.etIntroductionInput.doOnTextChanged { text, _, _, _ ->
            val newText = text?.toString() ?: ""
            binding.tvWordCount.text = "${newText.length}/2000"
        }
    }

    private fun setupButtons() {
        // 取消按钮
        binding.tvCancel.setOnClickListener {
            dismiss()
        }

        // 确认按钮
        binding.tvConfirm.setOnClickListener {
            // 直接将简介保存到 ViewModel
            viewModel.setIntroduction(binding.etIntroductionInput.text.toString())
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}