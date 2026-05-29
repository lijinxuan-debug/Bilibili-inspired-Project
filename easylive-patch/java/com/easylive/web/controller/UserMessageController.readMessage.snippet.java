// 合并到 UserMessageController.java

@Resource
private UserMessagePushService userMessagePushService;

@RequestMapping("/readMessage")
@GlobalInterceptor(checkLogin = true)
public ResponseVo readMessage(@NotNull Integer messageId) {
    TokenUserInfoDto tokenUserInfo = getTokenUserInfo();
    UserMessageQuery userMessageQuery = new UserMessageQuery();
    userMessageQuery.setUserId(tokenUserInfo.getUserId());
    userMessageQuery.setMessageId(messageId);
    UserMessage userMessage = new UserMessage();
    userMessage.setReadType(MessageReadTypeEnum.READ.getType());
    userMessageService.updateByQuery(userMessage, userMessageQuery);
    if (userMessagePushService != null) {
        userMessagePushService.onUnreadChanged(tokenUserInfo.getUserId());
    }
    return getSuccessResponseVo(null);
}

// readAll 方法末尾同样调用 onUnreadChanged(tokenUserInfo.getUserId())
