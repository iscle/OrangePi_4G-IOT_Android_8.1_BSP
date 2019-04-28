/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tv.guide;

import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

class GuideUtils {
    private static final int INVALID_INDEX = -1;
    private static int sWidthPerHour = 0;

    /**
     * Sets the width in pixels that corresponds to an hour in program guide.
     * Assume that this is called from main thread only, so, no synchronization.
     */
    static void setWidthPerHour(int widthPerHour) {
        sWidthPerHour = widthPerHour;
    }

    /**
     * Gets the number of pixels in program guide table that corresponds to the given milliseconds.
     */
    static int convertMillisToPixel(long millis) {
        return (int) (millis * sWidthPerHour / TimeUnit.HOURS.toMillis(1));
    }

    /**
     * Gets the number of pixels in program guide table that corresponds to the given range.
     */
    static int convertMillisToPixel(long startMillis, long endMillis) {
        // Convert to pixels first to avoid accumulation of rounding errors.
        return GuideUtils.convertMillisToPixel(endMillis)
                - GuideUtils.convertMillisToPixel(startMillis);
    }

    /**
     * Gets the time in millis that corresponds to the given pixels in the program guide.
     */
    static long convertPixelToMillis(int pixel) {
        return pixel * TimeUnit.HOURS.toMillis(1) / sWidthPerHour;
    }

    /**
     * Return the view should be focused in the given program row according to the focus range.

     * @param keepCurrentProgramFocused If {@code true}, focuses on the current program if possible,
     *                                  else falls back the general logic.
     */
    static View findNextFocusedProgram(View programRow, int focusRangeLeft,
            int focusRangeRight, boolean keepCurrentProgramFocused) {
        ArrayList<View> focusables = new ArrayList<>();
        findFocusables(programRow, focusables);

        if (keepCurrentProgramFocused) {
            // Select the current program if possible.
            for (int i = 0; i < focusables.size(); ++i) {
                View focusable = focusables.get(i);
                if (focusable instanceof ProgramItemView
                        && isCurrentProgram((ProgramItemView) focusable)) {
                    return focusable;
                }
            }
        }

        // Find the largest focusable among fully overlapped focusables.
        int maxFullyOverlappedWidth = Integer.MIN_VALUE;
        int maxPartiallyOverlappedWidth = Integer.MIN_VALUE;
        int nextFocusIndex = INVALID_INDEX;
        for (int i = 0; i < focusables.size(); ++i) {
            View focusable = focusables.get(i);
            Rect focusableRect = new Rect();
            focusable.getGlobalVisibleRect(focusableRect);
            if (focusableRect.left <= focusRangeLeft && focusRangeRight <= focusableRect.right) {
                // the old focused range is fully inside the focusable, return directly.
                return focusable;
            } else if (focusRangeLeft <= focusableRect.left
                    && focusableRect.right <= focusRangeRight) {
                // the focusable is fully inside the old focused range, choose the widest one.
                int width = focusableRect.width();
                if (width > maxFullyOverlappedWidth) {
                    nextFocusIndex = i;
                    maxFullyOverlappedWidth = width;
                }
            } else if (maxFullyOverlappedWidth == Integer.MIN_VALUE) {
                int overlappedWidth = (focusRangeLeft <= focusableRect.left) ?
                        focusRangeRight - focusableRect.left
                        : focusableRect.right - focusRangeLeft;
                if (overlappedWidth > maxPartiallyOverlappedWidth) {
                    nextFocusIndex = i;
                    maxPartiallyOverlappedWidth = overlappedWidth;
                }
            }
        }
        if (nextFocusIndex != INVALID_INDEX) {
            return focusables.get(nextFocusIndex);
        }
        return null;
    }

    /**
     *  Returns {@code true} if the program displayed in the give
     *  {@link com.android.tv.guide.ProgramItemView} is a current program.
     */
    static boolean isCurrentProgram(ProgramItemView view) {
        return view.getTableEntry().isCurrentProgram();
    }

    /**
     * Returns {@code true} if the given view is a descendant of the give container.
     */
    static boolean isDescendant(ViewGroup container, View view) {
        if (view == null) {
            return false;
        }
        for (ViewParent p = view.getParent(); p != null; p = p.getParent()) {
            if (p == container) {
                return true;
            }
        }
        return false;
    }

    private static void findFocusables(View v, ArrayList<View> outFocusable) {
        if (v.isFocusable()) {
            outFocusable.add(v);
        }
        if (v instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) v;
            for (int i = 0; i < viewGroup.getChildCount(); ++i) {
                findFocusables(viewGroup.getChildAt(i), outFocusable);
            }
        }
    }

    private GuideUtils() { }
}
