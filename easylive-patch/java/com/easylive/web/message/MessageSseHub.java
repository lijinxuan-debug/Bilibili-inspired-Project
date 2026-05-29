package com.easylive.web.message;

import com.alibaba.fastjson.JSON;
import com.easylive.entity.dto.UserMessageCountDto;
import com.easylive.entity.po.UserMessage;
import com.easylive.entity.query.UserMessageQuery;
import com.easylive.mappers.UserMessageMapper;
import com.easylive.service.UserMessagePushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class MessageSseHub implements UserMessagePushService {

    private static final Logger log = LoggerFactory.getLogger(MessageSseHub.class);
    private static final long SSE_TIMEOUT_MS = 30L * 60L * 1000L;

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    @Resource
    private UserMessageMapper<UserMessage, UserMessageQuery> userMessageMapper;

    public SseEmitter subscribe(String userId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitters.computeIfAbsent(userId, key -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> removeEmitter(userId, emitter));
        emitter.onTimeout(() -> removeEmitter(userId, emitter));
        emitter.onError(e -> removeEmitter(userId, emitter));
        try {
            sendSync(userId, emitter);
        } catch (IOException e) {
            log.warn("sse sync failed userId={}", userId, e);
        }
        return emitter;
    }

    @Override
    public void onNewMessage(String userId, UserMessage message) {
        if (userId == null || message == null) {
            return;
        }
        SsePayload payload = new SsePayload();
        payload.event = "message";
        payload.messageId = message.getMessageId();
        payload.messageType = message.getMessageType();
        payload.readType = message.getReadType();
        send(userId, payload);
        onUnreadChanged(userId);
    }

    @Override
    public void onUnreadChanged(String userId) {
        SsePayload payload = new SsePayload();
        payload.event = "sync";
        payload.counts = loadUnreadCounts(userId);
        send(userId, payload);
    }

    private void sendSync(String userId, SseEmitter emitter) throws IOException {
        SsePayload payload = new SsePayload();
        payload.event = "sync";
        payload.counts = loadUnreadCounts(userId);
        emitter.send(SseEmitter.event().name("message").data(JSON.toJSONString(payload)));
    }

    private List<UserMessageCountDto> loadUnreadCounts(String userId) {
        return userMessageMapper.findNoReadCountGroup(userId);
    }

    private void send(String userId, SsePayload payload) {
        List<SseEmitter> list = emitters.get(userId);
        if (list == null || list.isEmpty()) {
            return;
        }
        String json = JSON.toJSONString(payload);
        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name("message").data(json));
            } catch (Exception e) {
                dead.add(emitter);
            }
        }
        dead.forEach(emitter -> removeEmitter(userId, emitter));
    }

    private void removeEmitter(String userId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(userId);
        if (list == null) {
            return;
        }
        list.remove(emitter);
        if (list.isEmpty()) {
            emitters.remove(userId);
        }
        try {
            emitter.complete();
        } catch (Exception ignored) {
        }
    }

    public static class SsePayload {
        public String event;
        public Integer messageId;
        public Integer messageType;
        public Integer readType;
        public List<UserMessageCountDto> counts;
    }
}
