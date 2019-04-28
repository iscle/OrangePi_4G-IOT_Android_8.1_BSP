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

import android.app.AlertDialog;
import android.app.LoaderManager;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import com.android.mail.R;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.providers.UIProvider;
import com.android.mail.providers.UIProvider.FolderType;
import com.android.mail.ui.FolderSelectorAdapter.FolderRow;
import com.android.mail.utils.Utils;
import com.google.common.collect.ImmutableSet;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Displays a folder selection dialog for the conversation provided. It allows
 * the user to mark folders to assign that conversation to.
 */
public class MultiFoldersSelectionDialog extends FolderSelectionDialog {
    private boolean mSingle;
    private final HashMap<Uri, FolderOperation> mOperations;

    public MultiFoldersSelectionDialog() {
        mOperations = new HashMap<Uri, FolderOperation>();
    }

    private static final int FOLDER_LOADER_ID = 0;
    private static final String FOLDER_QUERY_URI_TAG = "folderQueryUri";

    private static final String SAVESTATE_OPERATIONS_TAG = "operations";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSingle = !mAccount
                .supportsCapability(UIProvider.AccountCapabilities.MULTIPLE_FOLDERS_PER_CONV);
        mTitleId = R.string.change_folders_selection_dialog_title;

        if (savedInstanceState != null) {
            final FolderOperation[] savedOps = (FolderOperation[])
                    savedInstanceState.getParcelableArray(SAVESTATE_OPERATIONS_TAG);
            for (final FolderOperation op : savedOps) {
                mOperations.put(op.mFolder.folderUri.fullUri, op);
            }
        }

        final Bundle args = new Bundle(1);
        args.putParcelable(FOLDER_QUERY_URI_TAG, !Utils.isEmpty(mAccount.fullFolderListUri) ?
                mAccount.fullFolderListUri : mAccount.folderListUri);
        final Context loaderContext = getActivity().getApplicationContext();
        getLoaderManager().initLoader(FOLDER_LOADER_ID, args,
                new LoaderManager.LoaderCallbacks<Cursor>() {
                    @Override
                    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
                        final Uri queryUri = args.getParcelable(FOLDER_QUERY_URI_TAG);
                        return new CursorLoader(loaderContext, queryUri,
                                UIProvider.FOLDERS_PROJECTION, null, null, null);
                    }

                    @Override
                    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
                        final Context context = getActivity();
                        if (data == null || context == null) {
                            return;
                        }
                        final AlertDialog dialog = (AlertDialog) getDialog();
                        if (dialog == null) {
                            // This could happen if the dialog is dismissed just before the
                            // load finishes.
                            return;
                        }
                        // The number of view types changes here, so we have to reset the listview's
                        // adapter.
                        dialog.getListView().setAdapter(null);
                        dialog.getListView().setDivider(null);

                        final HashSet<String> checked = new HashSet<String>();
                        for (final Conversation conversation : mTarget) {
                            final List<Folder> rawFolders = conversation.getRawFolders();
                            if (rawFolders != null && rawFolders.size() > 0) {
                                // Parse the raw folders and get all the uris.
                                checked.addAll(Arrays.asList(Folder.getUriArray(rawFolders)));
                            } else {
                                // There are no folders for this conversation, so it must
                                // belong to the folder we are currently looking at.
                                checked.add(mCurrentFolder.folderUri.fullUri.toString());
                            }
                        }
                        final Set<String> originalChecked = ImmutableSet.copyOf(checked);
                        for (final Map.Entry<Uri, FolderOperation> entry : mOperations.entrySet()) {
                            if (entry.getValue().mAdd) {
                                checked.add(entry.getKey().toString());
                            } else {
                                checked.remove(entry.getKey().toString());
                            }
                        }
                        mAdapter.clearSections();
                        // TODO(mindyp) : bring this back in UR8 when Email providers
                        // will have divided folder sections.
                        /* final String[] headers = mContext.getResources()
                                .getStringArray(R.array.moveto_folder_sections);
                         // Currently, the number of adapters are assumed to match the
                         // number of headers in the string array.
                         mAdapter.addSection(new SystemFolderSelectorAdapter(mContext,
                         foldersCursor, checked, R.layout.multi_folders_view, null));

                        // TODO(mindyp): we currently do not support frequently moved to
                        // folders, at headers[1]; need to define what that means.*/

                        Cursor c = AddableFolderSelectorAdapter.filterFolders(data,
                                ImmutableSet.of(FolderType.INBOX_SECTION), originalChecked,
                                true /* includeOnlyInitiallySelected */);
                        if (c.getCount() > 0) {
                            mAdapter.addSection(new AddableFolderSelectorAdapter(context, c,
                                    checked, R.layout.multi_folders_view));
                        }

                        c = AddableFolderSelectorAdapter.filterFolders(data,
                                ImmutableSet.of(FolderType.INBOX_SECTION), originalChecked,
                                false /* includeOnlyInitiallySelected */);
                        if (c.getCount() > 0) {
                            mAdapter.addSection(new AddableFolderSelectorAdapter(context, c,
                                    checked, R.layout.multi_folders_view));
                        }

                        dialog.getListView().setAdapter(mAdapter);
                    }

                    @Override
                    public void onLoaderReset(Loader<Cursor> loader) {
                        mAdapter.clearSections();
                    }
                });
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelableArray(SAVESTATE_OPERATIONS_TAG,
                mOperations.values().toArray(new FolderOperation[mOperations.size()]));
    }

    @Override
    protected void onListItemClick(int position) {
        final Object item = mAdapter.getItem(position);
        if (item instanceof FolderRow) {
            update((FolderRow) item);
        }
    }

    /**
     * Call this to update the state of folders as a result of them being
     * selected / de-selected.
     *
     * @param row The item being updated.
     */
    private void update(FolderSelectorAdapter.FolderRow row) {
        final boolean add = !row.isSelected();
        if (mSingle) {
            if (!add) {
                // This would remove the check on a single radio button, so just
                // return.
                return;
            }
            // Clear any other checked items.
            for (int i = 0, size = mAdapter.getCount(); i < size; i++) {
                final Object item = mAdapter.getItem(i);
                if (item instanceof FolderRow) {
                   ((FolderRow)item).setIsSelected(false);
                   final Folder folder = ((FolderRow)item).getFolder();
                   mOperations.put(folder.folderUri.fullUri,
                           new FolderOperation(folder, false));
                }
            }
        }
        row.setIsSelected(add);
        mAdapter.notifyDataSetChanged();
        final Folder folder = row.getFolder();
        mOperations.put(folder.folderUri.fullUri, new FolderOperation(folder, add));
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case DialogInterface.BUTTON_POSITIVE:
                getConversationUpdater().assignFolder(mOperations.values(), mTarget, mBatch,
                        true /* showUndo */, false /* isMoveTo */);
                break;
            case DialogInterface.BUTTON_NEGATIVE:
                break;
            default:
                break;
        }
    }
}
