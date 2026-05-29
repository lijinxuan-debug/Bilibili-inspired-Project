package com.example.bilibili.ui.releaseVideo.bottomSheet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.activityViewModels
import com.example.bilibili.R
import com.example.bilibili.databinding.LayoutBottomSheetPartTitleBinding
import com.example.bilibili.ui.releaseVideo.ReleaseVideoViewModel
import com.example.bilibili.util.TextSelectHandleHelper
import com.example.bilibili.util.ToastUtils
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class PartTitleBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var _binding: LayoutBottomSheetPartTitleBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ReleaseVideoViewModel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.PartTitleBottomSheetTheme)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = LayoutBottomSheetPartTitleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val partId = arguments?.getString(ARG_PART_ID).orEmpty()
        val maxLen = resources.getInteger(R.integer.release_part_title_max_length)
        val safeTitle = arguments?.getString(ARG_CURRENT_TITLE).orEmpty().take(maxLen)

        binding.etPartTitleInput.setText(safeTitle)
        TextSelectHandleHelper.applyPinkHandlesIn(view)
        val length = binding.etPartTitleInput.text?.length ?: 0
        if (length > 0) {
            binding.etPartTitleInput.setSelection(length)
        }
        binding.tvWordCount.text = "$length/$maxLen"
        binding.etPartTitleInput.doOnTextChanged { text, _, _, _ ->
            binding.tvWordCount.text = "${text?.length ?: 0}/$maxLen"
        }

        binding.tvCancel.setOnClickListener { dismiss() }
        binding.tvConfirm.setOnClickListener {
            val title = binding.etPartTitleInput.text.toString().trim()
            if (title.isBlank()) {
                ToastUtils.showShort(requireContext(), "标题不能为空")
                return@setOnClickListener
            }
            viewModel.updatePartTitle(partId, title.take(maxLen))
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_PART_ID = "arg_part_id"
        private const val ARG_CURRENT_TITLE = "arg_current_title"

        fun newInstance(partId: String, currentTitle: String): PartTitleBottomSheetDialogFragment {
            return PartTitleBottomSheetDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PART_ID, partId)
                    putString(ARG_CURRENT_TITLE, currentTitle)
                }
            }
        }
    }
}
