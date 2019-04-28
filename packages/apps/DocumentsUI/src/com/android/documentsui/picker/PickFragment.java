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

package com.android.documentsui.picker;

import static com.android.documentsui.services.FileOperationService.OPERATION_DELETE;
import static com.android.documentsui.services.FileOperationService.OPERATION_COPY;
import static com.android.documentsui.services.FileOperationService.OPERATION_COMPRESS;
import static com.android.documentsui.services.FileOperationService.OPERATION_EXTRACT;
import static com.android.documentsui.services.FileOperationService.OPERATION_MOVE;
import static com.android.documentsui.services.FileOperationService.OPERATION_UNKNOWN;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.android.documentsui.BaseActivity;
import com.android.documentsui.Injector;
import com.android.documentsui.R;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.State;
import com.android.documentsui.services.FileOperationService.OpType;

/**
 * Display pick confirmation bar, usually for selecting a directory.
 */
public class PickFragment extends Fragment {
    public static final String TAG = "PickFragment";

    private static final String ACTION_KEY = "action";
    private static final String COPY_OPERATION_SUBTYPE_KEY = "copyOperationSubType";
    private static final String PICK_TARGET_KEY = "pickTarget";

    private final View.OnClickListener mPickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            mInjector.actions.pickDocument(mPickTarget);
        }
    };

    private final View.OnClickListener mCancelListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            final BaseActivity activity = BaseActivity.get(PickFragment.this);
            activity.setResult(Activity.RESULT_CANCELED);
            activity.finish();
        }
    };

    private Injector<ActionHandler<PickActivity>> mInjector;
    private int mAction;
    // Only legal values are OPERATION_COPY, OPERATION_COMPRESS, OPERATION_EXTRACT,
    // OPERATION_MOVE, and unset (OPERATION_UNKNOWN).
    private @OpType int mCopyOperationSubType = OPERATION_UNKNOWN;
    private DocumentInfo mPickTarget;
    private View mContainer;
    private TextView mPick;
    private TextView mCancel;

    public static void show(FragmentManager fm) {
        // Fragment can be restored by FragmentManager automatically.
        if (get(fm) != null) {
            return;
        }

        final PickFragment fragment = new PickFragment();
        final FragmentTransaction ft = fm.beginTransaction();
        ft.replace(R.id.container_save, fragment, TAG);
        ft.commitAllowingStateLoss();
    }

    public static PickFragment get(FragmentManager fm) {
        return (PickFragment) fm.findFragmentByTag(TAG);
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mContainer = inflater.inflate(R.layout.fragment_pick, container, false);

        mPick = (TextView) mContainer.findViewById(android.R.id.button1);
        mPick.setOnClickListener(mPickListener);

        mCancel = (TextView) mContainer.findViewById(android.R.id.button2);
        mCancel.setOnClickListener(mCancelListener);

        updateView();
        return mContainer;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (savedInstanceState != null) {
            // Restore status
            mAction = savedInstanceState.getInt(ACTION_KEY);
            mCopyOperationSubType =
                    savedInstanceState.getInt(COPY_OPERATION_SUBTYPE_KEY);
            mPickTarget = savedInstanceState.getParcelable(PICK_TARGET_KEY);
            updateView();
        }

        mInjector = ((PickActivity) getActivity()).getInjector();
    }

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(ACTION_KEY, mAction);
        outState.putInt(COPY_OPERATION_SUBTYPE_KEY, mCopyOperationSubType);
        outState.putParcelable(PICK_TARGET_KEY, mPickTarget);
    }

    /**
     * @param action Which action defined in State is the picker shown for.
     */
    public void setPickTarget(
            int action, @OpType int copyOperationSubType, DocumentInfo pickTarget) {
        assert(copyOperationSubType != OPERATION_DELETE);

        mAction = action;
        mCopyOperationSubType = copyOperationSubType;
        mPickTarget = pickTarget;
        if (mContainer != null) {
            updateView();
        }
    }

    /**
     * Applies the state of fragment to the view components.
     */
    private void updateView() {
        switch (mAction) {
            case State.ACTION_OPEN_TREE:
                mPick.setText(R.string.button_select);
                mCancel.setVisibility(View.GONE);
                break;
            case State.ACTION_PICK_COPY_DESTINATION:
                int titleId;
                switch (mCopyOperationSubType) {
                    case OPERATION_COPY:
                        titleId = R.string.button_copy;
                        break;
                    case OPERATION_COMPRESS:
                        titleId = R.string.button_compress;
                        break;
                    case OPERATION_EXTRACT:
                        titleId = R.string.button_extract;
                        break;
                    case OPERATION_MOVE:
                        titleId = R.string.button_move;
                        break;
                    default:
                        throw new UnsupportedOperationException();
                }
                mPick.setText(titleId);
                mCancel.setVisibility(View.VISIBLE);
                break;
            default:
                mContainer.setVisibility(View.GONE);
                return;
        }

        if (mPickTarget != null && (
                mAction == State.ACTION_OPEN_TREE ||
                mPickTarget.isCreateSupported())) {
            mContainer.setVisibility(View.VISIBLE);
        } else {
            mContainer.setVisibility(View.GONE);
        }
    }
}
