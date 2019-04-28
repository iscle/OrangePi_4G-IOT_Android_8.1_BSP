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

package com.android.mail.browse;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Paint.FontMetricsInt;
import android.graphics.Typeface;
import android.support.v4.view.ViewCompat;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.mail.R;
import com.android.mail.utils.Utils;
import com.android.mail.utils.ViewUtils;
import com.google.common.base.Objects;

/**
 * Represents the coordinates of elements inside a CanvasConversationHeaderView
 * (eg, checkmark, star, subject, sender, folders, etc.) It will inflate a view,
 * and record the coordinates of each element after layout. This will allows us
 * to easily improve performance by creating custom view while still defining
 * layout in XML files.
 *
 * @author phamm
 */
public class ConversationItemViewCoordinates {
    private static final int SINGLE_LINE = 1;

    // Left-side gadget modes
    static final int GADGET_NONE = 0;
    static final int GADGET_CONTACT_PHOTO = 1;
    static final int GADGET_CHECKBOX = 2;

    /**
     * Simple holder class for an item's abstract configuration state. ListView binding creates an
     * instance per item, and {@link #forConfig(Context, Config, CoordinatesCache)} uses it to
     * hide/show optional views and determine the correct coordinates for that item configuration.
     */
    public static final class Config {
        private int mWidth;
        private int mGadgetMode = GADGET_NONE;
        private int mLayoutDirection = View.LAYOUT_DIRECTION_LTR;
        private boolean mShowFolders = false;
        private boolean mShowReplyState = false;
        private boolean mShowColorBlock = false;
        private boolean mShowPersonalIndicator = false;
        private boolean mUseFullMargins = false;

        public Config withGadget(int gadget) {
            mGadgetMode = gadget;
            return this;
        }

        public Config showFolders() {
            mShowFolders = true;
            return this;
        }

        public Config showReplyState() {
            mShowReplyState = true;
            return this;
        }

        public Config showColorBlock() {
            mShowColorBlock = true;
            return this;
        }

        public Config showPersonalIndicator() {
            mShowPersonalIndicator  = true;
            return this;
        }

        public Config updateWidth(int width) {
            mWidth = width;
            return this;
        }

        public int getWidth() {
            return mWidth;
        }

        public int getGadgetMode() {
            return mGadgetMode;
        }

        public boolean areFoldersVisible() {
            return mShowFolders;
        }

        public boolean isReplyStateVisible() {
            return mShowReplyState;
        }

        public boolean isColorBlockVisible() {
            return mShowColorBlock;
        }

        public boolean isPersonalIndicatorVisible() {
            return mShowPersonalIndicator;
        }

        private int getCacheKey() {
            // hash the attributes that contribute to item height and child view geometry
            return Objects.hashCode(mWidth, mGadgetMode, mShowFolders, mShowReplyState,
                    mShowPersonalIndicator, mLayoutDirection, mUseFullMargins);
        }

        public Config setLayoutDirection(int layoutDirection) {
            mLayoutDirection = layoutDirection;
            return this;
        }

        public int getLayoutDirection() {
            return mLayoutDirection;
        }

        public Config setUseFullMargins(boolean useFullMargins) {
            mUseFullMargins = useFullMargins;
            return this;
        }

        public boolean useFullPadding() {
            return mUseFullMargins;
        }
    }

    public static class CoordinatesCache {
        private final SparseArray<ConversationItemViewCoordinates> mCoordinatesCache
                = new SparseArray<ConversationItemViewCoordinates>();
        private final SparseArray<View> mViewsCache = new SparseArray<View>();

        public ConversationItemViewCoordinates getCoordinates(final int key) {
            return mCoordinatesCache.get(key);
        }

        public View getView(final int layoutId) {
            return mViewsCache.get(layoutId);
        }

        public void put(final int key, final ConversationItemViewCoordinates coords) {
            mCoordinatesCache.put(key, coords);
        }

        public void put(final int layoutId, final View view) {
            mViewsCache.put(layoutId, view);
        }
    }

    final int height;

    // Star.
    final int starX;
    final int starY;
    final int starWidth;

    // Senders.
    final int sendersX;
    final int sendersY;
    final int sendersWidth;
    final int sendersHeight;
    final int sendersLineCount;
    final float sendersFontSize;

    // Subject.
    final int subjectX;
    final int subjectY;
    final int subjectWidth;
    final int subjectHeight;
    final float subjectFontSize;

    // Snippet.
    final int snippetX;
    final int snippetY;
    final int maxSnippetWidth;
    final int snippetHeight;
    final float snippetFontSize;

    // Folders.
    final int folderLayoutWidth;
    final int folderCellWidth;
    final int foldersLeft;
    final int foldersRight;
    final int foldersY;
    final Typeface foldersTypeface;
    final float foldersFontSize;

    // Info icon
    final int infoIconX;
    final int infoIconXRight;
    final int infoIconY;

    // Date.
    final int dateX;
    final int dateXRight;
    final int dateY;
    final int datePaddingStart;
    final float dateFontSize;
    final int dateYBaseline;

    // Paperclip.
    final int paperclipY;
    final int paperclipPaddingStart;

    // Color block.
    final int colorBlockX;
    final int colorBlockY;
    final int colorBlockWidth;
    final int colorBlockHeight;

    // Reply state of a conversation.
    final int replyStateX;
    final int replyStateY;

    final int personalIndicatorX;
    final int personalIndicatorY;

    final int contactImagesHeight;
    final int contactImagesWidth;
    final int contactImagesX;
    final int contactImagesY;

    private ConversationItemViewCoordinates(final Context context, final Config config,
            final CoordinatesCache cache) {
        Utils.traceBeginSection("CIV coordinates constructor");
        final Resources res = context.getResources();

        final int layoutId = R.layout.conversation_item_view;

        ViewGroup view = (ViewGroup) cache.getView(layoutId);
        if (view == null) {
            view = (ViewGroup) LayoutInflater.from(context).inflate(layoutId, null);
            cache.put(layoutId, view);
        }

        // Show/hide optional views before measure/layout call
        final TextView folders = (TextView) view.findViewById(R.id.folders);
        folders.setVisibility(config.areFoldersVisible() ? View.VISIBLE : View.GONE);

        View contactImagesView = view.findViewById(R.id.contact_image);

        switch (config.getGadgetMode()) {
            case GADGET_CONTACT_PHOTO:
                contactImagesView.setVisibility(View.VISIBLE);
                break;
            case GADGET_CHECKBOX:
                contactImagesView.setVisibility(View.GONE);
                contactImagesView = null;
                break;
            default:
                contactImagesView.setVisibility(View.GONE);
                contactImagesView = null;
                break;
        }

        final View replyState = view.findViewById(R.id.reply_state);
        replyState.setVisibility(config.isReplyStateVisible() ? View.VISIBLE : View.GONE);

        final View personalIndicator = view.findViewById(R.id.personal_indicator);
        personalIndicator.setVisibility(
                config.isPersonalIndicatorVisible() ? View.VISIBLE : View.GONE);

        setFramePadding(context, view, config.useFullPadding());

        // Layout the appropriate view.
        ViewCompat.setLayoutDirection(view, config.getLayoutDirection());
        final int widthSpec = MeasureSpec.makeMeasureSpec(config.getWidth(), MeasureSpec.EXACTLY);
        final int heightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);

        view.measure(widthSpec, heightSpec);
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());

        // Once the view is measured, let's calculate the dynamic width variables.
        folderLayoutWidth = (int) (view.getWidth() *
                res.getInteger(R.integer.folder_max_width_proportion) / 100.0);
        folderCellWidth = (int) (view.getWidth() *
                res.getInteger(R.integer.folder_cell_max_width_proportion) / 100.0);

//        Utils.dumpViewTree((ViewGroup) view);

        // Records coordinates.

        // Contact images view
        if (contactImagesView != null) {
            contactImagesWidth = contactImagesView.getWidth();
            contactImagesHeight = contactImagesView.getHeight();
            contactImagesX = getX(contactImagesView);
            contactImagesY = getY(contactImagesView);
        } else {
            contactImagesX = contactImagesY = contactImagesWidth = contactImagesHeight = 0;
        }

        final boolean isRtl = ViewUtils.isViewRtl(view);

        final View star = view.findViewById(R.id.star);
        final int starPadding = res.getDimensionPixelSize(R.dimen.conv_list_star_padding_start);
        starX = getX(star) + (isRtl ? 0 : starPadding);
        starY = getY(star);
        starWidth = star.getWidth();

        final TextView senders = (TextView) view.findViewById(R.id.senders);
        final int sendersTopAdjust = getLatinTopAdjustment(senders);
        sendersX = getX(senders);
        sendersY = getY(senders) + sendersTopAdjust;
        sendersWidth = senders.getWidth();
        sendersHeight = senders.getHeight();
        sendersLineCount = SINGLE_LINE;
        sendersFontSize = senders.getTextSize();

        final TextView subject = (TextView) view.findViewById(R.id.subject);
        final int subjectTopAdjust = getLatinTopAdjustment(subject);
        subjectX = getX(subject);
        subjectY = getY(subject) + subjectTopAdjust;
        subjectWidth = subject.getWidth();
        subjectHeight = subject.getHeight();
        subjectFontSize = subject.getTextSize();

        final TextView snippet = (TextView) view.findViewById(R.id.snippet);
        final int snippetTopAdjust = getLatinTopAdjustment(snippet);
        snippetX = getX(snippet);
        snippetY = getY(snippet) + snippetTopAdjust;
        maxSnippetWidth = snippet.getWidth();
        snippetHeight = snippet.getHeight();
        snippetFontSize = snippet.getTextSize();

        if (config.areFoldersVisible()) {
            foldersLeft = getX(folders);
            foldersRight = foldersLeft + folders.getWidth();
            foldersY = getY(folders);
            foldersTypeface = folders.getTypeface();
            foldersFontSize = folders.getTextSize();
        } else {
            foldersLeft = 0;
            foldersRight = 0;
            foldersY = 0;
            foldersTypeface = null;
            foldersFontSize = 0;
        }

        final View colorBlock = view.findViewById(R.id.color_block);
        if (config.isColorBlockVisible() && colorBlock != null) {
            colorBlockX = getX(colorBlock);
            colorBlockY = getY(colorBlock);
            colorBlockWidth = colorBlock.getWidth();
            colorBlockHeight = colorBlock.getHeight();
        } else {
            colorBlockX = colorBlockY = colorBlockWidth = colorBlockHeight = 0;
        }

        if (config.isReplyStateVisible()) {
            replyStateX = getX(replyState);
            replyStateY = getY(replyState);
        } else {
            replyStateX = replyStateY = 0;
        }

        if (config.isPersonalIndicatorVisible()) {
            personalIndicatorX = getX(personalIndicator);
            personalIndicatorY = getY(personalIndicator);
        } else {
            personalIndicatorX = personalIndicatorY = 0;
        }

        final View infoIcon = view.findViewById(R.id.info_icon);
        infoIconX = getX(infoIcon);
        infoIconXRight = infoIconX + infoIcon.getWidth();
        infoIconY = getY(infoIcon);

        final TextView date = (TextView) view.findViewById(R.id.date);
        dateX = getX(date);
        dateXRight =  dateX + date.getWidth();
        dateY = getY(date);
        datePaddingStart = ViewUtils.getPaddingStart(date);
        dateFontSize = date.getTextSize();
        dateYBaseline = dateY + getLatinTopAdjustment(date) + date.getBaseline();

        final View paperclip = view.findViewById(R.id.paperclip);
        paperclipY = getY(paperclip);
        paperclipPaddingStart = ViewUtils.getPaddingStart(paperclip);

        height = view.getHeight() + sendersTopAdjust;
        Utils.traceEndSection();
    }

    @SuppressLint("NewApi")
    private static void setFramePadding(Context context, ViewGroup view, boolean useFullPadding) {
        final Resources res = context.getResources();
        final int padding = res.getDimensionPixelSize(useFullPadding ?
                R.dimen.conv_list_card_border_padding : R.dimen.conv_list_no_border_padding);

        final View frame = view.findViewById(R.id.conversation_item_frame);
        if (Utils.isRunningJBMR1OrLater()) {
            // start, top, end, bottom
            frame.setPaddingRelative(frame.getPaddingStart(), padding,
                    frame.getPaddingEnd(), padding);
        } else {
            frame.setPadding(frame.getPaddingLeft(), padding, frame.getPaddingRight(), padding);
        }
    }

    /**
     * Returns a negative corrective value that you can apply to a TextView's vertical dimensions
     * that will nudge the first line of text upwards such that uppercase Latin characters are
     * truly top-aligned.
     * <p>
     * N.B. this will cause other characters to draw above the top! only use this if you have
     * adequate top margin.
     *
     */
    private static int getLatinTopAdjustment(TextView t) {
        final FontMetricsInt fmi = t.getPaint().getFontMetricsInt();
        return (fmi.top - fmi.ascent);
    }

    /**
     * Returns the x coordinates of a view by tracing up its hierarchy.
     */
    private static int getX(View view) {
        int x = 0;
        while (view != null) {
            x += (int) view.getX();
            view = (View) view.getParent();
        }
        return x;
    }

    /**
     * Returns the y coordinates of a view by tracing up its hierarchy.
     */
    private static int getY(View view) {
        int y = 0;
        while (view != null) {
            y += (int) view.getY();
            view = (View) view.getParent();
        }
        return y;
    }

    /**
     * Returns the length (maximum of characters) of subject in this mode.
     */
    public static int getSendersLength(Context context, boolean hasAttachments) {
        final Resources res = context.getResources();
        if (hasAttachments) {
            return res.getInteger(R.integer.senders_with_attachment_lengths);
        } else {
            return res.getInteger(R.integer.senders_lengths);
        }
    }

    /**
     * Returns coordinates for elements inside a conversation header view given
     * the view width.
     */
    public static ConversationItemViewCoordinates forConfig(final Context context,
            final Config config, final CoordinatesCache cache) {
        final int cacheKey = config.getCacheKey();
        ConversationItemViewCoordinates coordinates = cache.getCoordinates(cacheKey);
        if (coordinates != null) {
            return coordinates;
        }

        coordinates = new ConversationItemViewCoordinates(context, config, cache);
        cache.put(cacheKey, coordinates);
        return coordinates;
    }
}
