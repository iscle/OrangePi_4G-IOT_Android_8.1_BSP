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

import static com.android.internal.util.Preconditions.checkNotNull;

import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.BaseExpandableListAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.AccessibilityContextMenuMaker;
import com.android.managedprovisioning.common.HtmlToSpannedParser;

import java.util.List;

/**
 * Allows for displaying {@link TermsDocument} objects in an
 * {@link android.widget.ExpandableListView}.
 */
class TermsListAdapter extends BaseExpandableListAdapter {
    private final List<TermsDocument> mTermsDocuments;
    private final LayoutInflater mInflater;
    private final HtmlToSpannedParser mHtmlToSpannedParser;
    private final AccessibilityContextMenuMaker mContextMenuMaker;
    private final GroupExpandedInfo mGroupExpandedInfo;

    /**
     * Creates a new instance of the class.
     */
    TermsListAdapter(List<TermsDocument> termsDocuments, LayoutInflater layoutInflater,
            AccessibilityContextMenuMaker contextMenuMaker, HtmlToSpannedParser htmlToSpannedParser,
            GroupExpandedInfo groupExpandedInfo) {
        mTermsDocuments = checkNotNull(termsDocuments);
        mInflater = checkNotNull(layoutInflater);
        mHtmlToSpannedParser = checkNotNull(htmlToSpannedParser);
        mGroupExpandedInfo = checkNotNull(groupExpandedInfo);
        mContextMenuMaker = checkNotNull(contextMenuMaker);
    }

    @Override
    public int getGroupCount() {
        return mTermsDocuments.size();
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        return 1; // one content piece per header
    }

    @Override
    public TermsDocument getGroup(int groupPosition) {
        return getDisclaimer(groupPosition);
    }

    @Override
    public TermsDocument getChild(int groupPosition, int childPosition) {
        return getDisclaimer(groupPosition);
    }

    @Override
    public long getGroupId(int groupPosition) {
        return groupPosition;
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return childPosition;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    // TODO: encapsulate this logic - too much direct view manipulation
    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView,
            ViewGroup parent) {
        String heading = getDisclaimer(groupPosition).getHeading();

        View groupView = convertView != null ? convertView : mInflater.inflate(
                R.layout.terms_disclaimer_header, parent, false);
        groupView.setContentDescription(
                parent.getResources().getString(R.string.section_heading, heading));
        groupView.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host,
                    AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                        AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK.getId(),
                        parent.getResources().getString(
                                isExpanded ? R.string.collapse : R.string.expand)));
            }
        });

        TextView textView = groupView.findViewById(R.id.header_text);
        textView.setText(heading);

        ImageView chevron = groupView.findViewById(R.id.chevron);
        chevron.setRotation(isExpanded ? 90 : -90); // chevron down / up retrospectively
        groupView.findViewById(R.id.divider).setVisibility(
                shouldShowGroupDivider(groupPosition) ? View.VISIBLE : View.INVISIBLE);

        return groupView;
    }

    /**
     * Helps avoid a double thick divider line: one above header, one from the bottom of prev child
     */
    private boolean shouldShowGroupDivider(int groupPosition) {
        return mGroupExpandedInfo.isGroupExpanded(groupPosition)
                && (groupPosition == 0 || !mGroupExpandedInfo.isGroupExpanded(groupPosition - 1));
    }

    // TODO: encapsulate this logic - too much direct view manipulation
    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild,
            View convertView, ViewGroup parent) {
        View view = convertView != null ? convertView : mInflater.inflate(
                R.layout.terms_disclaimer_content, parent, false);

        TermsDocument disclaimer = getDisclaimer(groupPosition);
        TextView textView = view.findViewById(R.id.disclaimer_content);
        Spanned content = mHtmlToSpannedParser.parseHtml(disclaimer.getContent());
        textView.setText(content);
        textView.setContentDescription(
                parent.getResources().getString(R.string.section_content, disclaimer.getHeading(),
                        content));
        textView.setMovementMethod(LinkMovementMethod.getInstance()); // makes html links clickable
        mContextMenuMaker.registerWithActivity(textView);
        return view;
    }

    private TermsDocument getDisclaimer(int index) {
        return mTermsDocuments.get(index);
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return false;
    }

    interface GroupExpandedInfo {
        boolean isGroupExpanded(int groupPosition);
    }
}