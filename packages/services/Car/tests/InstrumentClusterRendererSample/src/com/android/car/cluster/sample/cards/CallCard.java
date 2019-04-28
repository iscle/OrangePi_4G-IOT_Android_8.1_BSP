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

package com.android.car.cluster.sample.cards;

import android.animation.TimeInterpolator;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Chronometer;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.car.cluster.sample.DebugUtil;
import com.android.car.cluster.sample.R;

/**
 * Card responsible to display current call status.
 */
public class CallCard extends CardView {

    private final static String TAG = DebugUtil.getTag(CallCard.class);

    private LinearLayout mPhonePanel;
    private Chronometer mCallDuration;
    private TextView mContactName;
    private TextView mCallStatusTextView;
    private LinearLayout mCallDurationPanel;

    private int mIncomingCallTextHeight;
    private int mCallDurationTextHeight;

    public int getCallStatus() {
        return mCallStatus;
    }

    public @interface CallStatus {
        int INCOMING_OR_DIALING = 1;
        int ACTIVE = 2;
        int DISCONNECTED = 4;
    }

    @CallStatus
    private int mCallStatus;

    public CallCard(Context context, PriorityChangedListener listener) {
        super(context, CardType.PHONE_CALL, listener);
    }

    @Override
    protected void init() {
        if (DebugUtil.DEBUG) {
            Log.d(TAG, "init");
        }
        mCallStatus = CallStatus.INCOMING_OR_DIALING;
        mPriority = PRIORITY_CALL_INCOMING;

        inflate(R.layout.call_card);

        mPhonePanel = viewById(R.id.phone_text_panel);
        mCallStatusTextView = viewById(R.id.call_status);
        mCallDuration = viewById(R.id.call_duration);
        mContactName = viewById(R.id.contact_name);
        mCallDurationPanel = viewById(R.id.call_duration_panel);
        mDetailsPanel = mPhonePanel;

        mLeftIconSwitcher.setVisibility(GONE);
        mCallDurationPanel.setVisibility(INVISIBLE);

        setRightIcon(
                BitmapFactory.decodeResource(getResources(), R.drawable.phone), true);

        final ViewTreeObserver observer = mPhonePanel.getViewTreeObserver();
        // Make sure to get dimensions after layout has been created.
        observer.addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                mPhonePanel.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                mIncomingCallTextHeight = mCallStatusTextView.getHeight();
                mCallDurationTextHeight = mCallDuration.getHeight();

                // Call duration is hidden when call is not answered, adjust panel Y pos.
                mPhonePanel.setTranslationY(mCallDurationTextHeight / 2);
            }
        });
    }

    public void setContactName(String contactName) {
        mContactName.setText(contactName);
    }

    public void setStatusLabel(String status) {
        mCallStatusTextView.setText(status);
    }

    public void animateCallConnected(long connectedTimestamp) {
        if (DebugUtil.DEBUG) {
            Log.d(TAG, "animateCallConnected, current status: " + mCallStatus);
        }
        if (mCallStatus == CallStatus.INCOMING_OR_DIALING) {
            mCallStatus = CallStatus.ACTIVE;

            // Decrease priority after call is answered.
            runDelayed(3000, new Runnable() {
                @Override
                public void run() {
                    setPriority(PRIORITY_CALL_ACTIVE);
                }
            });

            final long duration = 500 * DebugUtil.ANIMATION_FACTOR;

            TimeInterpolator interpolator =
                    new AccelerateDecelerateInterpolator(getContext(), null);

            mCallDuration.setBase(connectedTimestamp);
            mCallDuration.start();

            mBackgroundImage.animate()
                    .alpha(0f)
                    .setDuration(duration);

            mLeftIconSwitcher.setTranslationX(mLeftPadding);
            mLeftIconSwitcher.setVisibility(VISIBLE);
            mRightIconSwitcher.animate()
                    .translationX(mLeftPadding + mIconsOverlap)
                    .setInterpolator(interpolator)
                    .setDuration(duration);
            setRightIcon(
                    BitmapFactory.decodeResource(getResources(), R.drawable.phone_active), true);
            mDetailsPanel.animate()
                    .translationX(
                            mLeftPadding + mIconsOverlap + mIconSize + mLeftPadding)
                    .setInterpolator(interpolator)
                    .setDuration(duration);

            mCallStatusTextView.animate()
                    .alpha(0f)
                    .setDuration(duration);

            mCallDurationPanel.setAlpha(0);
            mCallDurationPanel.setVisibility(VISIBLE);
            mCallDurationPanel.animate()
                    .setDuration(duration)
                    .alpha(1);

            mPhonePanel.animate()
                    .translationYBy(-(mIncomingCallTextHeight + mCallDurationTextHeight) / 2)
                    .setDuration(duration);
        }
    }

    public void animateCallDisconnected() {
        if (DebugUtil.DEBUG) {
            Log.d(TAG, "animateCallConnected, current status: " + mCallStatus);
        }
        if (mCallStatus == CallStatus.ACTIVE) {
            mCallStatus = CallStatus.DISCONNECTED;

            runDelayed(2000, new Runnable() {
                @Override
                public void run() {
                    setPriority(PRIORITY_GARBAGE);
                }
            });

            mCallDuration.stop();
            final int duration = 500;

            TimeInterpolator interpolator =
                    new AccelerateDecelerateInterpolator(getContext(), null);

            setRightIcon(
                    BitmapFactory.decodeResource(getResources(), R.drawable.phone), true);
            mRightIconSwitcher.animate()
                    .translationX(mLeftPadding)
                    .setInterpolator(interpolator)
                    .setDuration(duration * DebugUtil.ANIMATION_FACTOR)
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            mLeftIconSwitcher.setVisibility(GONE);
                        }
                    });
            mDetailsPanel.animate()
                    .translationX(mLeftPadding + mIconSize + mLeftPadding)
                    .setInterpolator(interpolator)
                    .setDuration(duration * DebugUtil.ANIMATION_FACTOR);
        }
    }

}
