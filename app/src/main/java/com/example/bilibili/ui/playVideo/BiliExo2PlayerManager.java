package com.example.bilibili.ui.playVideo;

import android.content.Context;
import android.media.AudioManager;
import android.net.TrafficStats;
import android.net.Uri;
import android.os.Message;
import android.view.Surface;

import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.exoplayer.video.PlaceholderSurface;

import com.shuyu.gsyvideoplayer.cache.ICacheManager;
import com.shuyu.gsyvideoplayer.model.GSYModel;
import com.shuyu.gsyvideoplayer.model.VideoOptionModel;
import com.shuyu.gsyvideoplayer.player.BasePlayerManager;

import java.util.List;

import tv.danmaku.ijk.media.exo2.IjkExo2MediaPlayer;
import tv.danmaku.ijk.media.player.IMediaPlayer;

/**
 * Exo 播放管理：加大缓冲，减轻卡顿与音频破裂。
 */
@UnstableApi
public class BiliExo2PlayerManager extends BasePlayerManager {

    private static final LoadControl BILI_LOAD_CONTROL = new DefaultLoadControl.Builder()
            .setBufferDurationsMs(30_000, 120_000, 2_500, 5_000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build();

    private Context context;
    private IjkExo2MediaPlayer mediaPlayer;
    private Surface surface;
    private PlaceholderSurface dummySurface;
    private long lastTotalRxBytes = 0;
    private long lastTimeStamp = 0;

    @Override
    public IMediaPlayer getMediaPlayer() {
        return mediaPlayer;
    }

    @Override
    public void initVideoPlayer(Context context, Message msg, List<VideoOptionModel> optionModelList,
                                ICacheManager cacheManager) {
        this.context = context.getApplicationContext();
        mediaPlayer = new IjkExo2MediaPlayer(context);
        mediaPlayer.setLoadControl(BILI_LOAD_CONTROL);
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        if (dummySurface == null) {
            dummySurface = PlaceholderSurface.newInstanceV17(context, false);
        }
        GSYModel gsyModel = (GSYModel) msg.obj;
        try {
            mediaPlayer.setLooping(gsyModel.isLooping());
            mediaPlayer.setPreview(gsyModel.getMapHeadData() != null && !gsyModel.getMapHeadData().isEmpty());
            if (gsyModel.isCache() && cacheManager != null) {
                cacheManager.doCacheLogic(context, mediaPlayer, gsyModel.getUrl(),
                        gsyModel.getMapHeadData(), gsyModel.getCachePath());
            } else {
                mediaPlayer.setCache(gsyModel.isCache());
                mediaPlayer.setCacheDir(gsyModel.getCachePath());
                mediaPlayer.setOverrideExtension(gsyModel.getOverrideExtension());
                mediaPlayer.setDataSource(context, Uri.parse(gsyModel.getUrl()), gsyModel.getMapHeadData());
            }
            if (mediaPlayer.getMediaSource() == null) {
                release();
                return;
            }
            if (gsyModel.getSpeed() != 1 && gsyModel.getSpeed() > 0) {
                mediaPlayer.setSpeed(gsyModel.getSpeed(), 1);
            }
        } catch (Exception e) {
            e.printStackTrace();
            release();
            return;
        }
        initSuccess(gsyModel);
    }

    @Override
    public void showDisplay(Message msg) {
        if (mediaPlayer == null) {
            return;
        }
        if (msg.obj == null) {
            mediaPlayer.setSurface(dummySurface);
        } else {
            surface = (Surface) msg.obj;
            mediaPlayer.setSurface(surface);
        }
    }

    @Override
    public void setSpeed(float speed, boolean soundTouch) {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.setSpeed(speed, 1);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void setNeedMute(boolean needMute) {
        if (mediaPlayer != null) {
            if (needMute) {
                mediaPlayer.setVolume(0, 0);
            } else {
                mediaPlayer.setVolume(1, 1);
            }
        }
    }

    @Override
    public void setVolume(float left, float right) {
        if (mediaPlayer != null) {
            mediaPlayer.setVolume(left, right);
        }
    }

    @Override
    public void releaseSurface() {
        surface = null;
    }

    @Override
    public void release() {
        if (mediaPlayer != null) {
            mediaPlayer.setSurface(null);
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (dummySurface != null) {
            dummySurface.release();
            dummySurface = null;
        }
        lastTotalRxBytes = 0;
        lastTimeStamp = 0;
    }

    @Override
    public int getBufferedPercentage() {
        return mediaPlayer != null ? mediaPlayer.getBufferedPercentage() : 0;
    }

    @Override
    public long getNetSpeed() {
        return mediaPlayer != null ? getNetSpeed(context) : 0;
    }

    @Override
    public void setSpeedPlaying(float speed, boolean soundTouch) {
    }

    @Override
    public void start() {
        if (mediaPlayer != null) {
            mediaPlayer.start();
        }
    }

    @Override
    public void stop() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
        }
    }

    @Override
    public void pause() {
        if (mediaPlayer != null) {
            mediaPlayer.pause();
        }
    }

    @Override
    public int getVideoWidth() {
        return mediaPlayer != null ? mediaPlayer.getVideoWidth() : 0;
    }

    @Override
    public int getVideoHeight() {
        return mediaPlayer != null ? mediaPlayer.getVideoHeight() : 0;
    }

    @Override
    public boolean isPlaying() {
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }

    @Override
    public void seekTo(long time) {
        if (mediaPlayer != null) {
            mediaPlayer.seekTo(time);
        }
    }

    @Override
    public long getCurrentPosition() {
        return mediaPlayer != null ? mediaPlayer.getCurrentPosition() : 0;
    }

    @Override
    public long getDuration() {
        return mediaPlayer != null ? mediaPlayer.getDuration() : 0;
    }

    @Override
    public int getVideoSarNum() {
        return mediaPlayer != null ? mediaPlayer.getVideoSarNum() : 1;
    }

    @Override
    public int getVideoSarDen() {
        return mediaPlayer != null ? mediaPlayer.getVideoSarDen() : 1;
    }

    @Override
    public boolean isSurfaceSupportLockCanvas() {
        return false;
    }

    public void setSeekParameter(@Nullable SeekParameters seekParameters) {
        if (mediaPlayer != null) {
            mediaPlayer.setSeekParameter(seekParameters);
        }
    }

    private long getNetSpeed(Context context) {
        if (context == null) {
            return 0;
        }
        long nowTotalRxBytes = TrafficStats.getUidRxBytes(context.getApplicationInfo().uid) == TrafficStats.UNSUPPORTED
                ? 0 : (TrafficStats.getTotalRxBytes() / 1024);
        long nowTimeStamp = System.currentTimeMillis();
        long calculationTime = nowTimeStamp - lastTimeStamp;
        if (calculationTime == 0) {
            return 0;
        }
        long speed = ((nowTotalRxBytes - lastTotalRxBytes) * 1000 / calculationTime);
        lastTimeStamp = nowTimeStamp;
        lastTotalRxBytes = nowTotalRxBytes;
        return speed;
    }
}
