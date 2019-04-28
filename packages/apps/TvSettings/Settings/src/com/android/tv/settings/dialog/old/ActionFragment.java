/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.tv.settings.dialog.old;

import android.os.Bundle;

import java.util.ArrayList;

/**
 * *************************
 *   DO NOT ADD LOGIC HERE!
 * *************************
 * This is a wrapper for {@link BaseActionFragment}. Place your logic in
 * there and call the function from here
 */
public class ActionFragment extends ScrollAdapterFragment implements ActionAdapter.Listener,
    ActionAdapter.OnFocusListener, ActionAdapter.OnKeyListener, LiteFragment {

    private final BaseActionFragment mBase = new BaseActionFragment(this);

    public static ActionFragment newInstance(ArrayList<Action> actions) {
        return newInstance(actions, null);
    }

    public static ActionFragment newInstance(ArrayList<Action> actions, String name) {
        ActionFragment fragment = new ActionFragment();
        fragment.setArguments(BaseActionFragment.buildArgs(actions, name));
        return fragment;
    }

    public static ActionFragment newInstance(ArrayList<Action> actions, int index) {
        ActionFragment fragment = new ActionFragment();
        fragment.setArguments(BaseActionFragment.buildArgs(actions, index));
        return fragment;
    }

    public static ActionFragment newInstance(ArrayList<Action> actions, String name, int index) {
        ActionFragment fragment = new ActionFragment();
        fragment.setArguments(BaseActionFragment.buildArgs(actions, name, index));
        return fragment;
    }

    public ActionFragment() {
        super();
        super.setBaseScrollAdapterFragment(mBase);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBase.onCreate(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        mBase.onResume();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mBase.onSaveInstanceState(outState);
    }

    @Override
    public void onActionClicked(Action action) {
        mBase.onActionClicked(action);
    }

    @Override
    public void onActionFocused(Action action) {
        mBase.onActionFocused(action);
    }

    @Override
    public void onActionSelect(Action action) {
        mBase.onActionSelect(action);
    }

    @Override
    public void onActionUnselect(Action action) {
        mBase.onActionUnselect(action);
    }

    public void setListener(ActionAdapter.Listener listener) {
        mBase.setListener(listener);
    }

    public boolean hasListener() {
        return mBase.hasListener();
    }

    public String getName() {
        return mBase.getName();
    }
}
