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
package com.android.cts.verifier.sensors.sixdof.Utils.ResultObjects;

import com.android.cts.verifier.sensors.sixdof.Dialogs.AccuracyResultDialog;
import com.android.cts.verifier.sensors.sixdof.Dialogs.BaseResultsDialog;

import java.util.HashMap;

/**
 * Handles the results from the tests
 */
public class ResultObject {
    private HashMap<BaseResultsDialog.ResultType, Boolean> mResults;

    /**
     * Constructor for this class.
     *
     * @param results List to indicate whether a test has failed or passed.
     */
    public ResultObject(HashMap<BaseResultsDialog.ResultType, Boolean> results) {
        mResults = results;
    }

    /**
     * Returns true if all tests pass and false for anything else.
     */
    public boolean hasPassed() {
        for (Boolean result : mResults.values()) {
            if (!result) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns List to indicate whether a test has failed or passed.
     */
    public HashMap<AccuracyResultDialog.ResultType, Boolean> getResults() {
        return new HashMap<>(mResults);
    }
}
