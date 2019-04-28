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

package com.android.tv.dialog;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;

import com.android.tv.MainActivity;
import com.android.tv.R;

/**
 * Dialog fragment with full screen.
 */
public class FullscreenDialogFragment extends SafeDismissDialogFragment {
    public static final String DIALOG_TAG = FullscreenDialogFragment.class.getSimpleName();
    public static final String VIEW_LAYOUT_ID = "viewLayoutId";
    public static final String TRACKER_LABEL = "trackerLabel";

    /**
     * Creates a FullscreenDialogFragment. View class of viewLayoutResId should
     * implement {@link DialogView}.
     */
    public static FullscreenDialogFragment newInstance(int viewLayoutResId, String trackerLabel) {
        FullscreenDialogFragment f = new FullscreenDialogFragment();
        Bundle args = new Bundle();
        args.putInt(VIEW_LAYOUT_ID, viewLayoutResId);
        args.putString(TRACKER_LABEL, trackerLabel);
        f.setArguments(args);
        return f;
    }

    private String mTrackerLabel;
    private DialogView mDialogView;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        FullscreenDialog dialog =
                new FullscreenDialog(getActivity(), R.style.Theme_TV_dialog_Fullscreen);
        LayoutInflater inflater = LayoutInflater.from(getActivity());
        Bundle args = getArguments();
        mTrackerLabel = args.getString(TRACKER_LABEL);
        int viewLayoutResId = args.getInt(VIEW_LAYOUT_ID);
        View v = inflater.inflate(viewLayoutResId, null);
        dialog.setContentView(v);
        mDialogView = (DialogView) v;
        mDialogView.initialize((MainActivity) getActivity(), dialog);
        return dialog;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mDialogView.onDestroy();
    }

    @Override
    public String getTrackerLabel() {
        return mTrackerLabel;
    }

    private class FullscreenDialog extends Dialog {
        public FullscreenDialog(Context context, int theme) {
            super(context, theme);
        }

        @Override
        public void setContentView(View dialogView) {
            super.setContentView(dialogView);
            mDialogView = (DialogView) dialogView;
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            boolean handled = super.dispatchKeyEvent(event);
            return handled || ((View) mDialogView).dispatchKeyEvent(event);
        }

        @Override
        public void onBackPressed() {
            mDialogView.onBackPressed();
        }
    }

    /**
     * Interface for the view of {@link FullscreenDialogFragment}.
     */
    public interface DialogView {
        /**
         * Called after the view is inflated and attached to the dialog.
         */
        void initialize(MainActivity activity, Dialog dialog);
        /**
         * Called when a back key is pressed.
         */
        void onBackPressed();
        /**
         * Called when {@link DialogFragment#onDestroy} is called.
         */
        void onDestroy();
    }
}
