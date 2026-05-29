package com.easylive.entity.po;

import java.io.Serializable;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.format.annotation.DateTimeFormat;
import com.easylive.enums.DateTimePatternEnum;
import com.easylive.utils.DateUtils;

/**
 * @description: 视频弹幕表
 * @date: 2025-03-07
 */
public class VideoDanmu implements Serializable {
    /**
     * 自增ID
     */
    private Integer danmuId;
    /**
     * 视频ID
     */
    private String videoId;
    /**
     * 文件ID
     */
    private String fileId;
    /**
     * 用户ID
     */
    private String userId;
    /**
     * 发布时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date postTime;
    /**
     * 内容
     */
    private String text;
    /**
     * 展示位置
     */
    private Integer mode;
    /**
     * 文字颜色
     */
    private String color;
    /**
     * 展示时间
     */
    private Integer time;

    private String videoName;

    private String videoCover;

    private String nickName;

    private String avatar;

    public String getVideoName() {
        return videoName;
    }

    public void setVideoName(String videoName) {
        this.videoName = videoName;
    }

    public String getVideoCover() {
        return videoCover;
    }

    public void setVideoCover(String videoCover) {
        this.videoCover = videoCover;
    }

    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickName) {
        this.nickName = nickName;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public void setDanmuId(Integer danmuId) {
        this.danmuId = danmuId;
    }

    public Integer getDanmuId() {
        return danmuId;
    }

    public void setVideoId(String videoId) {
        this.videoId = videoId;
    }

    public String getVideoId() {
        return videoId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public String getFileId() {
        return fileId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserId() {
        return userId;
    }

    public void setPostTime(Date postTime) {
        this.postTime = postTime;
    }

    public Date getPostTime() {
        return postTime;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public void setMode(Integer mode) {
        this.mode = mode;
    }

    public Integer getMode() {
        return mode;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getColor() {
        return color;
    }

    public void setTime(Integer time) {
        this.time = time;
    }

    public Integer getTime() {
        return time;
    }

    @Override
    public String toString() {
        return "自增ID:" + (danmuId == null ? "空" : danmuId) + " ,视频ID:" + (videoId == null ? "空" : videoId) + " ,文件ID:" + (fileId == null ? "空" : fileId) + " ,用户ID:" + (userId == null ? "空" : userId) + " ,发布时间:" + (postTime == null ? "空" : DateUtils.format(postTime, DateTimePatternEnum.YYYY_MM_DD_HH_MM_SS.getPattern())) + " ,内容:" + (text == null ? "空" : text) + " ,展示位置:" + (mode == null ? "空" : mode) + " ,文字颜色:" + (color == null ? "空" : color) + " ,展示时间:" + (time == null ? "空" : time);
    }
}
