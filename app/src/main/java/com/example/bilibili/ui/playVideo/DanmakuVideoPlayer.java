package com.example.bilibili.ui.playVideo;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import com.example.bilibili.R;
import com.example.bilibili.data.model.DanmuEntity;
import com.example.bilibili.ui.playVideo.danmu.BiliDanmukuParser;
import com.example.bilibili.ui.playVideo.danmu.DanamakuAdapter;
import com.shuyu.gsyvideoplayer.utils.CommonUtil;
import com.shuyu.gsyvideoplayer.utils.Debuger;
import com.shuyu.gsyvideoplayer.video.base.GSYVideoPlayer;
import com.shuyu.gsyvideoplayer.video.base.GSYBaseVideoPlayer;
import com.shuyu.gsyvideoplayer.video.StandardGSYVideoPlayer;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import master.flame.danmaku.controller.IDanmakuView;
import master.flame.danmaku.danmaku.loader.ILoader;
import master.flame.danmaku.danmaku.loader.IllegalDataException;
import master.flame.danmaku.danmaku.loader.android.DanmakuLoaderFactory;
import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.DanmakuTimer;
import master.flame.danmaku.danmaku.model.IDisplayer;
import master.flame.danmaku.danmaku.model.android.DanmakuContext;
import master.flame.danmaku.danmaku.model.android.SpannedCacheStuffer;
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser;
import master.flame.danmaku.danmaku.parser.IDataSource;
import master.flame.danmaku.ui.widget.DanmakuView;

/**
 * 弹幕与视频进度绑定：禁止 HLS 自动 onSeekComplete 触发弹幕 seekTo（否则全部弹幕会弹回）。
 */
public class DanmakuVideoPlayer extends StandardGSYVideoPlayer {

    // 进度表的总刻度，这里和gsy播放器的总刻度一致
    private static final int SEEK_BAR_MAX = 100;

    /** 很多内核无法 seek 到 duration 本身，需略提前；拖到结尾也按此判断 */
    private static final long SEEK_END_OFFSET_MS = 500L;
    private static final long SEEK_NEAR_END_THRESHOLD_MS = 800L;
    private static final long SEEK_TO_END_VERIFY_DELAY_MS = 450L;

    /** 进度条松手后等待 Exo seek 稳定，再对齐弹幕，避免中途被旧进度拉回 */
    private static final long SEEK_BAR_SETTLE_MS = 500L;
    /** 未收到 BUFFERING_END 时兜底恢复弹幕 */
    private static final long SEEK_BUFFER_FALLBACK_MS = 2500L;

    private BaseDanmakuParser mParser;
    private IDanmakuView mDanmakuView;
    private DanmakuContext mDanmakuContext;

    private TextView mSendDanmaku, mToogleDanmaku;

    private long mDanmakuStartSeekPosition = -1;

    private boolean mDanmaKuShow = true;

    private File mDumakuFile;

    private DanmakuTimer mDanmakuTimer;

    /** 已加载的弹幕条数，-1 表示尚未加载 */
    private int mAppliedDanmakuCount = -1;

    private boolean mDanmakuStarted = false;

    /** 仅用户拖进度条时为 true，才允许 seekTo 弹幕 */
    private boolean mUserSeekingDanmaku = false;

    /** GSY 进度回调里的时间，Exo 播放时比 getCurrentPositionWhenPlaying 更可靠 */
    private long mLastReportedVideoMs = 0L;

    /** 帧间用系统时钟平滑推进；播放中绝不因 Exo 进度回跳而倒退 */
    private long mClockAnchorVideoMs = 0L;
    private long mClockAnchorElapsedMs = 0L;
    private boolean mClockAnchorValid = false;

    private boolean mSeekBarSettling = false;
    private long mPendingSeekTargetMs = 0L;
    private boolean mDanmakuNeedsInitialSeek = true;
    /** 用户 seek 后 Exo 缓冲期间冻结弹幕，等 BUFFERING_END 再对齐 */
    private boolean mAwaitingBufferAfterSeek = false;
    private final Runnable mSeekBarSettleRunnable = new Runnable() {
        @Override
        public void run() {
            settleDanmakuAfterSeekBar(mPendingSeekTargetMs);
        }
    };
    private final Runnable mSeekBufferFallbackRunnable = new Runnable() {
        @Override
        public void run() {
            if (mAwaitingBufferAfterSeek) {
                releaseDanmakuAfterUserSeek(mPendingSeekTargetMs);
            }
        }
    };

    public interface OnPreviewSeekListener {
        void onSeekStart();
        void onSeekPreview(long seekTimeMs, long totalTimeMs);
        void onSeekEnd();
    }

    /** 底部/顶部控制栏显隐时通知外部（用于同步 Activity 层返回按钮） */
    public interface OnControlBarVisibilityListener {
        void onControlBarVisibilityChanged(boolean visible);
    }

    /** 播放自然结束，用于展示结束页推荐 */
    public interface OnPlaybackEndListener {
        void onPlaybackEnd();
    }

    private OnPreviewSeekListener mPreviewSeekListener;
    private boolean mPreviewSeekActive;
    private OnControlBarVisibilityListener mControlBarVisibilityListener;
    private OnPlaybackEndListener mPlaybackEndListener;
    private View mEndOverlay;
    private boolean mEndScreenShowing;

    public void setOnPreviewSeekListener(OnPreviewSeekListener listener) {
        mPreviewSeekListener = listener;
    }

    public void setOnControlBarVisibilityListener(OnControlBarVisibilityListener listener) {
        mControlBarVisibilityListener = listener;
    }

    public void setOnPlaybackEndListener(OnPlaybackEndListener listener) {
        mPlaybackEndListener = listener;
    }

    public boolean isEndScreenShowing() {
        return mEndScreenShowing;
    }

    public View getEndOverlay() {
        if (mEndOverlay == null) {
            mEndOverlay = findViewById(R.id.layout_end_overlay);
        }
        return mEndOverlay;
    }

    public void showEndOverlay() {
        if (mEndOverlay == null) {
            mEndOverlay = findViewById(R.id.layout_end_overlay);
        }
        if (mEndOverlay != null) {
            mEndOverlay.setVisibility(VISIBLE);
        }
        mEndScreenShowing = true;
    }

    public void hideEndOverlay() {
        if (mEndOverlay == null) {
            mEndOverlay = findViewById(R.id.layout_end_overlay);
        }
        if (mEndOverlay != null) {
            mEndOverlay.setVisibility(GONE);
        }
        mEndScreenShowing = false;
    }

    public DanmakuVideoPlayer(Context context, Boolean fullFlag) {
        super(context, fullFlag);
    }

    public DanmakuVideoPlayer(Context context) {
        super(context);
    }

    public DanmakuVideoPlayer(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public int getLayoutId() {
        return R.layout.danmaku_layout;
    }

    @Override
    protected void init(Context context) {
        super.init(context);
        mDanmakuView = (DanmakuView) findViewById(R.id.danmaku_view);
        mBackButton = findViewById(R.id.back);
        initDanmaku();
        configureProgressBarPrecision();
        updateStartImage();
    }

    @Override
    protected void updateStartImage() {
        if (mStartButton instanceof ImageView) {
            ImageView imageView = (ImageView) mStartButton;
            if (mCurrentState == CURRENT_STATE_PLAYING) {
                imageView.setImageResource(R.drawable.ic_play);
            } else {
                imageView.setImageResource(R.drawable.ic_pause);
            }
        } else {
            super.updateStartImage();
        }
    }

    @Override
    protected void setViewShowState(View view, int visibility) {
        super.setViewShowState(view, visibility);
        if (mControlBarVisibilityListener != null && view == mBottomContainer) {
            mControlBarVisibilityListener.onControlBarVisibilityChanged(visibility == VISIBLE);
        }
    }

    private void notifyControlBarVisibility() {
        if (mControlBarVisibilityListener != null && mBottomContainer != null) {
            mControlBarVisibilityListener.onControlBarVisibilityChanged(
                    mBottomContainer.getVisibility() == VISIBLE);
        }
    }

    private void configureProgressBarPrecision() {
        if (mProgressBar != null && mProgressBar.getMax() != SEEK_BAR_MAX) {
            mProgressBar.setMax(SEEK_BAR_MAX);
        }
    }

    private int getSeekBarMax() {
        if (mProgressBar != null && mProgressBar.getMax() > 0) {
            return mProgressBar.getMax();
        }
        return SEEK_BAR_MAX;
    }

    // 调整到当前的播放进度
    private static int toSeekBarProgress(long positionMs, long durationMs) {
        if (durationMs <= 0) return 0;
        int p = (int) (positionMs * SEEK_BAR_MAX / durationMs);
        return Math.min(p, SEEK_BAR_MAX);
    }

    private static long progressToTimeMs(int progress, int max, long durationMs) {
        if (max <= 0 || durationMs <= 0) return 0;
        return (long) progress * durationMs / max;
    }

    /**
     * Exo 播 HLS 时 GSY 常停在 PLAYING_BUFFERING_START，若只认 PLAYING 弹幕不会跟进度走。
     */
    private boolean isVideoPlayingForDanmaku() {
        return mCurrentState == CURRENT_STATE_PLAYING
                || mCurrentState == CURRENT_STATE_PLAYING_BUFFERING_START;
    }

    private void clearDanmakuClock() {
        mLastReportedVideoMs = 0L;
        mClockAnchorValid = false;
        mClockAnchorVideoMs = 0L;
        mClockAnchorElapsedMs = 0L;
        mDanmakuNeedsInitialSeek = true;
        mSeekBarSettling = false;
        mPendingSeekTargetMs = 0L;
        mAwaitingBufferAfterSeek = false;
        removeCallbacks(mSeekBufferFallbackRunnable);
    }

    private boolean isInPlaybackBuffering() {
        return mCurrentState == CURRENT_STATE_PLAYING_BUFFERING_START;
    }

    private void beginAwaitingBufferAfterUserSeek(long targetMs) {
        mAwaitingBufferAfterSeek = true;
        mPendingSeekTargetMs = targetMs;
        holdDanmakuDuringSeek();
        removeCallbacks(mSeekBufferFallbackRunnable);
        postDelayed(mSeekBufferFallbackRunnable, SEEK_BUFFER_FALLBACK_MS);
    }

    /** 拖进度 / 手势 seek 期间：暂停、清屏并隐藏，避免缓冲时弹幕被时钟拽飞 */
    private void holdDanmakuDuringSeek() {
        danmakuOnPause();
        clearOnScreenDanmaku();
        if (mDanmakuView != null && mDanmakuView.isPrepared()) {
            mDanmakuView.hide();
        }
    }

    /** 缓冲结束或兜底：按真实进度 seek 并恢复弹幕 */
    private void releaseDanmakuAfterUserSeek(long targetMs) {
        mAwaitingBufferAfterSeek = false;
        removeCallbacks(mSeekBufferFallbackRunnable);
        if (!mHadPlay || mDanmakuView == null || !mDanmakuView.isPrepared()) {
            return;
        }
        long pos = targetMs;
        long reported = resolveVideoPositionMs();
        if (reported > 0) {
            if (Math.abs(reported - targetMs) < 1500) {
                pos = reported;
            } else {
                pos = targetMs;
            }
        }
        resyncClockAnchor(pos);
        clearOnScreenDanmaku();
        mDanmakuView.seekTo(pos);
        mDanmakuNeedsInitialSeek = false;
        if (mDanmakuTimer != null) {
            mDanmakuTimer.update(mClockAnchorVideoMs);
        }
        resolveDanmakuShow();
        if (isVideoPlayingForDanmaku()) {
            danmakuOnResume();
        }
    }

    /** Exo 播 HLS 时 getCurrentPositionWhenPlaying 常为 0，需用进度回调与 GSY 缓存兜底 */
    private long resolveVideoPositionMs() {
        long pos = Math.max(0, getCurrentPositionWhenPlaying());
        if (pos <= 0 && mLastReportedVideoMs > 0) {
            pos = mLastReportedVideoMs;
        }
        if (pos <= 0 && mCurrentPosition > 0) {
            pos = mCurrentPosition;
        }
        return pos;
    }

    private void resyncClockAnchor(long videoMs) {
        mClockAnchorVideoMs = Math.max(0, videoMs);
        mClockAnchorElapsedMs = SystemClock.elapsedRealtime();
        mClockAnchorValid = true;
    }

    /** 将锚点推进到当前插值时刻并冻结（暂停 / 拖进度条前调用） */
    private void freezeClockAnchor() {
        if (!mClockAnchorValid) {
            resyncClockAnchor(resolveVideoPositionMs());
            return;
        }
        long now = SystemClock.elapsedRealtime();
        mClockAnchorVideoMs = mClockAnchorVideoMs + (now - mClockAnchorElapsedMs);
        mClockAnchorElapsedMs = now;
    }

    /**
     * 播放中按系统时钟向前推进；忽略 Exo/HLS 上报的瞬时回退，避免弹幕从左侧弹回右侧。
     */
    private long getMonotonicPlayClockMs() {
        if (mAwaitingBufferAfterSeek) {
            return mClockAnchorValid ? mClockAnchorVideoMs : resolveVideoPositionMs();
        }
        if (!mClockAnchorValid) {
            long pos = resolveVideoPositionMs();
            resyncClockAnchor(pos);
            return mClockAnchorVideoMs;
        }
        if (!isVideoPlayingForDanmaku()) {
            return mClockAnchorVideoMs;
        }
        long now = SystemClock.elapsedRealtime();
        long interpolated = mClockAnchorVideoMs + (now - mClockAnchorElapsedMs);
        return Math.max(mClockAnchorVideoMs, interpolated);
    }

    /** 继续播放：只重锚时钟，不 seekTo（避免暂停恢复时闪到前面再跳回来） */
    private void alignDanmakuOnResume() {
        if (mAwaitingBufferAfterSeek) {
            return;
        }
        if (!mDanmakuStarted || mDanmakuView == null || !mDanmakuView.isPrepared()) {
            tryStartDanmakuWithVideo();
            return;
        }
        long pos = resolveVideoPositionMs();
        if (mClockAnchorValid) {
            pos = Math.max(pos, mClockAnchorVideoMs);
        }
        resyncClockAnchor(pos);
        if (mDanmakuTimer != null) {
            mDanmakuTimer.update(mClockAnchorVideoMs);
        }
        danmakuOnResume();
    }

    private void ensureDanmakuPlaying() {
        if (!mDanmakuStarted) {
            tryStartDanmakuWithVideo();
            return;
        }
        if (isVideoPlayingForDanmaku()) {
            danmakuOnResume();
        }
    }

    /** 进度条拖动结束：恢复 UI 进度刷新；弹幕在缓冲结束后再恢复 */
    private void finishSeekBarDrag() {
        mSeekBarSettling = false;
        mHadSeekTouch = false;
        mUserSeekingDanmaku = false;
        if (mAwaitingBufferAfterSeek) {
            return;
        }
        if (isVideoPlayingForDanmaku()) {
            alignDanmakuOnResume();
        }
    }

    private void settleDanmakuAfterSeekBar(long targetMs) {
        long pos = targetMs;
        long reported = resolveVideoPositionMs();
        if (reported > 0) {
            if (Math.abs(reported - targetMs) < 1500) {
                pos = reported;
            } else {
                pos = targetMs;
            }
        }
        resyncClockAnchor(pos);
        seekDanmakuByUser(pos, true);
        finishSeekBarDrag();
        if (mAwaitingBufferAfterSeek || isInPlaybackBuffering()) {
            mAwaitingBufferAfterSeek = true;
            holdDanmakuDuringSeek();
            return;
        }
        releaseDanmakuAfterUserSeek(pos);
    }

    /** seek 前清掉屏幕上正在飘的弹幕，避免滚动弹幕被 seekTo 拽向左侧 */
    private void clearOnScreenDanmaku() {
        if (mDanmakuView != null && mDanmakuView.isPrepared()) {
            mDanmakuView.clearDanmakusOnScreen();
        }
    }

    private static boolean isUserSeekingToEnd(long timeMs, long durationMs, int progress, int max) {
        if (durationMs <= 0) {
            return false;
        }
        if (max > 0 && progress >= max - 1) {
            return true;
        }
        return timeMs >= durationMs - SEEK_NEAR_END_THRESHOLD_MS;
    }

    /** 避免 seek(duration)；HLS/IJK 对尾帧 seek 常失败导致进度条弹回 */
    private static long normalizeSeekTimeMs(long timeMs, long durationMs) {
        if (durationMs <= 0) {
            return Math.max(0, timeMs);
        }
        long cap = Math.max(0, durationMs - SEEK_END_OFFSET_MS);
        return Math.max(0, Math.min(timeMs, cap));
    }

    private void applySeekUiAtTime(long timeMs, long durationMs) {
        if (durationMs <= 0) {
            return;
        }
        if (mProgressBar != null) {
            mProgressBar.setProgress(toSeekBarProgress(timeMs, durationMs));
        }
        if (mCurrentTimeTextView != null) {
            mCurrentTimeTextView.setText(CommonUtil.stringForTime(timeMs));
        }
        if (mBottomProgressBar != null) {
            mBottomProgressBar.setProgress((int) (timeMs * 100 / durationMs));
        }
    }

    /**
     * 用户拖到片尾：先 seek 到 duration 前一点；若内核 seek 失败则直接走播放结束（与自然播完一致）。
     */
    private void seekByUser(long rawTimeMs, long durationMs, int progress, int max) {
        boolean seekToEnd = isUserSeekingToEnd(rawTimeMs, durationMs, progress, max);
        long time = normalizeSeekTimeMs(rawTimeMs, durationMs);
        mSeekTimePosition = time;
        mCurrentPosition = time;
        applySeekUiAtTime(time, durationMs);

        boolean wasComplete = mCurrentState == CURRENT_STATE_AUTO_COMPLETE;

        if (getGSYVideoManager() != null) {
            try {
                getGSYVideoManager().seekTo(time);
            } catch (Exception e) {
                Debuger.printfWarning(e.toString());
            }
        }

        if (wasComplete) {
            mHadPlay = true;
            restorePlaybackControlsUi();
            setStateAndUi(CURRENT_STATE_PAUSE);
        }

        if (seekToEnd) {
            postDelayed(() -> verifySeekToEndOrComplete(durationMs), SEEK_TO_END_VERIFY_DELAY_MS);
        }
    }

    private void verifySeekToEndOrComplete(long durationMs) {
        if (durationMs <= 0) {
            return;
        }
        if (mCurrentState == CURRENT_STATE_AUTO_COMPLETE) {
            return;
        }
        long pos = getCurrentPositionWhenPlaying();
        if (pos >= durationMs - SEEK_NEAR_END_THRESHOLD_MS) {
            if (mCurrentState == CURRENT_STATE_PLAYING) {
                onAutoCompletion();
            }
            return;
        }
        // IJK + HLS 拖到结尾经常 seek 失败，按用户意图直接结束
        if (mCurrentState == CURRENT_STATE_PLAYING || mCurrentState == CURRENT_STATE_PAUSE) {
            onAutoCompletion();
        }
    }

    @Override
    public void onPrepared() {
        super.onPrepared();
        onPrepareDanmaku(this);
    }

    @Override
    public void onVideoPause() {
        super.onVideoPause();
        freezeClockAnchor();
        danmakuOnPause();
    }

    @Override
    public void onVideoResume(boolean isResume) {
        super.onVideoResume(isResume);
        tryStartDanmakuWithVideo();
        alignDanmakuOnResume();
    }

    @Override
    public void onInfo(int what, int extra) {
        super.onInfo(what, extra);
        if (what == android.media.MediaPlayer.MEDIA_INFO_BUFFERING_START) {
            if (mAwaitingBufferAfterSeek || mSeekBarSettling || mUserSeekingDanmaku) {
                holdDanmakuDuringSeek();
            }
        } else if (what == android.media.MediaPlayer.MEDIA_INFO_BUFFERING_END) {
            if (mAwaitingBufferAfterSeek) {
                releaseDanmakuAfterUserSeek(mPendingSeekTargetMs);
            } else {
                ensureDanmakuPlaying();
            }
        }
    }

    @Override
    protected void clickStartIcon() {
        super.clickStartIcon();
        if (isVideoPlayingForDanmaku()) {
            tryStartDanmakuWithVideo();
            alignDanmakuOnResume();
        } else if (mCurrentState == CURRENT_STATE_PAUSE) {
            freezeClockAnchor();
            danmakuOnPause();
        }
    }

    /**
     * 播放结束：不保留最后一帧，隐藏进度条等控件，展示结束页遮罩。
     */
    @Override
    public void onAutoCompletion() {
        long duration = getDuration();
        danmakuOnPause();
        mDanmakuStarted = false;
        clearDanmakuClock();
        if (mDanmakuView != null && mDanmakuView.isPrepared() && duration > 0) {
            mDanmakuView.seekTo(duration);
        }
        pauseAndClearVideoSurface();
        applyEndScreenUi();

        setStateAndUi(CURRENT_STATE_AUTO_COMPLETE);

        mSaveChangeViewTIme = 0;
        mCurrentPosition = duration > 0 ? duration : 0;

        if (getGSYVideoManager() != null && !mIfCurrentIsFullscreen) {
            getGSYVideoManager().setLastListener(null);
        }
        if (mAudioFocusManager != null) {
            mAudioFocusManager.abandonAudioFocus();
        }
        if (mContext instanceof Activity) {
            try {
                ((Activity) mContext).getWindow()
                        .clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (mVideoAllCallBack != null && isCurrentMediaListener()) {
            mVideoAllCallBack.onAutoComplete(mOriginUrl, mTitle, this);
        }
        mHadPlay = false;

        if (mPlaybackEndListener != null) {
            mPlaybackEndListener.onPlaybackEnd();
        }
    }

    @Override
    protected void changeUiToCompleteShow() {
        super.changeUiToCompleteShow();
        applyEndScreenUi();
    }

    private void pauseAndClearVideoSurface() {
        if (getGSYVideoManager() != null) {
            try {
                getGSYVideoManager().pause();
            } catch (Exception e) {
                Debuger.printfWarning(e.toString());
            }
        }
        showBlackCover();
    }

    private void showBlackCover() {
        if (mThumbImageViewLayout == null) {
            return;
        }
        mThumbImageViewLayout.setVisibility(VISIBLE);
        mThumbImageViewLayout.setBackgroundColor(Color.BLACK);
        // mThumbImageViewLayout 是 RelativeLayout，封面图在其子 ImageView 中
        if (mThumbImageViewLayout instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) mThumbImageViewLayout;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child instanceof ImageView) {
                    ((ImageView) child).setImageDrawable(null);
                }
            }
        }
    }

    private void hideThumbCover() {
        if (mThumbImageViewLayout != null) {
            mThumbImageViewLayout.setVisibility(GONE);
            mThumbImageViewLayout.setBackground(null);
        }
    }

    private void applyEndScreenUi() {
        if (mBottomContainer != null) {
            setViewShowState(mBottomContainer, GONE);
        }
        if (mBottomProgressBar != null) {
            mBottomProgressBar.setVisibility(GONE);
        }
        if (mDanmakuView != null) {
            mDanmakuView.setVisibility(GONE);
        }
        if (mTopContainer != null) {
            setViewShowState(mTopContainer, VISIBLE);
        }
        showBlackCover();
        showEndOverlay();
    }

    private void restorePlaybackControlsUi() {
        hideEndOverlay();
        hideThumbCover();
        if (mDanmakuView != null) {
            mDanmakuView.setVisibility(VISIBLE);
        }
        if (mBottomContainer != null) {
            setViewShowState(mBottomContainer, VISIBLE);
        }
        if (mBottomProgressBar != null) {
            mBottomProgressBar.setVisibility(VISIBLE);
        }
    }

    /** 结束页点击重播：从头播放并恢复控制栏 */
    public void replayFromBeginning() {
        hideEndOverlay();
        hideThumbCover();
        if (mDanmakuView != null) {
            mDanmakuView.setVisibility(VISIBLE);
        }
        if (mBottomContainer != null) {
            setViewShowState(mBottomContainer, VISIBLE);
        }
        if (mBottomProgressBar != null) {
            mBottomProgressBar.setVisibility(VISIBLE);
        }
        if (mProgressBar != null) {
            mProgressBar.setProgress(0);
        }
        if (mBottomProgressBar != null) {
            mBottomProgressBar.setProgress(0);
        }
        if (mCurrentTimeTextView != null) {
            mCurrentTimeTextView.setText(CommonUtil.stringForTime(0));
        }

        mHadPlay = true;
        mSaveChangeViewTIme = 0;
        mCurrentPosition = 0;
        mSeekTimePosition = 0;

        if (getGSYVideoManager() != null) {
            try {
                getGSYVideoManager().seekTo(0);
            } catch (Exception e) {
                Debuger.printfWarning(e.toString());
            }
        }

        clearDanmakuClock();
        mDanmakuStarted = false;
        if (mDanmakuView != null) {
            if (mDanmakuView.isPrepared()) {
                mDanmakuView.seekTo(0L);
            } else if (mParser != null && mDanmakuContext != null) {
                prepareDanmakuForReplay();
            }
        }

        setStateAndUi(CURRENT_STATE_PLAYING);
        startPlayLogic();
        notifyControlBarVisibility();
        post(this::ensureDanmakuPlaying);
    }

    /** prepare 等场景触发的 completion，走父类重置逻辑即可 */
    @Override
    public void onCompletion() {
        super.onCompletion();
        releaseDanmaku(this);
    }

    @Override
    protected void setStateAndUi(int state) {
        super.setStateAndUi(state);
        if (state == CURRENT_STATE_AUTO_COMPLETE) {
            applyEndScreenUi();
        }
    }

    @Override
    protected void showDragProgressTextOnSeekBar(boolean fromUser, int progress) {
        if (fromUser && isShowDragProgressTextOnSeekBar) {
            long duration = getDuration();
            if (duration > 0 && mCurrentTimeTextView != null) {
                long time = progressToTimeMs(progress, getSeekBarMax(), duration);
                mCurrentTimeTextView.setText(CommonUtil.stringForTime(time));
            }
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        showDragProgressTextOnSeekBar(fromUser, progress);
        if (fromUser) {
            notifyPreviewSeek(progress, seekBar.getMax());
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        removeCallbacks(mSeekBarSettleRunnable);
        removeCallbacks(mSeekBufferFallbackRunnable);
        mAwaitingBufferAfterSeek = false;
        mSeekBarSettling = false;
        mHadSeekTouch = true;
        mUserSeekingDanmaku = true;
        freezeClockAnchor();
        clearOnScreenDanmaku();
        danmakuOnPause();
        startPreviewSeek();
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        if (mVideoAllCallBack != null && isCurrentMediaListener()) {
            if (isIfCurrentIsFullscreen()) {
                mVideoAllCallBack.onClickSeekbarFullscreen(mOriginUrl, mTitle, this);
            } else {
                mVideoAllCallBack.onClickSeekbar(mOriginUrl, mTitle, this);
            }
        }

        endPreviewSeek();

        if (getGSYVideoManager() == null) {
            finishSeekBarDrag();
            return;
        }

        long duration = getDuration();
        if (duration <= 0) {
            finishSeekBarDrag();
            return;
        }

        int max = seekBar.getMax() > 0 ? seekBar.getMax() : getSeekBarMax();
        int progress = seekBar.getProgress();
        long rawTime = Math.min(progressToTimeMs(progress, max, duration), duration);
        mSeekBarSettling = true;
        mPendingSeekTargetMs = rawTime;
        beginAwaitingBufferAfterUserSeek(rawTime);
        seekByUser(rawTime, duration, progress, max);
        postDelayed(mSeekBarSettleRunnable, SEEK_BAR_SETTLE_MS);
    }

    @Override
    protected void setProgressAndTime(long progress, long secProgress, long currentTime, long totalTime,
                                      boolean forceChange) {
        if (mGSYVideoProgressListener != null && mCurrentState == CURRENT_STATE_PLAYING) {
            mGSYVideoProgressListener.onProgress(progress, secProgress, currentTime, totalTime);
        }
        if (!mUserSeekingDanmaku && !mHadSeekTouch && !mSeekBarSettling) {
            if (currentTime > 0) {
                mLastReportedVideoMs = currentTime;
            }
            if (!mDanmakuStarted) {
                tryStartDanmakuWithVideo();
            }
        }
        if (mProgressBar == null || mTotalTimeTextView == null || mCurrentTimeTextView == null) {
            return;
        }
        if (mHadSeekTouch) {
            return;
        }
        if (!mTouchingProgressBar) {
            if (progress >= 0 || forceChange) {
                mProgressBar.setProgress(toSeekBarProgress(currentTime, totalTime));
            }
        }
        if (getGSYVideoManager().getBufferedPercentage() > 0) {
            secProgress = getGSYVideoManager().getBufferedPercentage();
        }
        if (secProgress > 94) {
            secProgress = 100;
        }
        setSecondaryProgress(secProgress);
        mTotalTimeTextView.setText(CommonUtil.stringForTime(totalTime));
        if (currentTime > 0) {
            mCurrentTimeTextView.setText(CommonUtil.stringForTime(currentTime));
        }
        if (mBottomProgressBar != null) {
            if (progress >= 0 || forceChange) {
                int bottomProgress = totalTime > 0 ? (int) (currentTime * 100 / totalTime) : 0;
                mBottomProgressBar.setProgress(bottomProgress);
            }
            setSecondaryProgress(secProgress);
        }
        tryStartDanmakuWithVideo();
    }

    @Override
    protected void touchSurfaceMove(float deltaX, float deltaY, float y) {
        super.touchSurfaceMove(deltaX, deltaY, y);
        if (mChangePosition) {
            long duration = getDuration();
            if (duration > 0 && mProgressBar != null) {
                mProgressBar.setProgress(toSeekBarProgress(mSeekTimePosition, duration));
            }
            notifyPreviewSeekByTime(mSeekTimePosition);
        }
    }

    @Override
    protected void touchSurfaceUp() {
        if (mChangePosition) {
            long duration = getDuration();
            if (duration > 0 && mBottomProgressBar != null) {
                mBottomProgressBar.setProgress((int) (mSeekTimePosition * 100 / duration));
            }
        }
        mTouchingProgressBar = false;
        dismissProgressDialog();
        dismissVolumeDialog();
        dismissBrightnessDialog();
        if (mChangePosition && getGSYVideoManager() != null) {
            boolean wasComplete = mCurrentState == CURRENT_STATE_AUTO_COMPLETE;
            boolean canSeek = mCurrentState == CURRENT_STATE_PLAYING
                    || mCurrentState == CURRENT_STATE_PAUSE
                    || wasComplete;
            if (canSeek) {
                long duration = getDuration();
                if (duration > 0) {
                    mUserSeekingDanmaku = true;
                    mSeekBarSettling = true;
                    int max = mProgressBar != null ? mProgressBar.getMax() : getSeekBarMax();
                    int progress = mProgressBar != null ? mProgressBar.getProgress() : -1;
                    long target = mSeekTimePosition;
                    mPendingSeekTargetMs = target;
                    beginAwaitingBufferAfterUserSeek(target);
                    seekByUser(target, duration, progress, max);
                    postDelayed(mSeekBarSettleRunnable, SEEK_BAR_SETTLE_MS);
                } else {
                    try {
                        getGSYVideoManager().seekTo(mSeekTimePosition);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    if (wasComplete) {
                        mHadPlay = true;
                        restorePlaybackControlsUi();
                        setStateAndUi(CURRENT_STATE_PAUSE);
                    }
                    mSeekBarSettling = true;
                    beginAwaitingBufferAfterUserSeek(mSeekTimePosition);
                    postDelayed(mSeekBarSettleRunnable, SEEK_BAR_SETTLE_MS);
                }
            }
            if (mVideoAllCallBack != null && isCurrentMediaListener()) {
                mVideoAllCallBack.onTouchScreenSeekPosition(mOriginUrl, mTitle, this);
            }
        } else if (mBrightness) {
            if (mVideoAllCallBack != null && isCurrentMediaListener()) {
                mVideoAllCallBack.onTouchScreenSeekLight(mOriginUrl, mTitle, this);
            }
        } else if (mChangeVolume) {
            if (mVideoAllCallBack != null && isCurrentMediaListener()) {
                mVideoAllCallBack.onTouchScreenSeekVolume(mOriginUrl, mTitle, this);
            }
        }
    }

    @Override
    protected void showProgressDialog(float deltaX, String seekTime, long seekTimePosition,
                                      String totalTime, long totalTimeDuration) {
        startPreviewSeek();
        if (totalTimeDuration > 0 && mProgressBar != null) {
            mProgressBar.setProgress(toSeekBarProgress(seekTimePosition, totalTimeDuration));
        }
        if (mCurrentTimeTextView != null) {
            mCurrentTimeTextView.setText(seekTime);
        }
        notifyPreviewSeekByTime(seekTimePosition);
    }

    @Override
    protected void dismissProgressDialog() {
        endPreviewSeek();
    }

    private void startPreviewSeek() {
        if (mPreviewSeekActive) return;
        mPreviewSeekActive = true;
        if (mPreviewSeekListener != null) {
            mPreviewSeekListener.onSeekStart();
        }
    }

    private void endPreviewSeek() {
        if (!mPreviewSeekActive) return;
        mPreviewSeekActive = false;
        if (mPreviewSeekListener != null) {
            mPreviewSeekListener.onSeekEnd();
        }
    }

    private void notifyPreviewSeek(int progress, int max) {
        long duration = getDuration();
        if (duration <= 0) return;
        notifyPreviewSeekByTime(progressToTimeMs(progress, max, duration));
    }

    private void notifyPreviewSeekByTime(long seekTimeMs) {
        long duration = getDuration();
        if (duration <= 0 || mPreviewSeekListener == null) return;
        mPreviewSeekListener.onSeekPreview(seekTimeMs, duration);
    }

    /**
     * 拖动进度条松手后
     */
    @Override
    public void onSeekComplete() {
        super.onSeekComplete();
        // 获取当前视频总时长
        long duration = getDuration();
        if (duration > 0 && mProgressBar != null) {
            long actualPos = getCurrentPositionWhenPlaying();
            mProgressBar.setProgress(toSeekBarProgress(actualPos, duration));
            if (mCurrentTimeTextView != null) {
                // 刷新当前播放进度时间
                mCurrentTimeTextView.setText(CommonUtil.stringForTime(actualPos));
            }
        }
        // 勿在此 seek 弹幕：HLS 开播/缓冲会多次 onSeekComplete，导致全部弹幕弹回
    }

    @Override
    public void onClick(View v) {
        super.onClick(v);
    }

    @Override
    protected void cloneParams(GSYBaseVideoPlayer from, GSYBaseVideoPlayer to) {
        super.cloneParams(from, to);
        if (from instanceof DanmakuVideoPlayer && to instanceof DanmakuVideoPlayer) {
            DanmakuVideoPlayer fromPlayer = (DanmakuVideoPlayer) from;
            DanmakuVideoPlayer toPlayer = (DanmakuVideoPlayer) to;
            toPlayer.mDanmakuContext = fromPlayer.mDanmakuContext;
            toPlayer.mParser = fromPlayer.mParser;
            toPlayer.mDanmaKuShow = fromPlayer.mDanmaKuShow;
            toPlayer.mDanmakuTimer = fromPlayer.mDanmakuTimer;
            toPlayer.mPreviewSeekListener = fromPlayer.mPreviewSeekListener;
            toPlayer.mControlBarVisibilityListener = fromPlayer.mControlBarVisibilityListener;
            toPlayer.mPlaybackEndListener = fromPlayer.mPlaybackEndListener;
            toPlayer.mAppliedDanmakuCount = fromPlayer.mAppliedDanmakuCount;
        }
    }

    @Override
    public GSYBaseVideoPlayer startWindowFullscreen(Context context, boolean actionBar, boolean statusBar) {
        GSYBaseVideoPlayer gsyBaseVideoPlayer = super.startWindowFullscreen(context, actionBar, statusBar);
        if (gsyBaseVideoPlayer != null) {
            DanmakuVideoPlayer gsyVideoPlayer = (DanmakuVideoPlayer) gsyBaseVideoPlayer;
            gsyVideoPlayer.configureProgressBarPrecision();
            gsyVideoPlayer.setDanmakuStartSeekPosition(getCurrentPositionWhenPlaying());
            gsyVideoPlayer.setDanmaKuShow(getDanmaKuShow());
            onPrepareDanmaku(gsyVideoPlayer);
        }
        return gsyBaseVideoPlayer;
    }

    @Override
    protected void resolveNormalVideoShow(View oldF, ViewGroup vp, GSYVideoPlayer gsyVideoPlayer) {
        super.resolveNormalVideoShow(oldF, vp, gsyVideoPlayer);
        notifyControlBarVisibility();
        if (gsyVideoPlayer != null) {
            DanmakuVideoPlayer gsyDanmaVideoPlayer = (DanmakuVideoPlayer) gsyVideoPlayer;
            setDanmaKuShow(gsyDanmaVideoPlayer.getDanmaKuShow());
            if (gsyDanmaVideoPlayer.getDanmakuView() != null
                    && gsyDanmaVideoPlayer.getDanmakuView().isPrepared()) {
                resolveDanmakuSeek(this, gsyDanmaVideoPlayer.getCurrentPositionWhenPlaying());
                resolveDanmakuShow();
                releaseDanmaku(gsyDanmaVideoPlayer);
            }
        }
    }

    protected void danmakuOnPause() {
        if (mDanmakuView != null && mDanmakuView.isPrepared()) {
            mDanmakuView.pause();
        }
    }

    protected void danmakuOnResume() {
        if (mAwaitingBufferAfterSeek) {
            return;
        }
        if (mDanmakuView != null && mDanmakuView.isPrepared() && mDanmakuView.isPaused()) {
            mDanmakuView.resume();
        }
    }

    /**
     * 等视频进入 PLAYING 再 start，并用 updateTimer 跟视频进度，避免弹幕时钟先跑。
     */
    private void tryStartDanmakuWithVideo() {
        if (mDanmakuStarted || mAppliedDanmakuCount < 0) {
            return;
        }
        if (mDanmakuView == null || !mDanmakuView.isPrepared() || !mHadPlay) {
            return;
        }
        if (!isVideoPlayingForDanmaku() || mAwaitingBufferAfterSeek) {
            return;
        }
        mDanmakuStarted = true;
        long videoMs = resolveVideoPositionMs();
        if (getDanmakuStartSeekPosition() >= 0) {
            videoMs = getDanmakuStartSeekPosition();
            setDanmakuStartSeekPosition(-1);
        }
        resyncClockAnchor(videoMs);
        if (mDanmakuNeedsInitialSeek && videoMs > 400) {
            mDanmakuView.seekTo(videoMs);
            mDanmakuNeedsInitialSeek = false;
        }
        mDanmakuView.start();
        danmakuOnResume();
        resolveDanmakuShow();
    }

    private void seekDanmakuByUser(long timeMs, boolean forceSeekTo) {
        if (timeMs < 0 || !mHadPlay || mDanmakuView == null || !mDanmakuView.isPrepared()) {
            return;
        }
        resyncClockAnchor(timeMs);
        if (forceSeekTo) {
            clearOnScreenDanmaku();
            mDanmakuView.seekTo(timeMs);
            mDanmakuNeedsInitialSeek = false;
        }
        if (mDanmakuTimer != null) {
            mDanmakuTimer.update(mClockAnchorVideoMs);
        }
    }

    /**
     * 弹幕位置类型
     * @param apiMode
     * @return
     */
    private static int apiModeToDanmakuType(int apiMode) {
        switch (apiMode) {
            case 1:
                return BaseDanmaku.TYPE_FIX_TOP;
            case 2:
                return BaseDanmaku.TYPE_FIX_BOTTOM;
            default:
                return BaseDanmaku.TYPE_SCROLL_RL;
        }
    }

    private InputStream buildBiliDanmakuXmlStream(List<DanmuEntity> entities) {
        StringBuilder sb = new StringBuilder("<i>");
        for (DanmuEntity entity : entities) {
            if (entity == null || entity.getText() == null) {
                continue;
            }
            int mode = apiModeToDanmakuType(entity.getMode());
            int colorInt = Color.WHITE;
            try {
                colorInt = Color.parseColor(entity.getColor());
            } catch (Exception ignored) {
            }
            sb.append("<d p=\"")
                    .append(entity.getTime())
                    .append(",")
                    .append(mode)
                    .append(",25,")
                    .append(colorInt & 0xFFFFFF)
                    .append(",0,0,0\">")
                    .append(escapeXml(entity.getText()))
                    .append("</d>");
        }
        sb.append("</i>");
        return new ByteArrayInputStream(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String escapeXml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    public void setDanmaKuStream(File is) {
        mDumakuFile = is;
        if (!getDanmakuView().isPrepared()) {
            onPrepareDanmaku((DanmakuVideoPlayer) getCurrentPlayer());
        }
    }

    private void initDanmaku() {
        HashMap<Integer, Integer> maxLinesPair = new HashMap<>();
        maxLinesPair.put(BaseDanmaku.TYPE_SCROLL_RL, 5);
        maxLinesPair.put(BaseDanmaku.TYPE_FIX_TOP, 3);
        maxLinesPair.put(BaseDanmaku.TYPE_FIX_BOTTOM, 3);
        HashMap<Integer, Boolean> overlappingEnablePair = new HashMap<>();
        overlappingEnablePair.put(BaseDanmaku.TYPE_SCROLL_RL, true);
        overlappingEnablePair.put(BaseDanmaku.TYPE_FIX_TOP, true);
        overlappingEnablePair.put(BaseDanmaku.TYPE_FIX_BOTTOM, true);

        DanamakuAdapter danamakuAdapter = new DanamakuAdapter(mDanmakuView);
        mDanmakuContext = DanmakuContext.create();
        mDanmakuContext.setDanmakuStyle(IDisplayer.DANMAKU_STYLE_STROKEN, 3)
                .setDuplicateMergingEnabled(false)
                .setScrollSpeedFactor(1.2f)
                .setScaleTextSize(1.2f)
                .setCacheStuffer(new SpannedCacheStuffer(), danamakuAdapter)
                .setMaximumLines(maxLinesPair)
                .preventOverlapping(overlappingEnablePair);

        if (mDanmakuView != null) {
            mDanmakuView.setCallback(new master.flame.danmaku.controller.DrawHandler.Callback() {
                @Override
                public void updateTimer(DanmakuTimer timer) {
                    mDanmakuTimer = timer;
                    if (mDanmakuStarted && isVideoPlayingForDanmaku()
                            && !mUserSeekingDanmaku && !mSeekBarSettling
                            && !mAwaitingBufferAfterSeek) {
                        timer.update(getMonotonicPlayClockMs());
                    }
                }

                @Override
                public void drawingFinished() {
                }

                @Override
                public void danmakuShown(BaseDanmaku danmaku) {
                }

                @Override
                public void prepared() {
                    resolveDanmakuShow();
                    if (mHadPlay && mAppliedDanmakuCount >= 0) {
                        post(DanmakuVideoPlayer.this::ensureDanmakuPlaying);
                    }
                }
            });
            mDanmakuView.enableDanmakuDrawingCache(false);
            if (mDumakuFile != null) {
                mParser = createParser(getIsStream(mDumakuFile));
                mDanmakuView.prepare(mParser, mDanmakuContext);
            }
        }
    }

    private InputStream getIsStream(File file) {
        try {
            InputStream instream = new FileInputStream(file);
            InputStreamReader inputreader = new InputStreamReader(instream);
            BufferedReader buffreader = new BufferedReader(inputreader);
            String line;
            StringBuilder sb1 = new StringBuilder();
            sb1.append("<i>");
            while ((line = buffreader.readLine()) != null) {
                sb1.append(line);
            }
            sb1.append("</i>");
            instream.close();
            return new ByteArrayInputStream(sb1.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            Log.d("TestFile", e.getMessage());
        }
        return null;
    }

    private void resolveDanmakuShow() {
        post(() -> {
            if (getDanmakuView() == null) return;
            if (mDanmaKuShow) {
                if (!getDanmakuView().isShown()) {
                    getDanmakuView().show();
                }
                if (mToogleDanmaku != null) {
                    mToogleDanmaku.setText("弹幕关");
                }
            } else {
                if (getDanmakuView().isShown()) {
                    getDanmakuView().hide();
                }
                if (mToogleDanmaku != null) {
                    mToogleDanmaku.setText("弹幕开");
                }
            }
        });
    }

    private void onPrepareDanmaku(DanmakuVideoPlayer gsyVideoPlayer) {
        if (gsyVideoPlayer.getDanmakuView() != null
                && !gsyVideoPlayer.getDanmakuView().isPrepared()
                && gsyVideoPlayer.getParser() != null) {
            gsyVideoPlayer.getDanmakuView().prepare(
                    gsyVideoPlayer.getParser(), gsyVideoPlayer.getDanmakuContext());
        }
    }

    private void resolveDanmakuSeek(DanmakuVideoPlayer gsyVideoPlayer, long time) {
        if (mHadPlay && gsyVideoPlayer.getDanmakuView() != null
                && gsyVideoPlayer.getDanmakuView().isPrepared()) {
            gsyVideoPlayer.getDanmakuView().seekTo(time);
        }
    }

    /**
     * 灌入网络弹幕并 prepare。须在视频 startPlayLogic 之前调用，与视频同时从 0 启动。
     */
    public void setDanmakuList(List<DanmuEntity> entities) {
        int count = entities == null ? 0 : entities.size();
        if (count == mAppliedDanmakuCount && mDanmakuView != null && mDanmakuView.isPrepared()) {
            return;
        }
        mAppliedDanmakuCount = count;
        if (count == 0) {
            mParser = createParser(null);
        } else {
            mParser = createParser(buildBiliDanmakuXmlStream(entities));
        }
        if (mDanmakuView == null || mDanmakuContext == null || mParser == null) {
            return;
        }
        if (mDanmakuView.isPrepared()) {
            mDanmakuView.release();
        }
        mDanmakuView.prepare(mParser, mDanmakuContext);
    }

    /** 重播：release 后重新 prepare，不丢 mParser */
    public void prepareDanmakuForReplay() {
        if (mParser == null || mAppliedDanmakuCount < 0 || mDanmakuView == null || mDanmakuContext == null) {
            return;
        }
        mDanmakuStarted = false;
        clearDanmakuClock();
        setDanmakuStartSeekPosition(0);
        if (mDanmakuView.isPrepared()) {
            mDanmakuView.seekTo(0L);
            return;
        }
        mDanmakuView.prepare(mParser, mDanmakuContext);
    }

    public void resetDanmakuForNewVideo() {
        mAppliedDanmakuCount = -1;
        mParser = null;
        mDanmakuStarted = false;
        clearDanmakuClock();
        setDanmakuStartSeekPosition(-1);
        if (mDanmakuView != null && mDanmakuView.isPrepared()) {
            mDanmakuView.pause();
            mDanmakuView.release();
        }
    }

    private BaseDanmakuParser createParser(InputStream stream) {
        if (stream == null) {
            stream = new ByteArrayInputStream("<i></i>".getBytes(StandardCharsets.UTF_8));
        }
        ILoader loader = DanmakuLoaderFactory.create(DanmakuLoaderFactory.TAG_BILI);
        try {
            loader.load(stream);
        } catch (IllegalDataException e) {
            e.printStackTrace();
        }
        BaseDanmakuParser parser = new BiliDanmukuParser();
        IDataSource<?> dataSource = loader.getDataSource();
        parser.load(dataSource);
        return parser;
    }

    private void releaseDanmaku(DanmakuVideoPlayer danmakuVideoPlayer) {
        if (danmakuVideoPlayer != null && danmakuVideoPlayer.getDanmakuView() != null) {
            danmakuVideoPlayer.mDanmakuStarted = false;
            danmakuVideoPlayer.getDanmakuView().release();
        }
    }

    public BaseDanmakuParser getParser() {
        if (mParser == null && mDumakuFile != null) {
            mParser = createParser(getIsStream(mDumakuFile));
        }
        return mParser;
    }

    public DanmakuContext getDanmakuContext() {
        return mDanmakuContext;
    }

    public IDanmakuView getDanmakuView() {
        return mDanmakuView;
    }

    public long getDanmakuStartSeekPosition() {
        return mDanmakuStartSeekPosition;
    }

    public void setDanmakuStartSeekPosition(long danmakuStartSeekPosition) {
        this.mDanmakuStartSeekPosition = danmakuStartSeekPosition;
    }

    public void syncDanmakuOnce(long posMs) {
        if (posMs < 0) {
            return;
        }
        if (mDanmakuView != null && mDanmakuView.isPrepared()) {
            seekDanmakuByUser(posMs, true);
            danmakuOnResume();
        } else {
            setDanmakuStartSeekPosition(posMs);
            onPrepareDanmaku(this);
        }
    }

    public void setDanmaKuShow(boolean danmaKuShow) {
        mDanmaKuShow = danmaKuShow;
        resolveDanmakuShow();
    }

    public boolean getDanmaKuShow() {
        return mDanmaKuShow;
    }

    public void addDanmakuEntity(DanmuEntity entity) {
        if (mDanmakuContext == null || mDanmakuView == null || entity == null) {
            return;
        }
        BaseDanmaku danmaku = mDanmakuContext.mDanmakuFactory.createDanmaku(
                apiModeToDanmakuType(entity.getMode()), mDanmakuContext);
        if (danmaku == null) return;

        danmaku.text = entity.getText();
        danmaku.padding = 5;
        danmaku.priority = 10;
        danmaku.isLive = false;
        danmaku.setTime(entity.getTime() * 1000L);
        danmaku.textSize = 18f * getContext().getResources().getDisplayMetrics().scaledDensity;
        try {
            danmaku.textColor = Color.parseColor(entity.getColor());
        } catch (Exception e) {
            danmaku.textColor = Color.WHITE;
        }

        if (mDanmakuTimer != null) {
            danmaku.setTimer(mDanmakuTimer);
        } else if (mParser != null && mParser.getTimer() != null) {
            danmaku.setTimer(mParser.getTimer());
        } else {
            danmaku.setTimer(new DanmakuTimer(getCurrentPositionWhenPlaying()));
        }

        danmaku.flags = mDanmakuContext.mGlobalFlagValues;
        mDanmakuView.addDanmaku(danmaku);
    }
}
