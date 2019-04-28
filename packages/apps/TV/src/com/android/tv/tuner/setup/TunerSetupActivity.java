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

package com.android.tv.tuner.setup;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.tv.TvContract;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.support.annotation.WorkerThread;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;

import com.android.tv.Features;
import com.android.tv.TvApplication;
import com.android.tv.common.AutoCloseableUtils;
import com.android.tv.common.SoftPreconditions;
import com.android.tv.common.TvCommonConstants;
import com.android.tv.common.TvCommonUtils;
import com.android.tv.common.ui.setup.SetupActivity;
import com.android.tv.common.ui.setup.SetupFragment;
import com.android.tv.common.ui.setup.SetupMultiPaneFragment;
import com.android.tv.experiments.Experiments;
import com.android.tv.tuner.R;
import com.android.tv.tuner.TunerHal;
import com.android.tv.tuner.TunerPreferences;
import com.android.tv.tuner.tvinput.TunerTvInputService;
import com.android.tv.tuner.util.PostalCodeUtils;

import java.util.concurrent.Executor;

/**
 * An activity that serves tuner setup process.
 */
public class TunerSetupActivity extends SetupActivity {
    private static final String TAG = "TunerSetupActivity";
    private static final boolean DEBUG = false;

    /**
     * Key for passing tuner type to sub-fragments.
     */
    public static final String KEY_TUNER_TYPE = "TunerSetupActivity.tunerType";

    // For the notification.
    private static final String TV_ACTIVITY_CLASS_NAME = "com.android.tv.TvActivity";
    private static final String TUNER_SET_UP_NOTIFICATION_CHANNEL_ID = "tuner_setup_channel";
    private static final String NOTIFY_TAG = "TunerSetup";
    private static final int NOTIFY_ID = 1000;
    private static final String TAG_DRAWABLE = "drawable";
    private static final String TAG_ICON = "ic_launcher_s";
    private static final int PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION = 1;

    private static final int CHANNEL_MAP_SCAN_FILE[] = {
        R.raw.ut_us_atsc_center_frequencies_8vsb,
        R.raw.ut_us_cable_standard_center_frequencies_qam256,
        R.raw.ut_us_all,
        R.raw.ut_kr_atsc_center_frequencies_8vsb,
        R.raw.ut_kr_cable_standard_center_frequencies_qam256,
        R.raw.ut_kr_all,
        R.raw.ut_kr_dev_cj_cable_center_frequencies_qam256,
        R.raw.ut_euro_dvbt_all,
        R.raw.ut_euro_dvbt_all,
        R.raw.ut_euro_dvbt_all
    };

    private ScanFragment mLastScanFragment;
    private Integer mTunerType;
    private TunerHalFactory mTunerHalFactory;
    private boolean mNeedToShowPostalCodeFragment;
    private String mPreviousPostalCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (DEBUG) Log.d(TAG, "onCreate");
        new AsyncTask<Void, Void, Integer>() {
            @Override
            protected Integer doInBackground(Void... arg0) {
                return TunerHal.getTunerTypeAndCount(TunerSetupActivity.this).first;
            }

            @Override
            protected void onPostExecute(Integer result) {
                if (!TunerSetupActivity.this.isDestroyed()) {
                    mTunerType = result;
                    if (result == null) {
                        finish();
                    } else {
                        showInitialFragment();
                    }
                }
            }
        }.execute();
        TvApplication.setCurrentRunningProcess(this, false);
        super.onCreate(savedInstanceState);
        // TODO: check {@link shouldShowRequestPermissionRationale}.
        if (checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // No need to check the request result.
            requestPermissions(new String[] {android.Manifest.permission.ACCESS_COARSE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION);
        }
        mTunerHalFactory = new TunerHalFactory(getApplicationContext());
        try {
            // Updating postal code takes time, therefore we called it here for "warm-up".
            mPreviousPostalCode = PostalCodeUtils.getLastPostalCode(this);
            PostalCodeUtils.setLastPostalCode(this, null);
            PostalCodeUtils.updatePostalCode(this);
        } catch (Exception e) {
            // Do nothing. If the last known postal code is null, we'll show guided fragment to
            // prompt users to input postal code before ConnectionTypeFragment is shown.
            Log.i(TAG, "Can't get postal code:" + e);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED
                    && Experiments.CLOUD_EPG.get()) {
                try {
                    // Updating postal code takes time, therefore we should update postal code
                    // right after the permission is granted, so that the subsequent operations,
                    // especially EPG fetcher, could get the newly updated postal code.
                    PostalCodeUtils.updatePostalCode(this);
                } catch (Exception e) {
                    // Do nothing
                }
            }
        }
    }

    @Override
    protected Fragment onCreateInitialFragment() {
        if (mTunerType != null) {
            SetupFragment fragment = new WelcomeFragment();
            Bundle args = new Bundle();
            args.putInt(KEY_TUNER_TYPE, mTunerType);
            fragment.setArguments(args);
            fragment.setShortDistance(SetupFragment.FRAGMENT_EXIT_TRANSITION
                    | SetupFragment.FRAGMENT_REENTER_TRANSITION);
            return fragment;
        } else {
            return null;
        }
    }

    @Override
    protected boolean executeAction(String category, int actionId, Bundle params) {
        switch (category) {
            case WelcomeFragment.ACTION_CATEGORY:
                switch (actionId) {
                    case SetupMultiPaneFragment.ACTION_DONE:
                        // If the scan was performed, then the result should be OK.
                        setResult(mLastScanFragment == null ? RESULT_CANCELED : RESULT_OK);
                        finish();
                        break;
                    default:
                        if (mNeedToShowPostalCodeFragment
                                || Features.ENABLE_CLOUD_EPG_REGION.isEnabled(
                                                getApplicationContext())
                                        && TextUtils.isEmpty(
                                                PostalCodeUtils.getLastPostalCode(this))) {
                            // We cannot get postal code automatically. Postal code input fragment
                            // should always be shown even if users have input some valid postal
                            // code in this activity before.
                            mNeedToShowPostalCodeFragment = true;
                            showPostalCodeFragment();
                        } else {
                            showConnectionTypeFragment();
                        }
                        break;
                }
                return true;
            case PostalCodeFragment.ACTION_CATEGORY:
                if (actionId == SetupMultiPaneFragment.ACTION_DONE
                        || actionId == SetupMultiPaneFragment.ACTION_SKIP) {
                    showConnectionTypeFragment();
                }
                return true;
            case ConnectionTypeFragment.ACTION_CATEGORY:
                if (mTunerHalFactory.getOrCreate() == null) {
                    finish();
                    Toast.makeText(getApplicationContext(),
                            R.string.ut_channel_scan_tuner_unavailable,Toast.LENGTH_LONG).show();
                    return true;
                }
                mLastScanFragment = new ScanFragment();
                Bundle args1 = new Bundle();
                args1.putInt(ScanFragment.EXTRA_FOR_CHANNEL_SCAN_FILE,
                        CHANNEL_MAP_SCAN_FILE[actionId]);
                args1.putInt(KEY_TUNER_TYPE, mTunerType);
                mLastScanFragment.setArguments(args1);
                showFragment(mLastScanFragment, true);
                return true;
            case ScanFragment.ACTION_CATEGORY:
                switch (actionId) {
                    case ScanFragment.ACTION_CANCEL:
                        getFragmentManager().popBackStack();
                        return true;
                    case ScanFragment.ACTION_FINISH:
                        mTunerHalFactory.clear();
                        SetupFragment fragment = new ScanResultFragment();
                        Bundle args2 = new Bundle();
                        args2.putInt(KEY_TUNER_TYPE, mTunerType);
                        fragment.setArguments(args2);
                        fragment.setShortDistance(SetupFragment.FRAGMENT_EXIT_TRANSITION
                                | SetupFragment.FRAGMENT_REENTER_TRANSITION);
                        showFragment(fragment, true);
                        return true;
                }
                break;
            case ScanResultFragment.ACTION_CATEGORY:
                switch (actionId) {
                    case SetupMultiPaneFragment.ACTION_DONE:
                        setResult(RESULT_OK);
                        finish();
                        break;
                    default:
                        SetupFragment fragment = new ConnectionTypeFragment();
                        fragment.setShortDistance(SetupFragment.FRAGMENT_ENTER_TRANSITION
                                | SetupFragment.FRAGMENT_RETURN_TRANSITION);
                        showFragment(fragment, true);
                        break;
                }
                return true;
        }
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            FragmentManager manager = getFragmentManager();
            int count = manager.getBackStackEntryCount();
            if (count > 0) {
                String lastTag = manager.getBackStackEntryAt(count - 1).getName();
                if (ScanResultFragment.class.getCanonicalName().equals(lastTag) && count >= 2) {
                    // Pops fragment including ScanFragment.
                    manager.popBackStack(manager.getBackStackEntryAt(count - 2).getName(),
                            FragmentManager.POP_BACK_STACK_INCLUSIVE);
                    return true;
                } else if (ScanFragment.class.getCanonicalName().equals(lastTag)) {
                    mLastScanFragment.finishScan(true);
                    return true;
                }
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onDestroy() {
        if (mPreviousPostalCode != null && PostalCodeUtils.getLastPostalCode(this) == null) {
            PostalCodeUtils.setLastPostalCode(this, mPreviousPostalCode);
        }
        super.onDestroy();
    }

    /**
     * A callback to be invoked when the TvInputService is enabled or disabled.
     *
     * @param context a {@link Context} instance
     * @param enabled {@code true} for the {@link TunerTvInputService} to be enabled;
     *                otherwise {@code false}
     */
    public static void onTvInputEnabled(Context context, boolean enabled, Integer tunerType) {
        // Send a notification for tuner setup if there's no channels and the tuner TV input
        // setup has been not done.
        boolean channelScanDoneOnPreference = TunerPreferences.isScanDone(context);
        int channelCountOnPreference = TunerPreferences.getScannedChannelCount(context);
        if (enabled && !channelScanDoneOnPreference && channelCountOnPreference == 0) {
            TunerPreferences.setShouldShowSetupActivity(context, true);
            sendNotification(context, tunerType);
        } else {
            TunerPreferences.setShouldShowSetupActivity(context, false);
            cancelNotification(context);
        }
    }

    /**
     * Returns a {@link Intent} to launch the tuner TV input service.
     *
     * @param context a {@link Context} instance
     */
    public static Intent createSetupActivity(Context context) {
        String inputId = TvContract.buildInputId(new ComponentName(context.getPackageName(),
                TunerTvInputService.class.getName()));

        // Make an intent to launch the setup activity of TV tuner input.
        Intent intent = TvCommonUtils.createSetupIntent(
                new Intent(context, TunerSetupActivity.class), inputId);
        intent.putExtra(TvCommonConstants.EXTRA_INPUT_ID, inputId);
        Intent tvActivityIntent = new Intent();
        tvActivityIntent.setComponent(new ComponentName(context, TV_ACTIVITY_CLASS_NAME));
        intent.putExtra(TvCommonConstants.EXTRA_ACTIVITY_AFTER_COMPLETION, tvActivityIntent);
        return intent;
    }

    /**
     * Gets the currently used tuner HAL.
     */
    TunerHal getTunerHal() {
        return mTunerHalFactory.getOrCreate();
    }

    /**
     * Generates tuner HAL.
     */
    void generateTunerHal() {
        mTunerHalFactory.generate();
    }

    /**
     * Clears the currently used tuner HAL.
     */
    void clearTunerHal() {
        mTunerHalFactory.clear();
    }

    /**
     * Returns a {@link PendingIntent} to launch the tuner TV input service.
     *
     * @param context a {@link Context} instance
     */
    private static PendingIntent createPendingIntentForSetupActivity(Context context) {
        return PendingIntent.getActivity(context, 0, createSetupActivity(context),
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static void sendNotification(Context context, Integer tunerType) {
        SoftPreconditions.checkState(tunerType != null, TAG,
                "tunerType is null when send notification");
        if (tunerType == null) {
            return;
        }
        Resources resources = context.getResources();
        String contentTitle = resources.getString(R.string.ut_setup_notification_content_title);
        int contentTextId = 0;
        switch (tunerType) {
            case TunerHal.TUNER_TYPE_BUILT_IN:
                contentTextId = R.string.bt_setup_notification_content_text;
                break;
            case TunerHal.TUNER_TYPE_USB:
                contentTextId = R.string.ut_setup_notification_content_text;
                break;
            case TunerHal.TUNER_TYPE_NETWORK:
                contentTextId = R.string.nt_setup_notification_content_text;
                break;
        }
        String contentText = resources.getString(contentTextId);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            sendNotificationInternal(context, contentTitle, contentText);
        } else {
            Bitmap largeIcon = BitmapFactory.decodeResource(resources,
                    R.drawable.recommendation_antenna);
            sendRecommendationCard(context, contentTitle, contentText, largeIcon);
        }
    }

    /**
     * Sends the recommendation card to start the tuner TV input setup activity.
     *
     * @param context a {@link Context} instance
     */
    private static void sendRecommendationCard(Context context, String contentTitle,
            String contentText, Bitmap largeIcon) {
        // Build and send the notification.
        Notification notification = new NotificationCompat.BigPictureStyle(
                new NotificationCompat.Builder(context)
                        .setAutoCancel(false)
                        .setContentTitle(contentTitle)
                        .setContentText(contentText)
                        .setContentInfo(contentText)
                        .setCategory(Notification.CATEGORY_RECOMMENDATION)
                        .setLargeIcon(largeIcon)
                        .setSmallIcon(context.getResources().getIdentifier(
                                TAG_ICON, TAG_DRAWABLE, context.getPackageName()))
                        .setContentIntent(createPendingIntentForSetupActivity(context)))
                .build();
        NotificationManager notificationManager = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFY_TAG, NOTIFY_ID, notification);
    }

    private static void sendNotificationInternal(Context context, String contentTitle,
            String contentText) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(
                Context.NOTIFICATION_SERVICE);
        notificationManager.createNotificationChannel(new NotificationChannel(
                TUNER_SET_UP_NOTIFICATION_CHANNEL_ID,
                context.getResources().getString(R.string.ut_setup_notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH));
        Notification notification = new Notification.Builder(
                context, TUNER_SET_UP_NOTIFICATION_CHANNEL_ID)
                .setContentTitle(contentTitle)
                .setContentText(contentText)
                .setSmallIcon(context.getResources().getIdentifier(
                        TAG_ICON, TAG_DRAWABLE, context.getPackageName()))
                .setContentIntent(createPendingIntentForSetupActivity(context))
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .extend(new Notification.TvExtender())
                .build();
        notificationManager.notify(NOTIFY_TAG, NOTIFY_ID, notification);
    }

    private void showPostalCodeFragment() {
        SetupFragment fragment = new PostalCodeFragment();
        fragment.setShortDistance(SetupFragment.FRAGMENT_ENTER_TRANSITION
                | SetupFragment.FRAGMENT_RETURN_TRANSITION);
        showFragment(fragment, true);
    }

    private void showConnectionTypeFragment() {
        SetupFragment fragment = new ConnectionTypeFragment();
        fragment.setShortDistance(SetupFragment.FRAGMENT_ENTER_TRANSITION
                | SetupFragment.FRAGMENT_RETURN_TRANSITION);
        showFragment(fragment, true);
    }

    /**
     * Cancels the previously shown notification.
     *
     * @param context a {@link Context} instance
     */
    public static void cancelNotification(Context context) {
        NotificationManager notificationManager = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(NOTIFY_TAG, NOTIFY_ID);
    }

    @VisibleForTesting
    static class TunerHalFactory {
        private Context mContext;
        @VisibleForTesting
        TunerHal mTunerHal;
        private GenerateTunerHalTask mGenerateTunerHalTask;
        private final Executor mExecutor;

        TunerHalFactory(Context context) {
            this(context, AsyncTask.SERIAL_EXECUTOR);
        }

        TunerHalFactory(Context context, Executor executor) {
            mContext = context;
            mExecutor = executor;
        }

        /**
         * Returns tuner HAL currently used. If it's {@code null} and tuner HAL is not generated
         * before, tries to generate it synchronously.
         */
        @WorkerThread
        TunerHal getOrCreate() {
            if (mGenerateTunerHalTask != null
                    && mGenerateTunerHalTask.getStatus() != AsyncTask.Status.FINISHED) {
                try {
                    return mGenerateTunerHalTask.get();
                } catch (Exception e) {
                    Log.e(TAG, "Cannot get Tuner HAL: " + e);
                }
            } else if (mGenerateTunerHalTask == null && mTunerHal == null) {
                mTunerHal = createInstance();
            }
            return mTunerHal;
        }

        /**
         * Generates tuner hal for scanning with asynchronous tasks.
         */
        @MainThread
        void generate() {
            if (mGenerateTunerHalTask == null && mTunerHal == null) {
                mGenerateTunerHalTask = new GenerateTunerHalTask();
                mGenerateTunerHalTask.executeOnExecutor(mExecutor);
            }
        }

        /**
         * Clears the currently used tuner hal.
         */
        @MainThread
        void clear() {
            if (mGenerateTunerHalTask != null) {
                mGenerateTunerHalTask.cancel(true);
                mGenerateTunerHalTask = null;
            }
            if (mTunerHal != null) {
                AutoCloseableUtils.closeQuietly(mTunerHal);
                mTunerHal = null;
            }
        }

        @WorkerThread
        protected TunerHal createInstance() {
            return TunerHal.createInstance(mContext);
        }

        class GenerateTunerHalTask extends AsyncTask<Void, Void, TunerHal> {
            @Override
            protected TunerHal doInBackground(Void... args) {
                return createInstance();
            }

            @Override
            protected void onPostExecute(TunerHal tunerHal) {
                mTunerHal = tunerHal;
            }
        }
    }
}