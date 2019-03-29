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

package com.android.cts.verifier.tv;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

/**
 * Base class for TV app tests.
 */
public abstract class TvAppVerifierActivity extends PassFailButtons.Activity {
    private static final String TAG = "TvAppVerifierActivity";

    private LayoutInflater mInflater;
    private ViewGroup mItemList;
    private View mPostTarget;

    protected View getPostTarget() {
        return mPostTarget;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mInflater = getLayoutInflater();
        // Reusing location_mode_main.
        View view = mInflater.inflate(R.layout.location_mode_main, null);
        mPostTarget = mItemList = (ViewGroup) view.findViewById(R.id.test_items);
        createTestItems();
        setContentView(view);
        setPassFailButtonClickListeners();
        setInfoResources();

        getPassButton().setEnabled(false);
    }

    protected void setButtonEnabled(View item, boolean enabled) {
        View button = item.findViewById(R.id.user_action_button);
        button.setFocusable(enabled);
        button.setClickable(enabled);
        button.setEnabled(enabled);
    }

    protected void setPassState(View item, boolean passed) {
        ImageView status = (ImageView) item.findViewById(R.id.status);
        status.setImageResource(passed ? R.drawable.fs_good : R.drawable.fs_error);
        setButtonEnabled(item, false);
        status.invalidate();
    }

    protected abstract void createTestItems();

    protected abstract void setInfoResources();

    /**
     * Call this to create a test step where the user must perform some action.
     */
    protected View createUserItem(int instructionTextId, int buttonTextId, View.OnClickListener l) {
        View item = mInflater.inflate(R.layout.tv_item, mItemList, false);
        TextView instructions = (TextView) item.findViewById(R.id.instructions);
        instructions.setText(instructionTextId);
        Button button = (Button) item.findViewById(R.id.user_action_button);
        button.setVisibility(View.VISIBLE);
        button.setText(buttonTextId);
        button.setOnClickListener(l);
        mItemList.addView(item);
        return item;
    }

    /**
     * Call this to create a test step where the test automatically evaluates whether
     * an expected condition is satisfied.
     */
    protected View createAutoItem(int stringId) {
        View item = mInflater.inflate(R.layout.tv_item, mItemList, false);
        TextView instructions = (TextView) item.findViewById(R.id.instructions);
        instructions.setText(stringId);
        mItemList.addView(item);
        return item;
    }

    /**
     * Call this to create alternative choice for the previous test step.
     */
    protected View createButtonItem(int buttonTextId, View.OnClickListener l) {
        View item = mInflater.inflate(R.layout.tv_item, mItemList, false);
        Button button = (Button) item.findViewById(R.id.user_action_button);
        button.setVisibility(View.VISIBLE);
        button.setText(buttonTextId);
        button.setOnClickListener(l);
        ImageView status = (ImageView) item.findViewById(R.id.status);
        status.setVisibility(View.INVISIBLE);
        TextView instructions = (TextView) item.findViewById(R.id.instructions);
        instructions.setVisibility(View.GONE);
        mItemList.addView(item);
        return item;
    }

    static boolean containsButton(View item, View button) {
        return item == null ? false : item.findViewById(R.id.user_action_button) == button;
    }
}
