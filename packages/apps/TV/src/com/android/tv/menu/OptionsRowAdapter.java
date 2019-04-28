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
import android.view.View;

import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.analytics.Tracker;

import java.util.List;

/*
 * An adapter of options.
 */
public abstract class OptionsRowAdapter extends ItemListRowView.ItemListAdapter<MenuAction> {
    private static final String CUSTOM_ACTION_LABEL = "custom action";
    protected final Tracker mTracker;
    private List<MenuAction> mActionList;

    private final View.OnClickListener mMenuActionOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            final MenuAction action = (MenuAction) view.getTag();
            view.post(new Runnable() {
                @Override
                public void run() {
                    int resId = action.getActionNameResId();
                    if (resId == 0) {
                        mTracker.sendMenuClicked(CUSTOM_ACTION_LABEL);
                    } else {
                        mTracker.sendMenuClicked(resId);
                    }
                    executeAction(action.getType());
                }
            });
        }
    };

    public OptionsRowAdapter(Context context) {
        super(context);
        mTracker = TvApplication.getSingletons(context).getTracker();
    }

    /**
     * Update action list and its content.
     */
    @Override
    public void update() {
        if (mActionList == null) {
            mActionList = createActions();
            setItemList(mActionList);
        } else {
            updateActions();
        }
    }

    @Override
    protected int getLayoutResId(int viewType) {
        return R.layout.menu_card_action;
    }

    protected abstract List<MenuAction> createActions();
    protected abstract void updateActions();
    protected abstract void executeAction(int type);

    /**
     * Gets the action at the given position.
     * Note that action at the position may differ from returned by {@link #createActions}.
     * See {@link CustomizableOptionsRowAdapter}
     */
    protected MenuAction getAction(int position) {
        return mActionList.get(position);
    }

    @Override
    public void onBindViewHolder(MyViewHolder viewHolder, int position) {
        super.onBindViewHolder(viewHolder, position);

        viewHolder.itemView.setTag(getItemList().get(position));
        viewHolder.itemView.setOnClickListener(mMenuActionOnClickListener);
    }

    @Override
    public int getItemViewType(int position) {
        // This makes 1:1 mapping from MenuAction to ActionCardView. That is, an ActionCardView will
        // not be used(recycled) by other type of MenuAction. So the selection state of the view can
        // be preserved.
        return mActionList.get(position).getType();
    }
}
