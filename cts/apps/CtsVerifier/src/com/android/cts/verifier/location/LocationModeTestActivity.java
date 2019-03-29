/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.cts.verifier.location;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.provider.Settings.Secure;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

/**
 * Asks the user to put the device in one of the four location modes and then checks to see if
 * {@link Secure#isLocationProviderEnabled(ContentResolver, String)} and {@link
 * LocationManager#isProviderEnabled(String)} have the expected values for GPS and Wi-Fi. For
 * example in battery saving mode, Wi-Fi should be on but GPS should be off.
 *
 * It would be hard to automate these tests because the {@link Secure#LOCATION_MODE} API is only
 * accessible to apps in the system image. Furthermore, selecting two of the modes requires the user
 * to accept the NLP confirmation dialog.
 */
public abstract class LocationModeTestActivity
        extends PassFailButtons.Activity implements Runnable {

    private static final String STATE = "state";
    protected static final int PASS = 1;
    protected static final int FAIL = 2;
    protected static final int WAIT_FOR_USER = 3;

    protected int mState;
    protected int[] mStatus;
    private LayoutInflater mInflater;
    private ViewGroup mItemList;
    private Runnable mRunner;
    private View mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            mState = savedInstanceState.getInt(STATE, 0);
        }

        mRunner = this;
        mInflater = getLayoutInflater();
        View view = mInflater.inflate(R.layout.location_mode_main, null);
        mItemList = (ViewGroup) view.findViewById(R.id.test_items);
        mHandler = mItemList;

        createTestItems();
        mStatus = new int[mItemList.getChildCount()];
        setContentView(view);

        setPassFailButtonClickListeners();

        setInfoResources();

        getPassButton().setEnabled(false);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(STATE, mState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        next();
    }

    /**
     * Template method used by the subclass to create the checks corresponding to each value of
     * {@link #mState}. Subclass should call {@link #createUserItem(int)} and {@link
     * #createAutoItem(int)} as appropriate to generate each item.
     */
    protected abstract void createTestItems();

    /**
     * Template method used by the subclass to call {@link #setInfoResources(int, int, int)} with
     * the appropriate resources.
     */
    protected abstract void setInfoResources();

    /**
     * Subclass can call this to create a test step where the user must perform some action such
     * as setting the location mode.
     */
    protected View createUserItem(int stringId) {
        View item = mInflater.inflate(R.layout.location_mode_item, mItemList, false);
        TextView instructions = (TextView) item.findViewById(R.id.instructions);
        instructions.setText(stringId);
        mItemList.addView(item);
        return item;
    }

    /**
     * Subclass can call this to create a test step where the test automatically evaluates whether
     * an expected condition is satisfied, such as GPS is off.
     */
    protected View createAutoItem(int stringId) {
        View item = mInflater.inflate(R.layout.location_mode_item, mItemList, false);
        TextView instructions = (TextView) item.findViewById(R.id.instructions);
        instructions.setText(stringId);
        View button = item.findViewById(R.id.launch_settings);
        button.setVisibility(View.GONE);
        mItemList.addView(item);
        return item;
    }

    /**
     * Set the visible state of a test item to passed or failed.
     */
    private void setItemState(int index, boolean passed) {
        ViewGroup item = (ViewGroup) mItemList.getChildAt(index);
        ImageView status = (ImageView) item.findViewById(R.id.status);
        status.setImageResource(passed ? R.drawable.fs_good : R.drawable.fs_error);
        View button = item.findViewById(R.id.launch_settings);
        button.setClickable(false);
        button.setEnabled(false);
        status.invalidate();
    }

    /**
     * Set the visible state of a test item to waiting.
     */
    protected void markItemWaiting(int index) {
        ViewGroup item = (ViewGroup) mItemList.getChildAt(index);
        ImageView status = (ImageView) item.findViewById(R.id.status);
        status.setImageResource(R.drawable.fs_warning);
        status.invalidate();
    }

    /**
     * Advances the state machine.
     */
    public void run() {
        // Advance test state until we find case where it hasn't passed (yet)
        while (mState < mStatus.length && mStatus[mState] != WAIT_FOR_USER) {
            if (mStatus[mState] == PASS) {
                setItemState(mState, true);
                mState++;
            } else if (mStatus[mState] == FAIL) {
                setItemState(mState, false);
                return;
            } else {
                break;
            }
        }

        if (mState < mStatus.length && mStatus[mState] == WAIT_FOR_USER) {
            markItemWaiting(mState);
        }

        testAdvance(mState);

        if (mState == mStatus.length - 1 && mStatus[mState] == PASS) {
            // All tests run and pass
            getPassButton().setEnabled(true);
        }
    }

    /**
     * Launches Locations &gt; Settings so the user can set the location mode. Public because it
     * is referenced by layout.
     */
    public void launchSettings(View button) {
        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
    }

    /**
     * Return to the state machine to progress through the tests.
     */
    protected void next() {
        mHandler.post(mRunner);
    }

    /**
     * Wait for things to settle before returning to the state machine.
     */
    protected void delay() {
        mHandler.postDelayed(mRunner, 2000);
    }

    // Tests

    private int getLocationMode() {
        ContentResolver cr = getContentResolver();
        return Secure.getInt(cr, Secure.LOCATION_MODE, Secure.LOCATION_MODE_OFF);
    }

    protected void testIsOn(int i) {
        int mode = getLocationMode();
        boolean passed = mode != Secure.LOCATION_MODE_OFF;
        if (passed) {
            mStatus[i] = PASS;
        } else {
            mStatus[i] = WAIT_FOR_USER;
        }
        next();
    }

    protected void testIsExpectedMode(int i, int expectedMode) {
        int mode = getLocationMode();
        boolean passed = mode == expectedMode;
        if (passed) {
            mStatus[i] = PASS;
            next();
        } else {
            mStatus[i] = WAIT_FOR_USER;
            delay();
        }
    }

    protected void testSecureProviderIsEnabled(int i, String provider) {
        ContentResolver cr = getContentResolver();
        boolean enabled = Secure.isLocationProviderEnabled(cr, provider);
        mStatus[i] = enabled ? PASS : FAIL;
        next();
    }

    protected void testSecureProviderIsDisabled(int i, String provider) {
        ContentResolver cr = getContentResolver();
        boolean enabled = Secure.isLocationProviderEnabled(cr, provider);
        mStatus[i] = !enabled ? PASS : FAIL;
        next();
    }

    protected void testManagerProviderIsEnabled(int i, String gpsProvider) {
        LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        boolean enabled = manager.isProviderEnabled(gpsProvider);
        mStatus[i] = enabled ? PASS : FAIL;
        next();
    }

    protected void testManagerProviderIsDisabled(int i, String gpsProvider) {
        LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        boolean enabled = manager.isProviderEnabled(gpsProvider);
        mStatus[i] = !enabled ? PASS : FAIL;
        next();
    }

    protected abstract void testAdvance(int state);
}
