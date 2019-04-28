/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tv.recommendation;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.tv.TvInputInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseLongArray;
import android.view.View;

import com.android.tv.ApplicationSingletons;
import com.android.tv.MainActivityWrapper.OnCurrentChannelChangeListener;
import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.common.WeakHandler;
import com.android.tv.data.Channel;
import com.android.tv.data.Program;
import com.android.tv.util.BitmapUtils;
import com.android.tv.util.BitmapUtils.ScaledBitmapInfo;
import com.android.tv.util.ImageLoader;
import com.android.tv.util.TvInputManagerHelper;
import com.android.tv.util.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 * A local service for notify recommendation at home launcher.
 */
public class NotificationService extends Service implements Recommender.Listener,
        OnCurrentChannelChangeListener {
    private static final String TAG = "NotificationService";
    private static final boolean DEBUG = false;

    public static final String ACTION_SHOW_RECOMMENDATION =
            "com.android.tv.notification.ACTION_SHOW_RECOMMENDATION";
    public static final String ACTION_HIDE_RECOMMENDATION =
            "com.android.tv.notification.ACTION_HIDE_RECOMMENDATION";

    /**
     * Recommendation intent has an extra data for the recommendation type. It'll be also
     * sent to a TV input as a tune parameter.
     */
    public static final String TUNE_PARAMS_RECOMMENDATION_TYPE =
            "com.android.tv.recommendation_type";

    private static final String TYPE_RANDOM_RECOMMENDATION = "random";
    private static final String TYPE_ROUTINE_WATCH_RECOMMENDATION = "routine_watch";
    private static final String TYPE_ROUTINE_WATCH_AND_FAVORITE_CHANNEL_RECOMMENDATION =
            "routine_watch_and_favorite";

    private static final String NOTIFY_TAG = "tv_recommendation";
    // TODO: find out proper number of notifications and whether to make it dynamically
    // configurable from system property or etc.
    private static final int NOTIFICATION_COUNT = 3;

    private static final int MSG_INITIALIZE_RECOMMENDER = 1000;
    private static final int MSG_SHOW_RECOMMENDATION = 1001;
    private static final int MSG_UPDATE_RECOMMENDATION = 1002;
    private static final int MSG_HIDE_RECOMMENDATION = 1003;

    private static final long RECOMMENDATION_RETRY_TIME_MS = 5 * 60 * 1000;  // 5 min
    private static final long RECOMMENDATION_THRESHOLD_LEFT_TIME_MS = 10 * 60 * 1000;  // 10 min
    private static final int RECOMMENDATION_THRESHOLD_PROGRESS = 90;  // 90%
    private static final int MAX_PROGRAM_UPDATE_COUNT = 20;

    private TvInputManagerHelper mTvInputManagerHelper;
    private Recommender mRecommender;
    private boolean mShowRecommendationAfterRecommenderReady;
    private NotificationManager mNotificationManager;
    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private final String mRecommendationType;
    private int mCurrentNotificationCount;
    private long[] mNotificationChannels;

    private Channel mPlayingChannel;

    private float mNotificationCardMaxWidth;
    private float mNotificationCardHeight;
    private int mCardImageHeight;
    private int mCardImageMaxWidth;
    private int mCardImageMinWidth;
    private int mChannelLogoMaxWidth;
    private int mChannelLogoMaxHeight;
    private int mLogoPaddingStart;
    private int mLogoPaddingBottom;

    public NotificationService() {
        mRecommendationType = TYPE_ROUTINE_WATCH_AND_FAVORITE_CHANNEL_RECOMMENDATION;
    }

    @Override
    public void onCreate() {
        if (DEBUG) Log.d(TAG, "onCreate");
        TvApplication.setCurrentRunningProcess(this, true);
        super.onCreate();
        mCurrentNotificationCount = 0;
        mNotificationChannels = new long[NOTIFICATION_COUNT];
        for (int i = 0; i < NOTIFICATION_COUNT; ++i) {
            mNotificationChannels[i] = Channel.INVALID_ID;
        }
        mNotificationCardMaxWidth = getResources().getDimensionPixelSize(
                R.dimen.notif_card_img_max_width);
        mNotificationCardHeight = getResources().getDimensionPixelSize(
                R.dimen.notif_card_img_height);
        mCardImageHeight = getResources().getDimensionPixelSize(R.dimen.notif_card_img_height);
        mCardImageMaxWidth = getResources().getDimensionPixelSize(R.dimen.notif_card_img_max_width);
        mCardImageMinWidth = getResources().getDimensionPixelSize(R.dimen.notif_card_img_min_width);
        mChannelLogoMaxWidth =
                getResources().getDimensionPixelSize(R.dimen.notif_ch_logo_max_width);
        mChannelLogoMaxHeight =
                getResources().getDimensionPixelSize(R.dimen.notif_ch_logo_max_height);
        mLogoPaddingStart =
                getResources().getDimensionPixelOffset(R.dimen.notif_ch_logo_padding_start);
        mLogoPaddingBottom =
                getResources().getDimensionPixelOffset(R.dimen.notif_ch_logo_padding_bottom);

        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        ApplicationSingletons appSingletons = TvApplication.getSingletons(this);
        mTvInputManagerHelper = appSingletons.getTvInputManagerHelper();
        mHandlerThread = new HandlerThread("tv notification");
        mHandlerThread.start();
        mHandler = new NotificationHandler(mHandlerThread.getLooper(), this);
        mHandler.sendEmptyMessage(MSG_INITIALIZE_RECOMMENDER);

        // Just called for early initialization.
        appSingletons.getChannelDataManager();
        appSingletons.getProgramDataManager();
        appSingletons.getMainActivityWrapper().addOnCurrentChannelChangeListener(this);
    }

    @UiThread
    @Override
    public void onCurrentChannelChange(@Nullable Channel channel) {
        if (DEBUG) Log.d(TAG, "onCurrentChannelChange");
        mPlayingChannel = channel;
        mHandler.removeMessages(MSG_SHOW_RECOMMENDATION);
        mHandler.sendEmptyMessage(MSG_SHOW_RECOMMENDATION);
    }

    private void handleInitializeRecommender() {
        mRecommender = new Recommender(NotificationService.this, NotificationService.this, true);
        if (TYPE_RANDOM_RECOMMENDATION.equals(mRecommendationType)) {
            mRecommender.registerEvaluator(new RandomEvaluator());
        } else if (TYPE_ROUTINE_WATCH_RECOMMENDATION.equals(mRecommendationType)) {
            mRecommender.registerEvaluator(new RoutineWatchEvaluator());
        } else if (TYPE_ROUTINE_WATCH_AND_FAVORITE_CHANNEL_RECOMMENDATION
                .equals(mRecommendationType)) {
            mRecommender.registerEvaluator(new FavoriteChannelEvaluator(), 0.5, 0.5);
            mRecommender.registerEvaluator(new RoutineWatchEvaluator(), 1.0, 1.0);
        } else {
            throw new IllegalStateException(
                    "Undefined recommendation type: " + mRecommendationType);
        }
    }

    private void handleShowRecommendation() {
        if (!mRecommender.isReady()) {
            mShowRecommendationAfterRecommenderReady = true;
        } else {
            showRecommendation();
        }
    }

    private void handleUpdateRecommendation(int notificationId, Channel channel) {
        if (mNotificationChannels[notificationId] == Channel.INVALID_ID || !sendNotification(
                channel.getId(), notificationId)) {
            changeRecommendation(notificationId);
        }
    }

    private void handleHideRecommendation() {
        if (!mRecommender.isReady()) {
            mShowRecommendationAfterRecommenderReady = false;
        } else {
            hideAllRecommendation();
        }
    }

    @Override
    public void onDestroy() {
        TvApplication.getSingletons(this).getMainActivityWrapper()
                .removeOnCurrentChannelChangeListener(this);
        if (mRecommender != null) {
            mRecommender.release();
            mRecommender = null;
        }
        if (mHandlerThread != null) {
            mHandlerThread.quit();
            mHandlerThread = null;
            mHandler = null;
        }
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DEBUG) Log.d(TAG, "onStartCommand");
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_SHOW_RECOMMENDATION.equals(action)) {
                mHandler.removeMessages(MSG_SHOW_RECOMMENDATION);
                mHandler.removeMessages(MSG_HIDE_RECOMMENDATION);
                mHandler.obtainMessage(MSG_SHOW_RECOMMENDATION).sendToTarget();
            } else if (ACTION_HIDE_RECOMMENDATION.equals(action)) {
                mHandler.removeMessages(MSG_SHOW_RECOMMENDATION);
                mHandler.removeMessages(MSG_UPDATE_RECOMMENDATION);
                mHandler.removeMessages(MSG_HIDE_RECOMMENDATION);
                mHandler.obtainMessage(MSG_HIDE_RECOMMENDATION).sendToTarget();
            }
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onRecommenderReady() {
        if (DEBUG) Log.d(TAG, "onRecommendationReady");
        if (mShowRecommendationAfterRecommenderReady) {
            mHandler.removeMessages(MSG_SHOW_RECOMMENDATION);
            mHandler.sendEmptyMessage(MSG_SHOW_RECOMMENDATION);
            mShowRecommendationAfterRecommenderReady = false;
        }
    }

    @Override
    public void onRecommendationChanged() {
        if (DEBUG) Log.d(TAG, "onRecommendationChanged");
        // Update recommendation on the handler thread.
        mHandler.removeMessages(MSG_SHOW_RECOMMENDATION);
        mHandler.sendEmptyMessage(MSG_SHOW_RECOMMENDATION);
    }

    private void showRecommendation() {
        if (DEBUG) Log.d(TAG, "showRecommendation");
        SparseLongArray notificationChannels = new SparseLongArray();
        for (int i = 0; i < NOTIFICATION_COUNT; ++i) {
            if (mNotificationChannels[i] == Channel.INVALID_ID) {
                continue;
            }
            notificationChannels.put(i, mNotificationChannels[i]);
        }
        List<Channel> channels = recommendChannels();
        for (Channel c : channels) {
            int index = notificationChannels.indexOfValue(c.getId());
            if (index >= 0) {
                notificationChannels.removeAt(index);
            }
        }
        // Cancel notification whose channels are not recommended anymore.
        if (notificationChannels.size() > 0) {
            for (int i = 0; i < notificationChannels.size(); ++i) {
                int notificationId = notificationChannels.keyAt(i);
                mNotificationManager.cancel(NOTIFY_TAG, notificationId);
                mNotificationChannels[notificationId] = Channel.INVALID_ID;
                --mCurrentNotificationCount;
            }
        }
        for (Channel c : channels) {
            if (mCurrentNotificationCount >= NOTIFICATION_COUNT) {
                break;
            }
            if (!isNotifiedChannel(c.getId())) {
                sendNotification(c.getId(), getAvailableNotificationId());
            }
        }
        if (mCurrentNotificationCount < NOTIFICATION_COUNT) {
            mHandler.sendEmptyMessageDelayed(MSG_SHOW_RECOMMENDATION, RECOMMENDATION_RETRY_TIME_MS);
        }
    }

    private void changeRecommendation(int notificationId) {
        if (DEBUG) Log.d(TAG, "changeRecommendation");
        List<Channel> channels = recommendChannels();
        if (mNotificationChannels[notificationId] != Channel.INVALID_ID) {
            mNotificationChannels[notificationId] = Channel.INVALID_ID;
            --mCurrentNotificationCount;
        }
        for (Channel c : channels) {
            if (!isNotifiedChannel(c.getId())) {
                if(sendNotification(c.getId(), notificationId)) {
                    return;
                }
            }
        }
        mNotificationManager.cancel(NOTIFY_TAG, notificationId);
    }

    private List<Channel> recommendChannels() {
        List channels = mRecommender.recommendChannels();
        if (channels.contains(mPlayingChannel)) {
            channels = new ArrayList<>(channels);
            channels.remove(mPlayingChannel);
        }
        return channels;
    }

    private void hideAllRecommendation() {
       for (int i = 0; i < NOTIFICATION_COUNT; ++i) {
           if (mNotificationChannels[i] != Channel.INVALID_ID) {
               mNotificationChannels[i] = Channel.INVALID_ID;
               mNotificationManager.cancel(NOTIFY_TAG, i);
           }
       }
       mCurrentNotificationCount = 0;
    }

    private boolean sendNotification(final long channelId, final int notificationId) {
        final ChannelRecord cr = mRecommender.getChannelRecord(channelId);
        if (cr == null) {
            return false;
        }
        final Channel channel = cr.getChannel();
        if (DEBUG) {
            Log.d(TAG, "sendNotification (channelName=" + channel.getDisplayName() + " notifyId="
                    + notificationId + ")");
        }

        // TODO: Move some checking logic into TvRecommendation.
        String inputId = Utils.getInputIdForChannel(this, channel.getId());
        if (TextUtils.isEmpty(inputId)) {
            return false;
        }
        TvInputInfo inputInfo = mTvInputManagerHelper.getTvInputInfo(inputId);
        if (inputInfo == null) {
            return false;
        }
        final String inputDisplayName = inputInfo.loadLabel(this).toString();

        final Program program = Utils.getCurrentProgram(this, channel.getId());
        if (program == null) {
            return false;
        }
        final long programDurationMs = program.getEndTimeUtcMillis()
                - program.getStartTimeUtcMillis();
        long programLeftTimsMs = program.getEndTimeUtcMillis() - System.currentTimeMillis();
        final int programProgress = (programDurationMs <= 0) ? -1
                : 100 - (int) (programLeftTimsMs * 100 / programDurationMs);

        // We recommend those programs that meet the condition only.
        if (programProgress >= RECOMMENDATION_THRESHOLD_PROGRESS
                && programLeftTimsMs <= RECOMMENDATION_THRESHOLD_LEFT_TIME_MS) {
            return false;
        }

        // We don't trust TIS to provide us with proper sized image
        ScaledBitmapInfo posterArtBitmapInfo = BitmapUtils.decodeSampledBitmapFromUriString(this,
                program.getPosterArtUri(), (int) mNotificationCardMaxWidth,
                (int) mNotificationCardHeight);
        if (posterArtBitmapInfo == null) {
            Log.e(TAG, "Failed to decode poster image for " + program.getPosterArtUri());
            return false;
        }
        final Bitmap posterArtBitmap = posterArtBitmapInfo.bitmap;

        channel.loadBitmap(this, Channel.LOAD_IMAGE_TYPE_CHANNEL_LOGO, mChannelLogoMaxWidth,
                mChannelLogoMaxHeight,
                createChannelLogoCallback(this, notificationId, inputDisplayName, channel, program,
                        posterArtBitmap));

        if (mNotificationChannels[notificationId] == Channel.INVALID_ID) {
            ++mCurrentNotificationCount;
        }
        mNotificationChannels[notificationId] = channel.getId();

        return true;
    }

    @NonNull
    private static ImageLoader.ImageLoaderCallback<NotificationService> createChannelLogoCallback(
            NotificationService service, final int notificationId, final String inputDisplayName,
            final Channel channel, final Program program, final Bitmap posterArtBitmap) {
        return new ImageLoader.ImageLoaderCallback<NotificationService>(service) {
            @Override
            public void onBitmapLoaded(NotificationService service, Bitmap channelLogo) {
                service.sendNotification(notificationId, channelLogo, channel, posterArtBitmap,
                        program, inputDisplayName);
            }
        };
    }

    private void sendNotification(int notificationId, Bitmap channelLogo, Channel channel,
            Bitmap posterArtBitmap, Program program, String inputDisplayName) {
        final long programDurationMs = program.getEndTimeUtcMillis() - program
                .getStartTimeUtcMillis();
        long programLeftTimsMs = program.getEndTimeUtcMillis() - System.currentTimeMillis();
        final int programProgress = (programDurationMs <= 0) ? -1
                : 100 - (int) (programLeftTimsMs * 100 / programDurationMs);
        Intent intent = new Intent(Intent.ACTION_VIEW, channel.getUri());
        intent.putExtra(TUNE_PARAMS_RECOMMENDATION_TYPE, mRecommendationType);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        final PendingIntent notificationIntent = PendingIntent.getActivity(this, 0, intent, 0);

        // This callback will run on the main thread.
        Bitmap largeIconBitmap = (channelLogo == null) ? posterArtBitmap
                : overlayChannelLogo(channelLogo, posterArtBitmap);
        String channelDisplayName = channel.getDisplayName();
        Notification notification = new Notification.Builder(this)
                .setContentIntent(notificationIntent)
                .setContentTitle(program.getTitle())
                .setContentText(TextUtils.isEmpty(channelDisplayName) ? channel.getDisplayNumber()
                        : channelDisplayName)
                .setContentInfo(channelDisplayName)
                .setAutoCancel(true).setLargeIcon(largeIconBitmap)
                .setSmallIcon(R.drawable.ic_launcher_s)
                .setCategory(Notification.CATEGORY_RECOMMENDATION)
                .setProgress((programProgress > 0) ? 100 : 0, programProgress, false)
                .setSortKey(mRecommender.getChannelSortKey(channel.getId()))
                .build();
        notification.color = getResources().getColor(R.color.recommendation_card_background, null);
        if (!TextUtils.isEmpty(program.getThumbnailUri())) {
            notification.extras
                    .putString(Notification.EXTRA_BACKGROUND_IMAGE_URI, program.getThumbnailUri());
        }
        mNotificationManager.notify(NOTIFY_TAG, notificationId, notification);
        Message msg = mHandler.obtainMessage(MSG_UPDATE_RECOMMENDATION, notificationId, 0, channel);
        mHandler.sendMessageDelayed(msg, programDurationMs / MAX_PROGRAM_UPDATE_COUNT);
    }

    private Bitmap overlayChannelLogo(Bitmap logo, Bitmap background) {
        Bitmap result = BitmapUtils.getScaledMutableBitmap(
                background, Integer.MAX_VALUE, mCardImageHeight);
        Bitmap scaledLogo = BitmapUtils.scaleBitmap(
                logo, mChannelLogoMaxWidth, mChannelLogoMaxHeight);
        Canvas canvas;
        try {
            canvas = new Canvas(result);
        } catch (Exception e) {
            Log.w(TAG, "Failed to create Canvas", e);
            return background;
        }
        canvas.drawBitmap(result, new Matrix(), null);
        Rect rect = new Rect();
        int startPadding;
        if (result.getWidth() < mCardImageMinWidth) {
            // TODO: check the positions.
            startPadding = mLogoPaddingStart;
            rect.bottom = result.getHeight() - mLogoPaddingBottom;
            rect.top = rect.bottom - scaledLogo.getHeight();
        } else if (result.getWidth() < mCardImageMaxWidth) {
            startPadding = mLogoPaddingStart;
            rect.bottom = result.getHeight() - mLogoPaddingBottom;
            rect.top = rect.bottom - scaledLogo.getHeight();
        } else {
            int marginStart = (result.getWidth() - mCardImageMaxWidth) / 2;
            startPadding = mLogoPaddingStart + marginStart;
            rect.bottom = result.getHeight() - mLogoPaddingBottom;
            rect.top = rect.bottom - scaledLogo.getHeight();
        }
        if (getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_LTR) {
            rect.left = startPadding;
            rect.right = startPadding + scaledLogo.getWidth();
        } else {
            rect.right = result.getWidth() - startPadding;
            rect.left = rect.right - scaledLogo.getWidth();
        }
        Paint paint = new Paint();
        paint.setAlpha(getResources().getInteger(R.integer.notif_card_ch_logo_alpha));
        canvas.drawBitmap(scaledLogo, null, rect, paint);
        return result;
    }

    private boolean isNotifiedChannel(long channelId) {
        for (int i = 0; i < NOTIFICATION_COUNT; ++i) {
            if (mNotificationChannels[i] == channelId) {
                return true;
            }
        }
        return false;
    }

    private int getAvailableNotificationId() {
        for (int i = 0; i < NOTIFICATION_COUNT; ++i) {
            if (mNotificationChannels[i] == Channel.INVALID_ID) {
                return i;
            }
        }
        return -1;
    }

    private static class NotificationHandler extends WeakHandler<NotificationService> {
        public NotificationHandler(@NonNull Looper looper, NotificationService ref) {
            super(looper, ref);
        }

        @Override
        public void handleMessage(Message msg, @NonNull NotificationService notificationService) {
            switch (msg.what) {
                case MSG_INITIALIZE_RECOMMENDER: {
                    notificationService.handleInitializeRecommender();
                    break;
                }
                case MSG_SHOW_RECOMMENDATION: {
                    notificationService.handleShowRecommendation();
                    break;
                }
                case MSG_UPDATE_RECOMMENDATION: {
                    int notificationId = msg.arg1;
                    Channel channel = ((Channel) msg.obj);
                    notificationService.handleUpdateRecommendation(notificationId, channel);
                    break;
                }
                case MSG_HIDE_RECOMMENDATION: {
                    notificationService.handleHideRecommendation();
                    break;
                }
                default: {
                    super.handleMessage(msg);
                }
            }
        }
    }
}
