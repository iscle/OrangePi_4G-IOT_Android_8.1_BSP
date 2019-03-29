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

package com.android.cts.verifier.projection.widgets;

import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

import com.android.cts.verifier.R;
import com.android.cts.verifier.projection.ProjectionActivity;
import com.android.cts.verifier.projection.ProjectionPresentationType;

public class ProjectionWidgetActivity extends ProjectionActivity {
    private static final String TAG = ProjectionWidgetActivity.class.getSimpleName();

    private class InjectDPadClickListener implements OnClickListener {
        private int mKeyCode;

        InjectDPadClickListener(int keyCode) {
            mKeyCode = keyCode;
        }

        @Override
        public void onClick(View view) {
            if (mService != null) {
                try {
                    mService.onKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, mKeyCode));
                } catch (RemoteException e) {
                    Log.e(TAG, "Error executing onKeyEvent", e);
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View view = setContentViewAndInfoResources(R.layout.pwa_widgets, R.string.pwa_test,
                R.string.pwa_info);

        mType = ProjectionPresentationType.BASIC_WIDGETS;

        Button button;
        {
            button = (Button) view.findViewById(R.id.up_button);
            button.setOnClickListener(new InjectDPadClickListener(KeyEvent.KEYCODE_DPAD_UP));
        }
        {
            button = (Button) view.findViewById(R.id.down_button);
            button.setOnClickListener(new InjectDPadClickListener(KeyEvent.KEYCODE_DPAD_DOWN));
        }
    }
}
