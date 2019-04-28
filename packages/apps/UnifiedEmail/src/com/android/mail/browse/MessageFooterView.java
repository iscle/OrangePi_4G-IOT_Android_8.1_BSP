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
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.text.BidiFormatter;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import com.android.mail.R;
import com.android.mail.analytics.Analytics;
import com.android.mail.browse.AttachmentLoader.AttachmentCursor;
import com.android.mail.browse.ConversationContainer.DetachListener;
import com.android.mail.browse.ConversationViewAdapter.MessageHeaderItem;
import com.android.mail.providers.Account;
import com.android.mail.providers.Attachment;
import com.android.mail.providers.Message;
import com.android.mail.ui.AccountFeedbackActivity;
import com.android.mail.ui.AttachmentTile;
import com.android.mail.ui.AttachmentTileGrid;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.List;

public class MessageFooterView extends LinearLayout implements DetachListener,
        LoaderManager.LoaderCallbacks<Cursor>, View.OnClickListener {

    private MessageHeaderItem mMessageHeaderItem;
    private LoaderManager mLoaderManager;
    private FragmentManager mFragmentManager;
    private AttachmentCursor mAttachmentsCursor;
    private View mViewEntireMessagePrompt;
    private AttachmentTileGrid mAttachmentGrid;
    private LinearLayout mAttachmentBarList;

    private final LayoutInflater mInflater;

    private static final String LOG_TAG = LogTag.getLogTag();

    private ConversationAccountController mAccountController;

    private BidiFormatter mBidiFormatter;

    private MessageFooterCallbacks mCallbacks;

    private Integer mOldAttachmentLoaderId;

    /**
     * Callbacks for the MessageFooterView to enable resizing the height.
     */
    public interface MessageFooterCallbacks {
        /**
         * @return <tt>true</tt> if this footer is contained within a SecureConversationViewFragment
         * and cannot assume the content is <strong>not</strong> malicious
         */
        boolean isSecure();
    }

    public MessageFooterView(Context context) {
        this(context, null);
    }

    public MessageFooterView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mInflater = LayoutInflater.from(context);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mViewEntireMessagePrompt = findViewById(R.id.view_entire_message_prompt);
        mAttachmentGrid = (AttachmentTileGrid) findViewById(R.id.attachment_tile_grid);
        mAttachmentBarList = (LinearLayout) findViewById(R.id.attachment_bar_list);

        mViewEntireMessagePrompt.setOnClickListener(this);
    }

    public void initialize(LoaderManager loaderManager, FragmentManager fragmentManager,
            ConversationAccountController accountController, MessageFooterCallbacks callbacks) {
        mLoaderManager = loaderManager;
        mFragmentManager = fragmentManager;
        mAccountController = accountController;
        mCallbacks = callbacks;
    }

    public void bind(
            MessageHeaderItem headerItem, boolean measureOnly) {
        mMessageHeaderItem = headerItem;

        final Integer attachmentLoaderId = getAttachmentLoaderId();

        // Destroy the loader if we are attempting to load a different attachment
        if (mOldAttachmentLoaderId != null &&
                !Objects.equal(mOldAttachmentLoaderId, attachmentLoaderId)) {
            mLoaderManager.destroyLoader(mOldAttachmentLoaderId);

            // Resets the footer view. This step is only done if the
            // attachmentsListUri changes so that we don't
            // repeat the work of layout and measure when
            // we're only updating the attachments.
            mAttachmentGrid.removeAllViewsInLayout();
            mAttachmentBarList.removeAllViewsInLayout();
            mViewEntireMessagePrompt.setVisibility(View.GONE);
            mAttachmentGrid.setVisibility(View.GONE);
            mAttachmentBarList.setVisibility(View.GONE);
        }
        mOldAttachmentLoaderId = attachmentLoaderId;

        // kick off load of Attachment objects in background thread
        // but don't do any Loader work if we're only measuring
        if (!measureOnly && attachmentLoaderId != null) {
            LogUtils.i(LOG_TAG, "binding footer view, calling initLoader for message %d",
                    attachmentLoaderId);
            mLoaderManager.initLoader(attachmentLoaderId, Bundle.EMPTY, this);
        }

        // Do an initial render if initLoader didn't already do one
        if (mAttachmentGrid.getChildCount() == 0 &&
                mAttachmentBarList.getChildCount() == 0) {
            renderAttachments(false);
        }

        final ConversationMessage message = mMessageHeaderItem.getMessage();
        mViewEntireMessagePrompt.setVisibility(
                message.clipped && !TextUtils.isEmpty(message.permalink) ? VISIBLE : GONE);
        setVisibility(mMessageHeaderItem.isExpanded() ? VISIBLE : GONE);
    }

    private void renderAttachments(boolean loaderResult) {
        final List<Attachment> attachments;
        if (mAttachmentsCursor != null && !mAttachmentsCursor.isClosed()) {
            int i = -1;
            attachments = Lists.newArrayList();
            while (mAttachmentsCursor.moveToPosition(++i)) {
                attachments.add(mAttachmentsCursor.get());
            }
        } else {
            // before the attachment loader results are in, we can still render immediately using
            // the basic info in the message's attachmentsJSON
            attachments = mMessageHeaderItem.getMessage().getAttachments();
        }
        renderAttachments(attachments, loaderResult);
    }

    private void renderAttachments(List<Attachment> attachments, boolean loaderResult) {
        if (attachments == null || attachments.isEmpty()) {
            return;
        }

        // filter the attachments into tiled and non-tiled
        final int maxSize = attachments.size();
        final List<Attachment> tiledAttachments = new ArrayList<Attachment>(maxSize);
        final List<Attachment> barAttachments = new ArrayList<Attachment>(maxSize);

        for (Attachment attachment : attachments) {
            // attachments in secure views are displayed in the footer so the user may interact with
            // them; for normal views there is no need to show inline attachments in the footer
            // since users can interact with them in place
            if (!attachment.isInlineAttachment() || mCallbacks.isSecure()) {
                if (AttachmentTile.isTiledAttachment(attachment)) {
                    tiledAttachments.add(attachment);
                } else {
                    barAttachments.add(attachment);
                }
            }
        }

        mMessageHeaderItem.getMessage().attachmentsJson = Attachment.toJSONArray(attachments);

        // All attachments are inline, don't display anything.
        if (tiledAttachments.isEmpty() && barAttachments.isEmpty()) {
            return;
        }

        if (!tiledAttachments.isEmpty()) {
            renderTiledAttachments(tiledAttachments, loaderResult);
        }
        if (!barAttachments.isEmpty()) {
            renderBarAttachments(barAttachments, loaderResult);
        }
    }

    private void renderTiledAttachments(List<Attachment> tiledAttachments, boolean loaderResult) {
        mAttachmentGrid.setVisibility(View.VISIBLE);

        // Setup the tiles.
        mAttachmentGrid.configureGrid(mFragmentManager, getAccount(),
                mMessageHeaderItem.getMessage(), tiledAttachments, loaderResult);
    }

    private void renderBarAttachments(List<Attachment> barAttachments, boolean loaderResult) {
        mAttachmentBarList.setVisibility(View.VISIBLE);

        final Account account = getAccount();
        for (Attachment attachment : barAttachments) {
            final Uri id = attachment.getIdentifierUri();
            MessageAttachmentBar barAttachmentView =
                    (MessageAttachmentBar) mAttachmentBarList.findViewWithTag(id);

            if (barAttachmentView == null) {
                barAttachmentView = MessageAttachmentBar.inflate(mInflater, this);
                barAttachmentView.setTag(id);
                barAttachmentView.initialize(mFragmentManager);
                mAttachmentBarList.addView(barAttachmentView);
            }

            barAttachmentView.render(attachment, account, mMessageHeaderItem.getMessage(),
                    loaderResult, getBidiFormatter());
        }
    }

    private Integer getAttachmentLoaderId() {
        Integer id = null;
        final Message msg = mMessageHeaderItem == null ? null : mMessageHeaderItem.getMessage();
        if (msg != null && msg.hasAttachments && msg.attachmentListUri != null) {
            id = msg.attachmentListUri.hashCode();
        }
        return id;
    }

    @Override
    public void onDetachedFromParent() {
        // Do nothing.
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new AttachmentLoader(getContext(),
                mMessageHeaderItem.getMessage().attachmentListUri);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        mAttachmentsCursor = (AttachmentCursor) data;

        if (mAttachmentsCursor == null || mAttachmentsCursor.isClosed()) {
            return;
        }

        renderAttachments(true);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mAttachmentsCursor = null;
    }

    private BidiFormatter getBidiFormatter() {
        if (mBidiFormatter == null) {
            final ConversationViewAdapter adapter = mMessageHeaderItem != null
                    ? mMessageHeaderItem.getAdapter() : null;
            if (adapter == null) {
                mBidiFormatter = BidiFormatter.getInstance();
            } else {
                mBidiFormatter = adapter.getBidiFormatter();
            }
        }
        return mBidiFormatter;
    }

    @Override
    public void onClick(View v) {
        viewEntireMessage();
    }

    private void viewEntireMessage() {
        Analytics.getInstance().sendEvent("view_entire_message", "clicked", null, 0);

        final Context context = getContext();
        final Intent intent = new Intent();
        final String activityName =
                context.getResources().getString(R.string.full_message_activity);
        if (TextUtils.isEmpty(activityName)) {
            LogUtils.wtf(LOG_TAG, "Trying to open clipped message with no activity defined");
            return;
        }
        intent.setClassName(context, activityName);
        final Account account = getAccount();
        final ConversationMessage message = mMessageHeaderItem.getMessage();
        if (account != null && !TextUtils.isEmpty(message.permalink)) {
            intent.putExtra(AccountFeedbackActivity.EXTRA_ACCOUNT_URI, account.uri);
            intent.putExtra(FullMessageContract.EXTRA_PERMALINK, message.permalink);
            intent.putExtra(FullMessageContract.EXTRA_ACCOUNT_NAME, account.getEmailAddress());
            intent.putExtra(FullMessageContract.EXTRA_SERVER_MESSAGE_ID, message.serverId);
            context.startActivity(intent);
        }
    }

    private Account getAccount() {
        return mAccountController != null ? mAccountController.getAccount() : null;
    }
}
