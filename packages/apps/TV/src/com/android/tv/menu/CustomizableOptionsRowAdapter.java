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

import com.android.tv.customization.CustomAction;

import java.util.ArrayList;
import java.util.List;

/**
 * An adapter of options that can accepts customization data.
 */
public abstract class CustomizableOptionsRowAdapter extends OptionsRowAdapter {
    private final List<CustomAction> mCustomActions;

    public CustomizableOptionsRowAdapter(Context context, List<CustomAction> customActions) {
        super(context);
        mCustomActions = customActions;
    }

    // Subclass should implement this and return list of {@link MenuAction}.
    // Custom actions will be added at the first or the last position in addition.
    // Note that {@link MenuAction} should have non-negative type
    // because negative types are reserved for custom actions.
    protected abstract List<MenuAction> createBaseActions();

    // Subclass should implement this to perform proper action
    // for {@link MenuAction} with the given type returned by {@link createBaseActions}.
    protected abstract void executeBaseAction(int type);

    @Override
    protected List<MenuAction> createActions() {
        List<MenuAction> actions = new ArrayList<>(createBaseActions());

        if (mCustomActions != null) {
            int position = 0;
            for (int i = 0; i < mCustomActions.size(); i++) {
                // Type of MenuAction should be unique in the Adapter.
                int type = -(i + 1);
                CustomAction customAction = mCustomActions.get(i);
                MenuAction action = new MenuAction(
                        customAction.getTitle(), type, customAction.getIconDrawable());

                if (customAction.isFront()) {
                    actions.add(position++, action);
                } else {
                    actions.add(action);
                }
            }
        }
        return actions;
    }

    @Override
    protected void executeAction(int type) {
        if (type < 0) {
            int position = -(type + 1);
            getMainActivity().startActivitySafe(mCustomActions.get(position).getIntent());
        } else {
            executeBaseAction(type);
        }
    }

    protected List<CustomAction> getCustomActions() {
        return mCustomActions;
    }
}
