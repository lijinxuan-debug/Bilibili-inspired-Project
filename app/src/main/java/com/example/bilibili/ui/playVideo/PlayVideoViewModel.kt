package com.example.bilibili.ui.playVideo

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bilibili.data.api.CommentService
import com.example.bilibili.data.api.DanmuService
import com.example.bilibili.data.api.PostService
import com.example.bilibili.data.api.UserActionService
import com.example.bilibili.data.api.VideoService
import com.example.bilibili.data.model.CommentDataContainer
import com.example.bilibili.data.model.CommentItem
import com.example.bilibili.data.model.DanmuEntity
import com.example.bilibili.data.model.PreviewConfigEntity
import com.example.bilibili.util.RetrofitClient
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class PlayVideoViewModel : ViewModel() {

    private val videoService = RetrofitClient.create(VideoService::class.java)
    private val postService = RetrofitClient.create(PostService::class.java)
    private val actionService = RetrofitClient.create(UserActionService::class.java)
    private val danmuService = RetrofitClient.create(DanmuService::class.java)
    private val commentService = RetrofitClient.create(CommentService::class.java)

    // 观察数据：视频详情与交互列表
    val videoDetailLive = MutableLiveData<JSONObject>()
    val userActionsLive = MutableLiveData<JSONArray?>()

    // 观察数据：作者详细信息
    val authorLive = MutableLiveData<JSONObject>()

    // 观察弹幕数据
    val danmuListLive = MutableLiveData<List<DanmuEntity>>()

    // 观察数据：播放地址
    val videoUrlLive = MutableLiveData<String>()

    // 状态：关注状态（用于 Fragment 实时反馈）
    val isFollowedLive = MutableLiveData<Boolean>()

    // 状态：错误消息
    val errorLive = MutableLiveData<String>()
    // 文件视频ID
    val fileIdLive = MutableLiveData<String>()

    // 弹幕常见颜色
    val danmuColors = listOf(
        "#FFFFFF", "#FE0302", "#FF7204", "#FFD700", "#99FF99", // 标准前5
        "#00CCFF", "#00B7FF", "#9D00FF", "#FF00FF", "#FF99CC", // 5-10
        "#CC66FF", "#66CCFF", "#99FF66", "#CCCCCC"             // 扩展颜色
    )

    // --- 新增评论相关观察数据 ---
    val commentListLive = MutableLiveData<List<CommentItem>>()
    val isCommentLoading = MutableLiveData<Boolean>()
    val commentTotalCount = MutableLiveData<Int>()
    /** 评论排序：0-按热度，1-按时间 */
    val commentOrderTypeLive = MutableLiveData(0)
    private var commentOrderType: Int
        get() = commentOrderTypeLive.value ?: 0
        set(value) { commentOrderTypeLive.value = value }

    // 发布评论的回调
    val postCommentResult = MutableLiveData<Boolean>()

    // 1. 在 PlayVideoViewModel 中新增一个数据发射源
    val previewConfigLive = MutableLiveData<PreviewConfigEntity>()

    /**
     * 上报视频播放（增加播放量、更新在线人数），需传分P的 fileId 而非 videoId
     */
    fun reportVideoPlayOnline(fileId: String, deviceId: String) {
        if (fileId.isBlank() || deviceId.isBlank()) return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    videoService.reportVideoPlayOnline(fileId, deviceId)
                }
            } catch (e: Exception) {
                Log.e("PlayVideo", "reportVideoPlayOnline failed: ${e.message}")
            }
        }
    }

    fun fetchAllData(videoId: String) {
        commentOrderType = 0
        viewModelScope.launch {
            try {
                // 并发请求：视频详情 + 分P列表
                val (infoRes, pListRes) = withContext(Dispatchers.IO) {
                    val info = videoService.getVideoInfo(videoId)
                    val pList = videoService.loadVideoPList(videoId)
                    Pair(info, pList)
                }
                fetchComments(videoId)

                val root = JSONObject(infoRes)
                if (root.optInt("code") == 200) {
                    val data = root.getJSONObject("data")
                    val videoInfo = data.getJSONObject("videoInfo")

                    // 发布视频详情数据和用户行为列表
                    videoDetailLive.postValue(videoInfo)
                    userActionsLive.postValue(data.optJSONArray("userActionList"))

                    // 提取雪碧图相关设置
                    if (data.has("previewConfig")) {
                        val configJson = data.getJSONObject("previewConfig")
                        val entity = PreviewConfigEntity(
                            url = configJson.optString("url"),
                            total = configJson.optInt("total", 400),
                            col = configJson.optInt("col", 10),
                            row = configJson.optInt("row", 40),
                            frameW = configJson.optInt("frameW", 160),
                            frameH = configJson.optInt("frameH", 90),
                            interval = configJson.optDouble("interval", 0.0) // 动态抓取变化的值
                        )
                        previewConfigLive.postValue(entity)
                    }

                    // 拿到 userId 去请求作者信息
                    val userId = videoInfo.getString("userId")
                    val uRes = withContext(Dispatchers.IO) { postService.getUserInfo(userId) }
                    val userData = JSONObject(uRes).getJSONObject("data")

                    authorLive.postValue(userData)
                    isFollowedLive.postValue(userData.optBoolean("haveFocus"))

                    // 解析播放地址
                    val dataArray = JSONObject(pListRes).getJSONArray("data")
                    if (dataArray.length() > 0) {
                        val fileId = dataArray.getJSONObject(0).optString("fileId")
                        fileIdLive.postValue(fileId)
                        videoUrlLive.postValue(
                            "${RetrofitClient.BASE_URL}file/videoResource/$fileId/index.m3u8"
                        )
                        // 解析弹幕信息
                        launch(Dispatchers.IO) {
                            try {
                                val danmuRes = danmuService.loadDanmu(fileId, videoId)
                                val danmuList = parseDanmuList(danmuRes)
                                danmuListLive.postValue(danmuList)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                danmuListLive.postValue(emptyList())
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                errorLive.postValue("获取数据失败")
            }
        }
    }

    /**
     * 2. 仿照原 Activity：处理关注/取关逻辑
     */
    fun toggleFollow(authorId: String) {
        val currentState = isFollowedLive.value ?: false
        val newState = !currentState

        // 乐观更新 UI
        isFollowedLive.value = newState

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (newState) postService.focus(authorId)
                    else postService.cancelFocus(authorId)
                }
            } catch (_: Exception) {
                // 失败回滚
                isFollowedLive.postValue(currentState)
                errorLive.postValue("操作失败，请检查网络")
            }
        }
    }

    /**
     * 3. 仿照原 Activity：通用三连操作（点赞/投币/收藏）
     * type: 2-点赞, 3-收藏, 4-投币
     */
    fun doVideoAction(
        videoId: String,
        type: Int,
        count: Int? = null,
        onSuccess: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    actionService.doAction(videoId, type, count)
                }
                val root = JSONObject(response)
                if (root.optInt("code") != 200) {
                    errorLive.postValue(root.optString("message", "操作失败"))
                    onSuccess(false)
                    return@launch
                }
                syncUserActionsAfterVideoAction(videoId, type, count ?: 1)
                onSuccess(true)
            } catch (e: Exception) {
                errorLive.postValue("操作失败")
                onSuccess(false)
            }
        }
    }

    /**
     * 三连操作成功后同步 userActionList；若接口未返回投币等记录则本地补全，避免重进页面状态丢失
     */
    private suspend fun syncUserActionsAfterVideoAction(
        videoId: String,
        actionType: Int,
        actionCount: Int
    ) {
        try {
            val infoRes = videoService.getVideoInfo(videoId)
            val root = JSONObject(infoRes)
            if (root.optInt("code") == 200) {
                val data = root.getJSONObject("data")
                videoDetailLive.postValue(data.getJSONObject("videoInfo"))
                var actions = data.optJSONArray("userActionList") ?: JSONArray()
                if (actionType == 4 && !containsVideoAction(actions, actionType)) {
                    actions = mergeVideoAction(actions, actionType, actionCount, videoId)
                }
                userActionsLive.postValue(actions)
                return
            }
        } catch (e: Exception) {
            Log.e("PlayVideo", "syncUserActions failed: ${e.message}")
        }
        if (actionType == 4) {
            userActionsLive.postValue(
                mergeVideoAction(userActionsLive.value ?: JSONArray(), actionType, actionCount, videoId)
            )
        }
    }

    private fun containsVideoAction(actions: JSONArray, actionType: Int): Boolean {
        for (i in 0 until actions.length()) {
            val item = actions.getJSONObject(i)
            if (isCommentAction(item)) continue
            if (item.optInt("actionType") == actionType) return true
        }
        return false
    }

    private fun mergeVideoAction(
        actions: JSONArray,
        actionType: Int,
        actionCount: Int,
        videoId: String
    ): JSONArray {
        val result = JSONArray()
        var merged = false
        for (i in 0 until actions.length()) {
            val item = actions.getJSONObject(i)
            if (isCommentAction(item)) {
                result.put(item)
                continue
            }
            if (item.optInt("actionType") == actionType) {
                result.put(
                    JSONObject().apply {
                        put("actionType", actionType)
                        put("actionCount", actionCount)
                        put("videoId", videoId)
                    }
                )
                merged = true
            } else {
                result.put(item)
            }
        }
        if (!merged) {
            result.put(
                JSONObject().apply {
                    put("actionType", actionType)
                    put("actionCount", actionCount)
                    put("videoId", videoId)
                }
            )
        }
        return result
    }

    private fun isCommentAction(item: JSONObject): Boolean {
        if (item.isNull("commentId")) return false
        return item.optInt("commentId", 0) > 0
    }

    // 发送指定的弹幕
    fun sendDanmu(entity: DanmuEntity) {
        viewModelScope.launch {
            try {
                // 调用你的 Retrofit 接口
                val result = danmuService.postDanmu(
                    text = entity.text,
                    mode = entity.mode,
                    color = entity.color,
                    time = entity.time,
                    fileId = entity.fileId, // 注意：接口里的 field 对应实体里的 fileId
                    videoId = entity.videoId
                )
                // 发送成功后的处理，比如打印个日志
                Log.d("Danmu", "发送成功: $result")
            } catch (e: Exception) {
                // 处理网络异常
                Log.e("Danmu", "发送失败: ${e.message}")
            }
        }
    }

    /**
     * 在 fetchAllData 结尾处，或者独立调用该方法来加载评论
     */
    /**
     * 加载评论及其关联的用户行为状态
     */
    fun toggleCommentOrderType(videoId: String) {
        commentOrderType = if (commentOrderType == 0) 1 else 0
        fetchComments(videoId)
    }

    fun fetchComments(videoId: String) {
        viewModelScope.launch {
            try {
                // 1. 获取原始 JSON 字符串
                val jsonString = withContext(Dispatchers.IO) {
                    commentService.loadComment(videoId, 1, commentOrderType)
                }

                val root = JSONObject(jsonString)
                // 这里的 "data" 节点包含了 commentData 和 userActionList
                val dataObj = root.optJSONObject("data") ?: return@launch

                // 2. 使用 Gson 直接解析整个 Data 容器
                val container = Gson().fromJson(dataObj.toString(), CommentDataContainer::class.java)

                val allComments = container.commentData.list ?: emptyList()
                val userActions = container.userActionList ?: emptyList()

                // 3. 更新评论总数
                commentTotalCount.postValue(container.commentData.totalCount)

                // 4. 将 UserAction 列表转为 Map (Key: commentId, Value: actionType)
                // 这样查找效率最高 O(1)
                val actionMap = userActions.associateBy({ it.commentId }, { it.actionType })

                // 5. 遍历评论列表，把 actionType 状态匹配进去
                allComments.forEach { comment ->
                    matchAndAssignStatus(comment, actionMap)

                    // 处理二级评论（楼中楼）
                    comment.children?.forEach { child ->
                        matchAndAssignStatus(child, actionMap)
                    }
                }

                // 6. 使用服务端返回的排序（orderType: 0-热度，1-时间）
                commentListLive.postValue(allComments)

            } catch (e: Exception) {
                e.printStackTrace()
                errorLive.postValue("加载评论失败: ${e.message}")
            }
        }
    }

    /**
     * 辅助方法：根据 Map 里的记录，给 CommentItem 的状态字段赋值
     */
    private fun matchAndAssignStatus(item: CommentItem, actionMap: Map<Int, Int>) {
        val type = actionMap[item.commentId]
        if (type != null) {
            // 根据你的定义：0 代表点赞，1 代表踩
            item.isLiked = (type == 0)
            item.isHated = (type == 1)
        } else {
            // 如果没有记录，确保状态为 false（防止 RecyclerView 复用导致状态错乱）
            item.isLiked = false
            item.isHated = false
        }
    }

    // 手动解析弹幕
    private fun parseDanmuList(jsonString: String): List<DanmuEntity> {
        val list = mutableListOf<DanmuEntity>()
        val root = JSONObject(jsonString)
        if (root.optInt("code") == 200) {
            val dataArray = root.optJSONArray("data") ?: return list
            for (i in 0 until dataArray.length()) {
                val item = dataArray.getJSONObject(i)
                list.add(
                    DanmuEntity(
                        danmuId = item.getInt("danmuId"),
                        videoId = item.getString("videoId"),
                        fileId = item.getString("fileId"),
                        userId = item.getString("userId"),
                        postTime = item.getString("postTime"),
                        text = item.getString("text"),
                        mode = item.getInt("mode"),
                        color = item.getString("color"),
                        time = item.getInt("time"),
                        videoName = item.optString("videoName", null),
                        videoCover = item.optString("videoCover", null),
                        nickName = item.optString("nickName", null)
                    )
                )
            }
        }
        return list
    }

    /**
     * 发布评论或回复评论
     * @param videoId 视频ID
     * @param content 评论内容
     * @param replyCommentId 回复的评论ID（null表示发布新评论）
     */
    fun postComment(videoId: String, content: String, replyCommentId: Int? = null, imgPath: String? = null) {
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    commentService.postComment(
                        replyCommentId = replyCommentId,
                        content = content,
                        imgPath = imgPath,
                        videoId = videoId
                    )
                }

                val root = JSONObject(result)
                if (root.optInt("code") == 200) {
                    postCommentResult.postValue(true)
                    // 发布成功后重新加载评论列表
                    fetchComments(videoId)
                } else {
                    postCommentResult.postValue(false)
                    errorLive.postValue(root.optString("msg", "发布失败"))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                postCommentResult.postValue(false)
                errorLive.postValue("发布评论失败: ${e.message}")
            }
        }
    }

    /**
     * 评论点赞/踩操作（不刷新整表，由 UI 层乐观更新）
     * @param actionType 操作类型：0-点赞，1-踩
     */
    fun doCommentAction(
        videoId: String,
        commentId: Int,
        actionType: Int,
        onResult: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    actionService.doAction(
                        videoId = videoId,
                        actionType = actionType,
                        actionCount = null,
                        commentId = commentId
                    )
                }
                onResult(true)
            } catch (e: Exception) {
                e.printStackTrace()
                errorLive.postValue("操作失败: ${e.message}")
                onResult(false)
            }
        }
    }

}