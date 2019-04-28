/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.storagemanager.deletionhelper;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.VisibleForTesting;
import android.text.format.Formatter;
import com.android.storagemanager.R;

import java.util.concurrent.TimeUnit;

/**
 * Fragment for activating the storage manager after a manual clear.
 */
public class StorageManagerUpsellDialog extends DialogFragment
        implements DialogInterface.OnClickListener, DialogInterface.OnDismissListener {
    public static final String TAG = "StorageManagerUpsellDialog";
    private static final String SHARED_PREFERENCES_NAME = "StorageManagerUpsellDialog";
    private static final String NEXT_SHOW_TIME = "next_show_time";
    private static final String DISMISSED_COUNT = "dismissed_count";
    private static final String NO_THANKS_COUNT = "no_thanks_count";

    private static final String ARGS_FREED_BYTES = "freed_bytes";

    private static final long NEVER = -1;
    private static final long DISMISS_SHORT_DELAY = TimeUnit.DAYS.toMillis(14);
    private static final long DISMISS_LONG_DELAY = TimeUnit.DAYS.toMillis(90);
    private static final int DISMISS_LONG_THRESHOLD = 9;
    private static final long NO_THANKS_SHORT_DELAY = TimeUnit.DAYS.toMillis(90);
    private static final long NO_THANKS_LONG_DELAY = NEVER;
    private static final int NO_THANKS_LONG_THRESHOLD = 3;

    private Clock mClock;

    public static StorageManagerUpsellDialog newInstance(long freedBytes) {
        StorageManagerUpsellDialog dialog = new StorageManagerUpsellDialog();
        Bundle args = new Bundle(1);
        args.putLong(ARGS_FREED_BYTES, freedBytes);
        dialog.setArguments(args);
        return dialog;
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    protected void setClock(Clock clock) {
        mClock = clock;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Bundle args = getArguments();
        long freedBytes = args.getLong(ARGS_FREED_BYTES);

        final Context context = getContext();
        return new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.deletion_helper_upsell_title))
                .setMessage(context.getString(R.string.deletion_helper_upsell_summary,
                        Formatter.formatFileSize(context, freedBytes)))
                .setPositiveButton(R.string.deletion_helper_upsell_activate, this)
                .setNegativeButton(R.string.deletion_helper_upsell_cancel, this)
                .create();
    }

    @Override
    public void onClick(DialogInterface dialog, int buttonId) {
        if (buttonId == DialogInterface.BUTTON_POSITIVE) {
            Settings.Secure.putInt(getActivity().getContentResolver(),
                    Settings.Secure.AUTOMATIC_STORAGE_MANAGER_ENABLED, 1);
        } else {
            SharedPreferences sp = getSharedPreferences(getContext());
            int noThanksCount = sp.getInt(NO_THANKS_COUNT, 0) + 1;
            SharedPreferences.Editor editor = sp.edit();
            editor.putInt(NO_THANKS_COUNT, noThanksCount);
            long noThanksDelay = getNoThanksDelay(noThanksCount);
            long nextShowTime = noThanksDelay == NEVER ? NEVER : getCurrentTime() + noThanksDelay;
            editor.putLong(NEXT_SHOW_TIME, nextShowTime);
            editor.apply();
        }

        finishActivity();
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        SharedPreferences sp = getSharedPreferences(getContext());
        int dismissCount = sp.getInt(DISMISSED_COUNT, 0) + 1;
        SharedPreferences.Editor editor = sp.edit();
        editor.putInt(DISMISSED_COUNT, dismissCount);
        editor.putLong(NEXT_SHOW_TIME, getCurrentTime() + getDismissDelay(dismissCount));
        editor.apply();

        finishActivity();
    }

    /**
     * Returns if the dialog should be shown, given the delays between when it is shown.
     * @param context Context to get shared preferences for determining the next show time.
     * @param time The current time in millis.
     */
    public static boolean shouldShow(Context context, long time) {
        boolean isEnabled =
                Settings.Secure.getInt(context.getContentResolver(),
                        Settings.Secure.AUTOMATIC_STORAGE_MANAGER_ENABLED, 0) != 0;
        if (isEnabled) {
            return false;
        }

        long nextTimeToShow = getSharedPreferences(context).getLong(NEXT_SHOW_TIME, 0);
        if (nextTimeToShow == NEVER) {
            return false;
        }

        return time >= nextTimeToShow;
    }

    private static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    private static long getNoThanksDelay(int noThanksCount) {
        return (noThanksCount > NO_THANKS_LONG_THRESHOLD)
                ? NO_THANKS_LONG_DELAY : NO_THANKS_SHORT_DELAY;
    }

    private static long getDismissDelay(int dismissCount) {
        return (dismissCount > DISMISS_LONG_THRESHOLD)
                ? DISMISS_LONG_DELAY : DISMISS_SHORT_DELAY;
    }

    private void finishActivity() {
        Activity activity = getActivity();
        if (activity != null) {
            activity.finish();
        }
    }

    private long getCurrentTime() {
        if (mClock == null) {
            mClock = new Clock();
        }

        return mClock.currentTimeMillis();
    }

    /**
     * Clock provides the current time.
     */
    protected static class Clock {
        /**
         * Returns the current time in milliseconds.
         */
        public long currentTimeMillis() {
            return System.currentTimeMillis();
        }
    }
}
