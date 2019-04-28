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

package com.android.documentsui.sidebar;

import android.annotation.Nullable;
import android.content.Context;
import android.provider.DocumentsProvider;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.DragEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.documentsui.ActionHandler;
import com.android.documentsui.MenuManager;
import com.android.documentsui.R;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.RootInfo;

/**
 * An {@link Item} for each root provided by {@link DocumentsProvider}s.
 */
class RootItem extends Item {
    private static final String STRING_ID_FORMAT = "RootItem{%s/%s}";

    public final RootInfo root;
    public @Nullable DocumentInfo docInfo;

    private final ActionHandler mActionHandler;

    public RootItem(RootInfo root, ActionHandler actionHandler) {
        super(R.layout.item_root, getStringId(root));
        this.root = root;
        mActionHandler = actionHandler;
    }

    private static String getStringId(RootInfo root) {
        // Empty URI authority is invalid, so we can use empty string if root.authority is null.
        // Directly passing null to String.format() will write "null" which can be a valid URI
        // authority.
        String authority = (root.authority == null ? "" : root.authority);
        return String.format(STRING_ID_FORMAT, authority, root.rootId);
    }

    @Override
    public void bindView(View convertView) {
        final ImageView icon = (ImageView) convertView.findViewById(android.R.id.icon);
        final TextView title = (TextView) convertView.findViewById(android.R.id.title);
        final TextView summary = (TextView) convertView.findViewById(android.R.id.summary);
        final ImageView ejectIcon = (ImageView) convertView.findViewById(R.id.eject_icon);

        final Context context = convertView.getContext();
        icon.setImageDrawable(root.loadDrawerIcon(context));
        title.setText(root.title);

        if (root.supportsEject()) {
            ejectIcon.setVisibility(View.VISIBLE);
            ejectIcon.setImageDrawable(root.loadEjectIcon(context));
            ejectIcon.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View unmountIcon) {
                    RootsFragment.ejectClicked(unmountIcon, root, mActionHandler);
                }
            });
        } else {
            ejectIcon.setVisibility(View.GONE);
            ejectIcon.setOnClickListener(null);
        }
        // Show available space if no summary
        String summaryText = root.summary;
        if (TextUtils.isEmpty(summaryText) && root.availableBytes >= 0) {
            summaryText = context.getString(R.string.root_available_bytes,
                    Formatter.formatFileSize(context, root.availableBytes));
        }

        summary.setText(summaryText);
        summary.setVisibility(TextUtils.isEmpty(summaryText) ? View.GONE : View.VISIBLE);
    }

    @Override
    boolean isRoot() {
        return true;
    }

    @Override
    void open() {
        mActionHandler.openRoot(root);
    }

    @Override
    boolean isDropTarget() {
        return root.supportsCreate();
    }

    @Override
    boolean dropOn(DragEvent event) {
        return mActionHandler.dropOn(event, root);
    }

    @Override
    void createContextMenu(Menu menu, MenuInflater inflater, MenuManager menuManager) {
        inflater.inflate(R.menu.root_context_menu, menu);
        menuManager.updateRootContextMenu(menu, root, docInfo);
    }

    @Override
    public String toString() {
        return "RootItem{"
                + "id=" + stringId
                + ", root=" + root
                + ", docInfo=" + docInfo
                + "}";
    }
}
