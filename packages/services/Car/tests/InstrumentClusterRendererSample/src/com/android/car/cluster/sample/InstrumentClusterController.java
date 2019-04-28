/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.car.cluster.sample;

import static com.android.car.cluster.sample.DebugUtil.DEBUG;

import android.annotation.Nullable;
import android.app.Presentation;
import android.car.cluster.renderer.NavigationRenderer;
import android.car.navigation.CarNavigationInstrumentCluster;
import android.car.navigation.CarNavigationStatusManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.hardware.display.DisplayManager;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.telecom.Call;
import android.telecom.GatewayInfo;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.Display;

import com.android.car.cluster.sample.MediaStateMonitor.MediaStateListener;
import com.android.car.cluster.sample.cards.MediaCard;
import com.android.car.cluster.sample.cards.NavCard;

import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

/**
 * This class is responsible for subscribing to system events (such as call status, media status,
 * etc.) and updating accordingly UI component {@link ClusterView}.
 */
/*package*/ class InstrumentClusterController {

    private final static String TAG = DebugUtil.getTag(InstrumentClusterController.class);

    private final Context mContext;
    private final NavigationRenderer mNavigationRenderer;
    private final SparseArray<String> mDistanceUnitNames = new SparseArray<>();

    private ClusterView mClusterView;
    private MediaStateMonitor mMediaStateMonitor;
    private MediaStateListenerImpl mMediaStateListener;
    private ClusterInCallService mInCallService;
    private MessagingNotificationHandler mNotificationHandler;
    private StatusBarNotificationListener mNotificationListener;
    private RetriableServiceBinder mInCallServiceRetriableBinder;
    private RetriableServiceBinder mNotificationServiceRetriableBinder;

    InstrumentClusterController(Context context) {
        mContext = context;
        mNavigationRenderer = new NavigationRendererImpl(this);

        init();
    }

    private void init() {
        grantNotificationListenerPermissionsIfNecessary(mContext);

        final Display display = getInstrumentClusterDisplay(mContext);
        if (DEBUG) {
            Log.d(TAG, "Instrument cluster display: " + display);
        }
        if (display == null) {
            return;
        }

        initDistanceUnitNames(mContext);

        mClusterView = new ClusterView(mContext);
        Presentation presentation = new InstrumentClusterPresentation(mContext, display);
        presentation.setContentView(mClusterView);

        // To handle incoming messages
        mNotificationHandler = new MessagingNotificationHandler(mClusterView);

        mMediaStateListener = new MediaStateListenerImpl(this);
        mMediaStateMonitor = new MediaStateMonitor(mContext, mMediaStateListener);

        mInCallServiceRetriableBinder = new RetriableServiceBinder(
                new Handler(Looper.getMainLooper()),
                mContext,
                ClusterInCallService.class,
                ClusterInCallService.ACTION_LOCAL_BINDING,
                mInCallServiceConnection);
        mInCallServiceRetriableBinder.attemptToBind();

        mNotificationServiceRetriableBinder = new RetriableServiceBinder(
                new Handler(Looper.getMainLooper()),
                mContext,
                StatusBarNotificationListener.class,
                StatusBarNotificationListener.ACTION_LOCAL_BINDING,
                mNotificationListenerConnection);
        mNotificationServiceRetriableBinder.attemptToBind();

        // Show default card - weather
        mClusterView.enqueueCard(mClusterView.createWeatherCard());

        presentation.show();
    }

    NavigationRenderer getNavigationRenderer() {
        return mNavigationRenderer;
    }

    private final ServiceConnection mInCallServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            if (DEBUG) {
                Log.d(TAG, "onServiceConnected, name: " + name + ", binder: " + binder);
            }

            mInCallService = ((ClusterInCallService.LocalBinder) binder).getService();
            mInCallService.registerListener(mCallServiceListener);

            // The InCallServiceImpl could be bound when we already have some active calls, let's
            // notify UI about these calls.
            for (Call call : mInCallService.getCalls()) {
                mCallServiceListener.onStateChanged(call, call.getState());
            }
            mInCallServiceRetriableBinder = null;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (DEBUG) {
                Log.d(TAG, "onServiceDisconnected, name: " + name);
            }
        }
    };

    private final ServiceConnection mNotificationListenerConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            if (DEBUG) {
                Log.d(TAG, "onServiceConnected, name: " + name + ", binder: " + binder);
            }

            mNotificationListener = ((StatusBarNotificationListener.LocalBinder) binder)
                    .getService();
            mNotificationListener.setHandler(mNotificationHandler);

            mNotificationServiceRetriableBinder = null;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (DEBUG) {
                Log.d(TAG, "onServiceDisconnected, name: "+ name);
            }
        }
    };

    private final Call.Callback mCallServiceListener = new Call.Callback() {
        @Override
        public void onStateChanged(Call call, int state) {
            if (DEBUG) {
                Log.d(TAG, "onCallStateChanged, call: " + call + ", state: " + state);
            }

            runOnMain(() -> InstrumentClusterController.this.onCallStateChanged(call, state));
        }
    };

    private String extractPhoneNumber(Call call) {
        String number = "";
        Call.Details details = call.getDetails();
        if (details != null) {
            GatewayInfo gatewayInfo = details.getGatewayInfo();

            if (gatewayInfo != null) {
                number = gatewayInfo.getOriginalAddress().getSchemeSpecificPart();
            } else if (details.getHandle() != null) {
                number = details.getHandle().getSchemeSpecificPart();
            }
        } else {
            number = mContext.getResources().getString(R.string.unknown);
        }

        return number;
    }

    private void initDistanceUnitNames(Context context) {
        mDistanceUnitNames.put(CarNavigationStatusManager.DISTANCE_METERS,
                context.getString(R.string.nav_distance_units_meters));
        mDistanceUnitNames.put(CarNavigationStatusManager.DISTANCE_KILOMETERS,
                context.getString(R.string.nav_distance_units_kilometers));
        mDistanceUnitNames.put(CarNavigationStatusManager.DISTANCE_FEET,
                context.getString(R.string.nav_distance_units_ft));
        mDistanceUnitNames.put(CarNavigationStatusManager.DISTANCE_MILES,
                context.getString(R.string.nav_distance_units_miles));
        mDistanceUnitNames.put(CarNavigationStatusManager.DISTANCE_YARDS,
                context.getString(R.string.nav_distance_units_yards));
    }

    private void onCallStateChanged(Call call, int state) {
        if (DEBUG) {
            Log.d(TAG, "onCallStateChanged, call: " + call + ", state: " + state);
        }

        switch (state) {
            case Call.STATE_ACTIVE: {
                Call.Details details = call.getDetails();
                if (details != null) {
                    long duration = System.currentTimeMillis() - details.getConnectTimeMillis();
                    mClusterView.handleCallConnected(SystemClock.elapsedRealtime() - duration);
                }
            } break;
            case Call.STATE_CONNECTING: {

            } break;
            case Call.STATE_DISCONNECTING: {
                mClusterView.handleCallDisconnected();
            } break;
            case Call.STATE_DIALING: {
                String phoneNumber = extractPhoneNumber(call);
                String displayName = TelecomUtils.getDisplayName(mContext, phoneNumber);
                Bitmap image = TelecomUtils
                        .getContactPhotoFromNumber(mContext.getContentResolver(), phoneNumber);
                mClusterView.handleDialingCall(image, displayName);
            } break;
            case Call.STATE_DISCONNECTED: {
                mClusterView.handleCallDisconnected();
            } break;
            case Call.STATE_HOLDING:
                break;
            case Call.STATE_NEW:
                break;
            case Call.STATE_RINGING: {
                String phoneNumber = extractPhoneNumber(call);
                String displayName = TelecomUtils.getDisplayName(mContext, phoneNumber);
                Bitmap image = TelecomUtils
                        .getContactPhotoFromNumber(mContext.getContentResolver(), phoneNumber);
                if (image != null) {
                    if (DEBUG) {
                        Log.d(TAG, "Incoming call, contact image size: " + image.getWidth()
                                + "x" + image.getHeight());
                    }
                }
                mClusterView.handleIncomingCall(image, displayName);
            } break;
            default:
                Log.w(TAG, "Unexpected call state: " + state + ", call : " + call);
        }
    }

    private static void grantNotificationListenerPermissionsIfNecessary(Context context) {
        ComponentName componentName = new ComponentName(context,
                StatusBarNotificationListener.class);
        String componentFlatten = componentName.flattenToString();

        ContentResolver resolver = context.getContentResolver();
        String grantedComponents = Settings.Secure.getString(resolver,
                Settings.Secure.ENABLED_NOTIFICATION_LISTENERS);

        if (grantedComponents != null) {
            String[] allowed = grantedComponents.split(":");
            for (String s : allowed) {
                if (s.equals(componentFlatten)) {
                    if (DEBUG) {
                        Log.d(TAG, "Notification listener permission granted.");
                    }
                    return;  // Permission already granted.
                }
            }
        }

        if (DEBUG) {
            Log.d(TAG, "Granting notification listener permission.");
        }
        Settings.Secure.putString(resolver,
                Settings.Secure.ENABLED_NOTIFICATION_LISTENERS,
                grantedComponents + ":" + componentFlatten);

    }

    /* package */ void onDestroy() {
        if (mMediaStateMonitor != null) {
            mMediaStateMonitor.release();
            mMediaStateMonitor = null;
        }
        if (mMediaStateListener != null) {
            mMediaStateListener.release();
            mMediaStateListener = null;
        }
        if (mInCallService != null) {
            mContext.unbindService(mInCallServiceConnection);
            mInCallService = null;
        }
        if (mNotificationListener != null) {
            mContext.unbindService(mNotificationListenerConnection);
            mNotificationListener = null;
        }
        if (mInCallServiceRetriableBinder != null) {
            mInCallServiceRetriableBinder.release();
            mInCallServiceRetriableBinder = null;
        }
        if (mNotificationServiceRetriableBinder != null) {
            mNotificationServiceRetriableBinder.release();
            mNotificationServiceRetriableBinder = null;
        }
    }

    private static Display getInstrumentClusterDisplay(Context context) {
        DisplayManager displayManager = context.getSystemService(DisplayManager.class);
        Display[] displays = displayManager.getDisplays();

        if (DEBUG) {
            Log.d(TAG, "There are currently " + displays.length + " displays connected.");
            for (Display display : displays) {
                Log.d(TAG, "  " + display);
            }
        }

        if (displays.length > 1) {
            // TODO: Put this into settings?
            return displays[displays.length - 1];
        }
        return null;
    }

    private static void runOnMain(Runnable runnable) {
        new Handler(Looper.getMainLooper()).post(runnable);
    }

    private static class MediaStateListenerImpl implements MediaStateListener {
        private final Timer mTimer = new Timer("ClusterMediaProgress");
        private final ClusterView mClusterView;

        private MediaData mCurrentMedia;
        private MediaAppInfo mMediaAppInfo;
        private MediaCard mCard;
        private PlaybackState mPlaybackState;
        private TimerTask mTimerTask;

        MediaStateListenerImpl(InstrumentClusterController renderer) {
            mClusterView = renderer.mClusterView;
        }

        void release() {
            if (mTimerTask != null) {
                mTimerTask.cancel();
                mTimerTask = null;
            }
        }

        @Override
        public void onPlaybackStateChanged(final PlaybackState playbackState) {
            if (DEBUG) {
                Log.d(TAG, "onPlaybackStateChanged, playbackState: " + playbackState);
            }

            if (mTimerTask != null) {
                mTimerTask.cancel();
                mTimerTask = null;
            }

            if (playbackState != null) {
                if ((playbackState.getState() == PlaybackState.STATE_PLAYING
                            || playbackState.getState() == PlaybackState.STATE_BUFFERING)) {
                    mPlaybackState = playbackState;

                    if (mCurrentMedia != null) {
                        showMediaCardIfNecessary(mCurrentMedia);

                        if (mCurrentMedia.duration > 0) {
                            startTrackProgressTimer();
                        }
                    }
                } else if (playbackState.getState() == PlaybackState.STATE_STOPPED
                        || playbackState.getState() == PlaybackState.STATE_ERROR
                        || playbackState.getState() == PlaybackState.STATE_NONE) {
                    hideMediaCard();
                }
            } else {
                hideMediaCard();
            }

        }

        private void startTrackProgressTimer() {
            mTimerTask = new TimerTask() {
                @Override
                public void run() {
                    runOnMain(() -> {
                        if (mPlaybackState == null || mCard == null) {
                            return;
                        }
                        long trackStarted = mPlaybackState.getLastPositionUpdateTime()
                                - mPlaybackState.getPosition();
                        long trackDuration = mCurrentMedia == null ? 0 : mCurrentMedia.duration;

                        long currentTime = SystemClock.elapsedRealtime();
                        long progressMs = (currentTime - trackStarted);
                        if (trackDuration > 0) {
                            mCard.setProgress((int)((progressMs * 100) / trackDuration));
                        }
                    });
                }
            };

            mTimer.scheduleAtFixedRate(mTimerTask, 0, 1000);
        }


        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            if (DEBUG) {
                Log.d(TAG, "onMetadataChanged: " + metadata);
            }
            MediaData data = MediaData.createFromMetadata(metadata);
            if (data == null) {
                hideMediaCard();
            }
            mCurrentMedia = data;
        }

        private void hideMediaCard() {
            if (DEBUG) {
                Log.d(TAG, "hideMediaCard");
            }

            if (mCard != null) {
                mClusterView.removeCard(mCard);
                mCard = null;
            }

            // Remove all existing media cards if any.
            MediaCard mediaCard;
            do {
                mediaCard = mClusterView.getCardOrNull(MediaCard.class);
                if (mediaCard != null) {
                    mClusterView.removeCard(mediaCard);
                }
            } while (mediaCard != null);
        }

        private void showMediaCardIfNecessary(MediaData data) {
            if (!needToCreateMediaCard(data)) {
                return;
            }

            int accentColor = mMediaAppInfo == null
                    ? Color.GRAY : mMediaAppInfo.getMediaClientAccentColor();

            mCard = mClusterView.createMediaCard(
                    data.albumCover, data.title, data.subtitle, accentColor);
            if (data.duration <= 0) {
                mCard.setProgress(100); // unknown position
            } else {
                mCard.setProgress(0);
            }
            mClusterView.enqueueCard(mCard);
        }

        private boolean needToCreateMediaCard(MediaData data) {
            return (mCard == null)
                    || !Objects.equals(mCard.getTitle(), data.title)
                    || !Objects.equals(mCard.getSubtitle(), data.subtitle);
        }

        @Override
        public void onMediaAppChanged(MediaAppInfo mediaAppInfo) {
            mMediaAppInfo = mediaAppInfo;
        }

        private static class MediaData {
            final Bitmap albumCover;
            final String subtitle;
            final String title;
            final long duration;

            private MediaData(MediaMetadata metadata) {
                MediaDescription mediaDescription = metadata.getDescription();
                title = charSequenceToString(mediaDescription.getTitle());
                subtitle = charSequenceToString(mediaDescription.getSubtitle());
                albumCover = mediaDescription.getIconBitmap();
                duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
            }

            static MediaData createFromMetadata(MediaMetadata metadata) {
                return  metadata == null ? null : new MediaData(metadata);
            }

            private static String charSequenceToString(@Nullable CharSequence cs) {
                return cs == null ? null : String.valueOf(cs);
            }

            @Override
            public String toString() {
                return "MediaData{" +
                        "albumCover=" + albumCover +
                        ", subtitle='" + subtitle + '\'' +
                        ", title='" + title + '\'' +
                        ", duration=" + duration +
                        '}';
            }
        }
    }

    private static class NavigationRendererImpl extends NavigationRenderer {

        private final InstrumentClusterController mController;

        private ClusterView mClusterView;
        private Resources mResources;

        private NavCard mNavCard;

        NavigationRendererImpl(InstrumentClusterController controller) {
            mController = controller;
        }

        @Override
        public CarNavigationInstrumentCluster getNavigationProperties() {
            if (DEBUG) {
                Log.d(TAG, "getNavigationProperties");
            }
            return CarNavigationInstrumentCluster.createCustomImageCluster(
                    1000, /* 1 Hz*/
                    64,   /* image width */
                    64,   /* image height */
                    32);  /* color depth */
        }

        @Override
        public void onStartNavigation() {
            if (DEBUG) {
                Log.d(TAG, "onStartNavigation");
            }
            mClusterView = mController.mClusterView;
            mResources = mController.mContext.getResources();
            mNavCard = mClusterView.createNavCard();
        }

        @Override
        public void onStopNavigation() {
            if (DEBUG) {
                Log.d(TAG, "onStopNavigation");
            }

            if (mNavCard != null) {
                mNavCard.removeGracefully();
                mNavCard = null;
            }
        }

        @Override
        public void onNextTurnChanged(int event, CharSequence eventName, int turnAngle,
                int turnNumber, Bitmap image, int turnSide) {
            if (DEBUG) {
                Log.d(TAG, "onNextTurnChanged, eventName: " + eventName + ", image: " + image +
                        (image == null ? "" : ", size: "
                                + image.getWidth() + "x" + image.getHeight()));
            }
            mNavCard.setManeuverImage(BitmapUtils.generateNavManeuverIcon(
                    (int) mResources.getDimension(R.dimen.card_icon_size),
                    mResources.getColor(R.color.maps_background, null),
                    image));
            mNavCard.setStreet(eventName);
            if (!mClusterView.cardExists(mNavCard)) {
                mClusterView.enqueueCard(mNavCard);
            }
        }

        @Override
        public void onNextTurnDistanceChanged(int meters, int timeSeconds,
                int displayDistanceMillis, int distanceUnit) {
            if (DEBUG) {
                Log.d(TAG, "onNextTurnDistanceChanged, distanceMeters: " + meters
                        + ", timeSeconds: " + timeSeconds
                        + ", displayDistanceMillis: " + displayDistanceMillis
                        + ", DistanceUnit: " + distanceUnit);
            }

            int remainder = displayDistanceMillis % 1000;
            String decimalPart = (remainder != 0)
                    ? String.format("%c%d",
                                    DecimalFormatSymbols.getInstance().getDecimalSeparator(),
                                    remainder)
                    : "";

            String distanceToDisplay = (displayDistanceMillis / 1000) + decimalPart;
            String unitsToDisplay = mController.mDistanceUnitNames.get(distanceUnit);

            mNavCard.setDistanceToNextManeuver(distanceToDisplay, unitsToDisplay);
        }
    }

    /**
     * Services might not be ready for binding. This class will retry binding after short interval
     * if previous binding failed.
     */
    private static class RetriableServiceBinder {
        private static final long RETRY_INTERVAL_MS = 500;
        private static final long MAX_RETRY = 30;

        private Handler mHandler;
        private final Context mContext;
        private final Intent mIntent;
        private final ServiceConnection mConnection;

        private long mAttemptsLeft = MAX_RETRY;

        private final Runnable mBindRunnable = () -> attemptToBind();

        RetriableServiceBinder(Handler handler, Context context, Class<?> cls, String action,
                ServiceConnection connection) {
            mHandler = handler;
            mContext = context;
            mIntent = new Intent(mContext, cls);
            mIntent.setAction(action);
            mConnection = connection;
        }

        void release() {
            mHandler.removeCallbacks(mBindRunnable);
        }

        void attemptToBind() {
            boolean bound = mContext.bindServiceAsUser(mIntent,
                    mConnection, Context.BIND_AUTO_CREATE, UserHandle.CURRENT_OR_SELF);

            if (!bound && --mAttemptsLeft > 0) {
                mHandler.postDelayed(mBindRunnable, RETRY_INTERVAL_MS);
            } else if (!bound) {
                Log.e(TAG, "Gave up to bind to a service: " + mIntent.getComponent() + " after "
                        + MAX_RETRY + " retries.");
            }
        }
    }
}
