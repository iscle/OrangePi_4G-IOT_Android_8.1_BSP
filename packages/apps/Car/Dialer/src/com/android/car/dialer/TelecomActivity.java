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

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.Fragment;
import android.support.v4.view.animation.FastOutSlowInInterpolator;
import android.telecom.Call;
import android.telephony.PhoneNumberUtils;
import android.util.Log;
import android.view.View;

import com.android.car.app.CarDrawerActivity;
import com.android.car.app.CarDrawerAdapter;
import com.android.car.app.DrawerItemViewHolder;
import com.android.car.dialer.telecom.PhoneLoader;
import com.android.car.dialer.telecom.UiCall;
import com.android.car.dialer.telecom.UiCallManager;
import com.android.car.dialer.telecom.UiCallManager.CallListener;

import java.util.List;

/**
 * Main activity for the Dialer app. Displays different fragments depending on call and
 * connectivity status:
 * <ul>
 * <li>OngoingCallFragment
 * <li>NoHfpFragment
 * <li>DialerFragment
 * <li>StrequentFragment
 * </ul>
 */
public class TelecomActivity extends CarDrawerActivity implements
        DialerFragment.DialerBackButtonListener {
    private static final String TAG = "TelecomActivity";

    private static final String ACTION_ANSWER_CALL = "com.android.car.dialer.ANSWER_CALL";
    private static final String ACTION_END_CALL = "com.android.car.dialer.END_CALL";

    private static final String DIALER_BACKSTACK = "DialerBackstack";
    private static final String CONTENT_FRAGMENT_TAG = "CONTENT_FRAGMENT_TAG";
    private static final String DIALER_FRAGMENT_TAG = "DIALER_FRAGMENT_TAG";

    private final UiBluetoothMonitor.Listener mBluetoothListener = this::updateCurrentFragment;

    private UiCallManager mUiCallManager;
    private UiBluetoothMonitor mUiBluetoothMonitor;

    /**
     * Whether or not it is safe to make transactions on the
     * {@link android.support.v4.app.FragmentManager}. This variable prevents a possible exception
     * when calling commit() on the FragmentManager.
     *
     * <p>The default value is {@code true} because it is only after
     * {@link #onSaveInstanceState(Bundle)} that fragment commits are not allowed.
     */
    private boolean mAllowFragmentCommits = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (vdebug()) {
            Log.d(TAG, "onCreate");
        }

        setMainContent(R.layout.telecom_activity);
        getWindow().getDecorView().setBackgroundColor(getColor(R.color.phone_theme));
        setTitle(getString(R.string.phone_app_name));

        mUiCallManager = new UiCallManager(this);
        mUiBluetoothMonitor = new UiBluetoothMonitor(this);

        findViewById(R.id.search).setOnClickListener(
                v -> startActivity(new Intent(this, ContactSearchActivity.class)));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (vdebug()) {
            Log.d(TAG, "onDestroy");
        }
        mUiBluetoothMonitor.tearDown();
        mUiCallManager = null;
    }

    @Override
    protected void onStop() {
        super.onStop();
        mUiCallManager.removeListener(mCarCallListener);
        mUiBluetoothMonitor.removeListener(mBluetoothListener);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        // A transaction can only be committed with this method prior to its containing activity
        // saving its state.
        mAllowFragmentCommits = false;
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
    }

    @Override
    protected void onStart() {
        if (vdebug()) {
            Log.d(TAG, "onStart");
        }
        super.onStart();

        // Fragment commits are not allowed once the Activity's state has been saved. Once
        // onStart() has been called, the FragmentManager should now allow commits.
        mAllowFragmentCommits = true;

        // Update the current fragment before handling the intent so that any UI updates in
        // handleIntent() is not overridden by updateCurrentFragment().
        updateCurrentFragment();
        handleIntent();

        mUiCallManager.addListener(mCarCallListener);
        mUiBluetoothMonitor.addListener(mBluetoothListener);
    }

    private void handleIntent() {
        Intent intent = getIntent();
        String action = intent != null ? intent.getAction() : null;

        if (vdebug()) {
            Log.d(TAG, "handleIntent, intent: " + intent + ", action: " + action);
        }

        if (action == null || action.length() == 0) {
            return;
        }

        String number;
        UiCall ringingCall;
        switch (action) {
            case ACTION_ANSWER_CALL:
                ringingCall = mUiCallManager.getCallWithState(Call.STATE_RINGING);
                if (ringingCall == null) {
                    Log.e(TAG, "Unable to answer ringing call. There is none.");
                } else {
                    mUiCallManager.answerCall(ringingCall);
                }
                break;

            case ACTION_END_CALL:
                ringingCall = mUiCallManager.getCallWithState(Call.STATE_RINGING);
                if (ringingCall == null) {
                    Log.e(TAG, "Unable to end ringing call. There is none.");
                } else {
                    mUiCallManager.disconnectCall(ringingCall);
                }
                break;

            case Intent.ACTION_DIAL:
                number = PhoneNumberUtils.getNumberFromIntent(intent, this);
                if (!(getCurrentFragment() instanceof NoHfpFragment)) {
                    showDialer(number);
                }
                break;

            case Intent.ACTION_CALL:
                number = PhoneNumberUtils.getNumberFromIntent(intent, this);
                mUiCallManager.safePlaceCall(number, false /* bluetoothRequired */);
                break;

            default:
                // Do nothing.
        }

        setIntent(null);
    }

    /**
     * Updates the content fragment of this Activity based on the state of the application.
     */
    private void updateCurrentFragment() {
        if (vdebug()) {
            Log.d(TAG, "updateCurrentFragment()");
        }

        boolean callEmpty = mUiCallManager.getCalls().isEmpty();
        if (!mUiBluetoothMonitor.isBluetoothEnabled() && callEmpty) {
            showNoHfpFragment(R.string.bluetooth_disabled);
        } else if (!mUiBluetoothMonitor.isBluetoothPaired() && callEmpty) {
            showNoHfpFragment(R.string.bluetooth_unpaired);
        } else if (!mUiBluetoothMonitor.isHfpConnected() && callEmpty) {
            showNoHfpFragment(R.string.no_hfp);
        } else {
            UiCall ongoingCall = mUiCallManager.getPrimaryCall();

            if (vdebug()) {
                Log.d(TAG, "ongoingCall: " + ongoingCall + ", mCurrentFragment: "
                        + getCurrentFragment());
            }

            if (ongoingCall == null && getCurrentFragment() instanceof OngoingCallFragment) {
                showSpeedDialFragment();
            } else if (ongoingCall != null) {
                showOngoingCallFragment();
            } else {
                showSpeedDialFragment();
            }
        }

        if (vdebug()) {
            Log.d(TAG, "updateCurrentFragment: done");
        }
    }

    private void showSpeedDialFragment() {
        if (vdebug()) {
            Log.d(TAG, "showSpeedDialFragment");
        }

        if (!mAllowFragmentCommits || getCurrentFragment() instanceof StrequentsFragment) {
            return;
        }

        Fragment fragment = StrequentsFragment.newInstance(mUiCallManager);
        if (getCurrentFragment() instanceof DialerFragment) {
            setContentFragmentWithSlideAndDelayAnimation(fragment);
        } else {
            setContentFragmentWithFadeAnimation(fragment);
        }
    }

    private void showOngoingCallFragment() {
        if (vdebug()) {
            Log.d(TAG, "showOngoingCallFragment");
        }
        if (!mAllowFragmentCommits || getCurrentFragment() instanceof OngoingCallFragment) {
            // in case the dialer is still open, (e.g. when dialing the second phone during
            // a phone call), close it
            maybeHideDialer();
            closeDrawer();
            return;
        }

        Fragment fragment = OngoingCallFragment.newInstance(mUiCallManager, mUiBluetoothMonitor);
        setContentFragmentWithFadeAnimation(fragment);
        closeDrawer();
    }

    private void showDialer() {
        if (vdebug()) {
            Log.d(TAG, "showDialer");
        }

        showDialer(null /* dialNumber */);
    }

    /**
     * Displays the {@link DialerFragment} and initialize it with the given phone number.
     */
    private void showDialer(@Nullable String dialNumber) {
        if (vdebug()) {
            Log.d(TAG, "showDialer with number: " + dialNumber);
        }

        if (!mAllowFragmentCommits ||
                getSupportFragmentManager().findFragmentByTag(DIALER_FRAGMENT_TAG) != null) {
            return;
        }

        Fragment fragment =
                DialerFragment.newInstance(mUiCallManager, this /* listener */, dialNumber);

        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "adding dialer to fragment backstack");
        }

        // Add the dialer fragment to the backstack so that it can be popped off to dismiss it.
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.telecom_slide_in, R.anim.telecom_slide_out,
                        R.anim.telecom_slide_in, R.anim.telecom_slide_out)
                .add(R.id.content_fragment_container, fragment, DIALER_FRAGMENT_TAG)
                .addToBackStack(DIALER_BACKSTACK)
                .commit();

        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "done adding fragment to backstack");
        }
    }

    /**
     * Checks if the dialpad fragment is opened and hides it if it is.
     */
    private void maybeHideDialer() {
        // The dialer is the only fragment to be added to the back stack. Dismiss the dialer by
        // removing it from the back stack.
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            getSupportFragmentManager().popBackStack();
        }
    }

    @Override
    public void onDialerBackClick() {
        maybeHideDialer();
    }

    private void showNoHfpFragment(@StringRes int stringResId) {
        if (!mAllowFragmentCommits) {
            return;
        }

        String errorMessage = getString(stringResId);
        Fragment currentFragment = getCurrentFragment();

        if (currentFragment instanceof NoHfpFragment) {
            ((NoHfpFragment) currentFragment).setErrorMessage(errorMessage);
        } else {
            setContentFragment(NoHfpFragment.newInstance(errorMessage));
        }
    }

    private void setContentFragmentWithSlideAndDelayAnimation(Fragment fragment) {
        if (vdebug()) {
            Log.d(TAG, "setContentFragmentWithSlideAndDelayAnimation, fragment: " + fragment);
        }
        setContentFragmentWithAnimations(fragment,
                R.anim.telecom_slide_in_with_delay, R.anim.telecom_slide_out);
    }

    private void setContentFragmentWithFadeAnimation(Fragment fragment) {
        if (vdebug()) {
            Log.d(TAG, "setContentFragmentWithFadeAnimation, fragment: " + fragment);
        }
        setContentFragmentWithAnimations(fragment,
                R.anim.telecom_fade_in, R.anim.telecom_fade_out);
    }

    private void setContentFragmentWithAnimations(Fragment fragment, int enter, int exit) {
        if (vdebug()) {
            Log.d(TAG, "setContentFragmentWithAnimations: " + fragment);
        }

        maybeHideDialer();
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(enter, exit)
                .replace(R.id.content_fragment_container, fragment, CONTENT_FRAGMENT_TAG)
                .commitNow();
    }

    /**
     * Sets the fragment that will be shown as the main content of this Activity. Note that this
     * fragment is not always visible. In particular, the dialer fragment can show up on top of this
     * fragment.
     */
    private void setContentFragment(Fragment fragment) {
        maybeHideDialer();
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.content_fragment_container, fragment, CONTENT_FRAGMENT_TAG)
                .commitNow();
    }

    /**
     * Returns the fragment that is currently being displayed as the content view. Note that this
     * is not necessarily the fragment that is visible. For example, the returned fragment
     * could be the content, but the dial fragment is being displayed on top of it. Check for
     * the existence of the dial fragment with the TAG {@link #DIALER_FRAGMENT_TAG}.
     */
    @Nullable
    private Fragment getCurrentFragment() {
        return getSupportFragmentManager().findFragmentByTag(CONTENT_FRAGMENT_TAG);
    }

    private final CallListener mCarCallListener = new UiCallManager.CallListener() {
        @Override
        public void onCallAdded(UiCall call) {
            if (vdebug()) {
                Log.d(TAG, "onCallAdded");
            }
            updateCurrentFragment();
        }

        @Override
        public void onCallRemoved(UiCall call) {
            if (vdebug()) {
                Log.d(TAG, "onCallRemoved");
            }
            updateCurrentFragment();
        }

        @Override
        public void onStateChanged(UiCall call, int state) {
            if (vdebug()) {
                Log.d(TAG, "onStateChanged");
            }
            updateCurrentFragment();
        }

        @Override
        public void onCallUpdated(UiCall call) {
            if (vdebug()) {
                Log.d(TAG, "onCallUpdated");
            }
            updateCurrentFragment();
        }
    };

    private static boolean vdebug() {
        return Log.isLoggable(TAG, Log.DEBUG);
    }

    @Override
    protected CarDrawerAdapter getRootAdapter() {
        return new DialerRootAdapter();
    }

    class CallLogAdapter extends CarDrawerAdapter {
        private List<CallLogListingTask.CallLogItem> mItems;

        public CallLogAdapter(int titleResId, List<CallLogListingTask.CallLogItem> items) {
            super(TelecomActivity.this, true  /* showDisabledListOnEmpty */);
            setTitle(getString(titleResId));
            mItems = items;
        }

        @Override
        protected boolean usesSmallLayout(int position) {
            return false;
        }

        @Override
        protected int getActualItemCount() {
            return mItems.size();
        }

        @Override
        public void populateViewHolder(DrawerItemViewHolder holder, int position) {
            CallLogListingTask.CallLogItem item = mItems.get(position);
            holder.getTitle().setText(item.mTitle);
            holder.getText().setText(item.mText);
            holder.getIcon().setImageBitmap(item.mIcon);
        }

        @Override
        public void onItemClick(int position) {
            closeDrawer();
            mUiCallManager.safePlaceCall(mItems.get(position).mNumber, false);
        }
    }

    private class DialerRootAdapter extends CarDrawerAdapter {
        private static final int ITEM_DIAL = 0;
        private static final int ITEM_CALLLOG_ALL = 1;
        private static final int ITEM_CALLLOG_MISSED = 2;
        private static final int ITEM_MAX = 3;

        DialerRootAdapter() {
            super(TelecomActivity.this, false /* showDisabledListOnEmpty */);
            setTitle(getString(R.string.phone_app_name));
        }

        @Override
        protected int getActualItemCount() {
            return ITEM_MAX;
        }

        @Override
        public void populateViewHolder(DrawerItemViewHolder holder, int position) {
            final int iconColor = getResources().getColor(R.color.car_tint);
            int textResId, iconResId;
            switch (position) {
                case ITEM_DIAL:
                    textResId = R.string.calllog_dial_number;
                    iconResId = R.drawable.ic_drawer_dialpad;
                    break;
                case ITEM_CALLLOG_ALL:
                    textResId = R.string.calllog_all;
                    iconResId = R.drawable.ic_drawer_history;
                    break;
                case ITEM_CALLLOG_MISSED:
                    textResId = R.string.calllog_missed;
                    iconResId = R.drawable.ic_drawer_call_missed;
                    break;
                default:
                    Log.wtf(TAG, "Unexpected position: " + position);
                    return;
            }
            holder.getTitle().setText(textResId);
            Drawable drawable = getDrawable(iconResId);
            drawable.setColorFilter(iconColor, PorterDuff.Mode.SRC_IN);
            holder.getIcon().setImageDrawable(drawable);
            if (position > 0) {
                drawable = getDrawable(R.drawable.ic_chevron_right);
                drawable.setColorFilter(iconColor, PorterDuff.Mode.SRC_IN);
                holder.getRightIcon().setImageDrawable(drawable);
            }
        }

        @Override
        public void onItemClick(int position) {
            switch (position) {
                case ITEM_DIAL:
                    closeDrawer();
                    showDialer();
                    break;
                case ITEM_CALLLOG_ALL:
                    loadCallHistoryAsync(PhoneLoader.CALL_TYPE_ALL, R.string.calllog_all);
                    break;
                case ITEM_CALLLOG_MISSED:
                    loadCallHistoryAsync(PhoneLoader.CALL_TYPE_MISSED, R.string.calllog_missed);
                    break;
                default:
                    Log.w(TAG, "Invalid position in ROOT menu! " + position);
            }
        }
    }

    private void loadCallHistoryAsync(final int callType, final int titleResId) {
        showLoadingProgressBar(true);
        // Warning: much callbackiness!
        // First load up the call log cursor using the PhoneLoader so that happens in a
        // background thread. TODO: Why isn't PhoneLoader using a LoaderManager?
        PhoneLoader.registerCallObserver(callType, this,
            (loader, data) -> {
                // This callback runs on the thread that created the loader which is
                // the ui thread so spin off another async task because we still need
                // to pull together all the data along with the contact photo.
                CallLogListingTask task = new CallLogListingTask(TelecomActivity.this, data,
                    (items) -> {
                            showLoadingProgressBar(false);
                            switchToAdapter(new CallLogAdapter(titleResId, items));
                        });
                task.execute();
            });
    }
}
