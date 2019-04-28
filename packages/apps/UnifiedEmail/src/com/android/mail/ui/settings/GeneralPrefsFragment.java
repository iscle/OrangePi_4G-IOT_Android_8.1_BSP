/*******************************************************************************
 *      Copyright (C) 2014 Google Inc.
 *      Licensed to The Android Open Source Project.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 *******************************************************************************/

package com.android.mail.ui.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import com.android.mail.preferences.MailPrefs;
import com.android.mail.preferences.MailPrefs.PreferenceKeys;
import com.android.mail.providers.SuggestionsProvider;
import com.android.mail.providers.UIProvider.AutoAdvance;
import com.android.mail.utils.LogUtils;
import com.android.mail.R;
import com.google.common.annotations.VisibleForTesting;

/**
 * This fragment shows general app preferences.
 */
public class GeneralPrefsFragment extends MailPreferenceFragment
        implements OnClickListener, OnPreferenceChangeListener {

    // Keys used to reference pref widgets which don't map directly to preference entries
    static final String AUTO_ADVANCE_WIDGET = "auto-advance-widget";

    static final String CALLED_FROM_TEST = "called-from-test";

    // Category for removal actions
    protected static final String REMOVAL_ACTIONS_GROUP = "removal-actions-group";

    protected MailPrefs mMailPrefs;

    private AlertDialog mClearSearchHistoryDialog;

    private ListPreference mAutoAdvance;
    private static final int[] AUTO_ADVANCE_VALUES = {
            AutoAdvance.NEWER,
            AutoAdvance.OLDER,
            AutoAdvance.LIST
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);

        mMailPrefs = MailPrefs.get(getActivity());

        // Set the shared prefs name to use prefs auto-persist behavior by default.
        // Any pref more complex than the default (say, involving migration), should set
        // "persistent=false" in the XML and manually handle preference initialization and change.
        getPreferenceManager()
                .setSharedPreferencesName(mMailPrefs.getSharedPreferencesName());

        addPreferencesFromResource(R.xml.general_preferences);

        mAutoAdvance = (ListPreference) findPreference(AUTO_ADVANCE_WIDGET);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        /*
         * We deliberately do not call super because our menu includes the parent's menu options to
         * allow custom ordering.
         */
        menu.clear();
        inflater.inflate(R.menu.general_prefs_fragment_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == R.id.clear_search_history_menu_item) {
            clearSearchHistory();
            return true;
        } else if (itemId == R.id.clear_picture_approvals_menu_item) {
            clearDisplayImages();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (getActivity() == null) {
            // Monkeys cause bad things. This callback may be delayed if for some reason the
            // preference screen was closed really quickly - just bail then.
            return false;
        }

        final String key = preference.getKey();

        if (PreferenceKeys.REMOVAL_ACTION.equals(key)) {
            final String removalAction = newValue.toString();
            mMailPrefs.setRemovalAction(removalAction);
        } else if (AUTO_ADVANCE_WIDGET.equals(key)) {
            final int prefsAutoAdvanceMode =
                    AUTO_ADVANCE_VALUES[mAutoAdvance.findIndexOfValue((String) newValue)];
            mMailPrefs.setAutoAdvanceMode(prefsAutoAdvanceMode);
        } else if (!PreferenceKeys.CONVERSATION_LIST_SWIPE.equals(key) &&
                !PreferenceKeys.SHOW_SENDER_IMAGES.equals(key) &&
                !PreferenceKeys.DEFAULT_REPLY_ALL.equals(key) &&
                !PreferenceKeys.CONVERSATION_OVERVIEW_MODE.equals(key) &&
                !PreferenceKeys.CONFIRM_DELETE.equals(key) &&
                !PreferenceKeys.CONFIRM_ARCHIVE.equals(key) &&
                !PreferenceKeys.CONFIRM_SEND.equals(key)) {
            return false;
        }

        return true;
    }

    private void clearDisplayImages() {
        final ClearPictureApprovalsDialogFragment fragment =
                ClearPictureApprovalsDialogFragment.newInstance();
        fragment.show(getActivity().getFragmentManager(),
                ClearPictureApprovalsDialogFragment.FRAGMENT_TAG);
    }

    private void clearSearchHistory() {
        mClearSearchHistoryDialog = new AlertDialog.Builder(getActivity())
                .setMessage(R.string.clear_history_dialog_message)
                .setTitle(R.string.clear_history_dialog_title)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setPositiveButton(R.string.clear, this)
                .setNegativeButton(R.string.cancel, this)
                .show();
    }


    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (dialog.equals(mClearSearchHistoryDialog)) {
            if (which == DialogInterface.BUTTON_POSITIVE) {
                final Context context = getActivity();
                // Clear the history in the background, as it causes a disk
                // write.
                new AsyncTask<Void, Void, Void>() {
                    @Override
                    protected Void doInBackground(Void... params) {
                        final SuggestionsProvider suggestions =
                                new SuggestionsProvider(context);
                        suggestions.clearHistory();
                        suggestions.cleanup();
                        return null;
                    }
                }.execute();
                Toast.makeText(getActivity(), R.string.search_history_cleared, Toast.LENGTH_SHORT)
                        .show();
            }
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mClearSearchHistoryDialog != null && mClearSearchHistoryDialog.isShowing()) {
            mClearSearchHistoryDialog.dismiss();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // Manually initialize the preference views that require massaging. Prefs that require
        // massaging include:
        //  1. a prefs UI control that does not map 1:1 to storage
        //  2. a pref that must obtain its initial value from migrated storage, and for which we
        //     don't want to always persist a migrated value
        final int autoAdvanceModeIndex = prefValueToWidgetIndex(AUTO_ADVANCE_VALUES,
                mMailPrefs.getAutoAdvanceMode(), AutoAdvance.DEFAULT);
        mAutoAdvance.setValueIndex(autoAdvanceModeIndex);

        listenForPreferenceChange(
                PreferenceKeys.REMOVAL_ACTION,
                PreferenceKeys.CONVERSATION_LIST_SWIPE,
                PreferenceKeys.SHOW_SENDER_IMAGES,
                PreferenceKeys.DEFAULT_REPLY_ALL,
                PreferenceKeys.CONVERSATION_OVERVIEW_MODE,
                AUTO_ADVANCE_WIDGET,
                PreferenceKeys.CONFIRM_DELETE,
                PreferenceKeys.CONFIRM_ARCHIVE,
                PreferenceKeys.CONFIRM_SEND
        );
    }

    protected boolean supportsArchive() {
        return true;
    }

    /**
     * Converts the prefs value into an index useful for configuring the UI widget, falling back to
     * the default value if the value from the prefs can't be found for some reason. If neither can
     * be found, it throws an {@link java.lang.IllegalArgumentException}
     *
     * @param conversionArray An array of prefs values, in widget order
     * @param prefValue Value of the preference
     * @param defaultValue Default value, as a fallback if we can't map the real value
     * @return Index of the entry (or fallback) in the conversion array
     */
    @VisibleForTesting
    static int prefValueToWidgetIndex(int[] conversionArray, int prefValue, int defaultValue) {
        for (int i = 0; i < conversionArray.length; i++) {
            if (conversionArray[i] == prefValue) {
                return i;
            }
        }
        LogUtils.e(LogUtils.TAG, "Can't map preference value " + prefValue);
        for (int i = 0; i < conversionArray.length; i++) {
            if (conversionArray[i] == defaultValue) {
                return i;
            }
        }
        throw new IllegalArgumentException("Can't map default preference value " + prefValue);
    }

    private void listenForPreferenceChange(String... keys) {
        for (String key : keys) {
            Preference p = findPreference(key);
            if (p != null) {
                p.setOnPreferenceChangeListener(this);
            }
        }
    }
}
