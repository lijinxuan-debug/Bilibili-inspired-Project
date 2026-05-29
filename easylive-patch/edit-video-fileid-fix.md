# 编辑稿件分P重复 / 转码 NPE 修复说明

## 根因

`VideoInfoPostServiceImpl.saveVideoInfo` 在**更新**稿件时：

- `addList = uploadFileList` 中 **`fileId == null`** 的项视为**新增分P**，会生成新 `fileId` 并入转码队列。
- 客户端编辑保存若只传 `uploadId`、不传已有分P的 `fileId`，后端会把**全部旧分P再插一遍** → 影分身 + 重复转码。
- `transferVideoFile` 第 283 行：`redisComponent.getVideoFileInfo` 对已转码过的 `uploadId` 返回 **null**（Redis 已清理）→ **NPE**。

分P标题在服务端字段为 **`fileName`**，客户端若只改 UI 的 `displayTitle` 却提交原始 `fileName`，标题会丢失。

## Android 已修复（本仓库）

- 加载编辑：`getVideoInfoByVideoId` 解析 `fileId`、`fileName`（作标题）、`fileSize`、`transferResult`。
- 保存：`uploadFileList` 对已有分P带 `fileId`，`fileName` 用用户修改后的标题。
- 转码中(0)/待审核(2)/分P转码失败(2) 时禁止保存。

## 后端建议补丁（easylive-common）

在 `VideoInfoPostServiceImpl.transferVideoFile` 开头增加 Redis 空判断，避免 NPE 打满日志：

```java
UploadingFileDto videoFileInfo = redisComponent.getVideoFileInfo(
        videoInfoFilePost.getUserId(), videoInfoFilePost.getUploadId());
if (videoFileInfo == null || StringTools.isEmpty(videoFileInfo.getFilePath())) {
    log.error("转码跳过：Redis 无上传信息 uploadId={}, fileId={}, videoId={}",
            videoInfoFilePost.getUploadId(), videoInfoFilePost.getFileId(), videoInfoFilePost.getVideoId());
    VideoInfoFilePost fail = new VideoInfoFilePost();
    fail.setTransferResult(VideoFileTransferResultEnum.FAIL.getStatus());
    videoInfoFilePostMapper.updateByFileId(fail, videoInfoFilePost.getFileId());
    return;
}
```

## 已脏数据清理（MySQL）

对重复稿件，在 `video_info_file_post` 按 `video_id` 查看，保留每组 `upload_id` 一条（通常保留 `transfer_result=1` 且 `file_path` 非空），删除重复行后再在管理端审核。

```sql
-- 仅作排查示例，执行前请备份
SELECT video_id, file_id, upload_id, file_index, file_name, transfer_result, file_path
FROM video_info_file_post
WHERE video_id = '你的videoId'
ORDER BY file_index, create_time;
```
