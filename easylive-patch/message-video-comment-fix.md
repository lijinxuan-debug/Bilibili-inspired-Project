# 视频下直接评论：列表可见 + 未读统计一致

## 问题

有人在视频下**直接评论**（不是回复某条评论）时：

- 未读气泡会增加（`getNoReadCountGroup` 统计所有 `message_type=4`）
- 但「评论回复」列表里看不到（被 `replyOnly` / `inboxFeed` / 客户端过滤掉）

## 后端修改

### 1. `UserMessageController.java` — `loadMessage`

删除或注释：

```java
if (MessageTypeEnum.COMMENT.getType().equals(messageType)) {
    userMessageQuery.setReplyOnly(1);
}
```

### 2. `UserMessageMapper.xml` — `inboxFeed`

将 `loadAllMessage` 用的 `inboxFeed` 条件改为包含所有评论，例如删除 `inboxFeed` 整段 `message_type != 4 or (...)` 限制，或改为仅排除系统消息。

### 3. `UserMessageServiceImpl.java` — `saveMessage`

当 `MessageTypeEnum.COMMENT` 且 `targetCommentId == 0`（直接评视频）时：

```java
userMessageDto.setPreviewType("video");
```

便于客户端区分展示。

## 客户端（已改）

- 去掉 `MessagePagingSource` / `AllMessagePagingSource` 对 `messageContentReply` 为空的过滤
- 展示：直接评视频 →「评论了我的视频」；回复评论 →「回复了我的评论」
