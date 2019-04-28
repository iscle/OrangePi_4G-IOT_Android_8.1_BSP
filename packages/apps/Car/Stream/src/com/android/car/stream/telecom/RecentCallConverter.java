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
import android.net.Uri;
import com.android.car.stream.BitmapUtils;
import com.android.car.stream.R;
import com.android.car.stream.StreamCard;
import com.android.car.stream.StreamConstants;

public class RecentCallConverter {

    /**
     * Creates a StreamCard of type {@link StreamConstants#CARD_TYPE_RECENT_CALL}
     * @return
     */
    public StreamCard createStreamCard(Context context, String number, long timestamp) {
        StreamCard.Builder builder = new StreamCard.Builder(StreamConstants.CARD_TYPE_RECENT_CALL,
                Long.parseLong(number), timestamp);
        String displayName = TelecomUtils.getDisplayName(context, number);

        builder.setPrimaryText(displayName);
        builder.setSecondaryText(context.getString(R.string.recent_call));
        builder.setDescription(context.getString(R.string.recent_call));
        Bitmap phoneIcon = BitmapUtils.getBitmap(
                (VectorDrawable) context.getDrawable(R.drawable.ic_phone));

        builder.setPrimaryIcon(phoneIcon);
        builder.setSecondaryIcon(TelecomUtils.createStreamCardSecondaryIcon(context, number));
        builder.setClickAction(createCallPendingIntent(context, number));
        return builder.build();
    }

    private PendingIntent createCallPendingIntent(Context context, String number) {
        Intent callIntent = new Intent(Intent.ACTION_DIAL);
        callIntent.setData(Uri.parse("tel: " + number));
        callIntent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT);
        return PendingIntent.getActivity(context, 0, callIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
