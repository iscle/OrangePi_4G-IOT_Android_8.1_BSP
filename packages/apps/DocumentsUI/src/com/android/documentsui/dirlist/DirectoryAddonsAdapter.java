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

import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView.AdapterDataObserver;
import android.view.ViewGroup;

import com.android.documentsui.Model;
import com.android.documentsui.base.EventListener;
import com.android.documentsui.dirlist.Message.HeaderMessage;
import com.android.documentsui.dirlist.Message.InflateMessage;
import com.android.documentsui.Model.Update;

import java.util.List;

/**
 * Adapter wrapper that embellishes the directory list by inserting Holder views inbetween
 * items.
 */
final class DirectoryAddonsAdapter extends DocumentsAdapter {

    private static final String TAG = "SectioningDocumentsAdapterWrapper";

    private final Environment mEnv;
    private final DocumentsAdapter mDelegate;
    private final EventListener<Update> mModelUpdateListener;

    private int mBreakPosition = -1;
    // TODO: There should be two header messages (or more here). Defaulting to showing only one for
    // now.
    private final Message mHeaderMessage;
    private final Message mInflateMessage;

    DirectoryAddonsAdapter(Environment environment, DocumentsAdapter delegate) {
        mEnv = environment;
        mDelegate = delegate;
        // TODO: We should not instantiate the messages here, but rather instantiate them
        // when we get an update event.
        mHeaderMessage = new HeaderMessage(environment, this::onDismissHeaderMessage);
        mInflateMessage = new InflateMessage(environment, this::onDismissHeaderMessage);

        // Relay events published by our delegate to our listeners (presumably RecyclerView)
        // with adjusted positions.
        mDelegate.registerAdapterDataObserver(new EventRelay());

        mModelUpdateListener = this::onModelUpdate;
    }

    @Override
    EventListener<Update> getModelUpdateListener() {
        return mModelUpdateListener;
    }

    @Override
    public GridLayoutManager.SpanSizeLookup createSpanSizeLookup() {
        return new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                // Make layout whitespace span the grid. This has the effect of breaking
                // grid rows whenever layout whitespace is encountered.
                if (getItemViewType(position) == ITEM_TYPE_SECTION_BREAK
                        || getItemViewType(position) == ITEM_TYPE_HEADER_MESSAGE
                        || getItemViewType(position) == ITEM_TYPE_INFLATED_MESSAGE) {
                    return mEnv.getColumnCount();
                } else {
                    return 1;
                }
            }
        };
    }

    @Override
    public DocumentHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        DocumentHolder holder = null;
        switch (viewType) {
            case ITEM_TYPE_SECTION_BREAK:
                holder = new TransparentDividerDocumentHolder(mEnv.getContext());
                mEnv.initDocumentHolder(holder);
                break;
            case ITEM_TYPE_HEADER_MESSAGE:
                holder = new HeaderMessageDocumentHolder(mEnv.getContext(), parent);
                mEnv.initDocumentHolder(holder);
                break;
            case ITEM_TYPE_INFLATED_MESSAGE:
                holder = new InflateMessageDocumentHolder(mEnv.getContext(), parent);
                mEnv.initDocumentHolder(holder);
                break;
            default:
                holder = mDelegate.createViewHolder(parent, viewType);
        }
        return holder;
    }

    private void onDismissHeaderMessage() {
        mHeaderMessage.reset();
        if (mBreakPosition > 0) {
            mBreakPosition--;
        }
        notifyItemRemoved(0);
    }

    @Override
    public void onBindViewHolder(DocumentHolder holder, int p, List<Object> payload) {
        switch (holder.getItemViewType()) {
            case ITEM_TYPE_SECTION_BREAK:
                ((TransparentDividerDocumentHolder) holder).bind(mEnv.getDisplayState());
                break;
            case ITEM_TYPE_HEADER_MESSAGE:
                ((HeaderMessageDocumentHolder) holder).bind(mHeaderMessage);
                break;
            case ITEM_TYPE_INFLATED_MESSAGE:
                ((InflateMessageDocumentHolder) holder).bind(mInflateMessage);
                break;
            default:
                mDelegate.onBindViewHolder(holder, toDelegatePosition(p), payload);
                break;
        }
    }

    @Override
    public void onBindViewHolder(DocumentHolder holder, int p) {
        switch (holder.getItemViewType()) {
            case ITEM_TYPE_SECTION_BREAK:
                ((TransparentDividerDocumentHolder) holder).bind(mEnv.getDisplayState());
                break;
            case ITEM_TYPE_HEADER_MESSAGE:
                ((HeaderMessageDocumentHolder) holder).bind(mHeaderMessage);
                break;
            case ITEM_TYPE_INFLATED_MESSAGE:
                ((InflateMessageDocumentHolder) holder).bind(mInflateMessage);
                break;
            default:
                mDelegate.onBindViewHolder(holder, toDelegatePosition(p));
                break;
        }
    }

    @Override
    public int getItemCount() {
        int addons = mHeaderMessage.shouldShow() ? 1 : 0;
        addons += mInflateMessage.shouldShow() ? 1 : 0;
        return mBreakPosition == -1
                ? mDelegate.getItemCount() + addons
                : mDelegate.getItemCount() + addons + 1;
    }

    private void onModelUpdate(Update event) {
        // make sure the delegate handles the update before we do.
        // This isn't ideal since the delegate might be listening
        // the updates itself. But this is the safe thing to do
        // since we read model ids from the delegate
        // in our update handler.
        mDelegate.getModelUpdateListener().accept(event);

        mBreakPosition = -1;
        mInflateMessage.update(event);
        mHeaderMessage.update(event);
        // If there's any fatal error (exceptions), then no need to update the rest.
        if (event.hasException()) {
            return;
        }

        // Walk down the list of IDs till we encounter something that's not a directory, and
        // insert a whitespace element - this introduces a visual break in the grid between
        // folders and documents.
        // TODO: This code makes assumptions about the model, namely, that it performs a
        // bucketed sort where directories will always be ordered before other files. CBB.
        Model model = mEnv.getModel();
        for (int i = 0; i < model.getModelIds().length; i++) {
            if (!isDirectory(model, i)) {
                // If the break is the first thing in the list, then there are actually no
                // directories. In that case, don't insert a break at all.
                if (i > 0) {
                    mBreakPosition = i + (mHeaderMessage.shouldShow() ? 1 : 0);
                }
                break;
            }
        }
    }

    @Override
    public int getItemViewType(int p) {
        if (p == 0 && mHeaderMessage.shouldShow()) {
            return ITEM_TYPE_HEADER_MESSAGE;
        }

        if (p == mBreakPosition) {
            return ITEM_TYPE_SECTION_BREAK;
        }

        if (p == getItemCount() - 1 && mInflateMessage.shouldShow()) {
            return ITEM_TYPE_INFLATED_MESSAGE;
        }

        return mDelegate.getItemViewType(toDelegatePosition(p));
    }

    /**
     * Returns the position of an item in the delegate, adjusting
     * values that are greater than the break position.
     *
     * @param p Position within the view
     * @return Position within the delegate
     */
    private int toDelegatePosition(int p) {
        int topOffset = mHeaderMessage.shouldShow() ? 1 : 0;
        return (mBreakPosition != -1 && p > mBreakPosition) ? p - 1 - topOffset : p - topOffset;
    }

    /**
     * Returns the position of an item in the view, adjusting
     * values that are greater than the break position.
     *
     * @param p Position within the delegate
     * @return Position within the view
     */
    private int toViewPosition(int p) {
        int topOffset = mHeaderMessage.shouldShow() ? 1 : 0;
        // Offset it first so we can compare break position correctly
        p += topOffset;
        // If position is greater than or equal to the break, increase by one.
        return (mBreakPosition != -1 && p >= mBreakPosition) ? p + 1 : p;
    }

    @Override
    public List<String> getModelIds() {
        return mDelegate.getModelIds();
    }

    @Override
    public int getAdapterPosition(String modelId) {
        return toViewPosition(mDelegate.getAdapterPosition(modelId));
    }

    @Override
    public String getModelId(int p) {
        if (p == mBreakPosition) {
            return null;
        }

        if (p == 0 && mHeaderMessage.shouldShow()) {
            return null;
        }

        if (p == getItemCount() - 1 && mInflateMessage.shouldShow()) {
            return null;
        }

        return mDelegate.getModelId(toDelegatePosition(p));
    }

    @Override
    public void onItemSelectionChanged(String id) {
        mDelegate.onItemSelectionChanged(id);
    }

    // Listener we add to our delegate. This allows us to relay events published
    // by the delegate to our listeners (presumably RecyclerView) with adjusted positions.
    private final class EventRelay extends AdapterDataObserver {
        @Override
        public void onChanged() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void onItemRangeChanged(int positionStart, int itemCount) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void onItemRangeChanged(int positionStart, int itemCount, Object payload) {
            assert(itemCount == 1);
            notifyItemRangeChanged(toViewPosition(positionStart), itemCount, payload);
        }

        @Override
        public void onItemRangeInserted(int positionStart, int itemCount) {
            assert(itemCount == 1);
            if (positionStart < mBreakPosition) {
                mBreakPosition++;
            }
            notifyItemRangeInserted(toViewPosition(positionStart), itemCount);
        }

        @Override
        public void onItemRangeRemoved(int positionStart, int itemCount) {
            assert(itemCount == 1);
            if (positionStart < mBreakPosition) {
                mBreakPosition--;
            }
            notifyItemRangeRemoved(toViewPosition(positionStart), itemCount);
        }

        @Override
        public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
            throw new UnsupportedOperationException();
        }
    }
}
