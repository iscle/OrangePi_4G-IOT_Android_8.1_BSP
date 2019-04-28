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
package com.android.support.car.lenspicker;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.PatternMatcher;
import android.provider.MediaStore;
import android.support.annotation.StringRes;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.TextView;

import com.android.car.stream.ui.ColumnCalculator;
import com.android.car.view.PagedListView;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * An activity that is displayed when the system attempts to start an Intent for which there is
 * more than one matching activity, allowing the user to decide which to go to.
 *
 * <p>This activity replaces the default ResolverActivity that Android uses.
 */
public class LensResolverActivity extends Activity implements
        ResolverListRow.ResolverSelectionHandler {
    private static final String TAG = "LensResolverActivity";
    private CheckBox mAlwaysCheckbox;

    /**
     * {@code true} if this ResolverActivity is asking to the user to determine the default
     * launcher.
     */
    private boolean mResolvingHome;

    /**
     * The Intent to disambiguate.
     */
    private Intent mResolveIntent;

    /**
     * A set of {@link ComponentName}s that represent the list of activities that the user is
     * picking from to handle {@link #mResolveIntent}.
     */
    private ComponentName[] mComponentSet;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // It seems that the title bar is added when this Activity is called by the system despite
        // the theme of this Activity specifying otherwise. As a result, explicitly turn off the
        // title bar.
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.resolver_list);

        mResolveIntent = new Intent(getIntent());

        // Clear the component since it would have been set to this LensResolverActivity.
        mResolveIntent.setComponent(null);

        // The resolver activity is set to be hidden from recent tasks. This attribute should not
        // be propagated to the next activity being launched.  Note that if the original Intent
        // also had this flag set, we are now losing it.  That should be a very rare case though.
        mResolveIntent.setFlags(
                mResolveIntent.getFlags()&~Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);

        // Check if we are setting the default launcher.
        Set<String> categories = mResolveIntent.getCategories();
        if (Intent.ACTION_MAIN.equals(mResolveIntent.getAction()) && categories != null
                && categories.size() == 1 && categories.contains(Intent.CATEGORY_HOME)) {
            mResolvingHome = true;
        }

        List<ResolveInfo> infos = getPackageManager().queryIntentActivities(mResolveIntent,
                PackageManager.MATCH_DEFAULT_ONLY | PackageManager.GET_RESOLVED_FILTER);
        buildComponentSet(infos);

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            int size = infos == null ? 0 : infos.size();
            Log.d(TAG, "Found " + size + " matching activities.");
        }

        // The title container should match the width of the StreamCards in the list. Those cards
        // have their width set depending on the column span, which changes between screen sizes.
        // As a result, need to set the width of the title container programmatically.
        int defaultColumnSpan =
                getResources().getInteger(R.integer.stream_card_default_column_span);
        int cardWidth = ColumnCalculator.getInstance(this /* context */).getSizeForColumnSpan(
                defaultColumnSpan);
        View titleAndCheckboxContainer = findViewById(R.id.title_checkbox_container);
        titleAndCheckboxContainer.getLayoutParams().width = cardWidth;

        mAlwaysCheckbox = (CheckBox) findViewById(R.id.always_checkbox);

        PagedListView pagedListView = (PagedListView) findViewById(R.id.list_view);
        pagedListView.setLightMode();

        ResolverAdapter adapter = new ResolverAdapter(this /* context */, infos);
        adapter.setSelectionHandler(this);
        pagedListView.setAdapter(adapter);

        TextView title = (TextView) findViewById(R.id.title);
        title.setText(getTitleForAction(mResolveIntent.getAction()));

        findViewById(R.id.dismiss_area).setOnClickListener(v -> finish());
    }

    /**
     * Constructs a set of {@link ComponentName}s that represent the set of activites that the user
     * was picking from within this list presented by this resolver activity.
     */
    private void buildComponentSet(List<ResolveInfo> infos) {
        int size = infos.size();
        mComponentSet = new ComponentName[size];

        for (int i = 0; i < size; i++) {
            ResolveInfo info = infos.get(i);
            mComponentSet[i] = new ComponentName(info.activityInfo.packageName,
                    info.activityInfo.name);
        }
    }

    /**
     * Returns the title that should be used for the given Intent action.
     *
     * @param action One of the actions in Intent, such as {@link Intent#ACTION_VIEW}.
     */
    private CharSequence getTitleForAction(String action) {
        ActionTitle title = mResolvingHome ? ActionTitle.HOME : ActionTitle.forAction(action);
        return getString(title.titleRes);
    }

    /**
     * Opens the activity that is specified by the given {@link ResolveInfo} and
     * {@link LensPickerItem}. If the {@link #mAlwaysCheckbox} has been checked, then the
     * activity will be set as the default activity for Intents of a matching format to
     * {@link #mResolveIntent}.
     */
    @Override
    public void onActivitySelected(ResolveInfo info, LensPickerItem item) {
        ComponentName component = item.getLaunchIntent().getComponent();

        if (mAlwaysCheckbox.isChecked()) {
            IntentFilter filter = buildIntentFilterForResolveInfo(info);
            getPackageManager().addPreferredActivity(filter, info.match, mComponentSet, component);
        }

        // Now launch the original resolve intent but correctly set the component.
        Intent launchIntent = new Intent(mResolveIntent);
        launchIntent.setComponent(component);

        // It might be necessary to use startActivityAsCaller() instead. The default
        // ResolverActivity does this. However, that method is unavailable to be called from
        // classes that are do not have "android" in the package name. As a result, just utilize
        // a regular startActivity(). If it becomes necessary to utilize this method, then
        // LensResolverActivity will have to extend ResolverActivity.
        startActivity(launchIntent);
        finish();
    }

    /**
     * Returns an {@link IntentFilter} based on the given {@link ResolveInfo} so that the
     * activity specified by that ResolveInfo will be the default for Intents like
     * {@link #mResolveIntent}.
     *
     * <p>This code is copied from com.android.internal.app.ResolverActivity.
     */
    private IntentFilter buildIntentFilterForResolveInfo(ResolveInfo info) {
        // Build a reasonable intent filter, based on what matched.
        IntentFilter filter = new IntentFilter();
        Intent filterIntent;

        if (mResolveIntent.getSelector() != null) {
            filterIntent = mResolveIntent.getSelector();
        } else {
            filterIntent = mResolveIntent;
        }

        String action = filterIntent.getAction();
        if (action != null) {
            filter.addAction(action);
        }
        Set<String> categories = filterIntent.getCategories();
        if (categories != null) {
            for (String cat : categories) {
                filter.addCategory(cat);
            }
        }
        filter.addCategory(Intent.CATEGORY_DEFAULT);

        int cat = info.match & IntentFilter.MATCH_CATEGORY_MASK;
        Uri data = filterIntent.getData();
        if (cat == IntentFilter.MATCH_CATEGORY_TYPE) {
            String mimeType = filterIntent.resolveType(this);
            if (mimeType != null) {
                try {
                    filter.addDataType(mimeType);
                } catch (IntentFilter.MalformedMimeTypeException e) {
                    Log.e(TAG, "Could not add data type", e);
                    filter = null;
                }
            }
        }
        if (data != null && data.getScheme() != null) {
            // We need the data specification if there was no type OR if the scheme is not one of
            // our magical "file:" or "content:" schemes (see IntentFilter for the reason).
            if (cat != IntentFilter.MATCH_CATEGORY_TYPE
                    || (!"file".equals(data.getScheme())
                    && !"content".equals(data.getScheme()))) {
                filter.addDataScheme(data.getScheme());

                // Look through the resolved filter to determine which part of it matched the
                // original Intent.
                Iterator<PatternMatcher> pIt = info.filter.schemeSpecificPartsIterator();
                if (pIt != null) {
                    String ssp = data.getSchemeSpecificPart();
                    while (ssp != null && pIt.hasNext()) {
                        PatternMatcher p = pIt.next();
                        if (p.match(ssp)) {
                            filter.addDataSchemeSpecificPart(p.getPath(), p.getType());
                            break;
                        }
                    }
                }
                Iterator<IntentFilter.AuthorityEntry> aIt = info.filter.authoritiesIterator();
                if (aIt != null) {
                    while (aIt.hasNext()) {
                        IntentFilter.AuthorityEntry a = aIt.next();
                        if (a.match(data) >= 0) {
                            int port = a.getPort();
                            filter.addDataAuthority(a.getHost(),
                                    port >= 0 ? Integer.toString(port) : null);
                            break;
                        }
                    }
                }
                pIt = info.filter.pathsIterator();
                if (pIt != null) {
                    String path = data.getPath();
                    while (path != null && pIt.hasNext()) {
                        PatternMatcher p = pIt.next();
                        if (p.match(path)) {
                            filter.addDataPath(p.getPath(), p.getType());
                            break;
                        }
                    }
                }
            }
        }

        return filter;
    }

    @Override
    protected void onStop() {
        super.onStop();

        if ((getIntent().getFlags() & FLAG_ACTIVITY_NEW_TASK) != 0 && !isVoiceInteraction()) {
            // This resolver is in the unusual situation where it has been launched at the top of a
            // new task.  We don't let it be added to the recent tasks shown to the user, and we
            // need to make sure that each time we are launched we get the correct launching
            // uid (not re-using the same resolver from an old launching uid), so we will now
            // finish since being no longer visible, the user probably can't get back to us.
            if (!isChangingConfigurations()) {
                finish();
            }
        }
    }

    /**
     * An enum mapping different Intent actions to the strings that should be displayed that
     * explain to the user what this ResolverActivity is doing.
     */
    private enum ActionTitle {
        VIEW(Intent.ACTION_VIEW,
                R.string.whichViewApplication,
                R.string.whichViewApplicationNamed,
                R.string.whichViewApplicationLabel),
        EDIT(Intent.ACTION_EDIT,
                R.string.whichEditApplication,
                R.string.whichEditApplicationNamed,
                R.string.whichEditApplicationLabel),
        SEND(Intent.ACTION_SEND,
                R.string.whichSendApplication,
                R.string.whichSendApplicationNamed,
                R.string.whichSendApplicationLabel),
        SENDTO(Intent.ACTION_SENDTO,
                R.string.whichSendToApplication,
                R.string.whichSendToApplicationNamed,
                R.string.whichSendToApplicationLabel),
        SEND_MULTIPLE(Intent.ACTION_SEND_MULTIPLE,
                R.string.whichSendApplication,
                R.string.whichSendApplicationNamed,
                R.string.whichSendApplicationLabel),
        CAPTURE_IMAGE(MediaStore.ACTION_IMAGE_CAPTURE,
                R.string.whichImageCaptureApplication,
                R.string.whichImageCaptureApplicationNamed,
                R.string.whichImageCaptureApplicationLabel),
        DEFAULT(null,
                R.string.whichApplication,
                R.string.whichApplicationNamed,
                R.string.whichApplicationLabel),
        HOME(Intent.ACTION_MAIN,
                R.string.whichHomeApplication,
                R.string.whichHomeApplicationNamed,
                R.string.whichHomeApplicationLabel);

        public final String action;
        public final int titleRes;
        public final int namedTitleRes;

        @StringRes
        public final int labelRes;

        ActionTitle(String action, int titleRes, int namedTitleRes, @StringRes int labelRes) {
            this.action = action;
            this.titleRes = titleRes;
            this.namedTitleRes = namedTitleRes;
            this.labelRes = labelRes;
        }

        /**
         * Returns a set of Strings that should be used for the given Intent action.
         */
        public static ActionTitle forAction(String action) {
            for (ActionTitle title : values()) {
                if (title != HOME && action != null && action.equals(title.action)) {
                    return title;
                }
            }
            return DEFAULT;
        }
    }
}
