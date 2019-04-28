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
package com.android.car.media;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.DrawableRes;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import com.android.car.apps.common.BitmapDownloader;
import com.android.car.apps.common.BitmapWorkerOptions;
import com.android.car.apps.common.ColorChecker;
import com.android.car.apps.common.util.Assert;
import com.android.car.media.util.widgets.PlayPauseStopImageView;

import java.util.List;
import java.util.Objects;

/**
 * Fragment that displays the media playback UI.
 */
public class MediaPlaybackFragment extends Fragment implements MediaPlaybackModel.Listener {
    private static final String TAG = "MediaPlayback";

    /**
     * The preferred ordering for bitmap to fetch. The metadata at lower indexes are preferred to
     * those at higher indexes.
     */
    private static final String[] PREFERRED_BITMAP_TYPE_ORDER = {
            MediaMetadata.METADATA_KEY_ALBUM_ART,
            MediaMetadata.METADATA_KEY_ART,
            MediaMetadata.METADATA_KEY_DISPLAY_ICON
    };

    /**
     * The preferred ordering for metadata URIs to fetch. The metadata at lower indexes are
     * preferred to those at higher indexes.
     */
    private static final String[] PREFERRED_URI_ORDER = {
            MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
            MediaMetadata.METADATA_KEY_ART_URI,
            MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI
    };

    // The different types of Views that are contained within this Fragment.
    private static final int NO_CONTENT_VIEW = 0;
    private static final int PLAYBACK_CONTROLS_VIEW = 1;
    private static final int LOADING_VIEW = 2;
    @IntDef({NO_CONTENT_VIEW, PLAYBACK_CONTROLS_VIEW, LOADING_VIEW})
    private @interface ViewType{}

    /**
     * The amount of time between seek bar updates.
     */
    private static final long SEEK_BAR_UPDATE_TIME_INTERVAL_MS = 500;

    /**
     * The delay time before automatically closing the overflow controls view.
     */
    private static final long DELAY_CLOSE_OVERFLOW_MS = 3500;

    /**
     * Delay before showing any content. When the media app cold starts, it usually takes a
     * moment to load the last played song from database. So wait for three seconds before showing
     * the no content view rather than showing it and immediately switching to the playback view
     * when the metadata loads.
     */
    private static final long DELAY_SHOW_NO_CONTENT_VIEW_MS = 3000;
    private static final long FEEDBACK_MESSAGE_DISPLAY_TIME_MS = 6000;
    private static final int MEDIA_SCRIM_FADE_DURATION_MS = 400;
    private static final int OVERFLOW_MENU_FADE_DURATION_MS = 250;
    private static final int NUM_OF_CUSTOM_ACTION_BUTTONS = 4;

    // The default width and height for an image. These are used if the mAlbumArtView has not laid
    // out by the time a Bitmap needs to be created to fit in it.
    private static final int DEFAULT_ALBUM_ART_WIDTH = 800;
    private static final int DEFAULT_ALBUM_ART_HEIGHT = 400;

    private MediaPlaybackModel mMediaPlaybackModel;
    private final Handler mHandler = new Handler();

    private View mScrimView;
    private float mDefaultScrimAlpha;
    private float mDarkenedScrimAlpha;

    private CrossfadeImageView mAlbumArtView;

    private TextView mTitleView;
    private TextView mArtistView;

    private ImageButton mPrevButton;
    private PlayPauseStopImageView mPlayPauseStopButton;
    private ImageButton mNextButton;
    private ImageButton mPlayQueueButton;

    private View mMusicPanel;
    private View mControlsView;
    private View mOverflowView;
    private ImageButton mOverflowOnButton;
    private ImageButton mOverflowOffButton;
    private boolean mIsOverflowVisible;
    private final ImageButton[] mCustomActionButtons =
            new ImageButton[NUM_OF_CUSTOM_ACTION_BUTTONS];

    private SeekBar mSeekBar;
    private ProgressBar mSpinner;
    private long mStartProgress;
    private long mStartTime;
    private MediaDescription mCurrentTrack;
    private boolean mShowingMessage;

    private View mInitialNoContentView;
    private View mMetadata;
    private View mMusicErrorIcon;
    private TextView mTapToSelectText;
    private ProgressBar mAppConnectingSpinner;

    private boolean mDelayedResetTitleInProgress;
    private int mAlbumArtWidth = DEFAULT_ALBUM_ART_WIDTH;
    private int mAlbumArtHeight = DEFAULT_ALBUM_ART_HEIGHT;
    private int mShowTitleDelayMs;

    private TelephonyManager mTelephonyManager;
    private boolean mInCall;

    private BitmapDownloader mDownloader;
    private boolean mReturnFromOnStop;
    @ViewType private int mCurrentView;
    private PlayQueueRevealer mPlayQueueRevealer;

    /**
     * An interface that is responsible for displaying a list of the items in the user's currently
     * playing queue.
     */
    interface PlayQueueRevealer {
        void showPlayQueue();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context context = getContext();
        mMediaPlaybackModel = new MediaPlaybackModel(context, null /* browserExtras */);
        mMediaPlaybackModel.addListener(this);
        mTelephonyManager =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);

        Resources res = context.getResources();
        mShowTitleDelayMs = res.getInteger(R.integer.new_album_art_fade_in_duration);
        mDefaultScrimAlpha = res.getFloat(R.dimen.media_scrim_alpha);
        mDarkenedScrimAlpha = res.getFloat(R.dimen.media_scrim_darkened_alpha);
    }

    /**
     * Sets the object that is responsible for displaying the current list of items in the user's
     * play queue.
     */
    void setPlayQueueRevealer(PlayQueueRevealer revealer) {
        mPlayQueueRevealer = revealer;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
        mMediaPlaybackModel = null;
        // Calling this with null will clear queue of callbacks and message.
        mHandler.removeCallbacksAndMessages(null);
        mDelayedResetTitleInProgress = false;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, final ViewGroup container,
            Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.now_playing_screen, container, false);
        mScrimView = v.findViewById(R.id.scrim);
        mAlbumArtView = v.findViewById(R.id.album_art);
        mAlbumArtView.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        mAlbumArtWidth = mAlbumArtView.getWidth();
                        mAlbumArtHeight = mAlbumArtView.getHeight();
                        mAlbumArtView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    }
                });

        setBackgroundColor(getContext().getColor(R.color.music_default_artwork));

        mTitleView = v.findViewById(R.id.title);
        mArtistView = v.findViewById(R.id.artist);
        mSeekBar = v.findViewById(R.id.seek_bar);
        mSeekBar.setOnTouchListener((v1, event) -> {
            // Eat up touch events from users as we set progress programmatically only.
            return true;
        });
        mControlsView = v.findViewById(R.id.controls);
        mOverflowView = v.findViewById(R.id.overflow_items);
        mMusicPanel = v.findViewById(R.id.music_panel);
        mSpinner = v.findViewById(R.id.spinner);
        mInitialNoContentView = v.findViewById(R.id.initial_view);
        mMetadata = v.findViewById(R.id.metadata);

        mMusicErrorIcon = v.findViewById(R.id.error_icon);
        mTapToSelectText = v.findViewById(R.id.tap_to_select_item);
        mAppConnectingSpinner = v.findViewById(R.id.loading_spinner);

        mCustomActionButtons[0] = v.findViewById(R.id.custom_action_1);
        mCustomActionButtons[1] = v.findViewById(R.id.custom_action_2);
        mCustomActionButtons[2] = v.findViewById(R.id.custom_action_3);
        mCustomActionButtons[3] = v.findViewById(R.id.custom_action_4);

        setupMediaButtons(v);

        return v;
    }

    private void setupMediaButtons(View parentView) {
        mPlayQueueButton = parentView.findViewById(R.id.play_queue);
        mPrevButton = parentView.findViewById(R.id.prev);
        mNextButton = parentView.findViewById(R.id.next);
        mPlayPauseStopButton = parentView.findViewById(R.id.play_pause);
        mOverflowOnButton = parentView.findViewById(R.id.overflow_on);
        mOverflowOffButton = parentView.findViewById(R.id.overflow_off);
        setActionDrawable(mOverflowOffButton, R.drawable.ic_overflow_activated, getResources());

        mPlayQueueButton.setOnClickListener(mControlsClickListener);
        mPrevButton.setOnClickListener(mControlsClickListener);
        mNextButton.setOnClickListener(mControlsClickListener);
        mPlayPauseStopButton.setOnClickListener(mControlsClickListener);
        mOverflowOnButton.setOnClickListener(mControlsClickListener);
        mOverflowOffButton.setOnClickListener(mControlsClickListener);
    }

    @Override
    public void onPause() {
        super.onPause();
        mMediaPlaybackModel.stop();
        mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
    }

    @Override
    public void onStop() {
        super.onStop();
        // When switch apps, onStop() will be called. Mark it and don't show fade in/out title and
        // background animations when come back.
        mReturnFromOnStop = true;
    }

    @Override
    public void onResume() {
        super.onResume();
        mMediaPlaybackModel.start();
        // Note: at registration, TelephonyManager will invoke the callback with the current state.
        mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
    }

    @Override
    public void onMediaAppChanged(@Nullable ComponentName currentName,
            @Nullable ComponentName newName) {
        Assert.isMainThread();
        resetTitle();
        if (Objects.equals(currentName, newName)) {
            return;
        }
        int accentColor = mMediaPlaybackModel.getAccentColor();
        mPlayPauseStopButton.setPrimaryActionColor(accentColor);
        mSeekBar.getProgressDrawable().setColorFilter(accentColor, PorterDuff.Mode.SRC_IN);
        int overflowViewColor = mMediaPlaybackModel.getPrimaryColorDark();
        mOverflowView.getBackground().setColorFilter(overflowViewColor, PorterDuff.Mode.SRC_IN);
        // Tint the overflow actions light or dark depending on contrast.
        int overflowTintColor = ColorChecker.getTintColor(getContext(), overflowViewColor);
        for (ImageView v : mCustomActionButtons) {
            v.setColorFilter(overflowTintColor, PorterDuff.Mode.SRC_IN);
        }
        mOverflowOffButton.setColorFilter(overflowTintColor, PorterDuff.Mode.SRC_IN);
        ColorStateList colorStateList = ColorStateList.valueOf(accentColor);
        mSpinner.setIndeterminateTintList(colorStateList);
        mAppConnectingSpinner.setIndeterminateTintList(ColorStateList.valueOf(accentColor));
        showLoadingView();
        closeOverflowMenu();
    }

    @Override
    public void onMediaAppStatusMessageChanged(@Nullable String message) {
        Assert.isMainThread();
        if (message == null) {
            resetTitle();
        } else {
            showMessage(message);
        }
    }

    @Override
    public void onMediaConnected() {
        Assert.isMainThread();
        onMetadataChanged(mMediaPlaybackModel.getMetadata());
        onQueueChanged(mMediaPlaybackModel.getQueue());
        onPlaybackStateChanged(mMediaPlaybackModel.getPlaybackState());
        mReturnFromOnStop = false;
    }

    @Override
    public void onMediaConnectionSuspended() {
        Assert.isMainThread();
        mReturnFromOnStop = false;
    }

    @Override
    public void onMediaConnectionFailed(CharSequence failedClientName) {
        Assert.isMainThread();
        showInitialNoContentView(getString(R.string.cannot_connect_to_app, failedClientName),
                true /* isError */);
        mReturnFromOnStop = false;
    }

    @Override
    @TargetApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    public void onPlaybackStateChanged(@Nullable PlaybackState state) {
        Assert.isMainThread();
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "onPlaybackStateChanged; state: "
                    + (state == null ? "<< NULL >>" : state.toString()));
        }

        if (state == null) {
            return;
        }

        if (state.getState() == PlaybackState.STATE_ERROR) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "ERROR: " + state.getErrorMessage());
            }
            String message = TextUtils.isEmpty(state.getErrorMessage())
                    ? getString(R.string.unknown_error)
                    : state.getErrorMessage().toString();
            showInitialNoContentView(message, true /* isError */);
            return;
        }

        mStartProgress = state.getPosition();
        mStartTime = System.currentTimeMillis();
        mSeekBar.setProgress((int) mStartProgress);
        if (state.getState() == PlaybackState.STATE_PLAYING) {
            mHandler.post(mSeekBarRunnable);
        } else {
            mHandler.removeCallbacks(mSeekBarRunnable);
        }
        if (!mInCall) {
            int playbackState = state.getState();
            mPlayPauseStopButton.setPlayState(playbackState);
            // Due to the action of PlaybackState will be changed when the state of PlaybackState is
            // changed, we set mode every time onPlaybackStateChanged() is called.
            if (playbackState == PlaybackState.STATE_PLAYING ||
                    playbackState == PlaybackState.STATE_BUFFERING) {
                mPlayPauseStopButton.setMode(((state.getActions() & PlaybackState.ACTION_STOP) != 0)
                        ? PlayPauseStopImageView.MODE_STOP : PlayPauseStopImageView.MODE_PAUSE);
            } else {
                mPlayPauseStopButton.setMode(PlayPauseStopImageView.MODE_PAUSE);
            }
            mPlayPauseStopButton.refreshDrawableState();
        }
        if (state.getState() == PlaybackState.STATE_BUFFERING) {
            mSpinner.setVisibility(View.VISIBLE);
        } else {
            mSpinner.setVisibility(View.GONE);
        }

        updateActions(state.getActions(), state.getCustomActions());

        if (mMediaPlaybackModel.getMetadata() == null) {
            return;
        }
        showMediaPlaybackControlsView();
    }

    @Override
    public void onMetadataChanged(@Nullable MediaMetadata metadata) {
        Assert.isMainThread();
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "onMetadataChanged; description: "
                    + (metadata == null ? "<< NULL >>" : metadata.getDescription().toString()));
        }
        if (metadata == null) {
            mHandler.postDelayed(mShowNoContentViewRunnable, DELAY_SHOW_NO_CONTENT_VIEW_MS);
            return;
        } else {
            mHandler.removeCallbacks(mShowNoContentViewRunnable);
        }

        showMediaPlaybackControlsView();
        mCurrentTrack = metadata.getDescription();
        Bitmap icon = getMetadataBitmap(metadata);
        if (!mShowingMessage) {
            mHandler.removeCallbacks(mSetTitleRunnable);
            // Show the title when the new album art starts to fade in, but don't need to show
            // the fade in animation when come back from switching apps.
            mHandler.postDelayed(mSetTitleRunnable,
                    icon == null || mReturnFromOnStop ? 0 : mShowTitleDelayMs);
        }
        Uri iconUri = getMetadataIconUri(metadata);
        if (icon != null) {
            Bitmap scaledIcon = cropAlbumArt(icon);
            if (scaledIcon != icon && !icon.isRecycled()) {
                icon.recycle();
            }
            // Fade out the old background and then fade in the new one when the new album art
            // starts, but don't need to show the fade out and fade in animations when come back
            // from switching apps.
            setBackgroundBitmap(scaledIcon, !mReturnFromOnStop /* showAnimation */);
        } else if (iconUri != null) {
            if (mDownloader == null) {
                mDownloader = new BitmapDownloader(getContext());
            }
            final int flags = BitmapWorkerOptions.CACHE_FLAG_DISK_DISABLED
                    | BitmapWorkerOptions.CACHE_FLAG_MEM_DISABLED;
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "Album art size " + mAlbumArtWidth + "x" + mAlbumArtHeight);
            }

            BitmapWorkerOptions bitmapWorkerOptions = new BitmapWorkerOptions.Builder(getContext())
                    .resource(iconUri)
                    .height(mAlbumArtHeight)
                    .width(mAlbumArtWidth)
                    .cacheFlag(flags)
                    .build();

            mDownloader.getBitmap(bitmapWorkerOptions,
                    new BitmapDownloader.BitmapCallback() {
                        @Override
                        public void onBitmapRetrieved(Bitmap bitmap) {
                            setBackgroundBitmap(bitmap, true /* showAnimation */);
                        }
                    });
        } else {
            setBackgroundColor(mMediaPlaybackModel.getPrimaryColorDark());
        }

        mSeekBar.setMax((int) metadata.getLong(MediaMetadata.METADATA_KEY_DURATION));
    }

    @Override
    public void onQueueChanged(List<MediaSession.QueueItem> queue) {
        Assert.isMainThread();
        mPlayQueueButton.setVisibility(queue.isEmpty() ? View.INVISIBLE : View.VISIBLE);
    }

    @Override
    public void onSessionDestroyed(CharSequence destroyedMediaClientName) {
        Assert.isMainThread();
        mHandler.removeCallbacks(mSeekBarRunnable);
        showInitialNoContentView(
                getString(R.string.cannot_connect_to_app, destroyedMediaClientName), true);
    }

    /**
     * Sets the given {@link Bitmap} as the background of this playback fragment. If
     *
     * @param showAnimation {@code true} if the bitmap should be faded in.
     */
    private void setBackgroundBitmap(Bitmap bitmap, boolean showAnimation) {
        mAlbumArtView.setImageBitmap(bitmap, showAnimation);
    }

    /**
     * Sets the given color as the background color of the view.
     */
    private void setBackgroundColor(int color) {
        mAlbumArtView.setBackgroundColor(color);
    }

    /**
     * Darkens the scrim's alpha level.
     */
    private void darkenScrim() {
        mScrimView.animate()
                .alpha(mDarkenedScrimAlpha)
                .setDuration(MEDIA_SCRIM_FADE_DURATION_MS);
    }

    /**
     * Sets whether or not the scrim is visible. The scrim is a semi-transparent View that darkens
     * an album art so that does not overpower any text that is over it.
     */
    private void setScrimVisible(boolean visible) {
        float alpha = visible ? mDefaultScrimAlpha : 0.f;
        mScrimView.animate()
                .alpha(alpha)
                .setDuration(MEDIA_SCRIM_FADE_DURATION_MS);
    }

    /**
     * Displays the given message to the user. The message is displayed in the field that
     * normally displays the title of the currently playing media item.
     */
    private void showMessage(String message) {
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "showMessage(); message: " + message);
        }

        mHandler.removeCallbacks(mResetTitleRunnable);
        darkenScrim();
        mTitleView.setText(message);
        mArtistView.setVisibility(View.GONE);
        mShowingMessage = true;
    }

    /**
     * Checks if the user is on the overflow view of the media controls. If they are, then this
     * view is closed, and the user is switched back to the usual controls (usually the play
     * controls).
     */
    void closeOverflowMenu() {
        if (mIsOverflowVisible) {
            mHandler.removeCallbacks(mCloseOverflowRunnable);
            setOverflowMenuVisible(false);
        }
    }

    /**
     * Hides the view for overflow controls over the regular media controls. The media controls will
     * fade in over the overflow view.
     */
    private void hideOverflowView() {
        mOverflowView.animate()
                .alpha(0f)
                .setDuration(OVERFLOW_MENU_FADE_DURATION_MS)
                .withStartAction(() -> mControlsView.setVisibility(View.VISIBLE))
                .withEndAction(() -> mOverflowView.setVisibility(View.GONE));
    }

    /**
     * Displays the view for overflow controls over the regular media controls. The overflow view
     * fades in over the media controls.
     */
    private void showOverflowView() {
        mOverflowView.animate()
                .alpha(1f)
                .setDuration(OVERFLOW_MENU_FADE_DURATION_MS)
                .withStartAction(() -> mOverflowView.setVisibility(View.VISIBLE))
                .withEndAction(() -> mControlsView.setVisibility(View.GONE));
    }

    private void setOverflowMenuVisible(boolean isVisible) {
        if (mIsOverflowVisible == isVisible) {
            return;
        }
        mIsOverflowVisible = isVisible;
        if (mIsOverflowVisible) {
            showOverflowView();
            int tint = ColorChecker.getTintColor(getContext(),
                    mMediaPlaybackModel.getPrimaryColorDark());
            mSeekBar.getProgressDrawable().setColorFilter(tint, PorterDuff.Mode.SRC_IN);
        } else {
            hideOverflowView();
            mSeekBar.getProgressDrawable().setColorFilter(
                    mMediaPlaybackModel.getAccentColor(), PorterDuff.Mode.SRC_IN);
        }
    }

    /**
     * For a given drawer slot, set the proper action of the slot's button,
     * based on the slot being reserved and the corresponding action being enabled.
     * If the slot is not reserved and the corresponding action is disabled,
     * then the next available custom action is assigned to the button.
     *
     * @param button The button corresponding to the slot
     * @param originalResId The drawable resource ID for the original button,
     * only used if the original action is not replaced by a custom action.
     * @param slotAlwaysReserved True if the slot should be empty when the
     * corresponding action is disabled. If false, when the action is disabled
     * the slot has its default action replaced by the next custom action, if any.
     * @param isOriginalEnabled True if the original action of this button is
     * enabled.
     * @param customActions A list of custom actions still unassigned to slots.
     */
    private void handleSlot(ImageButton button, int originalResId, boolean slotAlwaysReserved,
            boolean isOriginalEnabled, List<PlaybackState.CustomAction> customActions) {
        if (isOriginalEnabled || slotAlwaysReserved) {
            setActionDrawable(button, originalResId, getResources());
            button.setVisibility(isOriginalEnabled ? View.VISIBLE : View.INVISIBLE);
            button.setTag(null);
            return;
        }

        if (customActions.isEmpty()) {
            button.setVisibility(View.INVISIBLE);
            return;
        }

        PlaybackState.CustomAction customAction = customActions.remove(0);
        Bundle extras = customAction.getExtras();
        boolean repeatedAction = (extras != null && extras.getBoolean(
                MediaConstants.EXTRA_REPEATED_CUSTOM_ACTION_BUTTON, false));

        if (repeatedAction) {
            button.setOnTouchListener(mControlsTouchListener);
        } else {
            button.setOnClickListener(mControlsClickListener);
        }

        button.setVisibility(View.VISIBLE);
        setActionDrawable(button, customAction.getIcon(),
                mMediaPlaybackModel.getPackageResources());
        button.setTag(customAction);
    }

    /**
     * Takes a list of custom actions and standard actions and displays them in the media
     * controls card (or hides ones that aren't available).
     *
     * @param actions A bit mask of active actions (android.media.session.PlaybackState#ACTION_*).
     * @param customActions A list of custom actions specified by the
     *                      {@link android.media.session.MediaSession}.
     */
    private void updateActions(long actions, List<PlaybackState.CustomAction> customActions) {
        List<MediaSession.QueueItem> mediaQueue = mMediaPlaybackModel.getQueue();
        handleSlot(
                mPlayQueueButton, R.drawable.ic_tracklist,
                mMediaPlaybackModel.isSlotForActionReserved(
                        MediaConstants.EXTRA_RESERVED_SLOT_QUEUE),
                !mediaQueue.isEmpty(),
                customActions);

        handleSlot(
                mPrevButton, R.drawable.ic_skip_previous,
                mMediaPlaybackModel.isSlotForActionReserved(
                        MediaConstants.EXTRA_RESERVED_SLOT_SKIP_TO_PREVIOUS),
                (actions & PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0,
                customActions);

        handleSlot(
                mNextButton, R.drawable.ic_skip_next,
                mMediaPlaybackModel.isSlotForActionReserved(
                        MediaConstants.EXTRA_RESERVED_SLOT_SKIP_TO_NEXT),
                (actions & PlaybackState.ACTION_SKIP_TO_NEXT) != 0,
                customActions);

        handleSlot(
                mOverflowOnButton, R.drawable.ic_overflow_normal,
                customActions.size() > 1,
                customActions.size() > 1,
                customActions);

        for (ImageButton button: mCustomActionButtons) {
            handleSlot(button, 0, false, false, customActions);
        }
    }

    private void showInitialNoContentView(String msg, boolean isError) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "showInitialNoContentView()");
        }
        if (mCurrentView == NO_CONTENT_VIEW) {
            return;
        }
        mCurrentView = NO_CONTENT_VIEW;
        mAppConnectingSpinner.setVisibility(View.GONE);
        setScrimVisible(false);
        if (isError) {
            setBackgroundColor(getContext().getColor(R.color.car_error_screen));
            mMusicErrorIcon.setVisibility(View.VISIBLE);
        } else {
            setBackgroundColor(getContext().getColor(R.color.car_dark_blue_grey_800));
            mMusicErrorIcon.setVisibility(View.INVISIBLE);
        }
        mTapToSelectText.setVisibility(View.VISIBLE);
        mTapToSelectText.setText(msg);
        mInitialNoContentView.setVisibility(View.VISIBLE);
        mMetadata.setVisibility(View.GONE);
        mMusicPanel.setVisibility(View.GONE);
    }

    private void showMediaPlaybackControlsView() {
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "showMediaPlaybackControlsView()");
        }
        if (mCurrentView == PLAYBACK_CONTROLS_VIEW) {
            return;
        }
        mCurrentView = PLAYBACK_CONTROLS_VIEW;
        if (!mShowingMessage) {
            setScrimVisible(true);
        }
        mTapToSelectText.setVisibility(View.GONE);
        mInitialNoContentView.setVisibility(View.GONE);
        mMetadata.setVisibility(View.VISIBLE);
        mMusicPanel.setVisibility(View.VISIBLE);
    }

    private void showLoadingView() {
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "showLoadingView()");
        }
        if (mCurrentView == LOADING_VIEW) {
            return;
        }
        mCurrentView = LOADING_VIEW;
        setBackgroundColor(getContext().getColor(R.color.music_loading_view_background));
        mAppConnectingSpinner.setVisibility(View.VISIBLE);
        mMusicErrorIcon.setVisibility(View.GONE);
        mTapToSelectText.setVisibility(View.GONE);
        mInitialNoContentView.setVisibility(View.VISIBLE);
        mMetadata.setVisibility(View.GONE);
        mMusicPanel.setVisibility(View.GONE);
    }

    private void resetTitle() {
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "resetTitle()");
        }
        if (!mShowingMessage) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Message not currently shown; not resetting title");
            }
            return;
        }
        // Feedback message is currently being displayed, reset will automatically take place when
        // the display interval expires.
        if (mDelayedResetTitleInProgress) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Delayed reset title is in progress; not resetting title now");
            }
            return;
        }
        setScrimVisible(true);
        mArtistView.setVisibility(View.VISIBLE);
        if (mCurrentTrack != null) {
            mTitleView.setText(mCurrentTrack.getTitle());
            mArtistView.setText(mCurrentTrack.getSubtitle());
        }
        mShowingMessage = false;
    }

    private Bitmap cropAlbumArt(Bitmap icon) {
        if (icon == null) {
            return null;
        }
        int width = icon.getWidth();
        int height = icon.getHeight();
        int startX = width > mAlbumArtWidth ? (width - mAlbumArtWidth) / 2 : 0;
        int startY = height > mAlbumArtHeight ? (height - mAlbumArtHeight) / 2 : 0;
        int newWidth = width > mAlbumArtWidth ? mAlbumArtWidth : width;
        int newHeight = height > mAlbumArtHeight ? mAlbumArtHeight : height;

        return Bitmap.createBitmap(icon, startX, startY, newWidth, newHeight);
    }

    private Bitmap getMetadataBitmap(MediaMetadata metadata) {
        // Get the best art bitmap we can find
        for (String bitmapType : PREFERRED_BITMAP_TYPE_ORDER) {
            Bitmap bitmap = metadata.getBitmap(bitmapType);
            if (bitmap != null) {
                return bitmap;
            }
        }
        return null;
    }

    private Uri getMetadataIconUri(MediaMetadata metadata) {
        // Get the best Uri we can find
        for (String bitmapUri : PREFERRED_URI_ORDER) {
            String iconUri = metadata.getString(bitmapUri);
            if (!TextUtils.isEmpty(iconUri)) {
                return Uri.parse(iconUri);
            }
        }
        return null;
    }

    /**
     * Sets the drawable given by the {@code resId} on the specified {@link ImageButton}.
     *
     * @param resources The {@link Resources} to retrieve the Drawable from. This may be different
     *                  from the Resources of this Fragment.
     */
    private void setActionDrawable(ImageButton button, @DrawableRes int resId,
            Resources resources) {
        if (resources == null) {
            Log.e(TAG, "Resources is null. Icons will not show up.");
            return;
        }

        Resources myResources = getResources();
        // The resources may be from another package. We need to update the configuration using
        // the context from the activity so we get the drawable from the correct DPI bucket.
        resources.updateConfiguration(myResources.getConfiguration(),
                myResources.getDisplayMetrics());
        try {
            Drawable icon = resources.getDrawable(resId, null);
            int inset = myResources.getDimensionPixelSize(R.dimen.music_action_icon_inset);
            InsetDrawable insetIcon = new InsetDrawable(icon, inset);
            button.setImageDrawable(insetIcon);
        } catch (Resources.NotFoundException e) {
            Log.w(TAG, "Resource not found: " + resId);
        }
    }

    private void checkAndDisplayFeedbackMessage(PlaybackState.CustomAction ca) {
        Bundle extras = ca.getExtras();
        if (extras == null) {
            return;
        }

        String feedbackMessage = extras.getString(MediaConstants.EXTRA_CUSTOM_ACTION_STATUS, "");
        if (!TextUtils.isEmpty(feedbackMessage)) {
            // Show feedback message that appears for a time interval unless a new
            // message is shown.
            showMessage(feedbackMessage);
            mDelayedResetTitleInProgress = true;
            mHandler.postDelayed(mResetTitleRunnable, FEEDBACK_MESSAGE_DISPLAY_TIME_MS);
        }
    }

    private final View.OnTouchListener mControlsTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (!mMediaPlaybackModel.isConnected()) {
                Log.e(TAG, "Unable to send action for " + v
                        + ". The MediaPlaybackModel is not connected.");
                return true;
            }

            boolean onDown;
            switch (event.getAction() & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_DOWN:
                    onDown = true;
                    break;
                case MotionEvent.ACTION_UP:
                    onDown = false;
                    break;
                default:
                    return true;
            }

            if (v.getTag() instanceof PlaybackState.CustomAction) {
                PlaybackState.CustomAction ca = (PlaybackState.CustomAction) v.getTag();
                checkAndDisplayFeedbackMessage(ca);
                Bundle extras = ca.getExtras();
                extras.putBoolean(
                        MediaConstants.EXTRA_REPEATED_CUSTOM_ACTION_BUTTON_ON_DOWN, onDown);
                MediaController.TransportControls transportControls =
                        mMediaPlaybackModel.getTransportControls();
                transportControls.sendCustomAction(ca, extras);
                mHandler.removeCallbacks(mCloseOverflowRunnable);
                if (!onDown) {
                    mHandler.postDelayed(mCloseOverflowRunnable, DELAY_CLOSE_OVERFLOW_MS);
                }
            }
            return true;
        }
    };

    private final View.OnClickListener mControlsClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (!mMediaPlaybackModel.isConnected()) {
                Log.e(TAG, "Unable to send action for " + v
                        + ". The MediaPlaybackModel is not connected.");
                return;
            }

            MediaController.TransportControls transportControls =
                    mMediaPlaybackModel.getTransportControls();

            if (v.getTag() instanceof PlaybackState.CustomAction) {
                PlaybackState.CustomAction ca = (PlaybackState.CustomAction) v.getTag();
                checkAndDisplayFeedbackMessage(ca);
                transportControls.sendCustomAction(ca, ca.getExtras());
                mHandler.removeCallbacks(mCloseOverflowRunnable);
                mHandler.postDelayed(mCloseOverflowRunnable, DELAY_CLOSE_OVERFLOW_MS);
                return;
            }

            switch (v.getId()) {
                case R.id.play_queue:
                    if (mPlayQueueRevealer != null) {
                        mPlayQueueRevealer.showPlayQueue();
                    }
                    break;
                case R.id.prev:
                    transportControls.skipToPrevious();
                    break;
                case R.id.play_pause:
                case R.id.play_pause_container:
                    handlePlaybackStateForPlay(mMediaPlaybackModel.getPlaybackState(),
                            transportControls);
                    break;
                case R.id.next:
                    transportControls.skipToNext();
                    break;
                case R.id.overflow_off:
                    closeOverflowMenu();
                    break;
                case R.id.overflow_on:
                    setOverflowMenuVisible(true);
                    break;
                default:
                    throw new IllegalStateException("Unknown button press: " + v);
            }
        }

        /**
         * Plays, pauses or stops the music playback depending on the state given in
         * {@link PlaybackState}.
         */
        private void handlePlaybackStateForPlay(PlaybackState playbackState,
                MediaController.TransportControls transportControls) {
            if (playbackState == null) {
                return;
            }

            switch (playbackState.getState()) {
                // Only if the music is currently playing does this method need to handle pausing
                // and stopping of media.
                case PlaybackState.STATE_PLAYING:
                case PlaybackState.STATE_BUFFERING:
                    long actions = playbackState.getActions();
                    if ((actions & PlaybackState.ACTION_PAUSE) != 0) {
                        transportControls.pause();
                    } else if ((actions & PlaybackState.ACTION_STOP) != 0) {
                        transportControls.stop();
                    }
                    break;

                default:
                    transportControls.play();
            }
        }
    };

    private final PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            switch (state) {
                case TelephonyManager.CALL_STATE_RINGING:
                case TelephonyManager.CALL_STATE_OFFHOOK:
                    mPlayPauseStopButton
                            .setPlayState(PlayPauseStopImageView.PLAYBACKSTATE_DISABLED);
                    mPlayPauseStopButton.setMode(PlayPauseStopImageView.MODE_PAUSE);
                    mPlayPauseStopButton.refreshDrawableState();
                    mInCall = true;
                    break;
                case TelephonyManager.CALL_STATE_IDLE:
                    if (mInCall) {
                        PlaybackState playbackState = mMediaPlaybackModel.getPlaybackState();
                        if (playbackState != null) {
                            mPlayPauseStopButton.setPlayState(playbackState.getState());

                            boolean isStopAction =
                                    (playbackState.getActions() & PlaybackState.ACTION_STOP) != 0;

                            mPlayPauseStopButton.setMode(isStopAction
                                    ? PlayPauseStopImageView.MODE_STOP
                                    : PlayPauseStopImageView.MODE_PAUSE);
                            mPlayPauseStopButton.refreshDrawableState();
                        }
                        mInCall = false;
                    }
                    break;
                default:
                    Log.w(TAG, "TelephonyManager reports an unknown call state: " + state);
            }
        }
    };

    private final Runnable mSeekBarRunnable = new Runnable() {
        @Override
        public void run() {
            mSeekBar.setProgress((int) (System.currentTimeMillis() - mStartTime + mStartProgress));
            mHandler.postDelayed(this, SEEK_BAR_UPDATE_TIME_INTERVAL_MS);
        }
    };

    private final Runnable mCloseOverflowRunnable = () -> setOverflowMenuVisible(false);

    private final Runnable mShowNoContentViewRunnable =
            () -> showInitialNoContentView(getString(R.string.nothing_to_play), false);

    private final Runnable mResetTitleRunnable = () -> {
        mDelayedResetTitleInProgress = false;
        resetTitle();
    };

    private final Runnable mSetTitleRunnable = () -> {
        mTitleView.setText(mCurrentTrack.getTitle());
        mArtistView.setText(mCurrentTrack.getSubtitle());
    };
}
