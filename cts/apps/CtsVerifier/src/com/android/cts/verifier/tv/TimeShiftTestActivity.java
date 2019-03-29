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

package com.android.cts.verifier.tv;

import android.content.Intent;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.view.View;
import android.widget.Toast;

import com.android.cts.verifier.R;

import java.util.concurrent.TimeUnit;

/**
 * Tests for verifying TV app behavior on time shift.
 */
public class TimeShiftTestActivity extends TvAppVerifierActivity
        implements View.OnClickListener {
    private static final long TIMEOUT_MS = TimeUnit.MINUTES.toMillis(5);
    private static final boolean NOT_PASSED = false;
    private static final boolean PASSED = true;

    private View mPauseResumeItem;
    private View mVerifyResumeAfterPauseItem;
    private View mVerifyPositionTrackingItem;

    private View mSetPlaybackParamsItem;
    private View mVerifyRewindItem;
    private View mVerifyFastForwardItem;

    private View mSeekToItem;
    private View mVerifySeekToPreviousItem;
    private View mVerifySeekToNextItem;

    private View mSupportThirdPartyInputYesItem;
    private View mSupportThirdPartyInputNoItem;

    private Intent mTvAppIntent = null;

    @Override
    public void onClick(View v) {
        final View postTarget = getPostTarget();

        if (containsButton(mSupportThirdPartyInputYesItem, v)) {
            setPassState(mSupportThirdPartyInputYesItem, true);
            setButtonEnabled(mSupportThirdPartyInputNoItem, false);
            setButtonEnabled(mPauseResumeItem, true);
            return;
        } else if (containsButton(mSupportThirdPartyInputNoItem, v)){
            setPassState(mSupportThirdPartyInputYesItem, true);
            setButtonEnabled(mSupportThirdPartyInputNoItem, false);
            getPassButton().setEnabled(true);
            return;
        } else if (containsButton(mPauseResumeItem, v)) {
            mVerifyResumeAfterPauseItem.setTag(NOT_PASSED);
            mVerifyPositionTrackingItem.setTag(NOT_PASSED);

            final Runnable failCallback = new Runnable() {
                @Override
                public void run() {
                    setPassState(mVerifyResumeAfterPauseItem, false);
                    setPassState(mVerifyPositionTrackingItem, false);
                }
            };
            postTarget.postDelayed(failCallback, TIMEOUT_MS);
            MockTvInputService.expectResumeAfterPause(postTarget, new Runnable() {
                @Override
                public void run() {
                    postTarget.removeCallbacks(failCallback);
                    setPassState(mPauseResumeItem, true);
                    setPassState(mVerifyResumeAfterPauseItem, true);
                    mVerifyResumeAfterPauseItem.setTag(PASSED);
                    if (isPassedState(mVerifyResumeAfterPauseItem)
                            && isPassedState(mVerifyPositionTrackingItem)) {
                        setButtonEnabled(mSetPlaybackParamsItem, true);
                    }
                }
            });
            MockTvInputService.expectPositionTracking(postTarget, new Runnable() {
                @Override
                public void run() {
                    postTarget.removeCallbacks(failCallback);
                    setPassState(mPauseResumeItem, true);
                    setPassState(mVerifyPositionTrackingItem, true);
                    mVerifyPositionTrackingItem.setTag(PASSED);
                    if (isPassedState(mVerifyResumeAfterPauseItem)
                            && isPassedState(mVerifyPositionTrackingItem)) {
                        setButtonEnabled(mSetPlaybackParamsItem, true);
                    }
                }
            });
        } else if (containsButton(mSetPlaybackParamsItem, v)) {
            mVerifyRewindItem.setTag(NOT_PASSED);
            mVerifyFastForwardItem.setTag(NOT_PASSED);

            final Runnable failCallback = new Runnable() {
                @Override
                public void run() {
                    setPassState(mVerifyRewindItem, false);
                    setPassState(mVerifyFastForwardItem, false);
                }
            };
            postTarget.postDelayed(failCallback, TIMEOUT_MS);
            MockTvInputService.expectRewind(postTarget, new Runnable() {
                @Override
                public void run() {
                    postTarget.removeCallbacks(failCallback);
                    setPassState(mSetPlaybackParamsItem, true);
                    setPassState(mVerifyRewindItem, true);
                    mVerifyRewindItem.setTag(PASSED);
                    if (isPassedState(mVerifyRewindItem) && isPassedState(mVerifyFastForwardItem)) {
                        setButtonEnabled(mSeekToItem, true);
                    }
                }
            });
            MockTvInputService.expectFastForward(postTarget, new Runnable() {
                @Override
                public void run() {
                    postTarget.removeCallbacks(failCallback);
                    setPassState(mSetPlaybackParamsItem, true);
                    setPassState(mVerifyFastForwardItem, true);
                    mVerifyFastForwardItem.setTag(PASSED);
                    if (isPassedState(mVerifyRewindItem) && isPassedState(mVerifyFastForwardItem)) {
                        setButtonEnabled(mSeekToItem, true);
                    }
                }
            });
        } else if (containsButton(mSeekToItem, v)) {
            mVerifySeekToPreviousItem.setTag(NOT_PASSED);
            mVerifySeekToNextItem.setTag(NOT_PASSED);

            final Runnable failCallback = new Runnable() {
                @Override
                public void run() {
                    setPassState(mVerifySeekToPreviousItem, false);
                    setPassState(mVerifySeekToNextItem, false);
                }
            };
            postTarget.postDelayed(failCallback, TIMEOUT_MS);
            MockTvInputService.expectSeekToPrevious(postTarget, new Runnable() {
                @Override
                public void run() {
                    postTarget.removeCallbacks(failCallback);
                    setPassState(mSeekToItem, true);
                    setPassState(mVerifySeekToPreviousItem, true);
                    mVerifySeekToPreviousItem.setTag(PASSED);
                    if (isPassedState(mVerifySeekToPreviousItem)
                            && isPassedState(mVerifySeekToNextItem)) {
                        getPassButton().setEnabled(true);
                    }
                }
            });
            MockTvInputService.expectSeekToNext(postTarget, new Runnable() {
                @Override
                public void run() {
                    postTarget.removeCallbacks(failCallback);
                    setPassState(mSeekToItem, true);
                    setPassState(mVerifySeekToNextItem, true);
                    mVerifySeekToNextItem.setTag(PASSED);
                    if (isPassedState(mVerifySeekToPreviousItem)
                            && isPassedState(mVerifySeekToNextItem)) {
                        getPassButton().setEnabled(true);
                    }
                }
            });
        }
        if (mTvAppIntent == null) {
            String[] projection = { TvContract.Channels._ID };
            try (Cursor cursor = getContentResolver().query(
                    TvContract.buildChannelsUriForInput(MockTvInputService.getInputId(this)),
                    projection, null, null, null)) {
                if (cursor != null && cursor.moveToNext()) {
                    mTvAppIntent = new Intent(Intent.ACTION_VIEW,
                            TvContract.buildChannelUri(cursor.getLong(0)));
                }
            }
            if (mTvAppIntent == null) {
                Toast.makeText(this, R.string.tv_channel_not_found, Toast.LENGTH_SHORT).show();
                return;
            }
        }
        startActivity(mTvAppIntent);
    }

    @Override
    protected void createTestItems() {
        mSupportThirdPartyInputYesItem = createUserItem(
                R.string.tv_input_discover_test_third_party_tif_input_support,
                R.string.tv_yes, this);
        setButtonEnabled(mSupportThirdPartyInputYesItem, true);
        mSupportThirdPartyInputNoItem = createButtonItem(R.string.tv_no, this);
        setButtonEnabled(mSupportThirdPartyInputNoItem, true);
        mPauseResumeItem = createUserItem(
                R.string.tv_time_shift_test_pause_resume,
                R.string.tv_launch_tv_app, this);
        mVerifyResumeAfterPauseItem = createAutoItem(
                R.string.tv_time_shift_test_verify_resume_after_pause);
        mVerifyPositionTrackingItem = createAutoItem(
                R.string.tv_time_shift_test_verify_position_tracking);
        mSetPlaybackParamsItem = createUserItem(
                R.string.tv_time_shift_test_speed_rate,
                R.string.tv_launch_tv_app, this);
        mVerifyRewindItem = createAutoItem(
                R.string.tv_time_shift_test_verify_rewind);
        mVerifyFastForwardItem = createAutoItem(
                R.string.tv_time_shift_test_verify_fast_forward);
        mSeekToItem = createUserItem(
                R.string.tv_time_shift_test_seek,
                R.string.tv_launch_tv_app, this);
        mVerifySeekToPreviousItem = createAutoItem(
                R.string.tv_time_shift_test_verify_seek_to_previous);
        mVerifySeekToNextItem = createAutoItem(
                R.string.tv_time_shift_test_verify_seek_to_next);
    }

    @Override
    protected void setInfoResources() {
        setInfoResources(R.string.tv_time_shift_test,
                R.string.tv_time_shift_test_info, -1);
    }

    private boolean isPassedState(View view) {
        return ((Boolean) view.getTag()) == PASSED;
    }
}
