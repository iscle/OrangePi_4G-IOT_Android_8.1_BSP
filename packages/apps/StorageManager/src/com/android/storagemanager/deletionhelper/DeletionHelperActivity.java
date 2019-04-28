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

package com.android.storagemanager.deletionhelper;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView.BufferType;
import com.android.settingslib.widget.LinkTextView;
import com.android.storagemanager.ButtonBarProvider;
import com.android.storagemanager.R;
import com.android.storagemanager.utils.Utils;

/**
 * The DeletionHelperActivity is an activity for deleting apps, photos, and downloaded files which
 * have not been recently used.
 */
public class DeletionHelperActivity extends Activity implements ButtonBarProvider {
    private static final int ENABLED = 1;

    private ViewGroup mButtonBar;
    private Button mNextButton, mSkipButton;
    private DeletionHelperSettings mFragment;
    private boolean mIsShowingInterstitial;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_main_prefs);

        setIsEmptyState(false /* isEmptyState */);

        // If we are not returning from an existing activity, create a new fragment.
        if (savedInstanceState == null) {
            FragmentManager manager = getFragmentManager();
            mFragment = DeletionHelperSettings.newInstance(AppsAsyncLoader.NORMAL_THRESHOLD);
            manager.beginTransaction().replace(R.id.main_content, mFragment).commit();
        }
        SpannableString linkText =
                new SpannableString(
                        getString(R.string.empty_state_review_items_link).toUpperCase());
        LinkTextView emptyStateLink = (LinkTextView) findViewById(R.id.all_items_link);
        linkText = NoThresholdSpan.linkify(linkText, this);
        emptyStateLink.setText(linkText, BufferType.SPANNABLE);

        mButtonBar = (ViewGroup) findViewById(R.id.button_bar);
        mNextButton = (Button) findViewById(R.id.next_button);
        mSkipButton = (Button) findViewById(R.id.skip_button);

        getActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        mFragment.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    void setIsEmptyState(boolean isEmptyState) {
        final View emptyContent = findViewById(R.id.empty_state);
        final View mainContent = findViewById(R.id.main_content);

        // Update UI
        mainContent.setVisibility(isEmptyState ? View.GONE : View.VISIBLE);
        emptyContent.setVisibility(isEmptyState ? View.VISIBLE : View.GONE);
        findViewById(R.id.button_bar).setVisibility(isEmptyState ? View.GONE : View.VISIBLE);
        setTitle(isEmptyState ? R.string.empty_state_title : R.string.deletion_helper_title);

        // We are giving the user the option to show all in the interstitial, so let's hide the
        // overflow for this. (Also, the overflow's functions are busted while the empty view is
        // showing, so this also works around this bug.)
        mIsShowingInterstitial = isEmptyState && emptyContent.getVisibility() != View.VISIBLE;
        invalidateOptionsMenu();
    }

    public boolean isLoadingVisible() {
        View loading_container = findViewById(R.id.loading_container);
        if (loading_container != null) {
            return loading_container.getVisibility() == View.VISIBLE;
        }
        return false;
    }

    public void setLoading(View listView, boolean loading, boolean animate) {
        View loading_container = findViewById(R.id.loading_container);
        Utils.handleLoadingContainer(loading_container, listView, !loading, animate);
        getButtonBar().setVisibility(loading ? View.GONE : View.VISIBLE);
    }

    @Override
    public ViewGroup getButtonBar() {
        return mButtonBar;
    }

    @Override
    public Button getNextButton() {
        return mNextButton;
    }

    @Override
    public Button getSkipButton() {
        return mSkipButton;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        final int isNonthresholdAvailable =
                Settings.Global.getInt(
                        getContentResolver(),
                        Settings.Global.ENABLE_DELETION_HELPER_NO_THRESHOLD_TOGGLE,
                        ENABLED);
        if (isNonthresholdAvailable < ENABLED || mIsShowingInterstitial) {
            return false;
        }

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.deletion_helper_settings_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        FragmentManager manager = getFragmentManager();
        int thresholdType;
        switch (item.getItemId()) {
            case R.id.no_threshold:
                thresholdType = AppStateUsageStatsBridge.NO_THRESHOLD;
                break;
            case R.id.default_threshold:
                thresholdType = AppStateUsageStatsBridge.NORMAL_THRESHOLD;
                break;
            default:
                return super.onOptionsItemSelected(item);
        }

        mFragment = DeletionHelperSettings.newInstance(thresholdType);
        manager.beginTransaction().replace(R.id.main_content, mFragment).commit();
        return true;
    }

    @Override
    public boolean onNavigateUp() {
        finish();
        return true;
    }

    private static class NoThresholdSpan extends ClickableSpan {
        private final DeletionHelperActivity mParent;

        public NoThresholdSpan(DeletionHelperActivity parent) {
            super();
            mParent = parent;
        }

        @Override
        public void onClick(View widget) {
            FragmentManager manager = mParent.getFragmentManager();
            Fragment fragment = DeletionHelperSettings.newInstance(AppsAsyncLoader.NO_THRESHOLD);
            manager.beginTransaction().replace(R.id.main_content, fragment).commit();
            mParent.setIsEmptyState(false);
        }

        @Override
        public void updateDrawState(TextPaint ds) {
            super.updateDrawState(ds);
            // Remove underline
            ds.setUnderlineText(false);
        }

        /**
         * This method takes a string and turns it into a url span that will launch a
         * SupportSystemInformationDialogFragment
         *
         * @param msg The text to turn into a link
         * @param parent The dialog the text is in
         * @return A CharSequence containing the original text content as a url
         */
        public static SpannableString linkify(SpannableString msg, DeletionHelperActivity parent) {
            NoThresholdSpan link = new NoThresholdSpan(parent);
            msg.setSpan(link, 0, msg.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return msg;
        }
    }
}