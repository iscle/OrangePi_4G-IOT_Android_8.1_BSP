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
package com.android.car.stream.telecom;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.VectorDrawable;
import android.telecom.Call;
import com.android.car.stream.BitmapUtils;
import com.android.car.stream.CurrentCallExtension;
import com.android.car.stream.R;
import com.android.car.stream.StreamCard;
import com.android.car.stream.StreamConstants;

/**
 * A converter that creates a {@link StreamCard} for the current call events.
 */
public class CurrentCallConverter {
    private static final int MUTE_BUTTON_REQUEST_CODE = 12;
    private static final int CALL_BUTTON_REQUEST_CODE = 13;

    private PendingIntent mMuteAction;
    private PendingIntent mUnMuteAction;
    private PendingIntent mAcceptCallAction;
    private PendingIntent mHangupCallAction;

    public CurrentCallConverter(Context context) {
        mMuteAction =  getCurrentCallAction(context,
                TelecomConstants.ACTION_MUTE, MUTE_BUTTON_REQUEST_CODE);
        mUnMuteAction =  getCurrentCallAction(context,
                TelecomConstants.ACTION_MUTE, MUTE_BUTTON_REQUEST_CODE);

        mAcceptCallAction =  getCurrentCallAction(context,
                TelecomConstants.ACTION_ACCEPT_CALL, CALL_BUTTON_REQUEST_CODE);
        mHangupCallAction =  getCurrentCallAction(context,
                TelecomConstants.ACTION_HANG_UP_CALL, CALL_BUTTON_REQUEST_CODE);
    }

    private PendingIntent getCurrentCallAction(Context context,
            String action, int requestcode) {
        Intent intent = new Intent(TelecomConstants.INTENT_ACTION_STREAM_CALL_CONTROL);
        intent.setPackage(context.getPackageName());
        intent.putExtra(TelecomConstants.EXTRA_STREAM_CALL_ACTION, action);
        PendingIntent pendingIntent =
                PendingIntent.getBroadcast(
                        context,
                        requestcode,
                        intent,
                        PendingIntent.FLAG_CANCEL_CURRENT
                );
        return pendingIntent;
    }

    public StreamCard convert(Call call, Context context, boolean isMuted,
            long callStartTime, String dialerPackage) {
        long timeStamp = System.currentTimeMillis() - call.getDetails().getConnectTimeMillis();
        int callState = call.getState();
        String number = TelecomUtils.getNumber(call);
        String displayName = TelecomUtils.getDisplayName(context, call);
        long digits = Long.valueOf(number.replaceAll("[^0-9]", ""));

        PendingIntent dialerPendingIntent =
                PendingIntent.getActivity(
                        context,
                        0,
                        context.getPackageManager().getLaunchIntentForPackage(dialerPackage),
                        PendingIntent.FLAG_UPDATE_CURRENT
                );

        StreamCard.Builder builder = new StreamCard.Builder(StreamConstants.CARD_TYPE_CURRENT_CALL,
                digits /* id */, timeStamp);
        builder.setPrimaryText(displayName);
        builder.setSecondaryText(getCallState(context, callState));

        Bitmap phoneIcon = BitmapUtils.getBitmap(
                (VectorDrawable) context.getDrawable(R.drawable.ic_phone));
        builder.setPrimaryIcon(phoneIcon);
        builder.setSecondaryIcon(TelecomUtils.createStreamCardSecondaryIcon(context, number));
        builder.setClickAction(dialerPendingIntent);
        builder.setCardExtension(createCurrentCallExtension(context, callStartTime, displayName,
                callState, isMuted, number));
        return builder.build();
    }

    private CurrentCallExtension createCurrentCallExtension(Context context, long callStartTime,
            String displayName, int callState, boolean isMuted, String number) {

        Bitmap contactPhoto = TelecomUtils
                .getContactPhotoFromNumber(context.getContentResolver(), number);
        CurrentCallExtension extension
                = new CurrentCallExtension(callStartTime, displayName, callState, isMuted,
                contactPhoto, mMuteAction, mUnMuteAction, mAcceptCallAction, mHangupCallAction);
        return extension;
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

}
