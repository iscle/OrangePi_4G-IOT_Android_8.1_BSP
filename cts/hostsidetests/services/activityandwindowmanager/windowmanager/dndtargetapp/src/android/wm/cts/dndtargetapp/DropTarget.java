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

package android.wm.cts.dndtargetapp;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.util.Log;
import android.view.DragAndDropPermissions;
import android.view.DragEvent;
import android.view.View;
import android.widget.TextView;

public class DropTarget extends Activity {
    public static final String LOG_TAG = "DropTarget";

    private static final String RESULT_KEY_DRAG_STARTED = "DRAG_STARTED";
    private static final String RESULT_KEY_DRAG_ENDED = "DRAG_ENDED";
    private static final String RESULT_KEY_EXTRAS = "EXTRAS";
    private static final String RESULT_KEY_DROP_RESULT = "DROP";
    private static final String RESULT_KEY_DETAILS = "DETAILS";
    private static final String RESULT_KEY_ACCESS_AFTER = "AFTER";
    private static final String RESULT_KEY_ACCESS_BEFORE = "BEFORE";
    private static final String RESULT_KEY_CLIP_DATA_ERROR = "CLIP_DATA_ERROR";
    private static final String RESULT_KEY_CLIP_DESCR_ERROR = "CLIP_DESCR_ERROR";
    private static final String RESULT_KEY_LOCAL_STATE_ERROR = "LOCAL_STATE_ERROR";

    public static final String RESULT_OK = "OK";
    public static final String RESULT_EXCEPTION = "Exception";
    public static final String RESULT_MISSING = "MISSING";
    public static final String RESULT_LEAKING = "LEAKING";

    protected static final String MAGIC_VALUE = "42";

    private TextView mTextView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        View view = getLayoutInflater().inflate(R.layout.target_activity, null);
        setContentView(view);

        setUpDropTarget("request_none", new OnDragUriReadListener(false));
        setUpDropTarget("request_read", new OnDragUriReadListener());
        setUpDropTarget("request_write", new OnDragUriWriteListener());
        setUpDropTarget("request_read_nested", new OnDragUriReadPrefixListener());
        setUpDropTarget("request_take_persistable", new OnDragUriTakePersistableListener());
    }

    private void setUpDropTarget(String mode, OnDragUriListener listener) {
        if (!mode.equals(getIntent().getStringExtra("mode"))) {
            return;
        }
        mTextView = (TextView)findViewById(R.id.drag_target);
        mTextView.setText(mode);
        mTextView.setOnDragListener(listener);
    }

    private String checkExtraValue(DragEvent event) {
        PersistableBundle extras = event.getClipDescription().getExtras();
        if (extras == null) {
            return "Null";
        }

        final String value = extras.getString("extraKey");
        if ("extraValue".equals(value)) {
            return RESULT_OK;
        }
        return value;
    }

    private void logResult(String key, String value) {
        Log.i(LOG_TAG, key + "=" + value);
        mTextView.setText(mTextView.getText() + "\n" + key + "=" + value);
    }

    private abstract class OnDragUriListener implements View.OnDragListener {
        private final boolean requestPermissions;

        public OnDragUriListener(boolean requestPermissions) {
            this.requestPermissions = requestPermissions;
        }

        @Override
        public boolean onDrag(View v, DragEvent event) {
            checkDragEvent(event);

            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    logResult(RESULT_KEY_DRAG_STARTED, RESULT_OK);
                    logResult(RESULT_KEY_EXTRAS, checkExtraValue(event));
                    return true;

                case DragEvent.ACTION_DRAG_ENTERED:
                    return true;

                case DragEvent.ACTION_DRAG_LOCATION:
                    return true;

                case DragEvent.ACTION_DRAG_EXITED:
                    return true;

                case DragEvent.ACTION_DROP:
                    // Try accessing the Uri without the permissions grant.
                    accessContent(event, RESULT_KEY_ACCESS_BEFORE, false);

                    // Try accessing the Uri with the permission grant (if required);
                    accessContent(event, RESULT_KEY_DROP_RESULT, requestPermissions);

                    // Try accessing the Uri after the permissions have been released.
                    accessContent(event, RESULT_KEY_ACCESS_AFTER, false);
                    return true;

                case DragEvent.ACTION_DRAG_ENDED:
                    logResult(RESULT_KEY_DRAG_ENDED, RESULT_OK);
                    return true;

                default:
                    return false;
            }
        }

        private void accessContent(DragEvent event, String resultKey, boolean requestPermissions) {
            String result;
            try {
                result = processDrop(event, requestPermissions);
            } catch (SecurityException e) {
                result = RESULT_EXCEPTION;
                if (resultKey.equals(RESULT_KEY_DROP_RESULT)) {
                    logResult(RESULT_KEY_DETAILS, e.getMessage());
                }
            }
            logResult(resultKey, result);
        }

        private String processDrop(DragEvent event, boolean requestPermissions) {
            final ClipData clipData = event.getClipData();
            if (clipData == null) {
                return "Null ClipData";
            }
            if (clipData.getItemCount() == 0) {
                return "Empty ClipData";
            }
            ClipData.Item item = clipData.getItemAt(0);
            if (item == null) {
                return "Null ClipData.Item";
            }
            Uri uri = item.getUri();
            if (uri == null) {
                return "Null Uri";
            }

            DragAndDropPermissions permissions = null;
            if (requestPermissions) {
                permissions = requestDragAndDropPermissions(event);
                if (permissions == null) {
                    return "Null DragAndDropPermissions";
                }
            }

            try {
                return processUri(uri);
            } finally {
                if (permissions != null) {
                    permissions.release();
                }
            }
        }

        abstract protected String processUri(Uri uri);
    }

    private void checkDragEvent(DragEvent event) {
        final int action = event.getAction();

        // ClipData should be available for ACTION_DROP only.
        final ClipData clipData = event.getClipData();
        if (action == DragEvent.ACTION_DROP) {
            if (clipData == null) {
                logResult(RESULT_KEY_CLIP_DATA_ERROR, RESULT_MISSING);
            }
        } else {
            if (clipData != null) {
                logResult(RESULT_KEY_CLIP_DATA_ERROR, RESULT_LEAKING + action);
            }
        }

        // ClipDescription should be always available except for ACTION_DRAG_ENDED.
        final ClipDescription clipDescription = event.getClipDescription();
        if (action != DragEvent.ACTION_DRAG_ENDED) {
            if (clipDescription == null) {
                logResult(RESULT_KEY_CLIP_DESCR_ERROR, RESULT_MISSING + action);
            }
        } else {
            if (clipDescription != null) {
                logResult(RESULT_KEY_CLIP_DESCR_ERROR, RESULT_LEAKING);
            }
        }

        // Local state should be always null for cross-app drags.
        final Object localState = event.getLocalState();
        if (localState != null) {
            logResult(RESULT_KEY_LOCAL_STATE_ERROR, RESULT_LEAKING + action);
        }
    }

    private class OnDragUriReadListener extends OnDragUriListener {
        OnDragUriReadListener(boolean requestPermissions) {
            super(requestPermissions);
        }

        OnDragUriReadListener() {
            super(true);
        }

        protected String processUri(Uri uri) {
            return checkQueryResult(uri, MAGIC_VALUE);
        }

        protected String checkQueryResult(Uri uri, String expectedValue) {
            Cursor cursor = null;
            try {
                cursor = getContentResolver().query(uri, null, null, null, null);
                if (cursor == null) {
                    return "Null Cursor";
                }
                cursor.moveToPosition(0);
                String value = cursor.getString(0);
                if (!expectedValue.equals(value)) {
                    return "Wrong value: " + value;
                }
                return RESULT_OK;
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
    }

    private class OnDragUriWriteListener extends OnDragUriListener {
        OnDragUriWriteListener() {
            super(true);
        }

        protected String processUri(Uri uri) {
            ContentValues values = new ContentValues();
            values.put("key", 100);
            getContentResolver().update(uri, values, null, null);
            return RESULT_OK;
        }
    }

    private class OnDragUriReadPrefixListener extends OnDragUriReadListener {
        @Override
        protected String processUri(Uri uri) {
            final String result1 = queryPrefixed(uri, "1");
            if (!result1.equals(RESULT_OK)) {
                return result1;
            }
            final String result2 = queryPrefixed(uri, "2");
            if (!result2.equals(RESULT_OK)) {
                return result2;
            }
            return queryPrefixed(uri, "3");
        }

        private String queryPrefixed(Uri uri, String selector) {
            final Uri prefixedUri = Uri.parse(uri.toString() + "/" + selector);
            return checkQueryResult(prefixedUri, selector);
        }
    }

    private class OnDragUriTakePersistableListener extends OnDragUriListener {
        OnDragUriTakePersistableListener() {
            super(true);
        }

        @Override
        protected String processUri(Uri uri) {
            getContentResolver().takePersistableUriPermission(
                    uri, View.DRAG_FLAG_GLOBAL_URI_READ);
            getContentResolver().releasePersistableUriPermission(
                    uri, View.DRAG_FLAG_GLOBAL_URI_READ);
            return RESULT_OK;
        }
    }
}
