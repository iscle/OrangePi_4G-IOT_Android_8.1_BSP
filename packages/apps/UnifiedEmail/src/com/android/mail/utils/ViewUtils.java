/**
 * Copyright (c) 2013, Google Inc.
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

package com.android.mail.utils;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.support.annotation.ColorRes;
import android.support.v4.view.ViewCompat;
import android.view.View;
import android.view.ViewParent;
import android.view.Window;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;

/**
 * Utility class to perform some common operations on views.
 */
public class ViewUtils {

    /**
     * Determines whether the given view has RTL layout. NOTE: do not call this
     * on a view until it has been measured. This value is not guaranteed to be
     * accurate until then.
     */
    public static boolean isViewRtl(View view) {
        return ViewCompat.getLayoutDirection(view) == ViewCompat.LAYOUT_DIRECTION_RTL;
    }

    /**
     * @return the start padding of the view. Prior to API 17, will return the left padding.
     */
    @SuppressLint("NewApi")
    public static int getPaddingStart(View view) {
        return Utils.isRunningJBMR1OrLater() ? view.getPaddingStart() : view.getPaddingLeft();
    }

    /**
     * @return the end padding of the view. Prior to API 17, will return the right padding.
     */
    @SuppressLint("NewApi")
    public static int getPaddingEnd(View view) {
        return Utils.isRunningJBMR1OrLater() ? view.getPaddingEnd() : view.getPaddingRight();
    }

    /**
     * Sets the text alignment of the view. Prior to API 17, will no-op.
     */
    @SuppressLint("NewApi")
    public static void setTextAlignment(View view, int textAlignment) {
        if (Utils.isRunningJBMR1OrLater()) {
            view.setTextAlignment(textAlignment);
        }
    }

    /**
     * Convenience method for sending a {@link android.view.accessibility.AccessibilityEvent#TYPE_ANNOUNCEMENT}
     * {@link android.view.accessibility.AccessibilityEvent} to make an announcement which is related to some
     * sort of a context change for which none of the events representing UI transitions
     * is a good fit. For example, announcing a new page in a book. If accessibility
     * is not enabled this method does nothing.
     *
     * @param view view to perform the accessibility announcement
     * @param text The announcement text.
     */
    public static void announceForAccessibility(View view, CharSequence text) {
        final AccessibilityManager accessibilityManager = (AccessibilityManager)
                view.getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
        final ViewParent parent = view.getParent();
        if (accessibilityManager.isEnabled() && parent != null) {
            AccessibilityEvent event = AccessibilityEvent.obtain(
                    AccessibilityEvent.TYPE_ANNOUNCEMENT);
            view.onInitializeAccessibilityEvent(event);
            event.getText().add(text);
            event.setContentDescription(null);
            parent.requestSendAccessibilityEvent(view, event);
        }
    }

    /**
     * Sets the status bar color of the provided activity.
     */
    @SuppressLint("NewApi")
    public static void setStatusBarColor(Activity activity, @ColorRes int colorId) {
        if (Utils.isRunningLOrLater() && activity != null) {
            final Window window = activity.getWindow();
            if (window != null) {
                window.setStatusBarColor(activity.getResources().getColor(colorId));
            }
        }
    }
}
