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
import com.android.mail.providers.Folder;
import com.android.mail.providers.UIProvider;
import com.android.mail.ui.FolderSelectorAdapter.FolderRow;
import com.android.mail.utils.Utils;

import java.util.ArrayList;

/**
 * Displays a folder selection dialog for the conversation provided. It allows
 * the user to switch a conversation from one folder to another.
 */
public class SingleFolderSelectionDialog extends FolderSelectionDialog {
    public SingleFolderSelectionDialog() {}

    private static final int FOLDER_LOADER_ID = 0;
    private static final String FOLDER_QUERY_URI_TAG = "folderQueryUri";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mTitleId = R.string.move_to_selection_dialog_title;

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
                        // The number of view types changes here, so we have to reset the ListView's
                        // adapter.
                        dialog.getListView().setAdapter(null);
                        dialog.getListView().setDivider(null);

                        mAdapter.clearSections();

                        // Create a system folder adapter and an adapter for hierarchical
                        // and user folders. If there are no folders added to either of them,
                        // do not add as a section since a 0-count adapter will result in an
                        // IndexOutOfBoundsException.
                        SystemFolderSelectorAdapter sysFolderAdapter =
                                new SystemFolderSelectorAdapter(context, data,
                                    R.layout.single_folders_view, mCurrentFolder);
                        if (sysFolderAdapter.getCount() > 0) {
                            mAdapter.addSection(sysFolderAdapter);
                        }

                        // TODO(pwestbro): determine if we need to call filterFolders
                        // if filterFolders is not necessary, remove the method decl with one arg.
                        UserFolderHierarchicalFolderSelectorAdapter hierarchicalAdapter =
                                new UserFolderHierarchicalFolderSelectorAdapter(context,
                                    AddableFolderSelectorAdapter.filterFolders(data),
                                    R.layout.single_folders_view, mCurrentFolder);
                        if (hierarchicalAdapter.getCount() > 0) {
                            mAdapter.addSection(hierarchicalAdapter);
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
    protected void onListItemClick(int position) {
        final Object item = mAdapter.getItem(position);
        if (item instanceof FolderRow) {
            final Folder folder = ((FolderRow) item).getFolder();
            ArrayList<FolderOperation> ops = new ArrayList<FolderOperation>();
            // Remove the current folder and add the new folder.
            ops.add(new FolderOperation(mCurrentFolder, false));
            ops.add(new FolderOperation(folder, true));
            getConversationUpdater()
                    .assignFolder(ops, mTarget, mBatch, true /* showUndo */, true /* isMoveTo */);
            dismiss();
        }
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        // Do nothing.
    }
}
