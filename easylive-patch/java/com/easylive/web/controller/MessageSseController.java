package com.easylive.web.controller;

import com.easylive.entity.dto.TokenUserInfoDto;
import com.easylive.exception.BusinessException;
import com.easylive.web.annotation.GlobalInterceptor;
import com.easylive.web.message.MessageSseHub;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;

@RestController
@RequestMapping("/message")
public class MessageSseController extends ABaseController {

    @Resource
    private MessageSseHub messageSseHub;

    @RequestMapping(value = "/sse/subscribe", produces = "text/event-stream")
    @GlobalInterceptor(checkLogin = true)
    public SseEmitter subscribe() {
        TokenUserInfoDto tokenUserInfo = getTokenUserInfo();
        if (tokenUserInfo == null) {
            throw new BusinessException("请先登录");
        }
        return messageSseHub.subscribe(tokenUserInfo.getUserId());
    }
}
