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

import com.android.tv.settings.widget.ScrollAdapterView;

import java.util.ArrayList;

/**
 * Subclass of ScrollAdapterFragment which handles actions.
 * <p>
 * Users should instantiate using {@link #newInstance(ArrayList, String)}. To learn when items are
 * clicked, the activity should implement {@link ActionAdapter.Listener}. <br/>
 * fragments need to call {@link #setListener(ActionAdapter.Listener)} to call their custom listener
 */
public class BaseActionFragment extends BaseScrollAdapterFragment
        implements ActionAdapter.Listener, ActionAdapter.OnFocusListener,
        ActionAdapter.OnKeyListener {

    private final LiteFragment mFragment;

    /**
     * Key for a string name for the fragment.
     */
    private static final String EXTRA_NAME = "name";

    /**
     * Key for a parcelable array of actions.
     */
    private static final String EXTRA_ACTIONS = "actions";

    /**
     * Key for the selected item index.
     */
    private static final String EXTRA_INDEX = "index";

    /**
     * Optional name of the fragment.
     */
    private String mName;
    private ActionAdapter mAdapter;
    private boolean mAddedSavedActions;

    /**
     * If {@code true}, select the first checked item after populating.
     */
    private boolean mSelectFirstChecked;

    private int mIndexToSelect;

    private ActionAdapter.Listener mListener = null;

    public BaseActionFragment(LiteFragment fragment) {
        super(fragment);
        mFragment = fragment;
        mIndexToSelect = -1;
        mSelectFirstChecked = true;
    }

    /**
     * Creates a new action fragment with the given list of actions and a given name.
     */
    public static Bundle buildArgs(ArrayList<Action> actions, String name) {
        return buildArgs(actions, name, -1);
    }

    /**
     * Creates a new action fragment with the given list of actions and starting index.
     */
    public static Bundle buildArgs(ArrayList<Action> actions, int index) {
        return buildArgs(actions, null, index);
    }

    /**
     * Creates a new action fragment with the given list of actions, given name and starting index.
     */
    public static Bundle buildArgs(ArrayList<Action> actions, String name, int index) {
        Bundle args = new Bundle();
        args.putParcelableArrayList(EXTRA_ACTIONS, actions);
        args.putString(EXTRA_NAME, name);
        args.putInt(EXTRA_INDEX, index);
        return args;
    }

    public void onCreate(Bundle savedInstanceState) {
        mAdapter = new ActionAdapter(mFragment.getActivity());
        mAddedSavedActions = false;
        if (savedInstanceState != null) {
            ArrayList<Action> actions = savedInstanceState.getParcelableArrayList(EXTRA_ACTIONS);
            int savedIndex = savedInstanceState.getInt(EXTRA_INDEX, -1);
            if (actions != null) {
                for (Action action : actions) {
                    mAdapter.addAction(action);
                }
                if (savedIndex >= 0 && savedIndex < actions.size()) {
                    mIndexToSelect = savedIndex;
                }
                mAddedSavedActions = true;
            }
        } else {
            int startIndex = mFragment.getArguments().getInt(EXTRA_INDEX, -1);
            if (startIndex != -1) {
                // When first launching action fragment and start index is not -1, set it to
                // mIndexToSelect.
                mIndexToSelect = startIndex;
            }
        }
        mName = mFragment.getArguments().getString(EXTRA_NAME);
        loadActionsFromArgumentsIfNecessary();
        mAdapter.setListener(this);
        mAdapter.setOnFocusListener(this);
        mAdapter.setOnKeyListener(this);
    }

    public void onResume() {
        // ensure the list is built.
        ScrollAdapterView sav = getScrollAdapterView();

        sav.addOnScrollListener(mAdapter);
        if (getAdapter() != mAdapter) {
            mAdapter.setScrollAdapterView(sav);
            setAdapter(mAdapter);
        }
        if (mIndexToSelect != -1) {
            getScrollAdapterView().setSelection(mIndexToSelect);
            mIndexToSelect = -1; // reset this.
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (hasCreatedView()) {
            // Try to save instance state only if the view has already been created.
            outState.putParcelableArrayList(EXTRA_ACTIONS, mAdapter.getActions());
            outState.putInt(EXTRA_INDEX, getScrollAdapterView().getSelectedItemPosition());
        }
    }

    /**
     * If the custom lister has been set using {@link #setListener(ActionAdapter.Listener)}, use it.
     * <br/>
     * If not, use the activity's default listener.
     * <br/>
     * Don't broadcast the click if the action is disabled or only displays info.
     */
    @Override
    public void onActionClicked(Action action) {
        // eat events if action is disabled or only displays info
        if (!action.isEnabled() || action.infoOnly()) {
            return;
        }

        if (mListener != null) {
            mListener.onActionClicked(action);
        } else if (mFragment.getActivity() instanceof ActionAdapter.Listener) {
            ActionAdapter.Listener listener = (ActionAdapter.Listener) mFragment.getActivity();
            listener.onActionClicked(action);
        }
    }

    @Override
    public void onActionFocused(Action action) {
        if (mFragment.getActivity() instanceof ActionAdapter.OnFocusListener) {
            ActionAdapter.OnFocusListener listener = (ActionAdapter.OnFocusListener) mFragment
                    .getActivity();
            listener.onActionFocused(action);
        }
    }

    @Override
    public void onActionSelect(Action action) {
        if (mFragment.getActivity() instanceof ActionAdapter.OnKeyListener) {
            ActionAdapter.OnKeyListener listener = (ActionAdapter.OnKeyListener) mFragment
                    .getActivity();
            listener.onActionSelect(action);
        }
    }

    @Override
    public void onActionUnselect(Action action) {
        if (mFragment.getActivity() instanceof ActionAdapter.OnKeyListener) {
            ActionAdapter.OnKeyListener listener = (ActionAdapter.OnKeyListener) mFragment
                    .getActivity();
            listener.onActionUnselect(action);
        }
    }

    public String getName() {
        return mName;
    }

    /**
     * Fragments need to call this method in its {@link #onResume()} to set the
     * custom listener. <br/>
     * Activities do not need to call this method
     *
     * @param listener
     */
    public void setListener(ActionAdapter.Listener listener) {
        mListener = listener;
    }

    public boolean hasListener() {
        return mListener != null;
    }

    /**
     * Sets whether to not to select the first checked action on resume.
     */
    public void setSelectFirstChecked(boolean selectFirstChecked) {
        mSelectFirstChecked = selectFirstChecked;
    }

    private void loadActionsFromArgumentsIfNecessary() {
        if (mFragment.getArguments() != null && !mAddedSavedActions) {
            ArrayList<Action> actions = mFragment.getArguments()
                    .getParcelableArrayList(EXTRA_ACTIONS);
            if (actions != null) {
                final int size = actions.size();
                for (int index = 0; index < size; ++index) {
                    if (mSelectFirstChecked && actions.get(index).isChecked()
                            && mIndexToSelect == -1) {
                        mIndexToSelect = index;
                    }
                    mAdapter.addAction(actions.get(index));
                }
            }
        }
    }
}
