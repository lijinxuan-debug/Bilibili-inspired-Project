package com.example.bilibili.ui.playVideo.comment

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.bilibili.R
import com.example.bilibili.data.api.FileService
import com.example.bilibili.data.model.CommentItem
import com.example.bilibili.databinding.DialogCommentInputBinding
import com.example.bilibili.databinding.FragmentVideoCommentBinding
import com.example.bilibili.ui.playVideo.PlayVideoViewModel
import com.example.bilibili.ui.user.UserProfileActivity
import com.example.bilibili.ui.playVideo.ImagePreviewActivity
import com.example.bilibili.util.GlideEngine
import com.example.bilibili.util.RetrofitClient
import com.example.bilibili.util.ToastUtils
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class VideoCommentFragment : Fragment() {
    private var _binding: FragmentVideoCommentBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PlayVideoViewModel by activityViewModels()

    private val commentAdapter by lazy { CommentAdapter() }

    // 当前视频ID（用于发布评论）
    private var currentVideoId: String? = null

    // 当前回复的评论ID（null表示发表新评论）
    private var replyCommentId: Int? = null

    // 底部对话框
    private var commentBottomSheetDialog: BottomSheetDialog? = null

    private val fileService = RetrofitClient.create(FileService::class.java)

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
        setupBottomBar()
        setupEmptyState()
        setupSortButton()

        // 从 Activity 获取 videoId
        viewModel.videoDetailLive.observe(viewLifecycleOwner) { videoInfo ->
            currentVideoId = videoInfo.optString("videoId")
        }
    }

    private fun initRecyclerView() {
        binding.rvComments.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = commentAdapter
        }

        // 使用函数式编程设置回调
        setupAdapterCallbacks()
    }

    /**
     * 使用函数式编程设置 Adapter 回调
     */
    private fun setupAdapterCallbacks() {
        // 点击评论项（回复评论）
        commentAdapter.onCommentClick = { commentItem ->
            openCommentDialog(commentItem)
        }

        // 点赞回调
        commentAdapter.onLikeClick = { commentItem ->
            handleLikeAction(commentItem)
        }

        // 踩回调
        commentAdapter.onDislikeClick = { commentItem ->
            handleDislikeAction(commentItem)
        }

        // 头像点击回调（跳转到用户主页）
        commentAdapter.onAvatarClick = { userId ->
            val intent = Intent(requireContext(), UserProfileActivity::class.java)
            intent.putExtra("user_id", userId)
            startActivity(intent)
        }

        // 图片点击预览回调
        commentAdapter.onImageClick = { imageUrl ->
            val activity = requireActivity()
            if (activity is androidx.appcompat.app.AppCompatActivity) {
                ImagePreviewActivity.start(activity, imageUrl)
            }
        }
    }

    /**
     * 处理点赞操作
     */
    private fun handleLikeAction(commentItem: CommentItem) {
        val videoId = currentVideoId
        if (videoId.isNullOrEmpty()) {
            ToastUtils.showShort(requireContext(), "视频信息加载中，请稍后")
            return
        }

        val snapshot = commentActionSnapshot(commentItem)
        CommentActionHelper.applyLikeToggle(commentItem)
        notifyCommentItemChanged(commentItem)

        viewModel.doCommentAction(
            videoId = videoId,
            commentId = commentItem.commentId,
            actionType = 0,
        ) { success ->
            if (!success) {
                CommentActionHelper.revertLikeToggle(
                    commentItem,
                    snapshot.isLiked,
                    snapshot.isHated,
                    snapshot.likeCount,
                    snapshot.hateCount,
                )
                notifyCommentItemChanged(commentItem)
            }
        }
    }

    private fun handleDislikeAction(commentItem: CommentItem) {
        val videoId = currentVideoId
        if (videoId.isNullOrEmpty()) {
            ToastUtils.showShort(requireContext(), "视频信息加载中，请稍后")
            return
        }

        val snapshot = commentActionSnapshot(commentItem)
        CommentActionHelper.applyDislikeToggle(commentItem)
        notifyCommentItemChanged(commentItem)

        viewModel.doCommentAction(
            videoId = videoId,
            commentId = commentItem.commentId,
            actionType = 1,
        ) { success ->
            if (!success) {
                CommentActionHelper.revertDislikeToggle(
                    commentItem,
                    snapshot.isLiked,
                    snapshot.isHated,
                    snapshot.likeCount,
                    snapshot.hateCount,
                )
                notifyCommentItemChanged(commentItem)
            }
        }
    }

    private data class CommentActionSnapshot(
        val isLiked: Boolean,
        val isHated: Boolean,
        val likeCount: Int,
        val hateCount: Int,
    )

    private fun commentActionSnapshot(item: CommentItem) = CommentActionSnapshot(
        isLiked = item.isLiked,
        isHated = item.isHated,
        likeCount = item.likeCount,
        hateCount = item.hateCount,
    )

    private fun setupBottomBar() {
        // 点击输入框打开评论对话框
        binding.tvFakeInput.setOnClickListener {
            openCommentDialog(null)
        }
    }

    private fun setupEmptyState() {
        binding.tvCommentNow.setOnClickListener {
            openCommentDialog(null)
        }
    }

    private fun setupSortButton() {
        viewModel.commentOrderTypeLive.observe(viewLifecycleOwner) { orderType ->
            binding.tvSortLabel.text = if (orderType == 0) "按热度" else "按时间"
        }
        binding.btnSort.setOnClickListener {
            val videoId = currentVideoId
            if (videoId.isNullOrEmpty()) {
                ToastUtils.showShort(requireContext(), "视频信息加载中，请稍后")
                return@setOnClickListener
            }
            viewModel.toggleCommentOrderType(videoId)
        }
    }

    private fun notifyCommentItemChanged(commentItem: CommentItem) {
        val list = commentAdapter.differ.currentList
        val topIndex = list.indexOfFirst { it.commentId == commentItem.commentId }
        if (topIndex >= 0) {
            commentAdapter.notifyItemChanged(topIndex)
            return
        }
        val parentIndex = list.indexOfFirst { parent ->
            parent.children?.any { it.commentId == commentItem.commentId } == true
        }
        if (parentIndex >= 0) {
            commentAdapter.notifyItemChanged(parentIndex)
        }
    }

    private fun scrollCommentsToTop() {
        binding.rvComments.post {
            (binding.rvComments.layoutManager as? LinearLayoutManager)
                ?.scrollToPositionWithOffset(0, 0)
        }
    }

    private fun updateEmptyState(totalCount: Int?) {
        if (totalCount == null) {
            binding.layoutEmptyComment.visibility = View.GONE
            binding.rvComments.visibility = View.VISIBLE
            return
        }
        val isEmpty = totalCount == 0
        binding.layoutEmptyComment.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.rvComments.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    /**
     * 打开评论输入对话框
     * @param replyItem 回复的评论对象（null表示发表新评论）
     */
    private fun openCommentDialog(replyItem: CommentItem? = null) {
        // 如果对话框已显示，先关闭
        commentBottomSheetDialog?.dismiss()

        commentBottomSheetDialog = BottomSheetDialog(requireContext(), R.style.TransparentBottomSheetStyle).apply {
            // 设置窗口软输入模式，让软键盘推动内容
            window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

            val dialogBinding = DialogCommentInputBinding.inflate(layoutInflater)
            setContentView(dialogBinding.root)

            // 获取 BottomSheet 的行为对象
            val bottomSheet = dialogBinding.root.parent as? View
            val behavior = BottomSheetBehavior.from(bottomSheet ?: return@apply)
            behavior.peekHeight = ViewGroup.LayoutParams.WRAP_CONTENT
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            // 允许键盘推起对话框
            behavior.isHideable = false

            // 设置状态栏透明，确保软键盘能够正确推动内容
            window?.decorView?.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)

            // 设置回复标题提示
            dialogBinding.etCommentInput.hint = if (replyItem != null) {
                replyCommentId = replyItem.commentId
                "回复 @${replyItem.nickName}"
            } else {
                replyCommentId = null
                "尊重是评论打动人心的入场券"
            }

            // 字数统计和输入监听
            dialogBinding.etCommentInput.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val length = s?.length ?: 0
                    // 根据输入内容或选中图片动态调整发送按钮状态
                    dialogBinding.tvSend.isEnabled = length > 0
                    dialogBinding.tvSend.alpha = if (length > 0) 1f else 0.5f
                }

                override fun afterTextChanged(s: android.text.Editable?) {}
            })

            // 发送按钮
            dialogBinding.tvSend.setOnClickListener {
                val content = dialogBinding.etCommentInput.text.toString().trim()
                if (content.isEmpty()) {
                    ToastUtils.showShort(requireContext(), "请输入评论内容")
                    return@setOnClickListener
                }

                val videoId = currentVideoId
                if (videoId.isNullOrEmpty()) {
                    ToastUtils.showShort(requireContext(), "视频信息加载中，请稍后")
                    return@setOnClickListener
                }

                // 直接发送评论（不包含图片）
                viewModel.postComment(videoId, content, replyCommentId, null)

                // 隐藏键盘和对话框
                val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(dialogBinding.etCommentInput.windowToken, 0)
                dismiss()
            }

            show()

            // 延迟弹出键盘并聚焦输入框
            dialogBinding.etCommentInput.postDelayed({
                dialogBinding.etCommentInput.requestFocus()
                val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(dialogBinding.etCommentInput, InputMethodManager.SHOW_IMPLICIT)
            }, 150)
        }
    }

    private fun applyCommentAnchorIfNeeded(list: List<CommentItem>) {
        val anchor = viewModel.consumeCommentAnchor() ?: return
        val result = CommentLocator.findInList(list, anchor) ?: run {
            ToastUtils.showShort(requireContext(), getString(R.string.message_comment_not_found))
            return
        }
        if (result.expandParentCommentId != null) {
            // 定位到楼中楼回复：展开主评论以便看到子回复
            commentAdapter.expandComment(result.expandParentCommentId)
        } else {
            val parent = list.firstOrNull { it.commentId == result.highlightCommentId }
            if (parent != null && !parent.children.isNullOrEmpty()) {
                commentAdapter.collapseComment(result.highlightCommentId)
            }
        }
        commentAdapter.setHighlightedCommentId(result.highlightCommentId)
        binding.rvComments.post {
            val scrollTarget = result.expandParentCommentId ?: result.highlightCommentId
            commentAdapter.scrollToComment(binding.rvComments, scrollTarget)
            ToastUtils.showShort(requireContext(), getString(R.string.message_comment_located))
        }
    }

    private fun observeViewModel() {
        viewModel.commentTotalCount.observe(viewLifecycleOwner) { count ->
            updateEmptyState(count)
        }

        // 观察评论列表数据变化
        viewModel.commentListLive.observe(viewLifecycleOwner) { list ->
            commentAdapter.setData(list) {
                applyCommentAnchorIfNeeded(list)
            }
        }

        // 观察评论发布结果
        viewModel.postCommentResult.observe(viewLifecycleOwner) { success ->
            if (success) {
                ToastUtils.showShort(requireContext(), "评论发表成功")
                // 清空回复状态
                replyCommentId = null
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 关闭对话框并清除引用
        commentBottomSheetDialog?.dismiss()
        commentBottomSheetDialog = null
        _binding = null
    }

    /**
     * 选择图片
     */
    private fun selectImage(dialogBinding: DialogCommentInputBinding) {
        // 图片选择功能已移除
    }

    /**
     * 上传图片到服务器
     */
    private suspend fun uploadImage(onPathReceived: (String?) -> Unit): Boolean {
        // 图片上传功能已移除
        onPathReceived(null)
        return false
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