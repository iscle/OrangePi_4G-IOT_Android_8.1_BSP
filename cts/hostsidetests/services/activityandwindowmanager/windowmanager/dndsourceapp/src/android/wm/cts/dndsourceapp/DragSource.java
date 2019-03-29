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

package android.wm.cts.dndsourceapp;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipDescription;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.FileUriExposedException;
import android.os.PersistableBundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import java.io.File;

public class DragSource extends Activity{
    private static final String LOG_TAG = "DragSource";

    private static final String RESULT_KEY_START_DRAG = "START_DRAG";
    private static final String RESULT_KEY_DETAILS = "DETAILS";
    private static final String RESULT_OK = "OK";
    private static final String RESULT_EXCEPTION = "Exception";

    private static final String URI_PREFIX =
            "content://" + DragSourceContentProvider.AUTHORITY + "/data";

    private static final String MAGIC_VALUE = "42";
    private static final long TIMEOUT_CANCEL = 150;

    private TextView mTextView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        View view = getLayoutInflater().inflate(R.layout.source_activity, null);
        setContentView(view);

        final Uri plainUri = Uri.parse(URI_PREFIX + "/" + MAGIC_VALUE);

        setUpDragSource("disallow_global", plainUri, 0);
        setUpDragSource("cancel_soon", plainUri, View.DRAG_FLAG_GLOBAL);

        setUpDragSource("grant_none", plainUri, View.DRAG_FLAG_GLOBAL);
        setUpDragSource("grant_read", plainUri,
                View.DRAG_FLAG_GLOBAL | View.DRAG_FLAG_GLOBAL_URI_READ);
        setUpDragSource("grant_write", plainUri,
                View.DRAG_FLAG_GLOBAL | View.DRAG_FLAG_GLOBAL_URI_WRITE);
        setUpDragSource("grant_read_persistable", plainUri,
                View.DRAG_FLAG_GLOBAL | View.DRAG_FLAG_GLOBAL_URI_READ |
                        View.DRAG_FLAG_GLOBAL_PERSISTABLE_URI_PERMISSION);

        final Uri prefixUri = Uri.parse(URI_PREFIX);

        setUpDragSource("grant_read_prefix", prefixUri,
                View.DRAG_FLAG_GLOBAL | View.DRAG_FLAG_GLOBAL_URI_READ |
                        View.DRAG_FLAG_GLOBAL_PREFIX_URI_PERMISSION);
        setUpDragSource("grant_read_noprefix", prefixUri,
                View.DRAG_FLAG_GLOBAL | View.DRAG_FLAG_GLOBAL_URI_READ);

        final Uri fileUri = Uri.fromFile(new File("/sdcard/sample.jpg"));

        setUpDragSource("file_local", fileUri, 0);
        setUpDragSource("file_global", fileUri, View.DRAG_FLAG_GLOBAL);
    }

    private void setUpDragSource(String mode, final Uri uri, final int flags) {
        if (!mode.equals(getIntent().getStringExtra("mode"))) {
            return;
        }
        mTextView = (TextView) findViewById(R.id.drag_source);
        mTextView.setText(mode);
        mTextView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() != MotionEvent.ACTION_DOWN) {
                    return false;
                }
                try {
                    final ClipDescription clipDescription = new ClipDescription("", new String[] {
                            ClipDescription.MIMETYPE_TEXT_URILIST });
                    PersistableBundle extras = new PersistableBundle(1);
                    extras.putString("extraKey", "extraValue");
                    clipDescription.setExtras(extras);
                    final ClipData clipData = new ClipData(clipDescription, new ClipData.Item(uri));
                    v.startDragAndDrop(
                            clipData,
                            new View.DragShadowBuilder(v),
                            null,
                            flags);
                    logResult(RESULT_KEY_START_DRAG, RESULT_OK);
                } catch (FileUriExposedException e) {
                    logResult(RESULT_KEY_DETAILS, e.getMessage());
                    logResult(RESULT_KEY_START_DRAG, RESULT_EXCEPTION);
                }
                if (mode.equals("cancel_soon")) {
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            v.cancelDragAndDrop();
                        }
                    }, TIMEOUT_CANCEL);
                }
                return true;
            }
        });
    }

    private void logResult(String key, String value) {
        Log.i(LOG_TAG, key + "=" + value);
        mTextView.setText(mTextView.getText() + "\n" + key + "=" + value);
    }
}
