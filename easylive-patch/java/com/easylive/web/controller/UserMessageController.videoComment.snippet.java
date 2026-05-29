// loadMessage 中删除以下代码块，否则会过滤掉「视频下直接评论」：
//
// if (MessageTypeEnum.COMMENT.getType().equals(messageType)) {
//     userMessageQuery.setReplyOnly(1);
// }

// loadAllMessage 依赖的 inboxFeed：建议在 UserMessageMapper.xml 中移除
// message_type=4 必须带 messageContentReply 的限制（见 message-video-comment-fix.md）
