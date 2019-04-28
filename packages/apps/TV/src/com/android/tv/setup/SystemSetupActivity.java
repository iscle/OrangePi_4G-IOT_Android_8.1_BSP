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

package com.android.tv.setup;

import android.app.Activity;
import android.app.Fragment;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.media.tv.TvInputInfo;
import android.os.Bundle;
import android.widget.Toast;

import com.android.tv.ApplicationSingletons;
import com.android.tv.R;
import com.android.tv.SetupPassthroughActivity;
import com.android.tv.common.TvCommonUtils;
import com.android.tv.common.ui.setup.SetupActivity;
import com.android.tv.common.ui.setup.SetupFragment;
import com.android.tv.common.ui.setup.SetupMultiPaneFragment;
import com.android.tv.TvApplication;
import com.android.tv.data.ChannelDataManager;
import com.android.tv.onboarding.SetupSourcesFragment;
import com.android.tv.util.OnboardingUtils;
import com.android.tv.util.SetupUtils;
import com.android.tv.util.TvInputManagerHelper;

/**
 * A activity to start input sources setup fragment for initial setup flow.
 */
public class SystemSetupActivity extends SetupActivity {
    private static final String SYSTEM_SETUP =
            "com.android.tv.action.LAUNCH_SYSTEM_SETUP";
    private static final int SHOW_RIPPLE_DURATION_MS = 266;
    private static final int REQUEST_CODE_START_SETUP_ACTIVITY = 1;

    private TvInputManagerHelper mInputManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        if (!SYSTEM_SETUP.equals(intent.getAction())) {
            finish();
            return;
        }
        ApplicationSingletons singletons = TvApplication.getSingletons(this);
        mInputManager = singletons.getTvInputManagerHelper();
    }

    @Override
    protected Fragment onCreateInitialFragment() {
        return new SetupSourcesFragment();
    }

    private void showMerchantCollection() {
        executeActionWithDelay(new Runnable() {
            @Override
            public void run() {
                startActivity(OnboardingUtils.ONLINE_STORE_INTENT);
            }
        }, SHOW_RIPPLE_DURATION_MS);
    }

    @Override
    public boolean executeAction(String category, int actionId, Bundle params) {
        switch (category) {
            case SetupSourcesFragment.ACTION_CATEGORY:
                switch (actionId) {
                    case SetupSourcesFragment.ACTION_ONLINE_STORE:
                        showMerchantCollection();
                        return true;
                    case SetupSourcesFragment.ACTION_SETUP_INPUT: {
                        String inputId = params.getString(
                                SetupSourcesFragment.ACTION_PARAM_KEY_INPUT_ID);
                        TvInputInfo input = mInputManager.getTvInputInfo(inputId);
                        Intent intent = TvCommonUtils.createSetupIntent(input);
                        if (intent == null) {
                            Toast.makeText(this, R.string.msg_no_setup_activity, Toast.LENGTH_SHORT)
                                    .show();
                            return true;
                        }
                        // Even though other app can handle the intent, the setup launched by Live
                        // channels should go through Live channels SetupPassthroughActivity.
                        intent.setComponent(new ComponentName(this,
                                SetupPassthroughActivity.class));
                        try {
                            // Now we know that the user intends to set up this input. Grant
                            // permission for writing EPG data.
                            SetupUtils.grantEpgPermission(this, input.getServiceInfo().packageName);
                            startActivityForResult(intent, REQUEST_CODE_START_SETUP_ACTIVITY);
                        } catch (ActivityNotFoundException e) {
                            Toast.makeText(this,
                                    getString(R.string.msg_unable_to_start_setup_activity,
                                            input.loadLabel(this)), Toast.LENGTH_SHORT).show();
                        }
                        return true;
                    }
                    case SetupMultiPaneFragment.ACTION_DONE: {
                        // To make sure user can finish setup flow, set result as RESULT_OK.
                        setResult(Activity.RESULT_OK);
                        finish();
                        return true;
                    }
                }
                break;
        }
        return false;
    }
}
