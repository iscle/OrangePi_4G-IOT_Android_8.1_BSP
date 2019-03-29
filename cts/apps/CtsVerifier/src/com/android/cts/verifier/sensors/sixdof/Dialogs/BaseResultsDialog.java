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
import android.app.DialogFragment;
import android.app.AlertDialog;
import android.view.View;
import android.widget.TextView;

import java.util.HashMap;

/**
 * Class that sets up the displaying of test results in a dialog.
 */
public abstract class BaseResultsDialog extends DialogFragment {
    public enum ResultType {
        WAYPOINT,
        PATH,
        TIME,
        ROTATION,
        RINGS
    }

    protected ResultObject mResult;
    protected HashMap<ResultType, TextView> mTextViews;
    protected AlertDialog.Builder mBuilder;
    protected View mRootView;

    protected void setup(ResultObject result) {
        mResult = result;
        mTextViews = new HashMap<>();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Please override and call super.onCreateDialog after setting up mRootView and mBuilder.
        String title = getString(R.string.passed);
        for (ResultType resultType : mResult.getResults().keySet()) {
            if (mResult.getResults().get(resultType)) {
                mTextViews.get(resultType).setText(getString(R.string.passed));
            } else {
                title = getString(R.string.failed);
                mTextViews.get(resultType).setText(getString(R.string.failed));
            }
        }

        mBuilder.setView(mRootView);

        mBuilder.setTitle(title)
                .setPositiveButton(R.string.got_it, null);

        // Create the AlertDialog object and return it.
        return mBuilder.create();
    }
}
