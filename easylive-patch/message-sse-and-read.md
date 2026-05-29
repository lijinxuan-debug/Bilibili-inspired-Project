# 消息 SSE 推送与单条已读

将 `easylive-patch/java` 下文件合并到 EasyLive 工程后重启 `easylive-web`。

## 新增文件

- `easylive-common/.../service/UserMessagePushService.java`
- `easylive-web/.../web/message/MessageSseHub.java`
- `easylive-web/.../web/controller/MessageSseController.java`

## 修改 `UserMessageController.java`

在类中增加：

```java
@RequestMapping("/readMessage")
@GlobalInterceptor(checkLogin = true)
public ResponseVo readMessage(@NotNull Integer messageId) {
    TokenUserInfoDto tokenUserInfo = getTokenUserInfo();
    UserMessageQuery query = new UserMessageQuery();
    query.setUserId(tokenUserInfo.getUserId());
    query.setMessageId(messageId);
    UserMessage update = new UserMessage();
    update.setReadType(MessageReadTypeEnum.READ.getType());
    userMessageService.updateByQuery(update, query);
    return getSuccessResponseVo(null);
}
```

`readAll` 末尾可调用 `userMessagePushService.onUnreadChanged(tokenUserInfo.getUserId())`（需注入 `UserMessagePushService`）。

## 修改 `UserMessageServiceImpl.java`

1. 注入 `UserMessagePushService userMessagePushService`（`required = false` 亦可）。
2. `saveMessage` 在 `userMessageMapper.insert(userMessage)` 之后：

```java
if (userMessagePushService != null) {
    userMessagePushService.onNewMessage(receiveUserId, userMessage);
}
```

3. `saveFollowMessage` 在 `insert` 之后同样调用 `onNewMessage(receiveUserId, userMessage)`。

4. `updateByQuery` 用于已读时，可在 `UserMessageController.readAll/readMessage` 里调用 `onUnreadChanged`。

## 客户端

- SSE：`GET {baseUrl}message/sse/subscribe`，Header `webToken`
- 事件名：`message`，data 为 JSON：`event=sync|message`
