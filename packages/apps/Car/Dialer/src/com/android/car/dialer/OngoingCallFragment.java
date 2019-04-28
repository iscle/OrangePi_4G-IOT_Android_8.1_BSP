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
package com.android.car.dialer;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.car.apps.common.CircleBitmapDrawable;
import com.android.car.apps.common.FabDrawable;
import com.android.car.dialer.telecom.TelecomUtils;
import com.android.car.dialer.telecom.UiCall;
import com.android.car.dialer.telecom.UiCallManager;
import com.android.car.dialer.telecom.UiCallManager.CallListener;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A fragment that displays information about an on-going call with options to hang up.
 */
public class OngoingCallFragment extends Fragment {
    private static final String TAG = "OngoingCall";
    private static final SparseArray<Character> mDialpadButtonMap = new SparseArray<>();

    static {
        mDialpadButtonMap.put(R.id.one, '1');
        mDialpadButtonMap.put(R.id.two, '2');
        mDialpadButtonMap.put(R.id.three, '3');
        mDialpadButtonMap.put(R.id.four, '4');
        mDialpadButtonMap.put(R.id.five, '5');
        mDialpadButtonMap.put(R.id.six, '6');
        mDialpadButtonMap.put(R.id.seven, '7');
        mDialpadButtonMap.put(R.id.eight, '8');
        mDialpadButtonMap.put(R.id.nine, '9');
        mDialpadButtonMap.put(R.id.zero, '0');
        mDialpadButtonMap.put(R.id.star, '*');
        mDialpadButtonMap.put(R.id.pound, '#');
    }

    private final Handler mHandler = new Handler();

    private UiCall mLastRemovedCall;
    private UiCallManager mUiCallManager;
    private View mRingingCallControls;
    private View mActiveCallControls;
    private ImageButton mEndCallButton;
    private ImageButton mUnholdCallButton;
    private ImageButton mMuteButton;
    private ImageButton mToggleDialpadButton;
    private ImageButton mSwapButton;
    private ImageButton mMergeButton;
    private ImageButton mAnswerCallButton;
    private ImageButton mRejectCallButton;
    private TextView mNameTextView;
    private TextView mSecondaryNameTextView;
    private TextView mStateTextView;
    private TextView mSecondaryStateTextView;
    private ImageView mLargeContactPhotoView;
    private ImageView mSmallContactPhotoView;
    private View mDialpadContainer;
    private View mSecondaryCallContainer;
    private View mSecondaryCallControls;
    private String mLoadedNumber;
    private CharSequence mCallInfoLabel;
    private UiBluetoothMonitor mUiBluetoothMonitor;

    static OngoingCallFragment newInstance(UiCallManager callManager,
            UiBluetoothMonitor btMonitor) {
        OngoingCallFragment fragment = new OngoingCallFragment();
        fragment.mUiCallManager = callManager;
        fragment.mUiBluetoothMonitor = btMonitor;
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacks(mUpdateDurationRunnable);
        mHandler.removeCallbacks(mStopDtmfToneRunnable);
        mLoadedNumber = null;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.ongoing_call, container, false);
        initializeViews(view);
        initializeClickListeners();

        List<View> dialpadViews = Arrays.asList(
                mDialpadContainer.findViewById(R.id.one),
                mDialpadContainer.findViewById(R.id.two),
                mDialpadContainer.findViewById(R.id.three),
                mDialpadContainer.findViewById(R.id.four),
                mDialpadContainer.findViewById(R.id.five),
                mDialpadContainer.findViewById(R.id.six),
                mDialpadContainer.findViewById(R.id.seven),
                mDialpadContainer.findViewById(R.id.eight),
                mDialpadContainer.findViewById(R.id.nine),
                mDialpadContainer.findViewById(R.id.zero),
                mDialpadContainer.findViewById(R.id.pound),
                mDialpadContainer.findViewById(R.id.star));

        // In touch screen, we need to adjust the InCall card for the narrow screen to show the
        // full dial pad.
        for (View dialpadView : dialpadViews) {
            dialpadView.setOnTouchListener(mDialpadTouchListener);
            dialpadView.setOnKeyListener(mDialpadKeyListener);
        }

        mUiCallManager.addListener(mCallListener);

        updateCalls();

        return view;
    }

    private void initializeViews(View parent) {
        mRingingCallControls = parent.findViewById(R.id.ringing_call_controls);
        mActiveCallControls = parent.findViewById(R.id.active_call_controls);
        mEndCallButton = parent.findViewById(R.id.end_call);
        mUnholdCallButton = parent.findViewById(R.id.unhold_call);
        mMuteButton = parent.findViewById(R.id.mute);
        mToggleDialpadButton = parent.findViewById(R.id.toggle_dialpad);
        mDialpadContainer = parent.findViewById(R.id.dialpad_container);
        mNameTextView = parent.findViewById(R.id.name);
        mSecondaryNameTextView = parent.findViewById(R.id.name_secondary);
        mStateTextView = parent.findViewById(R.id.info);
        mSecondaryStateTextView = parent.findViewById(R.id.info_secondary);
        mLargeContactPhotoView = parent.findViewById(R.id.large_contact_photo);
        mSmallContactPhotoView = parent.findViewById(R.id.small_contact_photo);
        mSecondaryCallContainer = parent.findViewById(R.id.secondary_call_container);
        mSecondaryCallControls = parent.findViewById(R.id.secondary_call_controls);
        mSwapButton = parent.findViewById(R.id.swap);
        mMergeButton = parent.findViewById(R.id.merge);
        mAnswerCallButton = parent.findViewById(R.id.answer_call_button);
        mRejectCallButton = parent.findViewById(R.id.reject_call_button);

        Context context = getContext();
        FabDrawable drawable = new FabDrawable(context);
        drawable.setFabAndStrokeColor(context.getColor(R.color.phone_call));
        mAnswerCallButton.setBackground(drawable);

        drawable = new FabDrawable(context);
        drawable.setFabAndStrokeColor(context.getColor(R.color.phone_end_call));
        mEndCallButton.setBackground(drawable);

        drawable = new FabDrawable(context);
        drawable.setFabAndStrokeColor(context.getColor(R.color.phone_call));
        mUnholdCallButton.setBackground(drawable);
    }

    private void initializeClickListeners() {
        mAnswerCallButton.setOnClickListener((unusedView) -> {
            UiCall call = mUiCallManager.getCallWithState(Call.STATE_RINGING);
            if (call == null) {
                Log.w(TAG, "There is no incoming call to answer.");
                return;
            }
            mUiCallManager.answerCall(call);
        });

        mRejectCallButton.setOnClickListener((unusedView) -> {
            UiCall call = mUiCallManager.getCallWithState(Call.STATE_RINGING);
            if (call == null) {
                Log.w(TAG, "There is no incoming call to reject.");
                return;
            }
            mUiCallManager.rejectCall(call, false, null);
        });

        mEndCallButton.setOnClickListener((unusedView) -> {
            UiCall call = mUiCallManager.getPrimaryCall();
            if (call == null) {
                Log.w(TAG, "There is no active call to end.");
                return;
            }
            mUiCallManager.disconnectCall(call);
        });

        mUnholdCallButton.setOnClickListener((unusedView) -> {
            UiCall call = mUiCallManager.getPrimaryCall();
            if (call == null) {
                Log.w(TAG, "There is no active call to unhold.");
                return;
            }
            mUiCallManager.unholdCall(call);
        });

        mMuteButton.setOnClickListener(
                (unusedView) -> mUiCallManager.setMuted(!mUiCallManager.getMuted()));

        mSwapButton.setOnClickListener((unusedView) -> {
            UiCall call = mUiCallManager.getPrimaryCall();
            if (call == null) {
                Log.w(TAG, "There is no active call to hold.");
                return;
            }
            if (call.getState() == Call.STATE_HOLDING) {
                mUiCallManager.unholdCall(call);
            } else {
                mUiCallManager.holdCall(call);
            }
        });

        mMergeButton.setOnClickListener((unusedView) -> {
            UiCall call = mUiCallManager.getPrimaryCall();
            UiCall secondaryCall = mUiCallManager.getSecondaryCall();
            if (call == null || secondaryCall == null) {
                Log.w(TAG, "There aren't two call to merge.");
                return;
            }

            mUiCallManager.conference(call, secondaryCall);
        });

        mToggleDialpadButton.setOnClickListener((unusedView) -> {
            if (mToggleDialpadButton.isActivated()) {
                closeDialpad();
            } else {
                openDialpad(true /*animate*/);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mUiCallManager.removeListener(mCallListener);
    }

    @Override
    public void onStart() {
        super.onStart();
        trySpeakerAudioRouteIfNecessary();
    }

    private void updateCalls() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "updateCalls(); Primary call: " + mUiCallManager.getPrimaryCall()
                    + "; Secondary call:" + mUiCallManager.getSecondaryCall());
        }

        mHandler.removeCallbacks(mUpdateDurationRunnable);

        UiCall primaryCall = mUiCallManager.getPrimaryCall();
        CharSequence disconnectCauseLabel = mLastRemovedCall == null
                ? null : mLastRemovedCall.getDisconnectCause();
        if (primaryCall == null && !TextUtils.isEmpty(disconnectCauseLabel)) {
            closeDialpad();
            setStateText(disconnectCauseLabel);
            return;
        }

        if (primaryCall == null || primaryCall.getState() == Call.STATE_DISCONNECTED) {
            closeDialpad();
            setStateText(getString(R.string.call_state_call_ended));
            mRingingCallControls.setVisibility(View.GONE);
            mActiveCallControls.setVisibility(View.GONE);
            return;
        }

        if (primaryCall.getState() == Call.STATE_RINGING) {
            mRingingCallControls.setVisibility(View.VISIBLE);
            mActiveCallControls.setVisibility(View.GONE);
        } else {
            mRingingCallControls.setVisibility(View.GONE);
            mActiveCallControls.setVisibility(View.VISIBLE);
        }

        loadContactPhotoForPrimaryNumber(primaryCall.getNumber());

        String displayName = TelecomUtils.getDisplayName(getContext(), primaryCall);
        mNameTextView.setText(displayName);
        mNameTextView.setVisibility(TextUtils.isEmpty(displayName) ? View.GONE : View.VISIBLE);

        Context context = getContext();
        switch (primaryCall.getState()) {
            case Call.STATE_NEW:
                // Since the content resolver call is only cached when a contact is found,
                // this should only be called once on a new call to avoid jank.
                // TODO: consider moving TelecomUtils.getTypeFromNumber into a CursorLoader
                mCallInfoLabel = TelecomUtils.getTypeFromNumber(context, primaryCall.getNumber());
            case Call.STATE_CONNECTING:
            case Call.STATE_DIALING:
            case Call.STATE_SELECT_PHONE_ACCOUNT:
            case Call.STATE_HOLDING:
            case Call.STATE_DISCONNECTED:
                mHandler.removeCallbacks(mUpdateDurationRunnable);
                String callInfoText = TelecomUtils.getCallInfoText(context,
                        primaryCall, mCallInfoLabel);
                setStateText(callInfoText);
                break;
            case Call.STATE_ACTIVE:
                if (mUiBluetoothMonitor.isHfpConnected()) {
                    mHandler.post(mUpdateDurationRunnable);
                }
                break;
            case Call.STATE_RINGING:
                Log.w(TAG, "There should not be a ringing call in the ongoing call fragment.");
                break;
            default:
                Log.w(TAG, "Unhandled call state: " + primaryCall.getState());
        }

        // If it is a voicemail call, open the dialpad (with no animation).
        if (Objects.equals(primaryCall.getNumber(), TelecomUtils.getVoicemailNumber(context))) {
            openDialpad(false /*animate*/);
            mToggleDialpadButton.setVisibility(View.GONE);
        } else {
            mToggleDialpadButton.setVisibility(View.VISIBLE);
        }

        // Handle the holding case.
        if (primaryCall.getState() == Call.STATE_HOLDING) {
            mEndCallButton.setVisibility(View.GONE);
            mUnholdCallButton.setVisibility(View.VISIBLE);
            mMuteButton.setVisibility(View.INVISIBLE);
            mToggleDialpadButton.setVisibility(View.INVISIBLE);
        } else {
            mEndCallButton.setVisibility(View.VISIBLE);
            mUnholdCallButton.setVisibility(View.GONE);
            mMuteButton.setVisibility(View.VISIBLE);
            mToggleDialpadButton.setVisibility(View.VISIBLE);
        }

        updateSecondaryCall(primaryCall, mUiCallManager.getSecondaryCall());
    }

    private void updateSecondaryCall(UiCall primaryCall, UiCall secondaryCall) {
        if (primaryCall == null || secondaryCall == null) {
            mSecondaryCallContainer.setVisibility(View.GONE);
            mSecondaryCallControls.setVisibility(View.GONE);
            return;
        }

        mSecondaryCallContainer.setVisibility(View.VISIBLE);

        if (primaryCall.getState() == Call.STATE_ACTIVE
                && secondaryCall.getState() == Call.STATE_HOLDING) {
            mSecondaryCallControls.setVisibility(View.VISIBLE);
        } else {
            mSecondaryCallControls.setVisibility(View.GONE);
        }

        Context context = getContext();
        mSecondaryNameTextView.setText(TelecomUtils.getDisplayName(context, secondaryCall));
        mSecondaryStateTextView.setText(
                TelecomUtils.callStateToUiString(context, secondaryCall.getState()));

        loadContactPhotoForSecondaryNumber(secondaryCall.getNumber());
    }

    /**
     * Loads the contact photo associated with the given number and sets it in the views that
     * correspond with a primary number.
     */
    private void loadContactPhotoForPrimaryNumber(String primaryNumber) {
        // Don't reload the image if the number is the same.
        if (Objects.equals(primaryNumber, mLoadedNumber)) {
            return;
        }

        final ContentResolver cr = getContext().getContentResolver();
        BitmapWorkerTask.BitmapRunnable runnable = new BitmapWorkerTask.BitmapRunnable() {
            @Override
            public void run() {
                if (mBitmap != null) {
                    Resources r = getResources();
                    mSmallContactPhotoView.setImageDrawable(new CircleBitmapDrawable(r, mBitmap));
                    mLargeContactPhotoView.setImageBitmap(mBitmap);
                    mLargeContactPhotoView.clearColorFilter();
                } else {
                    mSmallContactPhotoView.setImageResource(R.drawable.logo_avatar);
                    mLargeContactPhotoView.setImageResource(R.drawable.ic_avatar_bg);
                }
            }
        };
        mLoadedNumber = primaryNumber;
        BitmapWorkerTask.loadBitmap(cr, mLargeContactPhotoView, primaryNumber, runnable);
    }

    /**
     * Loads the contact photo associated with the given number and sets it in the views that
     * correspond to a secondary number.
     */
    private void loadContactPhotoForSecondaryNumber(String secondaryNumber) {
        BitmapWorkerTask.BitmapRunnable runnable = new BitmapWorkerTask.BitmapRunnable() {
            @Override
            public void run() {
                if (mBitmap != null) {
                    mLargeContactPhotoView.setImageBitmap(mBitmap);
                } else {
                    mLargeContactPhotoView.setImageResource(R.drawable.logo_avatar);
                }
            }
        };

        Context context = getContext();
        BitmapWorkerTask.loadBitmap(context.getContentResolver(), mLargeContactPhotoView,
                secondaryNumber, runnable);

        int scrimColor = context.getColor(R.color.phone_secondary_call_scrim);
        mLargeContactPhotoView.setColorFilter(scrimColor);
    }

    private void setStateText(CharSequence stateText) {
        mStateTextView.setText(stateText);
        mStateTextView.setVisibility(TextUtils.isEmpty(stateText) ? View.GONE : View.VISIBLE);
    }

    /**
     * If the phone is using bluetooth, then do nothing. If the phone is not using bluetooth:
     * <p>
     * <ol>
     *     <li>If the phone supports bluetooth, use it.
     *     <li>If the phone doesn't support bluetooth and support speaker, use speaker
     *     <li>Otherwise, do nothing. Hopefully no phones won't have bt or speaker.
     * </ol>
     */
    private void trySpeakerAudioRouteIfNecessary() {
        if (mUiCallManager == null) {
            return;
        }

        int supportedAudioRouteMask = mUiCallManager.getSupportedAudioRouteMask();
        boolean supportsBluetooth = (supportedAudioRouteMask & CallAudioState.ROUTE_BLUETOOTH) != 0;
        boolean supportsSpeaker = (supportedAudioRouteMask & CallAudioState.ROUTE_SPEAKER) != 0;
        boolean isUsingBluetooth =
                mUiCallManager.getAudioRoute() == CallAudioState.ROUTE_BLUETOOTH;

        if (supportsBluetooth && !isUsingBluetooth) {
            mUiCallManager.setAudioRoute(CallAudioState.ROUTE_BLUETOOTH);
        } else if (!supportsBluetooth && supportsSpeaker) {
            mUiCallManager.setAudioRoute(CallAudioState.ROUTE_SPEAKER);
        }
    }

    private void openDialpad(boolean animate) {
        if (mToggleDialpadButton.isActivated()) {
            return;
        }
        mToggleDialpadButton.setActivated(true);
        // This array of of size 2 because getLocationOnScreen returns (x,y) coordinates.
        int[] location = new int[2];
        mToggleDialpadButton.getLocationOnScreen(location);

        // The dialpad should be aligned with the right edge of mToggleDialpadButton.
        int startingMargin = location[1] + mToggleDialpadButton.getWidth();

        ViewGroup.MarginLayoutParams layoutParams =
                (ViewGroup.MarginLayoutParams) mDialpadContainer.getLayoutParams();

        if (layoutParams.getMarginStart() != startingMargin) {
            layoutParams.setMarginStart(startingMargin);
            mDialpadContainer.setLayoutParams(layoutParams);
        }

        Animation anim = new DialpadAnimation(getContext(), false /* reverse */, animate);
        mDialpadContainer.startAnimation(anim);
    }

    private void closeDialpad() {
        if (!mToggleDialpadButton.isActivated()) {
            return;
        }
        mToggleDialpadButton.setActivated(false);
        Animation anim = new DialpadAnimation(getContext(), true /* reverse */);
        mDialpadContainer.startAnimation(anim);
    }

    private final View.OnTouchListener mDialpadTouchListener = new View.OnTouchListener() {

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            Character digit = mDialpadButtonMap.get(v.getId());
            if (digit == null) {
                Log.w(TAG, "Unknown dialpad button pressed.");
                return false;
            }
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                v.setPressed(true);
                mUiCallManager.playDtmfTone(mUiCallManager.getPrimaryCall(), digit);
                return true;
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                v.setPressed(false);
                v.performClick();
                mUiCallManager.stopDtmfTone(mUiCallManager.getPrimaryCall());
                return true;
            }

            return false;
        }
    };

    private final View.OnKeyListener mDialpadKeyListener = new View.OnKeyListener() {
        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            Character digit = mDialpadButtonMap.get(v.getId());
            if (digit == null) {
                Log.w(TAG, "Unknown dialpad button pressed.");
                return false;
            }

            if (event.getKeyCode() != KeyEvent.KEYCODE_DPAD_CENTER) {
                return false;
            }

            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                v.setPressed(true);
                mUiCallManager.playDtmfTone(mUiCallManager.getPrimaryCall(), digit);
                return true;
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                v.setPressed(false);
                mUiCallManager.stopDtmfTone(mUiCallManager.getPrimaryCall());
                return true;
            }

            return false;
        }
    };

    private final Runnable mUpdateDurationRunnable = new Runnable() {
        @Override
        public void run() {
            UiCall primaryCall = mUiCallManager.getPrimaryCall();
            if (primaryCall.getState() != Call.STATE_ACTIVE) {
                return;
            }
            String callInfoText = TelecomUtils.getCallInfoText(getContext(),
                    primaryCall, mCallInfoLabel);
            setStateText(callInfoText);
            mHandler.postDelayed(this /* runnable */, DateUtils.SECOND_IN_MILLIS);

        }
    };

    private final Runnable mStopDtmfToneRunnable =
            () -> mUiCallManager.stopDtmfTone(mUiCallManager.getPrimaryCall());

    private final class DialpadAnimation extends Animation {
        private static final int DURATION = 300;
        private static final float MAX_SCRIM_ALPHA = 0.6f;

        private final int mStartingTranslation;
        private final int mScrimColor;
        private final boolean mReverse;

        DialpadAnimation(Context context, boolean reverse) {
            this(context, reverse, true);
        }

        DialpadAnimation(Context context, boolean reverse, boolean animate) {
            setDuration(animate ? DURATION : 0);
            setInterpolator(new AccelerateDecelerateInterpolator());
            mStartingTranslation = context.getResources().getDimensionPixelOffset(
                    R.dimen.in_call_card_dialpad_translation_x);
            mScrimColor = context.getColor(R.color.phone_theme);
            mReverse = reverse;
        }

        @Override
        protected void applyTransformation(float interpolatedTime, Transformation t) {
            if (mReverse) {
                interpolatedTime = 1f - interpolatedTime;
            }
            int translationX = (int) (mStartingTranslation * (1f - interpolatedTime));
            mDialpadContainer.setTranslationX(translationX);
            mDialpadContainer.setAlpha(interpolatedTime);
            if (interpolatedTime == 0f) {
                mDialpadContainer.setVisibility(View.GONE);
            } else {
                mDialpadContainer.setVisibility(View.VISIBLE);
            }
            float alpha = 255f * interpolatedTime * MAX_SCRIM_ALPHA;
            mLargeContactPhotoView.setColorFilter(Color.argb((int) alpha, Color.red(mScrimColor),
                    Color.green(mScrimColor), Color.blue(mScrimColor)));

            mSecondaryNameTextView.setAlpha(1f - interpolatedTime);
            mSecondaryStateTextView.setAlpha(1f - interpolatedTime);
        }
    }

    private final CallListener mCallListener = new CallListener() {
        @Override
        public void onCallAdded(UiCall call) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onCallAdded(); call: " + call);
            }
            updateCalls();
            trySpeakerAudioRouteIfNecessary();
        }

        @Override
        public void onCallRemoved(UiCall call) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onCallRemoved(); call: " + call);
            }
            mLastRemovedCall = call;
            updateCalls();
        }

        @Override
        public void onAudioStateChanged(boolean isMuted, int audioRoute,
                int supportedAudioRouteMask) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, String.format("onAudioStateChanged(); isMuted: %b, audioRoute: %d, "
                        + " supportedAudioRouteMask: %d", isMuted, audioRoute,
                        supportedAudioRouteMask));
            }
            mMuteButton.setActivated(isMuted);
            trySpeakerAudioRouteIfNecessary();
        }

        @Override
        public void onStateChanged(UiCall call, int state) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onStateChanged(); call: " + call + ", state: " + state);
            }
            updateCalls();
        }

        @Override
        public void onCallUpdated(UiCall call) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onCallUpdated(); call: " + call);
            }
            updateCalls();
        }
    };
}
