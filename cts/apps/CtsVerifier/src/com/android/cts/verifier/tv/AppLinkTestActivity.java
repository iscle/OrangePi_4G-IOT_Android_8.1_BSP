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
import android.graphics.drawable.Drawable;
import android.media.tv.TvContract;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.android.cts.verifier.R;

/**
 * Tests for verifying TV app behavior for TV app-link.
 */
public class AppLinkTestActivity extends TvAppVerifierActivity implements View.OnClickListener {
    private static final long TIMEOUT_MS = 5l * 60l * 1000l;  // 5 mins.

    private boolean mSelectAppLinkItemPassed;
    private View mSelectAppLinkItem;
    private View mVerifyAppLinkIntentItem;
    private View mVerifyAppLinkCardItem;
    private View mSupportThirdPartyInputYesItem;
    private View mSupportThirdPartyInputNoItem;

    Runnable mSelectAppLinkFailCallback;

    @Override
    public void onClick(View v) {
        final View postTarget = getPostTarget();

        if (containsButton(mSupportThirdPartyInputYesItem, v)) {
            setPassState(mSupportThirdPartyInputYesItem, true);
            setButtonEnabled(mSupportThirdPartyInputNoItem, false);
            setButtonEnabled(mSelectAppLinkItem, true);
            return;
        } else if (containsButton(mSupportThirdPartyInputNoItem, v)){
            setPassState(mSupportThirdPartyInputYesItem, true);
            setButtonEnabled(mSupportThirdPartyInputNoItem, false);
            getPassButton().setEnabled(true);
            return;
        } else if (containsButton(mSelectAppLinkItem, v)) {
            Intent tvAppIntent = null;
            String[] projection = { TvContract.Channels._ID };
            try (Cursor cursor = getContentResolver().query(
                    TvContract.buildChannelsUriForInput(MockTvInputService.getInputId(this)),
                    projection, null, null, null)) {
                if (cursor != null && cursor.moveToNext()) {
                    tvAppIntent = new Intent(Intent.ACTION_VIEW,
                            TvContract.buildChannelUri(cursor.getLong(0)));
                }
            }
            if (tvAppIntent == null) {
                Toast.makeText(this, R.string.tv_channel_not_found, Toast.LENGTH_SHORT).show();
                return;
            }

            mSelectAppLinkFailCallback = new Runnable() {
                @Override
                public void run() {
                    mSelectAppLinkItemPassed = false;
                    setPassState(mSelectAppLinkItem, false);
                    setPassState(mVerifyAppLinkIntentItem, false);
                }
            };
            postTarget.postDelayed(mSelectAppLinkFailCallback, TIMEOUT_MS);
            mSelectAppLinkItemPassed = true;
            setPassState(mSelectAppLinkItem, true);

            startActivity(tvAppIntent);
        } else if (containsButton(mVerifyAppLinkCardItem, v)) {
            setPassState(mVerifyAppLinkCardItem, true);
            getPassButton().setEnabled(true);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (mSelectAppLinkItemPassed
                && TextUtils.equals(MockTvInputSetupActivity.APP_LINK_TEST_VALUE,
                        intent.getStringExtra(MockTvInputSetupActivity.APP_LINK_TEST_KEY))) {
            getPostTarget().removeCallbacks(mSelectAppLinkFailCallback);
            setPassState(mVerifyAppLinkIntentItem, true);
            setButtonEnabled(mVerifyAppLinkCardItem, true);
        }
    }

    @Override
    protected void createTestItems() {
        mSupportThirdPartyInputYesItem = createUserItem(
                R.string.tv_input_discover_test_third_party_tif_input_support,
                R.string.tv_yes, this);
        setButtonEnabled(mSupportThirdPartyInputYesItem, true);
        mSupportThirdPartyInputNoItem = createButtonItem(R.string.tv_no, this);
        setButtonEnabled(mSupportThirdPartyInputNoItem, true);
        mSelectAppLinkItem = createUserItem(R.string.tv_app_link_test_select_app_link,
                R.string.tv_launch_tv_app, this);
        mVerifyAppLinkIntentItem = createAutoItem(
                R.string.tv_app_link_test_verify_link_clicked);
        mVerifyAppLinkCardItem = createUserItem(R.string.tv_input_link_test_verify_link_interface,
                android.R.string.yes, this);
        TextView instructions = (TextView) mVerifyAppLinkCardItem.findViewById(R.id.instructions);
        Drawable image = getDrawable(R.drawable.app_link_img);
        image.setBounds(0, 0, 317, 241);
        instructions.setCompoundDrawablePadding(10);
        instructions.setCompoundDrawables(image, null, null, null);
    }

    @Override
    protected void setInfoResources() {
        setInfoResources(R.string.tv_app_link_test, R.string.tv_app_link_test_info, -1);
    }
}
