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

import android.app.FragmentManager;
import android.app.LoaderManager;
import android.content.Context;
import android.support.annotation.IntDef;
import android.support.v4.text.BidiFormatter;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.BaseAdapter;

import com.android.emailcommon.mail.Address;
import com.android.mail.ContactInfoSource;
import com.android.mail.FormattedDateBuilder;
import com.android.mail.R;
import com.android.mail.browse.ConversationFooterView.ConversationFooterCallbacks;
import com.android.mail.browse.ConversationViewHeader.ConversationViewHeaderCallbacks;
import com.android.mail.browse.MessageFooterView.MessageFooterCallbacks;
import com.android.mail.browse.MessageHeaderView.MessageHeaderViewCallbacks;
import com.android.mail.browse.SuperCollapsedBlock.OnClickListener;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.UIProvider;
import com.android.mail.ui.ControllableActivity;
import com.android.mail.ui.ConversationUpdater;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.VeiledAddressMatcher;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A specialized adapter that contains overlay views to draw on top of the underlying conversation
 * WebView. Each independently drawn overlay view gets its own item in this adapter, and indices
 * in this adapter do not necessarily line up with cursor indices. For example, an expanded
 * message may have a header and footer, and since they are not drawn coupled together, they each
 * get an adapter item.
 * <p>
 * Each item in this adapter is a {@link ConversationOverlayItem} to expose enough information
 * to {@link ConversationContainer} so that it can position overlays properly.
 *
 */
public class ConversationViewAdapter extends BaseAdapter {

    private static final String LOG_TAG = LogTag.getLogTag();
    private static final String OVERLAY_ITEM_ROOT_TAG = "overlay_item_root";

    private final Context mContext;
    private final FormattedDateBuilder mDateBuilder;
    private final ConversationAccountController mAccountController;
    private final LoaderManager mLoaderManager;
    private final FragmentManager mFragmentManager;
    private final MessageHeaderViewCallbacks mMessageCallbacks;
    private final MessageFooterCallbacks mFooterCallbacks;
    private final ContactInfoSource mContactInfoSource;
    private final ConversationViewHeaderCallbacks mConversationCallbacks;
    private final ConversationFooterCallbacks mConversationFooterCallbacks;
    private final ConversationUpdater mConversationUpdater;
    private final OnClickListener mSuperCollapsedListener;
    private final Map<String, Address> mAddressCache;
    private final LayoutInflater mInflater;

    private final List<ConversationOverlayItem> mItems;
    private final VeiledAddressMatcher mMatcher;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            VIEW_TYPE_CONVERSATION_HEADER,
            VIEW_TYPE_CONVERSATION_FOOTER,
            VIEW_TYPE_MESSAGE_HEADER,
            VIEW_TYPE_MESSAGE_FOOTER,
            VIEW_TYPE_SUPER_COLLAPSED_BLOCK,
            VIEW_TYPE_AD_HEADER,
            VIEW_TYPE_AD_SENDER_HEADER,
            VIEW_TYPE_AD_FOOTER
    })
    public @interface ConversationViewType {}
    public static final int VIEW_TYPE_CONVERSATION_HEADER = 0;
    public static final int VIEW_TYPE_CONVERSATION_FOOTER = 1;
    public static final int VIEW_TYPE_MESSAGE_HEADER = 2;
    public static final int VIEW_TYPE_MESSAGE_FOOTER = 3;
    public static final int VIEW_TYPE_SUPER_COLLAPSED_BLOCK = 4;
    public static final int VIEW_TYPE_AD_HEADER = 5;
    public static final int VIEW_TYPE_AD_SENDER_HEADER = 6;
    public static final int VIEW_TYPE_AD_FOOTER = 7;
    public static final int VIEW_TYPE_COUNT = 8;

    private final BidiFormatter mBidiFormatter;

    private final View.OnKeyListener mOnKeyListener;

    public class ConversationHeaderItem extends ConversationOverlayItem {
        public final Conversation mConversation;

        private ConversationHeaderItem(Conversation conv) {
            mConversation = conv;
        }

        @Override
        public @ConversationViewType int getType() {
            return VIEW_TYPE_CONVERSATION_HEADER;
        }

        @Override
        public View createView(Context context, LayoutInflater inflater, ViewGroup parent) {
            final ConversationViewHeader v = (ConversationViewHeader) inflater.inflate(
                    R.layout.conversation_view_header, parent, false);
            v.setCallbacks(
                    mConversationCallbacks, mAccountController, mConversationUpdater);
            v.setSubject(mConversation.subject);
            if (mAccountController.getAccount().supportsCapability(
                    UIProvider.AccountCapabilities.MULTIPLE_FOLDERS_PER_CONV)) {
                v.setFolders(mConversation);
            }
            v.setStarred(mConversation.starred);
            v.setTag(OVERLAY_ITEM_ROOT_TAG);

            return v;
        }

        @Override
        public void bindView(View v, boolean measureOnly) {
            ConversationViewHeader header = (ConversationViewHeader) v;
            header.bind(this);
        }

        @Override
        public boolean isContiguous() {
            return true;
        }

        @Override
        public View.OnKeyListener getOnKeyListener() {
            return mOnKeyListener;
        }

        public ConversationViewAdapter getAdapter() {
            return ConversationViewAdapter.this;
        }
    }

    public class ConversationFooterItem extends ConversationOverlayItem {
        private MessageHeaderItem mLastMessageHeaderItem;

        public ConversationFooterItem(MessageHeaderItem lastMessageHeaderItem) {
            setLastMessageHeaderItem(lastMessageHeaderItem);
        }

        @Override
        public @ConversationViewType int getType() {
            return VIEW_TYPE_CONVERSATION_FOOTER;
        }

        @Override
        public View createView(Context context, LayoutInflater inflater, ViewGroup parent) {
            final ConversationFooterView v = (ConversationFooterView)
                    inflater.inflate(R.layout.conversation_footer, parent, false);
            v.setAccountController(mAccountController);
            v.setConversationFooterCallbacks(mConversationFooterCallbacks);
            v.setTag(OVERLAY_ITEM_ROOT_TAG);

            // Register the onkey listener for all relevant views
            registerOnKeyListeners(v, v.findViewById(R.id.reply_button),
                    v.findViewById(R.id.reply_all_button), v.findViewById(R.id.forward_button));

            return v;
        }

        @Override
        public void bindView(View v, boolean measureOnly) {
            ((ConversationFooterView) v).bind(this);
            mRootView = v;
        }

        @Override
        public void rebindView(View view) {
            ((ConversationFooterView) view).rebind(this);
            mRootView = view;
        }

        @Override
        public View getFocusableView() {
            return mRootView.findViewById(R.id.reply_button);
        }

        @Override
        public boolean isContiguous() {
            return true;
        }

        @Override
        public View.OnKeyListener getOnKeyListener() {
            return mOnKeyListener;
        }

        public MessageHeaderItem getLastMessageHeaderItem() {
            return mLastMessageHeaderItem;
        }

        public void setLastMessageHeaderItem(MessageHeaderItem lastMessageHeaderItem) {
            mLastMessageHeaderItem = lastMessageHeaderItem;
        }
    }

    public static class MessageHeaderItem extends ConversationOverlayItem {

        private final ConversationViewAdapter mAdapter;

        private ConversationMessage mMessage;

        // view state variables
        private boolean mExpanded;
        public boolean detailsExpanded;
        private boolean mShowImages;

        // cached values to speed up re-rendering during view recycling
        private CharSequence mTimestampShort;
        private CharSequence mTimestampLong;
        private CharSequence mTimestampFull;
        private long mTimestampMs;
        private final FormattedDateBuilder mDateBuilder;
        public CharSequence recipientSummaryText;

        MessageHeaderItem(ConversationViewAdapter adapter, FormattedDateBuilder dateBuilder,
                ConversationMessage message, boolean expanded, boolean showImages) {
            mAdapter = adapter;
            mDateBuilder = dateBuilder;
            mMessage = message;
            mExpanded = expanded;
            mShowImages = showImages;

            detailsExpanded = false;
        }

        public ConversationMessage getMessage() {
            return mMessage;
        }

        @Override
        public @ConversationViewType int getType() {
            return VIEW_TYPE_MESSAGE_HEADER;
        }

        @Override
        public View createView(Context context, LayoutInflater inflater, ViewGroup parent) {
            final MessageHeaderView v = (MessageHeaderView) inflater.inflate(
                    R.layout.conversation_message_header, parent, false);
            v.initialize(mAdapter.mAccountController,
                    mAdapter.mAddressCache);
            v.setCallbacks(mAdapter.mMessageCallbacks);
            v.setContactInfoSource(mAdapter.mContactInfoSource);
            v.setVeiledMatcher(mAdapter.mMatcher);
            v.setTag(OVERLAY_ITEM_ROOT_TAG);

            // Register the onkey listener for all relevant views
            registerOnKeyListeners(v, v.findViewById(R.id.upper_header),
                    v.findViewById(R.id.hide_details), v.findViewById(R.id.edit_draft),
                    v.findViewById(R.id.reply), v.findViewById(R.id.reply_all),
                    v.findViewById(R.id.overflow), v.findViewById(R.id.send_date));
            return v;
        }

        @Override
        public void bindView(View v, boolean measureOnly) {
            final MessageHeaderView header = (MessageHeaderView) v;
            header.bind(this, measureOnly);
            mRootView = v;
        }

        @Override
        public View getFocusableView() {
            return mRootView.findViewById(R.id.upper_header);
        }

        @Override
        public void onModelUpdated(View v) {
            final MessageHeaderView header = (MessageHeaderView) v;
            header.refresh();
        }

        @Override
        public boolean isContiguous() {
            return !isExpanded();
        }

        @Override
        public View.OnKeyListener getOnKeyListener() {
            return mAdapter.getOnKeyListener();
        }

        @Override
        public boolean isExpanded() {
            return mExpanded;
        }

        public void setExpanded(boolean expanded) {
            if (mExpanded != expanded) {
                mExpanded = expanded;
            }
        }

        public boolean getShowImages() {
            return mShowImages;
        }

        public void setShowImages(boolean showImages) {
            mShowImages = showImages;
        }

        @Override
        public boolean canBecomeSnapHeader() {
            return isExpanded();
        }

        @Override
        public boolean canPushSnapHeader() {
            return true;
        }

        @Override
        public boolean belongsToMessage(ConversationMessage message) {
            return Objects.equal(mMessage, message);
        }

        @Override
        public void setMessage(ConversationMessage message) {
            mMessage = message;
            // setMessage signifies an in-place update to the message, so let's clear out recipient
            // summary text so the view will refresh it on the next render.
            recipientSummaryText = null;
        }

        public CharSequence getTimestampShort() {
            ensureTimestamps();
            return mTimestampShort;
        }

        public CharSequence getTimestampLong() {
            ensureTimestamps();
            return mTimestampLong;
        }

        public CharSequence getTimestampFull() {
            ensureTimestamps();
            return mTimestampFull;
        }

        private void ensureTimestamps() {
            if (mMessage.dateReceivedMs != mTimestampMs) {
                mTimestampMs = mMessage.dateReceivedMs;
                mTimestampShort = mDateBuilder.formatShortDateTime(mTimestampMs);
                mTimestampLong = mDateBuilder.formatLongDateTime(mTimestampMs);
                mTimestampFull = mDateBuilder.formatFullDateTime(mTimestampMs);
            }
        }

        public ConversationViewAdapter getAdapter() {
            return mAdapter;
        }

        @Override
        public void rebindView(View view) {
            final MessageHeaderView header = (MessageHeaderView) view;
            header.rebind(this);
            mRootView = view;
        }
    }

    public static class MessageFooterItem extends ConversationOverlayItem {
        private final ConversationViewAdapter mAdapter;

        /**
         * A footer can only exist if there is a matching header. Requiring a header allows a
         * footer to stay in sync with the expanded state of the header.
         */
        private final MessageHeaderItem mHeaderItem;

        private MessageFooterItem(ConversationViewAdapter adapter, MessageHeaderItem item) {
            mAdapter = adapter;
            mHeaderItem = item;
        }

        @Override
        public @ConversationViewType int getType() {
            return VIEW_TYPE_MESSAGE_FOOTER;
        }

        @Override
        public View createView(Context context, LayoutInflater inflater, ViewGroup parent) {
            final MessageFooterView v = (MessageFooterView) inflater.inflate(
                    R.layout.conversation_message_footer, parent, false);
            v.initialize(mAdapter.mLoaderManager, mAdapter.mFragmentManager,
                    mAdapter.mAccountController, mAdapter.mFooterCallbacks);
            v.setTag(OVERLAY_ITEM_ROOT_TAG);

            // Register the onkey listener for all relevant views
            registerOnKeyListeners(v, v.findViewById(R.id.view_entire_message_prompt));
            return v;
        }

        @Override
        public void bindView(View v, boolean measureOnly) {
            final MessageFooterView attachmentsView = (MessageFooterView) v;
            attachmentsView.bind(mHeaderItem, measureOnly);
            mRootView = v;
        }

        @Override
        public boolean isContiguous() {
            return true;
        }

        @Override
        public View.OnKeyListener getOnKeyListener() {
            return mAdapter.getOnKeyListener();
        }

        @Override
        public boolean isExpanded() {
            return mHeaderItem.isExpanded();
        }

        @Override
        public int getGravity() {
            // attachments are top-aligned within their spacer area
            // Attachments should stay near the body they belong to, even when zoomed far in.
            return Gravity.TOP;
        }

        @Override
        public int getHeight() {
            // a footer may change height while its view does not exist because it is offscreen
            // (but the header is onscreen and thus collapsible)
            if (!mHeaderItem.isExpanded()) {
                return 0;
            }
            return super.getHeight();
        }

        public MessageHeaderItem getHeaderItem() {
            return mHeaderItem;
        }
    }

    public class SuperCollapsedBlockItem extends ConversationOverlayItem {

        private final int mStart;
        private final int mEnd;
        private final boolean mHasDraft;

        private SuperCollapsedBlockItem(int start, int end, boolean hasDraft) {
            mStart = start;
            mEnd = end;
            mHasDraft = hasDraft;
        }

        @Override
        public @ConversationViewType int getType() {
            return VIEW_TYPE_SUPER_COLLAPSED_BLOCK;
        }

        @Override
        public View createView(Context context, LayoutInflater inflater, ViewGroup parent) {
            final SuperCollapsedBlock v = (SuperCollapsedBlock) inflater.inflate(
                    R.layout.super_collapsed_block, parent, false);
            v.initialize(mSuperCollapsedListener);
            v.setOnKeyListener(mOnKeyListener);
            v.setTag(OVERLAY_ITEM_ROOT_TAG);

            // Register the onkey listener for all relevant views
            registerOnKeyListeners(v);
            return v;
        }

        @Override
        public void bindView(View v, boolean measureOnly) {
            final SuperCollapsedBlock scb = (SuperCollapsedBlock) v;
            scb.bind(this);
            mRootView = v;
        }

        @Override
        public boolean isContiguous() {
            return true;
        }

        @Override
        public View.OnKeyListener getOnKeyListener() {
            return mOnKeyListener;
        }

        @Override
        public boolean isExpanded() {
            return false;
        }

        public int getStart() {
            return mStart;
        }

        public int getEnd() {
            return mEnd;
        }

        public boolean hasDraft() {
            return mHasDraft;
        }

        @Override
        public boolean canPushSnapHeader() {
            return true;
        }
    }

    public ConversationViewAdapter(ControllableActivity controllableActivity,
            ConversationAccountController accountController,
            LoaderManager loaderManager,
            MessageHeaderViewCallbacks messageCallbacks,
            MessageFooterCallbacks footerCallbacks,
            ContactInfoSource contactInfoSource,
            ConversationViewHeaderCallbacks convCallbacks,
            ConversationFooterCallbacks convFooterCallbacks,
            ConversationUpdater conversationUpdater,
            OnClickListener scbListener,
            Map<String, Address> addressCache,
            FormattedDateBuilder dateBuilder,
            BidiFormatter bidiFormatter,
            View.OnKeyListener onKeyListener) {
        mContext = controllableActivity.getActivityContext();
        mDateBuilder = dateBuilder;
        mAccountController = accountController;
        mLoaderManager = loaderManager;
        mFragmentManager = controllableActivity.getFragmentManager();
        mMessageCallbacks = messageCallbacks;
        mFooterCallbacks = footerCallbacks;
        mContactInfoSource = contactInfoSource;
        mConversationCallbacks = convCallbacks;
        mConversationFooterCallbacks = convFooterCallbacks;
        mConversationUpdater = conversationUpdater;
        mSuperCollapsedListener = scbListener;
        mAddressCache = addressCache;
        mInflater = LayoutInflater.from(mContext);

        mItems = Lists.newArrayList();
        mMatcher = controllableActivity.getAccountController().getVeiledAddressMatcher();

        mBidiFormatter = bidiFormatter;
        mOnKeyListener = onKeyListener;
    }

    @Override
    public int getCount() {
        return mItems.size();
    }

    @Override
    public @ConversationViewType int getItemViewType(int position) {
        return mItems.get(position).getType();
    }

    @Override
    public int getViewTypeCount() {
        return VIEW_TYPE_COUNT;
    }

    @Override
    public ConversationOverlayItem getItem(int position) {
        return mItems.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position; // TODO: ensure this works well enough
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return getView(getItem(position), convertView, parent, false /* measureOnly */);
    }

    public View getView(ConversationOverlayItem item, View convertView, ViewGroup parent,
            boolean measureOnly) {
        final View v;

        if (convertView == null) {
            v = item.createView(mContext, mInflater, parent);
        } else {
            v = convertView;
        }
        item.bindView(v, measureOnly);

        return v;
    }

    public LayoutInflater getLayoutInflater() {
        return mInflater;
    }

    public FormattedDateBuilder getDateBuilder() {
        return mDateBuilder;
    }

    public int addItem(ConversationOverlayItem item) {
        final int pos = mItems.size();
        item.setPosition(pos);
        mItems.add(item);
        return pos;
    }

    public void clear() {
        mItems.clear();
        notifyDataSetChanged();
    }

    public int addConversationHeader(Conversation conv) {
        return addItem(new ConversationHeaderItem(conv));
    }

    public int addConversationFooter(MessageHeaderItem headerItem) {
        return addItem(new ConversationFooterItem(headerItem));
    }

    public int addMessageHeader(ConversationMessage msg, boolean expanded, boolean showImages) {
        return addItem(new MessageHeaderItem(this, mDateBuilder, msg, expanded, showImages));
    }

    public int addMessageFooter(MessageHeaderItem headerItem) {
        return addItem(new MessageFooterItem(this, headerItem));
    }

    public static MessageHeaderItem newMessageHeaderItem(ConversationViewAdapter adapter,
            FormattedDateBuilder dateBuilder, ConversationMessage message,
            boolean expanded, boolean showImages) {
        return new MessageHeaderItem(adapter, dateBuilder, message, expanded, showImages);
    }

    public static MessageFooterItem newMessageFooterItem(
            ConversationViewAdapter adapter, MessageHeaderItem headerItem) {
        return new MessageFooterItem(adapter, headerItem);
    }

    public int addSuperCollapsedBlock(int start, int end, boolean hasDraft) {
        return addItem(new SuperCollapsedBlockItem(start, end, hasDraft));
    }

    public void replaceSuperCollapsedBlock(SuperCollapsedBlockItem blockToRemove,
            Collection<ConversationOverlayItem> replacements) {
        final int pos = mItems.indexOf(blockToRemove);
        if (pos == -1) {
            return;
        }

        mItems.remove(pos);
        mItems.addAll(pos, replacements);

        // update position for all items
        for (int i = 0, size = mItems.size(); i < size; i++) {
            mItems.get(i).setPosition(i);
        }
    }

    public void updateItemsForMessage(ConversationMessage message,
            List<Integer> affectedPositions) {
        for (int i = 0, len = mItems.size(); i < len; i++) {
            final ConversationOverlayItem item = mItems.get(i);
            if (item.belongsToMessage(message)) {
                item.setMessage(message);
                affectedPositions.add(i);
            }
        }
    }

    /**
     * Remove and return the {@link ConversationFooterItem} from the adapter.
     */
    public ConversationFooterItem removeFooterItem() {
        final int count = mItems.size();
        if (count < 4) {
            LogUtils.e(LOG_TAG, "not enough items in the adapter. count: %s", count);
            return null;
        }
        final ConversationFooterItem item = (ConversationFooterItem) mItems.remove(count - 1);
        if (item == null) {
            LogUtils.e(LOG_TAG, "removed wrong overlay item: %s", item);
            return null;
        }

        return item;
    }

    public ConversationFooterItem getFooterItem() {
        final int count = mItems.size();
        if (count < 4) {
            LogUtils.e(LOG_TAG, "not enough items in the adapter. count: %s", count);
            return null;
        }
        final ConversationOverlayItem item = mItems.get(count - 1);
        try {
            return (ConversationFooterItem) item;
        } catch (ClassCastException e) {
            LogUtils.e(LOG_TAG, "Last item is not a conversation footer. type: %s", item.getType());
            return null;
        }
    }

    /**
     * Returns true if the item before this one is of type
     * {@link #VIEW_TYPE_SUPER_COLLAPSED_BLOCK}.
     */
    public boolean isPreviousItemSuperCollapsed(ConversationOverlayItem item) {
        // super-collapsed will be the item just before the header
        final int position = item.getPosition() - 1;
        final int count = mItems.size();
        return !(position < 0 || position >= count)
                && mItems.get(position).getType() == VIEW_TYPE_SUPER_COLLAPSED_BLOCK;
    }

    // This should be a safe call since all containers should have at least a conv header and a
    // message header.
    public boolean focusFirstMessageHeader() {
        if (mItems.size() > 1) {
            final View v = mItems.get(1).getFocusableView();
            if (v != null && v.isShown() && v.isFocusable()) {
                v.requestFocus();
                return true;
            }
        }
        return false;
    }

    /**
     * Find the next view that should grab focus with respect to the current position.
     */
    public View getNextOverlayView(View curr, boolean isDown, Set<View> scraps) {
        // First find the root view of the overlay item
        while (curr.getTag() != OVERLAY_ITEM_ROOT_TAG) {
            final ViewParent parent = curr.getParent();
            if (parent != null && parent instanceof View) {
                curr = (View) parent;
            } else {
                return null;
            }
        }

        // Find the position of the root view
        for (int i = 0; i < mItems.size(); i++) {
            if (mItems.get(i).mRootView == curr) {
                // Found view, now find the next applicable view
                if (isDown && i >= 0) {
                    while (++i < mItems.size()) {
                        final ConversationOverlayItem item = mItems.get(i);
                        final View next = item.getFocusableView();
                        if (item.mRootView != null && !scraps.contains(item.mRootView) &&
                                next != null && next.isFocusable()) {
                            return next;
                        }
                    }
                } else {
                    while (--i >= 0) {
                        final ConversationOverlayItem item = mItems.get(i);
                        final View next = item.getFocusableView();
                        if (item.mRootView != null && !scraps.contains(item.mRootView) &&
                                next != null && next.isFocusable()) {
                            return next;
                        }
                    }
                }
                return null;
            }
        }
        return null;
    }


    public BidiFormatter getBidiFormatter() {
        return mBidiFormatter;
    }

    public View.OnKeyListener getOnKeyListener() {
        return mOnKeyListener;
    }
}
