package com.example.bilibili.ui.releaseVideo.bottomSheet

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.RadioGroup
import androidx.fragment.app.activityViewModels
import androidx.core.widget.doOnTextChanged
import com.example.bilibili.R
import com.example.bilibili.databinding.LayoutBottomSheetAuthorStatementBinding
import com.example.bilibili.ui.releaseVideo.ReleaseVideoViewModel
import com.example.bilibili.util.TextSelectHandleHelper
import com.example.bilibili.util.ToastUtils
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class AuthorStatementBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var _binding: LayoutBottomSheetAuthorStatementBinding? = null
    private val binding get() = _binding!!

    // 直接访问 ViewModel
    private val viewModel: ReleaseVideoViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = LayoutBottomSheetAuthorStatementBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        TextSelectHandleHelper.applyPinkHandlesIn(view)
        setupRadioGroup()
        setupSourceInput()
        setupButtons()
    }

    private fun setupRadioGroup() {
        binding.rgStatement.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rb_original -> {
                    // 直接更新 ViewModel
                    viewModel.setStatementType(ReleaseVideoViewModel.StatementType.ORIGINAL)
                    binding.layoutRepostInput.visibility = View.GONE

                    // 收起软键盘
                    hideKeyboard()
                }
                R.id.rb_repost -> {
                    viewModel.setStatementType(ReleaseVideoViewModel.StatementType.REPOST)
                    binding.layoutRepostInput.visibility = View.VISIBLE
                }
            }
        }

        // 从 ViewModel 获取当前选择的状态
        val currentType = viewModel.statementType.value ?: ReleaseVideoViewModel.StatementType.ORIGINAL
        when (currentType) {
            ReleaseVideoViewModel.StatementType.ORIGINAL -> binding.rbOriginal.isChecked = true
            ReleaseVideoViewModel.StatementType.REPOST -> binding.rbRepost.isChecked = true
        }
    }

    private fun setupSourceInput() {
        // 从 ViewModel 获取当前的转载来源
        val currentSource = viewModel.repostSource.value ?: ""

        binding.etSource.setText(currentSource)
        binding.tvCount.text = "${currentSource.length}/200"

        binding.etSource.doOnTextChanged { text, _, _, _ ->
            val newSource = text?.toString() ?: ""
            binding.tvCount.text = "${newSource.length}/200"

            // 直接更新 ViewModel
            viewModel.setRepostSource(newSource)
        }
    }

    /**
     * 确认按钮
     */
    private fun setupButtons() {
        binding.tvConfirm.setOnClickListener {
            val currentType = viewModel.statementType.value ?: ReleaseVideoViewModel.StatementType.ORIGINAL
            val currentSource = viewModel.repostSource.value ?: ""

            if (currentType == ReleaseVideoViewModel.StatementType.REPOST && currentSource.isBlank()) {
                // 转载说明不得为空
                ToastUtils.showShort(requireContext(),"转载说明不得为空")
                return@setOnClickListener
            }

            // 数据已经直接保存到 ViewModel，只需关闭弹窗
            dismiss()
        }
    }

    /**
     * 收起软键盘
     */
    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etSource.windowToken, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}