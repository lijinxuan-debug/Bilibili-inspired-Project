package com.easylive.service;

import com.easylive.entity.po.UserMessage;

/**
 * 新消息推送抽象，由 easylive-web 中的 SSE 实现。
 */
public interface UserMessagePushService {

    void onNewMessage(String userId, UserMessage message);

    void onUnreadChanged(String userId);
}
