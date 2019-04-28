/*
 * Copyright (C) 2012 Google Inc.
 * Licensed to The Android Open Source Project.
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

package com.android.mail.ui;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.support.v4.text.BidiFormatter;

import com.android.mail.R;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.providers.UIProvider.FolderType;
import com.android.mail.utils.FolderUri;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.Utils;
import com.google.common.collect.Sets;

import java.util.NavigableSet;
import java.util.Set;

/**
 * Used to generate folder display information given a raw folders string.
 * (The raw folders string can be obtained from {@link Conversation#getRawFolders()}.)
 */
public abstract class FolderDisplayer {
    public static final String LOG_TAG = LogTag.getLogTag();
    protected Context mContext;
    protected final NavigableSet<Folder> mFoldersSortedSet = Sets.newTreeSet();
    protected final FolderDrawableResources mFolderDrawableResources =
            new FolderDrawableResources();

    public static class FolderDrawableResources {
        public int defaultFgColor;
        public int defaultBgColor;
        public int folderRoundedCornerRadius;
        public int overflowGradientPadding;
        public int folderHorizontalPadding;
        public int folderInBetweenPadding;
        public int folderFontSize;
        public int folderVerticalOffset;
    }

    public FolderDisplayer(Context context) {
        mContext = context;
        initializeDrawableResources();
    }

    protected void initializeDrawableResources() {
        // Set default values used across all folder chips
        final Resources res = mContext.getResources();
        mFolderDrawableResources.defaultFgColor =
                res.getColor(R.color.default_folder_foreground_color);
        mFolderDrawableResources.defaultBgColor =
                res.getColor(R.color.default_folder_background_color);
        mFolderDrawableResources.folderRoundedCornerRadius =
                res.getDimensionPixelOffset(R.dimen.folder_rounded_corner_radius);
        mFolderDrawableResources.folderInBetweenPadding =
                res.getDimensionPixelOffset(R.dimen.folder_start_padding);
    }

    /**
     * Configure the FolderDisplayer object by filtering and copying from the list of raw folders.
     *
     * @param conv {@link Conversation} containing the folders to display.
     * @param ignoreFolderUri (optional) folder to omit from the displayed set
     * @param ignoreFolderType -1, or the {@link FolderType} to omit from the displayed set
     */
    public void loadConversationFolders(Conversation conv, final FolderUri ignoreFolderUri,
            final int ignoreFolderType) {
        mFoldersSortedSet.clear();
        for (Folder folder : conv.getRawFolders()) {
            // Skip the ignoreFolderType
            if (ignoreFolderType >= 0 && folder.isType(ignoreFolderType)) {
                continue;
            }
            // skip the ignoreFolder
            if (ignoreFolderUri != null && ignoreFolderUri.equals(folder.folderUri)) {
                continue;
            }
            mFoldersSortedSet.add(folder);
        }
    }

    /**
     * Reset this FolderDisplayer so that it can be reused.
     */
    public void reset() {
        mFoldersSortedSet.clear();
    }

    /**
     * Helper function to calculate exactly how much space the displayed folders should take.
     * @param folders the set of folders to display.
     * @param maxCellWidth this signifies the absolute max for each folder cell, no exceptions.
     * @param maxLayoutWidth the view's layout width, aka how much space we have.
     * @param foldersInBetweenPadding the padding between folder chips.
     * @param foldersHorizontalPadding the padding between the edge of the chip and the text.
     * @param maxFolderCount the maximum number of folder chips to display.
     * @param paint work paint.
     * @return an array of integers that signifies the length of each folder chip.
     */
    public static int[] measureFolderDimen(Set<Folder> folders, int maxCellWidth,
            int maxLayoutWidth, int foldersInBetweenPadding, int foldersHorizontalPadding,
            int maxFolderCount, Paint paint) {

        final int numDisplayedFolders = Math.min(maxFolderCount, folders.size());
        if (numDisplayedFolders == 0) {
            return new int[0];
        }

        // This variable is calculated based on the number of folders we are displaying
        final int maxAllowedCellSize = Math.min(maxCellWidth, (maxLayoutWidth -
                (numDisplayedFolders - 1) * foldersInBetweenPadding) / numDisplayedFolders);
        final int[] measurements = new int[numDisplayedFolders];

        int count = 0;
        int missingWidth = 0;
        int extraWidth = 0;
        for (Folder f : folders) {
            if (count > numDisplayedFolders - 1) {
                break;
            }

            final String folderString = f.name;
            final int neededWidth = (int) paint.measureText(folderString) +
                    2 * foldersHorizontalPadding;

            if (neededWidth > maxAllowedCellSize) {
                // What we can take from others is the minimum of the width we need to borrow
                // and the width we are allowed to borrow.
                final int borrowedWidth = Math.min(neededWidth - maxAllowedCellSize,
                        maxCellWidth - maxAllowedCellSize);
                final int extraWidthLeftover = extraWidth - borrowedWidth;
                if (extraWidthLeftover >= 0) {
                    measurements[count] = Math.min(neededWidth, maxCellWidth);
                    extraWidth = extraWidthLeftover;
                } else {
                    measurements[count] = maxAllowedCellSize + extraWidth;
                    extraWidth = 0;
                }
                missingWidth = -extraWidthLeftover;
            } else {
                extraWidth = maxAllowedCellSize - neededWidth;
                measurements[count] = neededWidth;
                if (missingWidth > 0) {
                    if (extraWidth >= missingWidth) {
                        measurements[count - 1] += missingWidth;
                        extraWidth -= missingWidth;
                    } else {
                        measurements[count - 1] += extraWidth;
                        extraWidth = 0;
                    }
                }
                missingWidth = 0;
            }

            count++;
        }

        return measurements;
    }

    public static void drawFolder(Canvas canvas, float x, float y, int width, int height,
            Folder f, FolderDisplayer.FolderDrawableResources res, BidiFormatter formatter,
            Paint paint) {
        drawFolder(canvas, x, y, width, height, f.name,
                f.getForegroundColor(res.defaultFgColor), f.getBackgroundColor(res.defaultBgColor),
                res, formatter, paint);
    }

    public static void drawFolder(Canvas canvas, float x, float y, int width, int height,
            String name, int fgColor, int bgColor, FolderDisplayer.FolderDrawableResources res,
            BidiFormatter formatter, Paint paint) {
        canvas.save();
        canvas.translate(x, y + res.folderVerticalOffset);

        // Draw the box.
        paint.setColor(bgColor);
        paint.setStyle(Paint.Style.FILL);
        final RectF rect = new RectF(0, 0, width, height);
        canvas.drawRoundRect(rect, res.folderRoundedCornerRadius, res.folderRoundedCornerRadius,
                paint);

        // Draw the text based on the language locale and layout direction.
        paint.setColor(fgColor);
        paint.setStyle(Paint.Style.FILL);

        // Compute the text/gradient indices
        final int textLength = (int) paint.measureText(name);
        final int gradientX0;
        final int gradientX1;
        final int textX;

/***************************************************************************************************
 * width               - the actual folder chip rectangle.                                         *
 * textLength          - the length of the folder's full name (can be longer than                  *
 *                         the actual chip, which is what overflow gradient is for).               *
 * innerPadding        - the padding between the text and the chip edge.                           *
 * overflowPadding     - the padding between start of overflow and the chip edge.                  *
 *                                                                                                 *
 *                                                                                                 *
 * text is in a RTL language                                                                       *
 *                                                                                                 *
 *                   index-0                                                                       *
 *                      |<---------------------------- width ---------------------------->|        *
 *        |<-------------------------textLength------------------>|                       |        *
 *        |             |<----- overflowPadding ----->|                                   |        *
 *        |             |<- innerPadding ->|<-------->|<--------->|<- horizontalPadding ->|        *
 *       textX                            gX1        gX0                                           *
 *                                                                                                 *
 *                                                                                                 *
 * text is in a LTR language.                                                                      *
 *                                                                                                 *
 *     index-0                                                                                     *
 *        |<------------------------------ width ------------------------------->|                 *
 *        |                       |<-------------------------textLength-------------------->|      *
 *        |                                   |<-------- overflowPadding ------->|                 *
 *        |<- horizontalPadding ->|<--------->|<-------->|<- horizontalPadding ->|                 *
 *                              textX        gX0        gX1                                        *
 *                                                                                                 *
 **************************************************************************************************/
        if (formatter.isRtl(name)) {
            gradientX0 = res.overflowGradientPadding;
            gradientX1 = res.folderHorizontalPadding;
            textX = width - res.folderHorizontalPadding - textLength;
        } else {
            gradientX0 = width - res.overflowGradientPadding;
            gradientX1 = width - res.folderHorizontalPadding;
            textX = res.folderHorizontalPadding;
        }

        // Draw the text and the possible overflow gradient
        // Overflow happens when the text is longer than the chip width minus side paddings.
        if (textLength > width - 2 * res.folderHorizontalPadding) {
            final Shader shader = new LinearGradient(gradientX0, 0, gradientX1, 0, fgColor,
                    Utils.getTransparentColor(fgColor), Shader.TileMode.CLAMP);
            paint.setShader(shader);
        }
        final int textY = height / 2 - (int) (paint.descent() + paint.ascent()) / 2;
        canvas.drawText(name, textX, textY, paint);
        paint.setShader(null);

        canvas.restore();
    }
}
