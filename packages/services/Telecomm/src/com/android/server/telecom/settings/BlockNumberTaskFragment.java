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
 * limitations under the License
 */

package com.android.server.telecom.settings;

import android.annotation.Nullable;
import android.app.Fragment;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.BlockedNumberContract;
import com.android.server.telecom.R;

/**
 * Retained fragment that runs an async task to add a blocked number.
 *
 * <p>We run the task inside a retained fragment so that if the screen orientation changed, the
 * task does not get lost.
 */
public class BlockNumberTaskFragment extends Fragment {
    @Nullable private BlockNumberTask mTask;
    @Nullable Listener mListener;

    /**
     * Task to block a number.
     */
    private class BlockNumberTask extends AsyncTask<String, Void, Boolean> {
        private String mNumber;

        /**
         * @return true if number was blocked; false if number is already blocked.
         */
        @Override
        protected Boolean doInBackground(String... params) {
            mNumber = params[0];
            if (BlockedNumberContract.isBlocked(getContext(), mNumber)) {
                return false;
            } else {
                ContentResolver contentResolver = getContext().getContentResolver();
                ContentValues newValues = new ContentValues();
                newValues.put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER,
                        mNumber);
                contentResolver.insert(BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                        newValues);
                return true;
            }
        }

        @Override
        protected void onPostExecute(Boolean result) {
            mTask = null;
            if (mListener != null) {
                mListener.onBlocked(mNumber, !result /* alreadyBlocked */);
            }
            mListener = null;
        }
    }

    public interface Listener {
        void onBlocked(String number, boolean alreadyBlocked);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    public void onDestroy() {
        if (mTask != null) {
            mTask.cancel(true /* mayInterruptIfRunning */);
        }
        super.onDestroy();
    }

    /**
     * Runs an async task to write the number to the blocked numbers provider if it does not already
     * exist.
     *
     * Triggers {@link Listener#onBlocked(String, boolean)} when task finishes to show proper UI.
     */
    public void blockIfNotAlreadyBlocked(String number, Listener listener) {
        mListener = listener;
        mTask = new BlockNumberTask();
        mTask.execute(number);
    }
}