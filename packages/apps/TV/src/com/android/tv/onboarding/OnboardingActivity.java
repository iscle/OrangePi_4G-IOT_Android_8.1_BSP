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

package com.android.tv.onboarding;

import android.app.Fragment;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.tv.TvInputInfo;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.widget.Toast;

import com.android.tv.ApplicationSingletons;
import com.android.tv.R;
import com.android.tv.SetupPassthroughActivity;
import com.android.tv.TvApplication;
import com.android.tv.common.TvCommonUtils;
import com.android.tv.common.ui.setup.SetupActivity;
import com.android.tv.common.ui.setup.SetupMultiPaneFragment;
import com.android.tv.data.ChannelDataManager;
import com.android.tv.util.OnboardingUtils;
import com.android.tv.util.PermissionUtils;
import com.android.tv.util.SetupUtils;
import com.android.tv.util.TvInputManagerHelper;

public class OnboardingActivity extends SetupActivity {
    private static final String KEY_INTENT_AFTER_COMPLETION = "key_intent_after_completion";

    private static final int PERMISSIONS_REQUEST_READ_TV_LISTINGS = 1;

    private static final int SHOW_RIPPLE_DURATION_MS = 266;

    private static final int REQUEST_CODE_START_SETUP_ACTIVITY = 1;

    private ChannelDataManager mChannelDataManager;
    private TvInputManagerHelper mInputManager;
    private final ChannelDataManager.Listener mChannelListener = new ChannelDataManager.Listener() {
        @Override
        public void onLoadFinished() {
            mChannelDataManager.removeListener(this);
            SetupUtils.getInstance(OnboardingActivity.this).markNewChannelsBrowsable();
        }

        @Override
        public void onChannelListUpdated() { }

        @Override
        public void onChannelBrowsableChanged() { }
    };

    /**
     * Returns an intent to start {@link OnboardingActivity}.
     *
     * @param context context to create an intent. Should not be {@code null}.
     * @param intentAfterCompletion intent which will be used to start a new activity when this
     * activity finishes. Should not be {@code null}.
     */
    public static Intent buildIntent(@NonNull Context context,
            @NonNull Intent intentAfterCompletion) {
        return new Intent(context, OnboardingActivity.class)
                .putExtra(OnboardingActivity.KEY_INTENT_AFTER_COMPLETION, intentAfterCompletion);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ApplicationSingletons singletons = TvApplication.getSingletons(this);
        mInputManager = singletons.getTvInputManagerHelper();
        if (PermissionUtils.hasAccessAllEpg(this) || PermissionUtils.hasReadTvListings(this)) {
            mChannelDataManager = singletons.getChannelDataManager();
            // Make the channels of the new inputs which have been setup outside Live TV
            // browsable.
            if (mChannelDataManager.isDbLoadFinished()) {
                SetupUtils.getInstance(this).markNewChannelsBrowsable();
            } else {
                mChannelDataManager.addListener(mChannelListener);
            }
        } else {
            requestPermissions(new String[] {PermissionUtils.PERMISSION_READ_TV_LISTINGS},
                    PERMISSIONS_REQUEST_READ_TV_LISTINGS);
        }
    }

    @Override
    protected void onDestroy() {
        if (mChannelDataManager != null) {
            mChannelDataManager.removeListener(mChannelListener);
        }
        super.onDestroy();
    }

    @Override
    protected Fragment onCreateInitialFragment() {
        if (PermissionUtils.hasAccessAllEpg(this) || PermissionUtils.hasReadTvListings(this)) {
            return OnboardingUtils.isFirstRunWithCurrentVersion(this) ? new WelcomeFragment()
                    : new SetupSourcesFragment();
        }
        return null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST_READ_TV_LISTINGS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                finish();
                Intent intentForNextActivity = getIntent().getParcelableExtra(
                        KEY_INTENT_AFTER_COMPLETION);
                startActivity(buildIntent(this, intentForNextActivity));
            } else {
                Toast.makeText(this, R.string.msg_read_tv_listing_permission_denied,
                        Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void finishActivity() {
        Intent intentForNextActivity = getIntent().getParcelableExtra(
                KEY_INTENT_AFTER_COMPLETION);
        if (intentForNextActivity != null) {
            startActivity(intentForNextActivity);
        }
        finish();
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
    protected boolean executeAction(String category, int actionId, Bundle params) {
        switch (category) {
            case WelcomeFragment.ACTION_CATEGORY:
                switch (actionId) {
                    case WelcomeFragment.ACTION_NEXT:
                        OnboardingUtils.setFirstRunWithCurrentVersionCompleted(
                                OnboardingActivity.this);
                        showFragment(new SetupSourcesFragment(), false);
                        return true;
                }
                break;
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
                        ChannelDataManager manager = TvApplication.getSingletons(
                                OnboardingActivity.this).getChannelDataManager();
                        if (manager.getChannelCount() == 0) {
                            finish();
                        } else {
                            finishActivity();
                        }
                        return true;
                    }
                }
                break;
        }
        return false;
    }
}
