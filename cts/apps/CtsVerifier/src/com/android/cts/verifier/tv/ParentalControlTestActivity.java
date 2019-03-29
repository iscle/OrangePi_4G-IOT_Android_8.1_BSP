/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.annotation.SuppressLint;
import android.content.Intent;
import android.database.Cursor;
import android.media.tv.TvContentRating;
import android.media.tv.TvContract;
import android.media.tv.TvInputManager;
import android.view.View;
import android.widget.Toast;

import com.android.cts.verifier.R;

/**
 * Tests for verifying TV app behavior on parental control.
 */
@SuppressLint("NewApi")
public class ParentalControlTestActivity extends TvAppVerifierActivity
        implements View.OnClickListener {
    private static final String TAG = "ParentalControlTestActivity";

    private static final long TIMEOUT_MS = 5l * 60l * 1000l;  // 5 mins.

    private View mTurnOnParentalControlItem;
    private View mVerifyReceiveBroadcast1Item;
    private View mBlockTvMaItem;
    private View mVerifyReceiveBroadcast2Item;
    private View mBlockUnblockItem;
    private View mParentalControlsSwitchYesItem;
    private View mParentalControlsSwitchNoItem;
    private View mSupportThirdPartyInputYesItem;
    private View mSupportThirdPartyInputNoItem;

    private Intent mTvAppIntent = null;

    @Override
    public void onClick(View v) {
        final View postTarget = getPostTarget();

        if (containsButton(mSupportThirdPartyInputYesItem, v)) {
            setPassState(mSupportThirdPartyInputYesItem, true);
            setButtonEnabled(mSupportThirdPartyInputNoItem, false);
            setButtonEnabled(mParentalControlsSwitchYesItem, true);
            setButtonEnabled(mParentalControlsSwitchNoItem, true);
            return;
        } else if (containsButton(mSupportThirdPartyInputNoItem, v)){
            setPassState(mSupportThirdPartyInputYesItem, true);
            setButtonEnabled(mSupportThirdPartyInputNoItem, false);
            getPassButton().setEnabled(true);
            return;
        } else if (containsButton(mParentalControlsSwitchYesItem, v)) {
            setPassState(mParentalControlsSwitchYesItem, true);
            setButtonEnabled(mParentalControlsSwitchNoItem, false);
            mTurnOnParentalControlItem.setVisibility(View.VISIBLE);
            mVerifyReceiveBroadcast1Item.setVisibility(View.VISIBLE);
            mBlockTvMaItem.setVisibility(View.VISIBLE);
            mVerifyReceiveBroadcast2Item.setVisibility(View.VISIBLE);
            mBlockUnblockItem.setVisibility(View.VISIBLE);
            setButtonEnabled(mTurnOnParentalControlItem, true);
            return;
        } else if (containsButton(mParentalControlsSwitchNoItem, v)){
            setPassState(mParentalControlsSwitchYesItem, true);
            setButtonEnabled(mParentalControlsSwitchNoItem, false);
            mBlockTvMaItem.setVisibility(View.VISIBLE);
            mVerifyReceiveBroadcast2Item.setVisibility(View.VISIBLE);
            mBlockUnblockItem.setVisibility(View.VISIBLE);
            setButtonEnabled(mBlockTvMaItem, true);
            return;
        } else if (containsButton(mTurnOnParentalControlItem, v)) {
            final Runnable failCallback = new Runnable() {
                @Override
                public void run() {
                    setPassState(mVerifyReceiveBroadcast1Item, false);
                }
            };
            postTarget.postDelayed(failCallback, TIMEOUT_MS);
            MockTvInputService.expectBroadcast(postTarget,
                    TvInputManager.ACTION_PARENTAL_CONTROLS_ENABLED_CHANGED, new Runnable() {
                @Override
                public void run() {
                    postTarget.removeCallbacks(failCallback);
                    setPassState(mTurnOnParentalControlItem, true);
                    setPassState(mVerifyReceiveBroadcast1Item, true);
                    setButtonEnabled(mBlockTvMaItem, true);
                }
            });
        } else if (containsButton(mBlockTvMaItem, v)) {
            final Runnable failCallback = new Runnable() {
                @Override
                public void run() {
                    setPassState(mVerifyReceiveBroadcast2Item, false);
                }
            };
            postTarget.postDelayed(failCallback, TIMEOUT_MS);
            MockTvInputService.expectBroadcast(postTarget,
                    TvInputManager.ACTION_BLOCKED_RATINGS_CHANGED, new Runnable() {
                @Override
                public void run() {
                    postTarget.removeCallbacks(failCallback);
                    setPassState(mBlockTvMaItem, true);
                    setPassState(mVerifyReceiveBroadcast2Item, true);
                    setButtonEnabled(mBlockUnblockItem, true);
                }
            });
        } else if (containsButton(mBlockUnblockItem, v)) {
            final Runnable failCallback = new Runnable() {
                @Override
                public void run() {
                    setPassState(mBlockUnblockItem, false);
                }
            };
            postTarget.postDelayed(failCallback, TIMEOUT_MS);
            MockTvInputService.setBlockRating(TvContentRating.createRating(
                    "com.android.cts.verifier", "CTS_VERIFIER", "FAKE"));
            MockTvInputService.expectUnblockContent(postTarget, new Runnable() {
                @Override
                public void run() {
                    postTarget.removeCallbacks(failCallback);
                    setPassState(mBlockUnblockItem, true);
                    getPassButton().setEnabled(true);
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
        mParentalControlsSwitchYesItem = createUserItem(
                R.string.tv_parental_control_test_check_parental_controls_switch,
                R.string.tv_yes, this);
        mParentalControlsSwitchNoItem = createButtonItem(R.string.tv_no, this);
        mTurnOnParentalControlItem = createUserItem(
                R.string.tv_parental_control_test_turn_on_parental_control,
                R.string.tv_launch_tv_app, this);
        mVerifyReceiveBroadcast1Item = createAutoItem(
                R.string.tv_parental_control_test_verify_receive_broadcast1);
        mBlockTvMaItem = createUserItem(R.string.tv_parental_control_test_block_tv_ma,
                R.string.tv_launch_tv_app, this);
        mVerifyReceiveBroadcast2Item = createAutoItem(
                R.string.tv_parental_control_test_verify_receive_broadcast2);
        mBlockUnblockItem = createUserItem(R.string.tv_parental_control_test_block_unblock,
                R.string.tv_launch_tv_app, this);
        mTurnOnParentalControlItem.setVisibility(View.GONE);
        mVerifyReceiveBroadcast1Item.setVisibility(View.GONE);
        mBlockTvMaItem.setVisibility(View.GONE);
        mVerifyReceiveBroadcast2Item.setVisibility(View.GONE);
        mBlockUnblockItem.setVisibility(View.GONE);
    }

    @Override
    protected void setInfoResources() {
        setInfoResources(R.string.tv_parental_control_test,
                R.string.tv_parental_control_test_info, -1);
    }
}
