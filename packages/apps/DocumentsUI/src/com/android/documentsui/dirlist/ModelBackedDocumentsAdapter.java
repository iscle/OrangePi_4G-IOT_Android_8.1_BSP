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

package com.android.documentsui.dirlist;

import static com.android.documentsui.base.DocumentInfo.getCursorInt;
import static com.android.documentsui.base.DocumentInfo.getCursorString;
import static com.android.documentsui.base.State.MODE_GRID;
import static com.android.documentsui.base.State.MODE_LIST;

import android.database.Cursor;
import android.provider.DocumentsContract.Document;
import android.util.Log;
import android.view.ViewGroup;

import com.android.documentsui.Model;
import com.android.documentsui.base.EventListener;
import com.android.documentsui.base.Lookup;
import com.android.documentsui.base.State;
import com.android.documentsui.Model.Update;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapts from dirlist.Model to something RecyclerView understands.
 */
final class ModelBackedDocumentsAdapter extends DocumentsAdapter {

    private static final String TAG = "ModelBackedDocuments";

    // Provides access to information needed when creating and view holders. This
    // isn't an ideal pattern (more transitive dependency stuff) but good enough for now.
    private final Environment mEnv;
    private final IconHelper mIconHelper;  // a transitive dependency of the holders.
    private final Lookup<String, String> mFileTypeLookup;

    /**
     * An ordered list of model IDs. This is the data structure that determines what shows up in
     * the UI, and where.
     */
    private List<String> mModelIds = new ArrayList<>();
    private EventListener<Model.Update> mModelUpdateListener;

    public ModelBackedDocumentsAdapter(
            Environment env, IconHelper iconHelper, Lookup<String, String> fileTypeLookup) {
        mEnv = env;
        mIconHelper = iconHelper;
        mFileTypeLookup = fileTypeLookup;

        mModelUpdateListener = new EventListener<Model.Update>() {
            @Override
            public void accept(Update event) {
                if (event.hasException()) {
                    onModelUpdateFailed(event.getException());
                } else {
                    onModelUpdate(mEnv.getModel());
                }
            }
        };
    }

    @Override
    EventListener<Update> getModelUpdateListener() {
        return mModelUpdateListener;
    }

    @Override
    public DocumentHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        DocumentHolder holder = null;
        final State state = mEnv.getDisplayState();
        switch (state.derivedMode) {
            case MODE_GRID:
                switch (viewType) {
                    case ITEM_TYPE_DIRECTORY:
                        holder = new GridDirectoryHolder(mEnv.getContext(), parent);
                        break;
                    case ITEM_TYPE_DOCUMENT:
                        holder = new GridDocumentHolder(mEnv.getContext(), parent, mIconHelper);
                        break;
                    default:
                        throw new IllegalStateException("Unsupported layout type.");
                }
                break;
            case MODE_LIST:
                holder = new ListDocumentHolder(
                        mEnv.getContext(), parent, mIconHelper, mFileTypeLookup);
                break;
            default:
                throw new IllegalStateException("Unsupported layout mode.");
        }

        mEnv.initDocumentHolder(holder);
        return holder;
    }

    @Override
    public void onBindViewHolder(DocumentHolder holder, int position, List<Object> payload) {
        if (payload.contains(SELECTION_CHANGED_MARKER)) {
            final boolean selected = mEnv.isSelected(mModelIds.get(position));
            holder.setSelected(selected, true);
        } else {
            onBindViewHolder(holder, position);
        }
    }

    @Override
    public void onBindViewHolder(DocumentHolder holder, int position) {
        String modelId = mModelIds.get(position);
        Cursor cursor = mEnv.getModel().getItem(modelId);
        holder.bind(cursor, modelId);

        final String docMimeType = getCursorString(cursor, Document.COLUMN_MIME_TYPE);
        final int docFlags = getCursorInt(cursor, Document.COLUMN_FLAGS);

        boolean enabled = mEnv.isDocumentEnabled(docMimeType, docFlags);
        boolean selected = mEnv.isSelected(modelId);
        if (!enabled) {
            assert(!selected);
        }
        holder.setEnabled(enabled);
        holder.setSelected(mEnv.isSelected(modelId), false);

        mEnv.onBindDocumentHolder(holder, cursor);
    }

    @Override
    public int getItemCount() {
        return mModelIds.size();
    }

    private void onModelUpdate(Model model) {
        String[] modelIds = model.getModelIds();
        mModelIds = new ArrayList<>(modelIds.length);
        for (String id : modelIds) {
            mModelIds.add(id);
        }
    }

    private void onModelUpdateFailed(Exception e) {
        Log.w(TAG, "Model update failed.", e);
        mModelIds.clear();
    }

    @Override
    public String getModelId(int adapterPosition) {
        return mModelIds.get(adapterPosition);
    }

    @Override
    public int getAdapterPosition(String modelId) {
        return mModelIds.indexOf(modelId);
    }

    @Override
    public List<String> getModelIds() {
        return mModelIds;
    }

    @Override
    public int getItemViewType(int position) {
        return isDirectory(mEnv.getModel(), position)
                ? ITEM_TYPE_DIRECTORY
                : ITEM_TYPE_DOCUMENT;
    }

    /**
     * @return true if the item type is either document or directory, false for all other
     * possible types.
     */
    public static boolean isContentType(int type) {
        switch (type) {
            case ModelBackedDocumentsAdapter.ITEM_TYPE_DOCUMENT:
            case ModelBackedDocumentsAdapter.ITEM_TYPE_DIRECTORY:
                return true;
        }
        return false;
    }

    @Override
    public void onItemSelectionChanged(String id) {
        int position = mModelIds.indexOf(id);

        if (position >= 0) {
            notifyItemChanged(position, SELECTION_CHANGED_MARKER);
        } else {
            Log.w(TAG, "Item change notification received for unknown item: " + id);
        }
    }
}
