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
package com.android.car.media.util.widgets;

import android.content.Context;
import android.graphics.PorterDuff;
import android.media.session.PlaybackState;
import android.support.annotation.IntDef;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageView;
import com.android.car.media.R;

import com.android.car.apps.common.ColorChecker;
import com.android.car.apps.common.FabDrawable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Custom {@link android.widget.ImageButton} that has three custom states:
 *     state_playing
 *     state_paused
 *     state_buffering
 *     state_stopped
 */
public class PlayPauseStopImageView extends ImageView {
    private static final String TAG = "GH.PlayPauseImageView";
    /**
     * All existing play states in {@link android.media.session.PlaybackState} are
     * positive integers.
     */
    public static final int PLAYBACKSTATE_DISABLED = -2;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({MODE_PAUSE, MODE_STOP})
    @interface ActionModes {}
    public static final int MODE_PAUSE = 1;
    public static final int MODE_STOP = 2;

    private final int[] STATE_PLAYING_TO_PAUSE = {R.attr.state_playing_to_pause};
    private final int[] STATE_PLAYING_TO_STOP = {R.attr.state_playing_to_stop};
    private final int[] STATE_PAUSED = {R.attr.state_paused};
    private final int[] STATE_BUFFERING_TO_PAUSE = {R.attr.state_buffering_to_pause};
    private final int[] STATE_BUFFERING_TO_STOP = {R.attr.state_buffering_to_stop};
    private final int[] STATE_STOPPED = {R.attr.state_stopped};
    private final int[] STATE_DISABLED = {R.attr.state_disabled};

    private int mPlaybackState = -1;
    // Set pause mode as default, so it will show pause icon if the mode flag doesn't change.
    private int mMode = MODE_PAUSE;

    public PlayPauseStopImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setBackground(new FabDrawable(context));
    }

    /**
     * Sets the current play state to be represented by this Button.
     *
     * @param playState One of the values returned by {@link PlaybackState#getState()}.
     */
    public void setPlayState(int playState) {
        mPlaybackState = playState;
    }

    public void setMode(@ActionModes int mode) {
        mMode = mode;
    }

    @Override
    public int[] onCreateDrawableState(int extraSpace) {
        // + 1 so we can potentially add our custom PlayState
        final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);

        switch(mPlaybackState) {
            case PlaybackState.STATE_PLAYING:
                if (mMode == MODE_STOP) {
                    mergeDrawableStates(drawableState, STATE_PLAYING_TO_STOP);
                } else {
                    mergeDrawableStates(drawableState, STATE_PLAYING_TO_PAUSE);
                }
                break;
            case PlaybackState.STATE_PAUSED:
                mergeDrawableStates(drawableState, STATE_PAUSED);
                break;
            case PlaybackState.STATE_BUFFERING:
            case PlaybackState.STATE_CONNECTING:
            case PlaybackState.STATE_FAST_FORWARDING:
            case PlaybackState.STATE_REWINDING:
            case PlaybackState.STATE_SKIPPING_TO_NEXT:
            case PlaybackState.STATE_SKIPPING_TO_PREVIOUS:
                if (mMode == MODE_STOP) {
                    mergeDrawableStates(drawableState, STATE_BUFFERING_TO_STOP);
                } else {
                    mergeDrawableStates(drawableState, STATE_BUFFERING_TO_PAUSE);
                }
                break;
            case PlaybackState.STATE_STOPPED:
                mergeDrawableStates(drawableState, STATE_STOPPED);
                break;
            case PLAYBACKSTATE_DISABLED:
                mergeDrawableStates(drawableState, STATE_DISABLED);
                break;
            default:
                Log.e(TAG, "Unknown PlaybackState: " + mPlaybackState);
        }
        if (getBackground() != null) {
            getBackground().setState(drawableState);
        }
        return drawableState;
    }

    public void setPrimaryActionColor(int color) {
        ((FabDrawable) getBackground()).setFabAndStrokeColor(color);
        if (getDrawable() != null) {
            int tintColor = ColorChecker.getTintColor(getContext(), color);
            getDrawable().setColorFilter(tintColor, PorterDuff.Mode.SRC_IN);
        }
    }
}
