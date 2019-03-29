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
import android.media.tv.TvContract;
import android.media.tv.TvTrackInfo;
import android.view.View;
import android.widget.Toast;

import com.android.cts.verifier.R;

/**
 * Tests for verifying TV app behavior on multiple tracks and subtitle.
 */
@SuppressLint("NewApi")
public class MultipleTracksTestActivity extends TvAppVerifierActivity
        implements View.OnClickListener {
    private static final String TAG = "MultipleTracksTestActivity";

    private static final long TIMEOUT_MS = 5l * 60l * 1000l;  // 5 mins.

    private View mSelectSubtitleItem;
    private View mVerifySetCaptionEnabledItem;
    private View mVerifySelectSubtitleItem;
    private View mSelectAudioItem;
    private View mVerifySelectAudioItem;
    private View mSupportThirdPartyInputYesItem;
    private View mSupportThirdPartyInputNoItem;

    private Intent mTvAppIntent = null;

    @Override
    public void onClick(View v) {
        final View postTarget = getPostTarget();

        if (containsButton(mSupportThirdPartyInputYesItem, v)) {
            setPassState(mSupportThirdPartyInputYesItem, true);
            setButtonEnabled(mSupportThirdPartyInputNoItem, false);
            setButtonEnabled(mSelectSubtitleItem, true);
            return;
        } else if (containsButton(mSupportThirdPartyInputNoItem, v)){
            setPassState(mSupportThirdPartyInputYesItem, true);
            setButtonEnabled(mSupportThirdPartyInputNoItem, false);
            getPassButton().setEnabled(true);
            return;
        } else if (containsButton(mSelectSubtitleItem, v)) {
            final Runnable failCallback = new Runnable() {
                @Override
                public void run() {
                    setPassState(mVerifySetCaptionEnabledItem, false);
                    setPassState(mVerifySelectSubtitleItem, false);
                }
            };
            postTarget.postDelayed(failCallback, TIMEOUT_MS);
            MockTvInputService.expectSetCaptionEnabled(true, postTarget, new Runnable() {
                @Override
                public void run() {
                    postTarget.removeCallbacks(failCallback);
                    setPassState(mSelectSubtitleItem, true);
                    setPassState(mVerifySetCaptionEnabledItem, true);
                    Integer tag = (Integer) mSelectAudioItem.getTag();
                    if (tag == 0) {
                        mSelectAudioItem.setTag(Integer.valueOf(1));
                    } else if (tag == 1) {
                        setButtonEnabled(mSelectAudioItem, true);
                    }
                }
            });
            MockTvInputService.expectSelectTrack(TvTrackInfo.TYPE_SUBTITLE,
                    MockTvInputService.sEngSubtitleTrack.getId(), postTarget, new Runnable() {
                @Override
                public void run() {
                    postTarget.removeCallbacks(failCallback);
                    setPassState(mSelectSubtitleItem, true);
                    setPassState(mVerifySelectSubtitleItem, true);
                    Integer tag = (Integer) mSelectAudioItem.getTag();
                    if (tag == 0) {
                        mSelectAudioItem.setTag(Integer.valueOf(1));
                    } else if (tag == 1) {
                        setButtonEnabled(mSelectAudioItem, true);
                    }
                }
            });
        } else if (containsButton(mSelectAudioItem, v)) {
            final Runnable failCallback = new Runnable() {
                @Override
                public void run() {
                    setPassState(mVerifySelectAudioItem, false);
                }
            };
            postTarget.postDelayed(failCallback, TIMEOUT_MS);
            MockTvInputService.expectSelectTrack(TvTrackInfo.TYPE_AUDIO,
                    MockTvInputService.sSpaAudioTrack.getId(), postTarget, new Runnable() {
                @Override
                public void run() {
                    postTarget.removeCallbacks(failCallback);
                    setPassState(mSelectAudioItem, true);
                    setPassState(mVerifySelectAudioItem, true);
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
        mSelectSubtitleItem = createUserItem(
                R.string.tv_multiple_tracks_test_select_subtitle,
                R.string.tv_launch_tv_app, this);
        mVerifySetCaptionEnabledItem = createAutoItem(
                R.string.tv_multiple_tracks_test_verify_set_caption_enabled);
        mVerifySelectSubtitleItem = createAutoItem(
                R.string.tv_multiple_tracks_test_verify_select_subtitle);
        mSelectAudioItem = createUserItem(
                R.string.tv_multiple_tracks_test_select_audio,
                R.string.tv_launch_tv_app, this);
        mSelectAudioItem.setTag(Integer.valueOf(0));
        mVerifySelectAudioItem = createAutoItem(
                R.string.tv_multiple_tracks_test_verify_select_audio);
    }

    @Override
    protected void setInfoResources() {
        setInfoResources(R.string.tv_multiple_tracks_test,
                R.string.tv_multiple_tracks_test_info, -1);
    }
}
