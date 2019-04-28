/*
 * Copyright (c) 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.car.stream.radio;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.VectorDrawable;
import android.support.annotation.ColorInt;
import com.android.car.radio.service.RadioStation;
import com.android.car.stream.MediaPlaybackExtension;
import com.android.car.stream.R;
import com.android.car.stream.StreamCard;
import com.android.car.stream.StreamConstants;
import com.android.car.stream.StreamServiceConstants;

/**
 * A converter that is responsible for transforming a {@link RadioStation} into a
 * {@link StreamCard}.
 */
public class RadioConverter {
    /**
     * The separator between the radio channel and band (e.g. between 99.7 and FM).
     */
    private static final String CHANNEL_AND_BAND_SEPARATOR = " ";

    private final Context mContext;

    private final PendingIntent mGoToRadioAction;
    private final PendingIntent mPauseAction;
    private final PendingIntent mForwardSeekAction;
    private final PendingIntent mBackwardSeekAction;
    private final PendingIntent mPlayAction;
    private final PendingIntent mStopAction;

    @ColorInt
    private final int mAccentColor;

    private final Bitmap mPlayIcon;
    private final Bitmap mPauseIcon;

    public RadioConverter(Context context) {
        mContext = context;

        mGoToRadioAction = createGoToRadioIntent();
        mPauseAction = createRadioActionIntent(RadioStreamProducer.ACTION_PAUSE);
        mPlayAction = createRadioActionIntent(RadioStreamProducer.ACTION_PLAY);
        mStopAction = createRadioActionIntent(RadioStreamProducer.ACTION_STOP);
        mForwardSeekAction = createRadioActionIntent(RadioStreamProducer.ACTION_SEEK_FORWARD);
        mBackwardSeekAction = createRadioActionIntent(RadioStreamProducer.ACTION_SEEK_BACKWARD);

        mAccentColor = mContext.getColor(R.color.car_radio_accent_color);

        int iconSize = context.getResources()
                .getDimensionPixelSize(R.dimen.stream_card_secondary_icon_dimen);
        mPlayIcon = getBitmap((VectorDrawable)
                mContext.getDrawable(R.drawable.ic_play_arrow), iconSize, iconSize);
        mPauseIcon = getBitmap((VectorDrawable)
                mContext.getDrawable(R.drawable.ic_pause), iconSize, iconSize);
    }

    /**
     * Converts the given {@link RadioStation} and play status into a {@link StreamCard} that can
     * be used to display a radio card.
     */
    public StreamCard convert(RadioStation station, boolean isPlaying) {
        StreamCard.Builder builder = new StreamCard.Builder(StreamConstants.CARD_TYPE_MEDIA,
                StreamConstants.RADIO_CARD_ID, System.currentTimeMillis());

        builder.setClickAction(mGoToRadioAction);

        String title = createTitleText(station);
        builder.setPrimaryText(title);

        String subtitle = null;
        if (station.getRds() != null) {
            subtitle = station.getRds().getProgramService();
            builder.setSecondaryText(subtitle);
        }

        Bitmap icon = isPlaying ? mPlayIcon : mPauseIcon;
        builder.setPrimaryIcon(icon);

        MediaPlaybackExtension extension = new MediaPlaybackExtension(title, subtitle,
                null /* albumArt */, mAccentColor, true /* canSkipToNext */,
                true /* canSkipToPrevious */, true /* hasPause */, isPlaying,
                mContext.getString(R.string.radio_app_name), mStopAction, mPauseAction, mPlayAction,
                mForwardSeekAction, mBackwardSeekAction);

        builder.setCardExtension(extension);
        return builder.build();
    }

    /**
     * Returns the String that represents the title text of the radio card. The title should be
     * a combination of the current channel number and radio band.
     */
    private String createTitleText(RadioStation station) {
        int radioBand = station.getRadioBand();
        String channel = RadioFormatter.formatRadioChannel(radioBand,
                station.getChannelNumber());
        String band = RadioFormatter.formatRadioBand(mContext, radioBand);

        return channel + CHANNEL_AND_BAND_SEPARATOR + band;
    }

    /**
     * Returns an {@link Intent} that will take the user to the radio application.
     */
    private PendingIntent createGoToRadioIntent() {
        ComponentName radioComponent = new ComponentName(
                mContext.getString(R.string.car_radio_component_package),
                mContext.getString(R.string.car_radio_component_activity));

        Intent intent = new Intent();
        intent.setComponent(radioComponent);
        intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT);

        return PendingIntent.getActivity(mContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /**
     * Returns an {@link Intent} that will perform the given action.
     *
     * @param action One of the action values in {@link RadioStreamProducer}. e.g.
     *               {@link RadioStreamProducer#ACTION_PAUSE}.
     */
    private PendingIntent createRadioActionIntent(int action) {
        Intent intent = new Intent(RadioStreamProducer.RADIO_INTENT_ACTION);
        intent.setPackage(mContext.getPackageName());
        intent.putExtra(RadioStreamProducer.RADIO_ACTION_EXTRA, action);

        return PendingIntent.getBroadcast(mContext, action /* requestCode */,
                intent, PendingIntent.FLAG_CANCEL_CURRENT);
    }

    /**
     * Returns a {@link Bitmap} that corresponds to the given {@link VectorDrawable}.
     */
    private static Bitmap getBitmap(VectorDrawable vectorDrawable, int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        vectorDrawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        vectorDrawable.draw(canvas);
        return bitmap;
    }
}
