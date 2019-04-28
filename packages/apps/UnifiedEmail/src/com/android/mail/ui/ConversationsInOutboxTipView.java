/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Context;
import android.content.Loader;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.TextAppearanceSpan;
import android.view.View;

import com.android.mail.R;
import com.android.mail.browse.ConversationCursor;
import com.android.mail.content.ObjectCursor;
import com.android.mail.content.ObjectCursorLoader;
import com.android.mail.preferences.AccountPreferences;
import com.android.mail.providers.Account;
import com.android.mail.providers.Folder;
import com.android.mail.providers.UIProvider;

/**
 * Tip that is displayed in conversation list of 'Sent' folder whenever there are
 * one or more messages in the Outbox.
 */
public class ConversationsInOutboxTipView extends ConversationTipView {
    private Account mAccount = null;
    private AccountPreferences mAccountPreferences;
    private LoaderManager mLoaderManager;
    private FolderSelector mFolderSelector;
    private Folder mOutbox;
    private int mOutboxCount = -1;

    private static final int LOADER_FOLDER_LIST =
            AbstractActivityController.LAST_FRAGMENT_LOADER_ID + 100;

    public ConversationsInOutboxTipView(Context context) {
        super(context);
    }

    public void bind(final Account account, final FolderSelector folderSelector) {
        mAccount = account;
        mAccountPreferences = AccountPreferences.get(getContext(), account);
        mFolderSelector = folderSelector;
    }

    @Override
    protected OnClickListener getTextAreaOnClickListener() {
        return new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mOutbox != null) {
                    mFolderSelector.onFolderSelected(mOutbox);
                }
            }
        };
    }

    @Override
    public void onUpdate(Folder folder, ConversationCursor cursor) {
        if (mLoaderManager != null && folder != null) {
            if ((folder.type & UIProvider.FolderType.SENT) > 0) {
                // Only display this tip if user is viewing the Sent folder
                mLoaderManager.initLoader(LOADER_FOLDER_LIST, null, mFolderListLoaderCallbacks);
            }
        }
    }

    private final LoaderCallbacks<ObjectCursor<Folder>> mFolderListLoaderCallbacks =
            new LoaderManager.LoaderCallbacks<ObjectCursor<Folder>>() {
        @Override
        public void onLoaderReset(final Loader<ObjectCursor<Folder>> loader) {
            // Do nothing
        }

        @Override
        public void onLoadFinished(final Loader<ObjectCursor<Folder>> loader,
                final ObjectCursor<Folder> data) {
            if (data != null && data.moveToFirst()) {
                do {
                    final Folder folder = data.getModel();
                    if ((folder.type & UIProvider.FolderType.OUTBOX) > 0) {
                        mOutbox = folder;
                        onOutboxTotalCount(folder.totalCount);
                    }
                } while (data.moveToNext());
            }
        }

        @Override
        public Loader<ObjectCursor<Folder>> onCreateLoader(final int id, final Bundle args) {
            // This loads all folders in order to find 'Outbox'.  We could consider adding a new
            // query to load folders of a given type to make this more efficient, but should be
            // okay for now since this is triggered infrequently (only when user visits the
            // 'Sent' folder).
            return new ObjectCursorLoader<Folder>(getContext(),
                    mAccount.folderListUri, UIProvider.FOLDERS_PROJECTION, Folder.FACTORY);
        }
    };

    private void onOutboxTotalCount(int outboxCount) {
        if (mOutboxCount != outboxCount) {
            mOutboxCount = outboxCount;
            if (outboxCount > 0) {
                updateText();
            }
        }
        if (outboxCount == 0) {
            // Clear the last seen count, so that new messages in Outbox will always cause this
            // tip to appear again.
            mAccountPreferences.setLastSeenOutboxCount(0);
        }
    }

    private void updateText() {
        // Update the display text to reflect current mOutboxCount
        final Resources resources = getContext().getResources();
        final String subString = mOutbox.name;
        final String entireString = resources.getString(R.string.unsent_messages_in_outbox,
                String.valueOf(mOutboxCount), subString);
        final SpannableString text = new SpannableString(entireString);
        final int index = entireString.indexOf(subString);
        text.setSpan(new TextAppearanceSpan(getContext(), R.style.LinksInTipTextAppearance), index,
                index + subString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        setText(text);
    }

    @Override
    public boolean getShouldDisplayInList() {
        return (mOutboxCount > 0 && mOutboxCount != mAccountPreferences.getLastSeenOutboxCount());
    }

    @Override
    public void bindFragment(final LoaderManager loaderManager, final Bundle savedInstanceState) {
        mLoaderManager = loaderManager;
    }

    @Override
    public void dismiss() {
        // Do not show this tip again until we have a new count.  Note this is not quite
        // ideal behavior since after a user dismisses an "1 unsent in outbox" tip,
        // the message stuck in Outbox could get sent, and a new one gets stuck.
        // If the user checks back on on Sent folder then, we don't reshow the message since count
        // itself hasn't changed, but ideally we should since it's a different message than before.
        // However if user checks the Sent folder in between (when there were 0 messages
        // in Outbox), the preference is cleared (see {@link onOutboxTotalCount}).
        mAccountPreferences.setLastSeenOutboxCount(mOutboxCount);
        super.dismiss();
    }
}
