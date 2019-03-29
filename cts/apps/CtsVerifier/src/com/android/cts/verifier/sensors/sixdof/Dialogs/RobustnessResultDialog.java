/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.cts.verifier.sensors.sixdof.Dialogs;

import com.android.cts.verifier.R;
import com.android.cts.verifier.sensors.sixdof.Utils.ResultObjects.ResultObject;

import android.app.Dialog;
import android.os.Bundle;
import android.app.AlertDialog;
import android.view.LayoutInflater;
import android.widget.TextView;

/**
 * Dialog that displays the results of the first test.
 */
public class RobustnessResultDialog extends BaseResultsDialog {

    public static RobustnessResultDialog newInstance(ResultObject resultObject) {
        RobustnessResultDialog dialog = new RobustnessResultDialog();
        dialog.setup(resultObject);
        return dialog;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        mBuilder = new AlertDialog.Builder(getActivity());
        // Get the layout inflater.
        LayoutInflater inflater = getActivity().getLayoutInflater();
        mRootView = inflater.inflate(R.layout.dialog_result_robustness, null);

        mTextViews.put(ResultType.WAYPOINT, (TextView) mRootView.findViewById(R.id.tvWaypointResult));
        mTextViews.put(ResultType.PATH, (TextView) mRootView.findViewById(R.id.tvPathResult));
        mTextViews.put(ResultType.TIME, (TextView) mRootView.findViewById(R.id.tvTimeResult));
        mTextViews.put(ResultType.ROTATION, (TextView) mRootView.findViewById(R.id.tvRotationResult));

        return super.onCreateDialog(savedInstanceState);
    }
}
