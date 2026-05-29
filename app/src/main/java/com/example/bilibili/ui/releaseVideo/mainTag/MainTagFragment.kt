package com.example.bilibili.ui.releaseVideo.mainTag

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.bilibili.R
import com.example.bilibili.databinding.FragmentMainTagBinding
import com.example.bilibili.databinding.ItemChipAddTagBinding
import com.example.bilibili.databinding.ItemChipTagBinding
import com.example.bilibili.databinding.LayoutBottomSheetAddTagBinding
import com.example.bilibili.ui.releaseVideo.ReleaseVideoViewModel
import com.example.bilibili.ui.releaseVideo.bottomSheet.TagBottomSheetDialogFragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

class MainTagFragment : Fragment() {
    private var _binding: FragmentMainTagBinding? = null
    private val binding get() = _binding!!

    // 共享 ViewModel
    private val viewModel: ReleaseVideoViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMainTagBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tags.setOnClickListener {
            // 跳转到第二页
            (parentFragment as? TagBottomSheetDialogFragment)?.switchToPage(1)
        }

        // 观察数据，更新 UI
        viewModel.selectedPartition.observe(viewLifecycleOwner) { category ->
            binding.tvSelectedCategory.text = viewModel.formatPartitionDisplay(category)
        }

        // 渲染标签的逻辑（这里就是你之前写的 ChipGroup 渲染）
        viewModel.selectedTags.observe(viewLifecycleOwner) { tags ->
            refreshTagChips(tags)
        }
    }

    /**
     * 刷新标签
     */
    private fun refreshTagChips(tags: List<String>) {
        binding.chipGroupSelected.removeAllViews() // 先清空旧的

        // 先把选中的标签一个一个塞进去
        tags.forEach { tagName ->
            val tagChip = ItemChipTagBinding.inflate(layoutInflater).root
            tagChip.text = tagName
            tagChip.setOnCloseIconClickListener { viewModel.removeTag(tagName) }
            binding.chipGroupSelected.addView(tagChip)
        }

        // 最后再添加自定义标签
        val addButton = createAddButton()
        binding.chipGroupSelected.addView(addButton)

        // 更新文字统计
        binding.partitionCount.text = "还可添加${10 - tags.size}个标签"
    }

    /**
     * 创建自定义标签
     */
    private fun createAddButton(): View {
        val addBinding = ItemChipAddTagBinding.inflate(layoutInflater)
        addBinding.root.setOnClickListener {
            // 会弹出输入框让用户输入
            showAddTagInputDialog()
        }
        return addBinding.root
    }

    private fun showAddTagInputDialog() {
        val addBinding = LayoutBottomSheetAddTagBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(requireContext(), R.style.TransparentBottomSheetStyle)
        dialog.setContentView(addBinding.root)

        val editText = addBinding.etTagInput

        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        // 强制展开
        dialog.setOnShowListener {
            // 获取 BottomSheet 的 Behavior 对象
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED // 强制展开
                behavior.skipCollapsed = true // 禁止折叠状态
            }

            // 3. 延迟拉起键盘
            editText.requestFocus()
            editText.postDelayed({
                val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
            }, 150) // 略微增加延迟确保布局计算完成
        }

        // 2. 字数统计监听
        editText.addTextChangedListener {
            addBinding.tvWordCount.text = "${it?.length ?: 0}/30"
        }

        // 3. 取消
        addBinding.tvCancel.setOnClickListener { dialog.dismiss() }

        // 4. 确认添加
        addBinding.tvConfirm.setOnClickListener {
            val tagName = editText.text.toString().trim()
            if (tagName.isNotEmpty()) {
                // 调用共享 ViewModel 的方法添加标签
                viewModel.addTag(tagName)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}