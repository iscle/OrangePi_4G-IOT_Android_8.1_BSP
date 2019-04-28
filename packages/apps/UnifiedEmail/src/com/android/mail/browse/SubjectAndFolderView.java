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

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.support.v4.text.BidiFormatter;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ReplacementSpan;
import android.util.AttributeSet;
import android.widget.TextView;

import com.android.mail.R;
import com.android.mail.browse.ConversationViewHeader.ConversationViewHeaderCallbacks;
import com.android.mail.providers.Account;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.providers.Settings;
import com.android.mail.text.ChangeLabelsSpan;
import com.android.mail.text.FolderSpan;
import com.android.mail.ui.FolderDisplayer;

/**
 * A TextView that displays the conversation subject and list of folders for the message.
 * The view knows the widest that any of its containing {@link com.android.mail.text.FolderSpan}s
 * can be. They cannot exceed the TextView line width, or else {@link Layout} will split up the
 * spans in strange places.
 */
public class SubjectAndFolderView extends TextView implements FolderSpan.FolderSpanDimensions {
    private final String mNoFolderChipName;
    private final int mNoFolderBgColor;
    private final int mNoFolderFgColor;
    private final Drawable mImportanceMarkerDrawable;
    private final int mChipVerticalOffset;

    private int mMaxSpanWidth;

    private ConversationFolderDisplayer mFolderDisplayer;

    private String mSubject;

    private boolean mVisibleFolders;

    private ConversationViewAdapter.ConversationHeaderItem mHeaderItem;

    private BidiFormatter mBidiFormatter;

    public SubjectAndFolderView(Context context) {
        this(context, null);
    }

    public SubjectAndFolderView(Context context, AttributeSet attrs) {
        super(context, attrs);

        final Resources res = getResources();
        mNoFolderChipName = res.getString(R.string.add_label);
        mNoFolderBgColor = res.getColor(R.color.conv_header_add_label_background);
        mNoFolderFgColor = res.getColor(R.color.conv_header_add_label_text);
        mImportanceMarkerDrawable = res.getDrawable(
                R.drawable.ic_email_caret_none_important_unread);
        mImportanceMarkerDrawable.setBounds(0, 0, mImportanceMarkerDrawable.getIntrinsicWidth(),
                mImportanceMarkerDrawable.getIntrinsicHeight());
        mChipVerticalOffset = res.getDimensionPixelOffset(R.dimen.folder_cv_vertical_offset);

        mVisibleFolders = false;
        mFolderDisplayer = new ConversationFolderDisplayer(getContext());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        mMaxSpanWidth = MeasureSpec.getSize(widthMeasureSpec) - getTotalPaddingLeft()
                - getTotalPaddingRight();

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    public void setSubject(String subject) {
        mSubject = Conversation.getSubjectForDisplay(getContext(), null /* badgeText */, subject);

        if (!mVisibleFolders) {
            setText(mSubject);
        }
    }

    public void setFolders(ConversationViewHeaderCallbacks callbacks, Account account,
            Conversation conv) {
        mVisibleFolders = true;
        final BidiFormatter bidiFormatter = getBidiFormatter();
        final String wrappedSubject = mSubject == null ? "" : bidiFormatter.unicodeWrap(mSubject);
        final SpannableStringBuilder sb = new SpannableStringBuilder(wrappedSubject);
        sb.append('\u0020');
        final Settings settings = account.settings;
        final int start = sb.length();
        if (settings.importanceMarkersEnabled && conv.isImportant()) {
            sb.append(".\u0020");
            sb.setSpan(new ReplacementSpan() {
                @Override
                public int getSize(Paint paint, CharSequence text, int start, int end,
                        Paint.FontMetricsInt fm) {
                    return mImportanceMarkerDrawable.getIntrinsicWidth();
                }

                @Override
                public void draw(Canvas canvas, CharSequence text, int start, int end, float x,
                        int top, int baseline, int bottom, Paint paint) {
                    canvas.save();
                    final int transY = baseline + mChipVerticalOffset -
                            mImportanceMarkerDrawable.getIntrinsicHeight();
                    canvas.translate(x, transY);
                    mImportanceMarkerDrawable.draw(canvas);
                    canvas.restore();
                }
            }, start, start + 1, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        }

        mFolderDisplayer.loadConversationFolders(conv, null /* ignoreFolder */,
                -1 /* ignoreFolderType */);
        mFolderDisplayer.constructFolderChips(sb);

        final int end = sb.length();
        sb.setSpan(new ChangeLabelsSpan(callbacks), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        setText(sb);
        setMovementMethod(LinkMovementMethod.getInstance());
    }

    public void bind(ConversationViewAdapter.ConversationHeaderItem headerItem) {
        mHeaderItem = headerItem;
    }

    private BidiFormatter getBidiFormatter() {
        if (mBidiFormatter == null) {
            final ConversationViewAdapter adapter = mHeaderItem != null
                    ? mHeaderItem.getAdapter() : null;
            if (adapter == null) {
                mBidiFormatter = BidiFormatter.getInstance();
            } else {
                mBidiFormatter = adapter.getBidiFormatter();
            }
        }
        return mBidiFormatter;
    }

    public String getSubject() {
        return mSubject;
    }

    @Override
    public int getMaxChipWidth() {
        return mMaxSpanWidth;
    }

    private class ConversationFolderDisplayer extends FolderDisplayer {

        public ConversationFolderDisplayer(Context context) {
            super(context);
        }

        @Override
        protected void initializeDrawableResources() {
            super.initializeDrawableResources();
            final Resources res = mContext.getResources();
            mFolderDrawableResources.overflowGradientPadding = 0;   // not applicable
            mFolderDrawableResources.folderHorizontalPadding =
                    res.getDimensionPixelOffset(R.dimen.folder_cv_cell_content_padding);
            mFolderDrawableResources.folderFontSize =
                    res.getDimensionPixelOffset(R.dimen.folder_cv_font_size);
            mFolderDrawableResources.folderVerticalOffset = mChipVerticalOffset;
        }

        private void constructFolderChips(SpannableStringBuilder sb) {
            for (final Folder f : mFoldersSortedSet) {
                addSpan(sb, f.name, f.getForegroundColor(mFolderDrawableResources.defaultFgColor),
                        f.getBackgroundColor(mFolderDrawableResources.defaultBgColor));
            }

            if (mFoldersSortedSet.isEmpty()) {
                addSpan(sb, mNoFolderChipName, mNoFolderFgColor, mNoFolderBgColor);
            }
        }

        private void addSpan(SpannableStringBuilder sb, String name, int fgColor, int bgColor) {
            final FolderSpan span = new FolderSpan(name, fgColor, bgColor, mFolderDrawableResources,
                    getBidiFormatter(), SubjectAndFolderView.this);
            final int start = sb.length();
            sb.append(name);
            sb.setSpan(span, start, start + name.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.append(" ");
        }
    }
}
