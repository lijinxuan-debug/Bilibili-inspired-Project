package com.example.bilibili.ui.playVideo;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
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
import master.flame.danmaku.danmaku.model.AbsDanmakuSync;
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

    private static final int SEEK_BAR_MAX = 1000;

    /** 与视频偏差超过该值才走引擎 requestSync（设很大，避免播放中弹回） */
    private static final long DANMAKU_SYNC_THRESHOLD_MS = 60 * 60 * 1000L;

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

    public interface OnPreviewSeekListener {
        void onSeekStart();
        void onSeekPreview(long seekTimeMs, long totalTimeMs);
        void onSeekEnd();
    }

    private OnPreviewSeekListener mPreviewSeekListener;
    private boolean mPreviewSeekActive;

    public void setOnPreviewSeekListener(OnPreviewSeekListener listener) {
        mPreviewSeekListener = listener;
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
    }

    private void configureProgressBarPrecision() {
        if (mProgressBar != null) {
            mProgressBar.setMax(SEEK_BAR_MAX);
        }
    }

    private int getSeekBarMax() {
        if (mProgressBar != null && mProgressBar.getMax() > 0) {
            return mProgressBar.getMax();
        }
        return SEEK_BAR_MAX;
    }

    private static int toSeekBarProgress(long positionMs, long durationMs) {
        if (durationMs <= 0) return 0;
        int p = (int) (positionMs * SEEK_BAR_MAX / durationMs);
        return Math.min(p, SEEK_BAR_MAX);
    }

    private static long progressToTimeMs(int progress, int max, long durationMs) {
        if (max <= 0 || durationMs <= 0) return 0;
        return (long) progress * durationMs / max;
    }

    @Override
    public void onPrepared() {
        super.onPrepared();
        onPrepareDanmaku(this);
    }

    @Override
    public void onVideoPause() {
        super.onVideoPause();
        danmakuOnPause();
    }

    @Override
    public void onVideoResume(boolean isResume) {
        super.onVideoResume(isResume);
        danmakuOnResume();
        tryStartDanmakuWithVideo();
    }

    @Override
    protected void clickStartIcon() {
        super.clickStartIcon();
        if (mCurrentState == CURRENT_STATE_PLAYING) {
            danmakuOnResume();
            tryStartDanmakuWithVideo();
        } else if (mCurrentState == CURRENT_STATE_PAUSE) {
            danmakuOnPause();
        }
    }

    @Override
    public void onCompletion() {
        super.onCompletion();
        releaseDanmaku(this);
        mCurrentState = CURRENT_STATE_AUTO_COMPLETE;
        changeUiToCompleteShow();
        cancelProgressTimer();
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
        mHadSeekTouch = true;
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
        if (getGSYVideoManager() != null && mHadPlay) {
            try {
                long duration = getDuration();
                if (duration > 0) {
                    long time = progressToTimeMs(seekBar.getProgress(), seekBar.getMax(), duration);
                    mCurrentPosition = time;
                    getGSYVideoManager().seekTo(time);
                }
            } catch (Exception e) {
                Debuger.printfWarning(e.toString());
            }
        }
        mHadSeekTouch = false;
        endPreviewSeek();
        if (getGSYVideoManager() != null && mHadPlay) {
            long pos = getCurrentPositionWhenPlaying();
            if (pos >= 0) {
                post(() -> seekDanmakuByUser(pos));
            }
        }
    }

    @Override
    protected void setProgressAndTime(long progress, long secProgress, long currentTime, long totalTime,
                                      boolean forceChange) {
        if (mGSYVideoProgressListener != null && mCurrentState == CURRENT_STATE_PLAYING) {
            mGSYVideoProgressListener.onProgress(progress, secProgress, currentTime, totalTime);
        }
        tryStartDanmakuWithVideo();
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
        if (mChangePosition && getGSYVideoManager() != null
                && (mCurrentState == CURRENT_STATE_PLAYING || mCurrentState == CURRENT_STATE_PAUSE)) {
            try {
                getGSYVideoManager().seekTo(mSeekTimePosition);
            } catch (Exception e) {
                e.printStackTrace();
            }
            long duration = getDuration();
            if (duration > 0 && mProgressBar != null) {
                mProgressBar.setProgress(toSeekBarProgress(mSeekTimePosition, duration));
            }
            seekDanmakuByUser(mSeekTimePosition);
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

    @Override
    public void onSeekComplete() {
        super.onSeekComplete();
        long duration = getDuration();
        if (duration > 0 && mProgressBar != null) {
            long actualPos = getCurrentPositionWhenPlaying();
            mProgressBar.setProgress(toSeekBarProgress(actualPos, duration));
            if (mCurrentTimeTextView != null) {
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
        if (mCurrentState != CURRENT_STATE_PLAYING) {
            return;
        }
        mDanmakuStarted = true;
        long videoMs = Math.max(0, getCurrentPositionWhenPlaying());
        if (getDanmakuStartSeekPosition() >= 0) {
            videoMs = getDanmakuStartSeekPosition();
            setDanmakuStartSeekPosition(-1);
        }
        if (videoMs > 400) {
            mDanmakuView.seekTo(videoMs);
        }
        mDanmakuView.start();
        resolveDanmakuShow();
    }

    private void seekDanmakuByUser(long timeMs) {
        if (timeMs < 0 || mDanmakuView == null || !mDanmakuView.isPrepared()) {
            return;
        }
        mUserSeekingDanmaku = true;
        resolveDanmakuSeek(this, timeMs);
        mUserSeekingDanmaku = false;
    }

    /** API mode(0滚动/1置顶/2置底) -> 烈焰/B站弹幕类型 */
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
        mDanmakuContext.setDanmakuSync(createVideoDanmakuSync());

        if (mDanmakuView != null) {
            mDanmakuView.setCallback(new master.flame.danmaku.controller.DrawHandler.Callback() {
                @Override
                public void updateTimer(DanmakuTimer timer) {
                    mDanmakuTimer = timer;
                    if (mDanmakuStarted && mCurrentState == CURRENT_STATE_PLAYING && !mUserSeekingDanmaku) {
                        long videoMs = getCurrentPositionWhenPlaying();
                        if (videoMs >= 0) {
                            timer.update(videoMs);
                        }
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
                }
            });
            mDanmakuView.enableDanmakuDrawingCache(true);
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
        if (mDanmakuView.isPrepared()) {
            return;
        }
        mDanmakuStarted = false;
        setDanmakuStartSeekPosition(0);
        mDanmakuView.prepare(mParser, mDanmakuContext);
    }

    private AbsDanmakuSync createVideoDanmakuSync() {
        return new AbsDanmakuSync() {
            @Override
            public long getUptimeMillis() {
                long t = getCurrentPositionWhenPlaying();
                return Math.max(0, t);
            }

            @Override
            public int getSyncState() {
                return mCurrentState == CURRENT_STATE_PLAYING
                        ? SYNC_STATE_PLAYING : SYNC_STATE_HALT;
            }

            @Override
            public boolean isSyncPlayingState() {
                return true;
            }

            @Override
            public long getThresholdTimeMills() {
                return DANMAKU_SYNC_THRESHOLD_MS;
            }
        };
    }

    public void resetDanmakuForNewVideo() {
        mAppliedDanmakuCount = -1;
        mParser = null;
        mDanmakuStarted = false;
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
            seekDanmakuByUser(posMs);
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
