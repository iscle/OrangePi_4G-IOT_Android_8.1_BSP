/*
 * Copyright 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.managedprovisioning.preprovisioning.terms;

import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.PROVISIONING_TERMS_ACTIVITY_TIME_MS;
import static com.android.internal.util.Preconditions.checkNotNull;

import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.VisibleForTesting;
import android.util.ArraySet;
import android.view.ContextMenu;
import android.view.View;
import android.widget.ExpandableListView;
import android.widget.TextView;
import android.widget.Toolbar;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.analytics.ProvisioningAnalyticsTracker;
import com.android.managedprovisioning.common.ClickableSpanFactory;
import com.android.managedprovisioning.common.AccessibilityContextMenuMaker;
import com.android.managedprovisioning.common.HtmlToSpannedParser;
import com.android.managedprovisioning.common.SetupLayoutActivity;
import com.android.managedprovisioning.common.StoreUtils;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.preprovisioning.WebActivity;

import java.util.List;
import java.util.Set;

/**
 * Activity responsible for displaying the Terms screen
 */
public class TermsActivity extends SetupLayoutActivity {
    private final TermsProvider mTermsProvider;
    private final AccessibilityContextMenuMaker mContextMenuMaker;
    private final ProvisioningAnalyticsTracker mProvisioningAnalyticsTracker;
    private final Set<Integer> mExpandedGroupsPosition = new ArraySet<>();

    @SuppressWarnings("unused")
    public TermsActivity() {
        this(StoreUtils::readString, null);
    }

    @VisibleForTesting TermsActivity(StoreUtils.TextFileReader textFileReader,
            AccessibilityContextMenuMaker contextMenuMaker) {
        super(new Utils());
        mTermsProvider = new TermsProvider(this, textFileReader, mUtils);
        mProvisioningAnalyticsTracker = ProvisioningAnalyticsTracker.getInstance();
        mContextMenuMaker =
                contextMenuMaker != null ? contextMenuMaker : new AccessibilityContextMenuMaker(
                        this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.terms_screen);
        setTitle(R.string.terms);
        setMainColor(Color.BLACK);

        ProvisioningParams params = checkNotNull(
                getIntent().getParcelableExtra(ProvisioningParams.EXTRA_PROVISIONING_PARAMS));
        List<TermsDocument> terms = mTermsProvider.getTerms(params, 0);

        ExpandableListView container = (ExpandableListView) findViewById(R.id.terms_container);
        container.setAdapter(
                new TermsListAdapter(terms,
                        getLayoutInflater(),
                        new AccessibilityContextMenuMaker(this),
                        new HtmlToSpannedParser(
                                new ClickableSpanFactory(getColor(R.color.blue)),
                                url -> WebActivity.createIntent(this, url,
                                        this.getWindow().getStatusBarColor())),
                        container::isGroupExpanded));
        container.expandGroup(0); // expand the 'General' section

        // Add default open terms to the expanded groups set.
        for (int i = 0; i < terms.size(); i++) {
            if (container.isGroupExpanded(i)) mExpandedGroupsPosition.add(i);
        }

        // keep at most one group expanded at a time
        container.setOnGroupExpandListener((int groupPosition) -> {
            mExpandedGroupsPosition.add(groupPosition);
            for (int i = 0; i < terms.size(); i++) {
                if (i != groupPosition && container.isGroupExpanded(i)) {
                    container.collapseGroup(i);
                }
            }
        });

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> TermsActivity.this.finish());

        mProvisioningAnalyticsTracker.logNumberOfTermsDisplayed(this, terms.size());
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (v instanceof TextView) {
            mContextMenuMaker.populateMenuContent(menu, (TextView) v);
        }
    }

    @Override
    public void onDestroy() {
        mProvisioningAnalyticsTracker.logNumberOfTermsRead(this, mExpandedGroupsPosition.size());
        super.onDestroy();
    }

    @Override
    protected int getMetricsCategory() {
        return PROVISIONING_TERMS_ACTIVITY_TIME_MS;
    }
}