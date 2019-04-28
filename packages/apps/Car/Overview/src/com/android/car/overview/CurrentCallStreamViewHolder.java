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
package com.android.car.overview;

import android.app.PendingIntent;
import android.content.Context;
import android.graphics.Color;
import android.telecom.Call;
import android.util.Log;
import android.view.View;
import android.widget.Chronometer;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import com.android.car.overview.utils.BitmapUtils;
import com.android.car.stream.CurrentCallExtension;
import com.android.car.stream.StreamCard;

/**
 * A {@link StreamViewHolder} that binds a {@link CurrentCallExtension} to
 * an interactive in call UI.
 */
public class CurrentCallStreamViewHolder extends StreamViewHolder {
    private static final String TAG = "CurrentCallStreamVH";

    private final ImageView mBackgroundImage;
    private final TextView mDisplayNameTextView;

    private final TextView mCallStateTextView;
    private final Chronometer mTimerView;

    private final OverviewFabButton mCallActionButton;
    private final ImageButton mMuteActionButton;

    private PendingIntent mCallAction;
    private PendingIntent mMuteAction;
    private PendingIntent mContainerClickAction;

    public CurrentCallStreamViewHolder(Context context, View itemView) {
        super(context, itemView);

        mBackgroundImage = (ImageView) itemView.findViewById(R.id.background_image);
        mDisplayNameTextView = (TextView) itemView.findViewById(R.id.display_name);
        mCallStateTextView = (TextView) itemView.findViewById(R.id.call_state);
        mTimerView = (Chronometer) itemView.findViewById(R.id.timer);

        mCallActionButton = (OverviewFabButton) itemView.findViewById(R.id.call_button);
        mMuteActionButton = (ImageButton) itemView.findViewById(R.id.mute_button);

        mCallActionButton.setAccentColor(Color.RED);
        mCallActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mCallAction == null) {
                    return;
                }
                try {
                    mCallAction.send(mContext, 0 /* resultCode */, null /* intent */);
                } catch (PendingIntent.CanceledException e) {
                    Log.e(TAG, "Failed to send call action pending intent", e);
                }
            }
        });

        mMuteActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mMuteAction == null) {
                    return;
                }
                try {
                    mMuteAction.send(mContext, 0 /* resultCode */, null /* intent */);
                } catch (PendingIntent.CanceledException e) {
                    Log.e(TAG, "Failed to send mute action pending intent", e);
                }
            }
        });

        mActionContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mContainerClickAction == null) {
                    return;
                }
                try {
                    mContainerClickAction.send(mContext, 0 /* resultCode */, null /* intent */);
                } catch (PendingIntent.CanceledException e) {
                    Log.e(TAG, "Failed to send call action pending intent", e);
                }
            }
        });
    }

    @Override
    public void bindStreamCard(StreamCard card) {
        super.bindStreamCard(card);

        if (!(card.getCardExtension() instanceof CurrentCallExtension)) {
            Log.e(TAG, "StreamCard does not contain a CurrentCallExtension");
            return;
        }

        mContainerClickAction = card.getContentPendingIntent();

        CurrentCallExtension call = (CurrentCallExtension) card.getCardExtension();
        int callState = call.getCallState();

        mDisplayNameTextView.setText(call.getDisplayName());
        mCallStateTextView.setText(getCallState(mContext, callState));

        // For active calls set up mute button and timer view.
        if (callState == Call.STATE_ACTIVE) {
            mTimerView.setVisibility(View.VISIBLE);
            mTimerView.setBase(call.getCallStartTime());
            mTimerView.start();

            int muteIconRes = call.isMuted() ? R.drawable.ic_mic_muted : R.drawable.ic_mic;
            mMuteActionButton.setVisibility(View.VISIBLE);
            mMuteActionButton.setImageResource(muteIconRes);
            mMuteAction = call.isMuted() ? call.getUnMuteAction() : call.getMuteAction();
        }

        // Setup the call button.
        if (callState == Call.STATE_DIALING || callState == Call.STATE_ACTIVE
                || callState == Call.STATE_RINGING) {
            mCallActionButton.setVisibility(View.VISIBLE);
            mCallActionButton.setImageResource(R.drawable.ic_phone_hangup);

            if (callState == Call.STATE_RINGING) {
                mCallAction = call.getAcceptCallAction();
            } else {
                mCallAction = call.getHangupCallAction();
            }
        }

        if (call.getContactPhoto() != null) {
            mBackgroundImage
                    .setImageBitmap(BitmapUtils.applySaturation(call.getContactPhoto(), 01.f));
        }
    }

    private String getCallState(Context context, int state) {
        switch (state) {
            case Call.STATE_ACTIVE:
                return context.getString(R.string.ongoing_call);
            case Call.STATE_DIALING:
                return context.getString(R.string.dialing_call);
            case Call.STATE_DISCONNECTING:
                return context.getString(R.string.disconnecting_call);
            case Call.STATE_RINGING:
                return context.getString(R.string.notification_incoming_call);
            default:
                return context.getString(R.string.unknown);
        }
    }

    @Override
    protected void resetViews() {
        mBackgroundImage.setImageBitmap(null);
        mDisplayNameTextView.setText(null);
        mCallStateTextView.setText(null);

        mTimerView.setText(null);
        mTimerView.setVisibility(View.INVISIBLE);

        mCallActionButton.setImageBitmap(null);
        mCallActionButton.setVisibility(View.INVISIBLE);

        mMuteActionButton.setImageBitmap(null);
        mMuteActionButton.setVisibility(View.INVISIBLE);

        mCallAction = null;
        mMuteAction = null;
    }
}
