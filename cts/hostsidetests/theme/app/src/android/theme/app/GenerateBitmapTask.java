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

package android.theme.app;

import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Canvas;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;
import android.view.View;

import java.io.File;
import java.io.FileOutputStream;

/**
 * A task which gets the UI element to render to a bitmap and then saves that
 * as a PNG asynchronously.
 */
class GenerateBitmapTask extends AsyncTask<Void, Void, Boolean> {
    private static final String TAG = "GenerateBitmapTask";

    private final View mView;
    private final File mOutDir;

    private Bitmap mBitmap;

    protected final String mName;

    public GenerateBitmapTask(View view, File outDir, String name) {
        mView = view;
        mOutDir = outDir;
        mName = name;
    }

    @Override
    protected void onPreExecute() {
        if (mView.getWidth() == 0 || mView.getHeight() == 0) {
            Log.e(TAG, "Unable to draw view due to incorrect size: " + mName);
            return;
        }

        mBitmap = Bitmap.createBitmap(mView.getWidth(), mView.getHeight(),
                Bitmap.Config.ARGB_8888);

        final Canvas canvas = new Canvas(mBitmap);
        mView.draw(canvas);
    }

    @Override
    protected Boolean doInBackground(Void... ignored) {
        final Bitmap bitmap = mBitmap;
        if (bitmap == null) {
            return false;
        }

        final File file = new File(mOutDir, mName + ".png");
        if (file.exists() && !file.canWrite()) {
            Log.e(TAG, "Unable to write file: " + file.getAbsolutePath());
            return false;
        }

        boolean success = false;
        try {
            FileOutputStream stream = null;
            try {
                stream = new FileOutputStream(file);
                success = bitmap.compress(CompressFormat.PNG, 100, stream);
            } finally {
                if (stream != null) {
                    stream.flush();
                    stream.close();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        } finally {
            bitmap.recycle();
        }

        return success;
    }
}
