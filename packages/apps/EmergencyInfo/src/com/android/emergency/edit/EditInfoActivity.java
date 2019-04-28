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
package com.android.emergency.edit;

import static android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v14.preference.PreferenceFragment;
import android.support.v7.preference.PreferenceManager;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.android.emergency.PreferenceKeys;
import com.android.emergency.R;
import com.android.emergency.overlay.FeatureFactory;
import com.android.emergency.util.PreferenceUtils;
import com.android.emergency.view.ViewInfoActivity;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;

import java.util.ArrayList;

/**
 * Activity for editing emergency information.
 */
public class EditInfoActivity extends Activity {
    static final String TAG_CLEAR_ALL_DIALOG = "clear_all_dialog";

    private EditInfoFragment mEditInfoFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Protect against b/28401242 by enabling ViewInfoActivity.
        // We used to have code that disabled/enabled it and it could have been left in disabled
        // state.
        PackageManager pm = getPackageManager();
        pm.setComponentEnabledSetting(new ComponentName(this, ViewInfoActivity.class),
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP);

        getActionBar().setDisplayHomeAsUpEnabled(true);

        // We only add a new EditInfoFragment if no fragment is restored.
        Fragment fragment = getFragmentManager().findFragmentById(android.R.id.content);
        if (fragment == null) {
            mEditInfoFragment = new EditInfoFragment();
            getFragmentManager().beginTransaction()
                .add(android.R.id.content, mEditInfoFragment)
                .commit();
        } else {
            mEditInfoFragment = (EditInfoFragment) fragment;
        }

        // Show or hide the settings suggestion, depending on whether any emergency settings exist.
        PreferenceUtils.updateSettingsSuggestionState(this);

        getWindow().addFlags(FLAG_DISMISS_KEYGUARD);
        MetricsLogger.visible(this, MetricsEvent.ACTION_EDIT_EMERGENCY_INFO);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.edit_info_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                // The user asked to navigate up, which, in this case, can easily be accomplished
                // by finishing the activity.
                finish();
                return true;

            case R.id.action_clear_all:
                showClearAllDialog();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** @return The single fragment managed by this activity. */
    @VisibleForTesting
    public PreferenceFragment getFragment() {
        return mEditInfoFragment;
    }

    private void showClearAllDialog() {
        final ClearAllDialogFragment previousFragment =
                (ClearAllDialogFragment) getFragmentManager()
                        .findFragmentByTag(EditInfoActivity.TAG_CLEAR_ALL_DIALOG);
        if (previousFragment == null) {
            DialogFragment newFragment = ClearAllDialogFragment.newInstance();
            newFragment.show(getFragmentManager(), TAG_CLEAR_ALL_DIALOG);
        }
    }

    private void onClearAllPreferences() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        for (String key : PreferenceKeys.KEYS_EDIT_EMERGENCY_INFO) {
            sharedPreferences.edit().remove(key).commit();
        }
        sharedPreferences.edit().remove(PreferenceKeys.KEY_EMERGENCY_CONTACTS).commit();
        // Show the settings suggestion again, since no emergency info is set.
        PreferenceUtils.enableSettingsSuggestion(this);

        // Refresh the UI.
        mEditInfoFragment.reloadFromPreference();
    }

    /**
     * Dialog shown to the user when they tap on the CLEAR ALL menu item. Using a {@link
     * DialogFragment} takes care of screen rotation issues.
     */
    public static class ClearAllDialogFragment extends DialogFragment {

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            Dialog dialog = new AlertDialog.Builder(getActivity())
                    .setMessage(R.string.clear_all_message)
                    .setPositiveButton(R.string.clear, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ((EditInfoActivity) getActivity()).onClearAllPreferences();
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .create();
            return dialog;
        }

        public static DialogFragment newInstance() {
            return new ClearAllDialogFragment();
        }
    }
}
