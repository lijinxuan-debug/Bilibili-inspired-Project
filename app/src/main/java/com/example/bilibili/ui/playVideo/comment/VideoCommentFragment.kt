package com.example.bilibili.ui.playVideo.comment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.bilibili.databinding.FragmentVideoCommentBinding
import com.example.bilibili.ui.playVideo.PlayVideoViewModel

class VideoCommentFragment : Fragment() {
    private var _binding: FragmentVideoCommentBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PlayVideoViewModel by activityViewModels()

    private val commentAdapter by lazy { CommentAdapter() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVideoCommentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initRecyclerView()
        observeViewModel()

    }

    private fun initRecyclerView() {
        binding.rvComments.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = commentAdapter
            // 优化：如果有固定高度，可以开启 setHasFixedSize
            setHasFixedSize(true)
        }
    }

    private fun observeViewModel() {
        // 5. 观察评论列表数据变化
        viewModel.commentListLive.observe(viewLifecycleOwner) { list ->
            commentAdapter.setData(list)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        @JvmStatic
        fun newInstance(videoId: String) = VideoCommentFragment().apply {
            arguments = Bundle().apply {
                putString("videoId", videoId)
            }
        }
    }
}