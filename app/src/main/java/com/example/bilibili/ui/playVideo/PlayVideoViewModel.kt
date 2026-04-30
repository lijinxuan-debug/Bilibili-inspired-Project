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
import com.example.bilibili.data.model.CommentResponse
import com.example.bilibili.data.model.DanmuEntity
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

    // 弹幕常见颜色
    val danmuColors = listOf(
        "#FFFFFF", "#FE0302", "#FF7204", "#FFD700", "#99FF99", // 标准前5
        "#00CCFF", "#00B7FF", "#9D00FF", "#FF00FF", "#FF99CC", // 5-10
        "#CC66FF", "#66CCFF", "#99FF66", "#CCCCCC"             // 扩展颜色
    )

    // --- 新增评论相关观察数据 ---
    val commentListLive = MutableLiveData<List<CommentItem>>()
    val isCommentLoading = MutableLiveData<Boolean>()

    /**
     * 1. 仿照原 Activity：加载视频详情、作者信息、播放地址
     */
    fun fetchAllData(videoId: String) {
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
                        // 拼接 m3u8 地址
                        videoUrlLive.postValue("${RetrofitClient.BASE_URL}file/videoResource/$fileId/index.m3u8")
                        // 解析弹幕信息
                        launch(Dispatchers.IO) {
                            try {
                                val danmuRes = danmuService.loadDanmu(fileId, videoId)
                                val danmuList = parseDanmuList(danmuRes)
                                // 假设你有一个 danmuListLive 来存放结果
                                danmuListLive.postValue(danmuList)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                // 弹幕加载失败通常不影响视频播放，这里可以静默处理或报个小错
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
                withContext(Dispatchers.IO) {
                    actionService.doAction(videoId, type, count)
                }
                onSuccess(true)
            } catch (e: Exception) {
                errorLive.postValue("操作失败")
                onSuccess(false)
            }
        }
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
                    field = entity.fileId, // 注意：接口里的 field 对应实体里的 fileId
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
    fun fetchComments(videoId: String) {
        viewModelScope.launch {
            try {
                // 1. 获取原始 JSON 字符串
                val jsonString = withContext(Dispatchers.IO) {
                    commentService.loadComment(videoId, 1, 0)
                }

                val root = JSONObject(jsonString)
                // 这里的 "data" 节点包含了 commentData 和 userActionList
                val dataObj = root.optJSONObject("data") ?: return@launch

                // 2. 使用 Gson 直接解析整个 Data 容器
                val container = Gson().fromJson(dataObj.toString(), CommentDataContainer::class.java)

                val allComments = container.commentData.list ?: emptyList()
                val userActions = container.userActionList ?: emptyList()

                // 3. 将 UserAction 列表转为 Map (Key: commentId, Value: actionType)
                // 这样查找效率最高 O(1)
                val actionMap = userActions.associateBy({ it.commentId }, { it.actionType })

                // 4. 遍历评论列表，把 actionType 状态匹配进去
                allComments.forEach { comment ->
                    matchAndAssignStatus(comment, actionMap)

                    // 处理二级评论（楼中楼）
                    comment.children?.forEach { child ->
                        matchAndAssignStatus(child, actionMap)
                    }
                }

                // 5. 最终提交给 UI 观察
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

}