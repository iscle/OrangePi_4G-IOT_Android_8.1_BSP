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

package com.android.car.radio;

import android.content.Context;
import android.media.session.PlaybackState;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageView;

import com.android.car.apps.common.FabDrawable;

/**
 * An {@link ImageView} that renders a play/pause button like a floating action button.
 */
public class PlayPauseButton extends ImageView {
    private static final String TAG = "Em.PlayPauseButton";

    private final int[] STATE_PLAYING = {R.attr.state_playing};
    private final int[] STATE_PAUSED = {R.attr.state_paused};

    private int mPlaybackState = -1;

    public PlayPauseButton(Context context, AttributeSet attrs) {
        super(context, attrs);

        FabDrawable fabDrawable = new FabDrawable(context);
        fabDrawable.setFabAndStrokeColor(
                context.getColor(R.color.car_radio_accent_color));
        setBackground(fabDrawable);
    }

    /**
     * Set the current play state of the button.
     *
     * @param playState One of the values from {@link PlaybackState}. Only
     *                  {@link PlaybackState#STATE_PAUSED} and {@link PlaybackState#STATE_PLAYING}
     *                  are valid.
     */
    public void setPlayState(int playState) {
        if (playState != PlaybackState.STATE_PAUSED && playState != PlaybackState.STATE_PLAYING) {
            throw new IllegalArgumentException("Playback state should be either "
                    + "PlaybackState.STATE_PAUSED or PlaybackState.STATE_PLAYING");
        }

        mPlaybackState = playState;
    }

    @Override
    public int[] onCreateDrawableState(int extraSpace) {
        // + 1 so we can potentially add our custom PlayState
        final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);

        switch(mPlaybackState) {
            case PlaybackState.STATE_PLAYING:
                mergeDrawableStates(drawableState, STATE_PLAYING);
                break;
            case PlaybackState.STATE_PAUSED:
                mergeDrawableStates(drawableState, STATE_PAUSED);
                break;
            default:
                Log.e(TAG, "Unknown PlaybackState: " + mPlaybackState);
        }
        if (getBackground() != null) {
            getBackground().setState(drawableState);
        }
        return drawableState;
    }
}
