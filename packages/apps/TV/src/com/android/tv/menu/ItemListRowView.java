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

package com.android.tv.menu;

import android.content.Context;
import android.support.v17.leanback.widget.HorizontalGridView;
import android.support.v17.leanback.widget.OnChildSelectedListener;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.tv.MainActivity;
import com.android.tv.R;
import com.android.tv.util.ViewCache;

import java.util.Collections;
import java.util.List;

/**
 * A view that shows a title and list view.
 */
public class ItemListRowView extends MenuRowView implements OnChildSelectedListener {
    private static final String TAG = MenuView.TAG;
    private static final boolean DEBUG = MenuView.DEBUG;

    public interface CardView<T> {
        void onBind(T row, boolean selected);
        void onRecycled();
        void onSelected();
        void onDeselected();
    }

    private HorizontalGridView mListView;
    private CardView<?> mSelectedCard;

    public ItemListRowView(Context context) {
        this(context, null);
    }

    public ItemListRowView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ItemListRowView(Context context, AttributeSet attrs, int defStyle) {
        this(context, attrs, defStyle, 0);
    }

    public ItemListRowView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mListView = (HorizontalGridView) getContentsView();
        // Disable the position change animation of the cards.
        mListView.setItemAnimator(null);
    }

    @Override
    protected int getContentsViewId() {
        return R.id.list_view;
    }

    @Override
    public void onBind(MenuRow row) {
        super.onBind(row);
        ItemListAdapter<?> adapter = ((ItemListRow) row).getAdapter();
        adapter.mItemListView = this;

        mListView.setOnChildSelectedListener(this);
        mListView.setAdapter(adapter);
    }

    @Override
    public void initialize(int reason) {
        super.initialize(reason);
        setInitialFocusView(mListView);
        mListView.setSelectedPosition(getAdapter().getInitialPosition());
    }

    private ItemListAdapter<?> getAdapter() {
        return (ItemListAdapter<?>) mListView.getAdapter();
    }

    @Override
    public void onChildSelected(ViewGroup parent, View child, int position, long id) {
        if (DEBUG) Log.d(TAG, "onChildSelected: child=" + child);
        if (mSelectedCard == child) {
            return;
        }
        if (mSelectedCard != null) {
            mSelectedCard.onDeselected();
        }
        mSelectedCard = (CardView<?>) child;
        if (mSelectedCard != null) {
            mSelectedCard.onSelected();
        }
    }

    public static abstract class ItemListAdapter<T>
            extends RecyclerView.Adapter<ItemListAdapter.MyViewHolder> {
        private final MainActivity mMainActivity;
        private final LayoutInflater mLayoutInflater;
        private List<T> mItemList = Collections.emptyList();
        private ItemListRowView mItemListView;

        public ItemListAdapter(Context context) {
            // Only MainActivity can use the main menu.
            mMainActivity = (MainActivity) context;
            mLayoutInflater = LayoutInflater.from(context);
        }

        /**
         * In most cases, implementation should call {@link #setItemList(java.util.List)} with
         * newly update item list.
         */
        public abstract void update();

        /**
         * Gets layout resource ID. It'll be used in {@link #onCreateViewHolder}.
         */
        protected abstract int getLayoutResId(int viewType);

        /**
         * Releases all the resources which need to be released.
        */
        public void release() {
        }

        /**
         * The initial position of list that will be selected when the main menu appears.
         * By default, the first item is initially selected.
         */
        public int getInitialPosition() {
            return 0;
        }

        /** The MainActivity that the corresponding ItemListView belongs to. */
        protected MainActivity getMainActivity() {
            return mMainActivity;
        }

        /** The item list. */
        protected List<T> getItemList() {
            return mItemList;
        }

        /**
         * Sets the item list.
         *
         * <p>This sends an item change event, not a structural change event. The items of the same
         * positions retain the same identity.
         *
         * <p>If there's any structural change and relayout and rebind is needed, call
         * {@link #notifyDataSetChanged} explicitly.
         */
        protected void setItemList(List<T> itemList) {
            int oldSize = mItemList.size();
            int newSize = itemList.size();
            mItemList = itemList;
            if (oldSize > newSize) {
                notifyItemRangeChanged(0, newSize);
                notifyItemRangeRemoved(newSize, oldSize - newSize);
            } else if (oldSize < newSize) {
                notifyItemRangeChanged(0, oldSize);
                notifyItemRangeInserted(oldSize, newSize - oldSize);
            } else {
                notifyItemRangeChanged(0, oldSize);
            }
        }

        @Override
        public int getItemViewType(int position) {
            return 0;
        }

        @Override
        public int getItemCount() {
            return mItemList.size();
        }

        /**
         * Returns the position of the item.
         */
        protected int getItemPosition(T item) {
            return mItemList.indexOf(item);
        }

        /**
         * Returns {@code true} if the item list contains the item, otherwise {@code false}.
         */
        protected boolean containsItem(T item) {
            return mItemList.contains(item);
        }

        @Override
        public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = ViewCache.getInstance().getOrCreateView(
                    mLayoutInflater, getLayoutResId(viewType), parent);
            return new MyViewHolder(view);
        }

        @Override
        public void onBindViewHolder(MyViewHolder viewHolder, int position) {
            @SuppressWarnings("unchecked")
            CardView<T> cardView = (CardView<T>) viewHolder.itemView;
            cardView.onBind(mItemList.get(position), cardView.equals(mItemListView.mSelectedCard));
        }

        @Override
        public void onViewRecycled(MyViewHolder viewHolder) {
            super.onViewRecycled(viewHolder);
            CardView<T> cardView = (CardView<T>) viewHolder.itemView;
            cardView.onRecycled();
        }

        public static class MyViewHolder extends RecyclerView.ViewHolder {
            public MyViewHolder(View view) {
                super(view);
            }
        }
    }
}
