/*
 * Copyright 2017, The Android Open Source Project
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
package com.android.managedprovisioning.common;

import static android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK;

import android.app.Activity;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.view.ContextMenu;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import com.android.managedprovisioning.R;

/**
 * Creates a new {@link ContextMenu}, and populates it with a list of links contained in a target
 * {@link TextView}.
 * <p>
 * Known issue: does not listen to TalkBack on / off events.
 */
public class AccessibilityContextMenuMaker {
    private final Activity mActivity;

    /**
     * @param activity the target {@link TextView} belongs to
     */
    public AccessibilityContextMenuMaker(Activity activity) {
        mActivity = activity;
    }

    /**
     * If {@link ClickableSpan} links present, registers a context menu with the {@link Activity}.
     * If no links present, unregisters, which is useful in case of recyclable views.
     *
     * @param textView target TextView potentially containing links.
     */
    public void registerWithActivity(TextView textView) {
        if (getSpans(getText(textView)).length == 0) {
            mActivity.unregisterForContextMenu(textView);
            textView.setAccessibilityDelegate(null);
            textView.setClickable(false);
            textView.setLongClickable(false);
            return;
        }

        mActivity.registerForContextMenu(textView);
        textView.setOnClickListener(View::showContextMenu);
        textView.setLongClickable(false);
        textView.setAccessibilityDelegate(
                new View.AccessibilityDelegate() {
                    @Override
                    public void onInitializeAccessibilityNodeInfo(View host,
                            AccessibilityNodeInfo info) {
                        super.onInitializeAccessibilityNodeInfo(host, info);
                        info.addAction(
                                new AccessibilityNodeInfo.AccessibilityAction(ACTION_CLICK.getId(),
                                        textView.getContext().getString(
                                                R.string.access_list_of_links)));
                    }
                }
        );
    }

    /**
     * Call inside
     * {@link Activity#onCreateContextMenu(ContextMenu, View, ContextMenu.ContextMenuInfo)}
     */
    public void populateMenuContent(ContextMenu menu, TextView textView) {
        if (!isScreenReaderEnabled()) {
            return;
        }

        Spanned spanned = getText(textView);
        ClickableSpan[] spans = getSpans(spanned);

        if (spanned == null || spans.length == 0) {
            return;
        }

        for (ClickableSpan span : spans) {
            int s = spanned.getSpanStart(span);
            int t = spanned.getSpanEnd(span);
            menu.add(spanned.subSequence(s, t)).setOnMenuItemClickListener(menuItem -> {
                span.onClick(textView);
                return false;
            });
        }
        menu.add(R.string.close_list).setOnMenuItemClickListener(menuItem -> {
            menu.close();
            return false;
        });
    }

    private boolean isScreenReaderEnabled() {
        AccessibilityManager am = mActivity.getSystemService(AccessibilityManager.class);
        return am.isEnabled() && am.isTouchExplorationEnabled();
    }

    private @Nullable Spanned getText(TextView textView) {
        CharSequence text = textView.getText();
        return (text instanceof Spanned) ? (Spanned) text : null;
    }

    private @NonNull ClickableSpan[] getSpans(Spanned spanned) {
        if (spanned == null) {
            return new ClickableSpan[0];
        }
        ClickableSpan[] spans = spanned.getSpans(0, spanned.length(), ClickableSpan.class);
        return spans.length == 0 ? new ClickableSpan[0] : spans;
    }
}