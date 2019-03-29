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

package android.wm.cts.dndtargetappsdk23;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.DragEvent;
import android.view.View;
import android.widget.TextView;

/**
 * This application is compiled against SDK 23 and used to verify that apps targeting SDK 23 and
 * below do not receive global drags.
 */
public class DropTarget extends Activity {
    public static final String LOG_TAG = "DropTarget";

    private static final String RESULT_KEY_DRAG_STARTED = "DRAG_STARTED";
    private static final String RESULT_KEY_DROP_RESULT = "DROP";

    public static final String RESULT_OK = "OK";

    private TextView mTextView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        View view = getLayoutInflater().inflate(R.layout.target_activity, null);
        setContentView(view);

        mTextView = (TextView) findViewById(R.id.drag_target);
        mTextView.setOnDragListener(new OnDragListener());
    }

    private void logResult(String key, String value) {
        String result = key + "=" + value;
        Log.i(LOG_TAG, result);
        mTextView.setText(result);
    }

    private class OnDragListener implements View.OnDragListener {
        @Override
        public boolean onDrag(View v, DragEvent event) {
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    logResult(RESULT_KEY_DRAG_STARTED, RESULT_OK);
                    return true;

                case DragEvent.ACTION_DRAG_ENTERED:
                    return true;

                case DragEvent.ACTION_DRAG_LOCATION:
                    return true;

                case DragEvent.ACTION_DRAG_EXITED:
                    return true;

                case DragEvent.ACTION_DROP:
                    logResult(RESULT_KEY_DROP_RESULT, RESULT_OK);
                    return true;

                case DragEvent.ACTION_DRAG_ENDED:
                    return true;

                default:
                    return false;
            }
        }
    }
}
