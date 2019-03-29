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

package com.android.cts.verifier.managedprovisioning;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.UserManager;
import android.support.v4.content.FileProvider;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Toast;

import com.android.cts.verifier.R;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

public class NfcTestActivity extends Activity {
    private static final String TAG = "NfcTestActivity";

    /* package */ static final String EXTRA_DISALLOW_BY_POLICY = "disallowByPolicy";

    private static final String NFC_BEAM_PACKAGE = "com.android.nfc";
    private static final String NFC_BEAM_ACTIVITY = "com.android.nfc.BeamShareActivity";
    private static final String SAMPLE_IMAGE_FILENAME = "image_to_share.jpg";
    private static final String SAMPLE_IMAGE_CONTENT = "sample image";
    private static final String SAMPLE_TEXT = "sample text";
    private static final int MARGIN = 80;
    private static final int TEXT_SIZE = 200;

    private ComponentName mAdminReceiverComponent;
    private DevicePolicyManager mDevicePolicyManager;
    private UserManager mUserMangaer;
    private NfcAdapter mNfcAdapter;
    private boolean mDisallowByPolicy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.byod_nfc_test_activity);

        mAdminReceiverComponent = new ComponentName(this, DeviceAdminTestReceiver.class.getName());
        mDevicePolicyManager = (DevicePolicyManager) getSystemService(
                Context.DEVICE_POLICY_SERVICE);
        mUserMangaer = (UserManager) getSystemService(Context.USER_SERVICE);
        mDisallowByPolicy = getIntent().getBooleanExtra(EXTRA_DISALLOW_BY_POLICY, false);
        if (mDisallowByPolicy) {
            mDevicePolicyManager.addUserRestriction(mAdminReceiverComponent,
                    UserManager.DISALLOW_OUTGOING_BEAM);
        }

        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);

        final Intent shareIntent = new Intent(Intent.ACTION_SEND);

        // Sending a large amount of data requires hand-over to bluetooth, so determine here
        // if supported by the device. If bluetooth is not supported, a simple text message
        // will be transferred instead.
        final boolean hasBluetooth =
                getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH);

        if (hasBluetooth) {
            final Uri uri = createUriForImage(SAMPLE_IMAGE_FILENAME, SAMPLE_IMAGE_CONTENT);
            Uri[] uris = new Uri[]{uri};

            mNfcAdapter.setBeamPushUris(uris, this);

            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.setType("image/jpg");
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            shareIntent.putExtra(Intent.EXTRA_TEXT, SAMPLE_TEXT);
        }

        findViewById(R.id.manual_beam_button).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                mNfcAdapter.invokeBeam(NfcTestActivity.this);
            }
        });
        findViewById(R.id.intent_share_button).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                // Specify the package name of NfcBeamActivity so that the tester don't need to
                // select the activity manually.
                shareIntent.setClassName(NFC_BEAM_PACKAGE, NFC_BEAM_ACTIVITY);
                try {
                    startActivity(shareIntent);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(NfcTestActivity.this,
                            R.string.provisioning_byod_cannot_resolve_beam_activity,
                            Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Nfc beam activity not found", e);
                }
            }
        });
    }

    @Override
    public void finish() {
        if (mUserMangaer.hasUserRestriction(UserManager.DISALLOW_OUTGOING_BEAM)) {
            mDevicePolicyManager.clearUserRestriction(mAdminReceiverComponent,
                    UserManager.DISALLOW_OUTGOING_BEAM);
        }
        super.finish();
    }

    /**
     * Creates a Bitmap image that contains red on white text with a specified margin.
     * @param text Text to be displayed in the image.
     * @return A Bitmap image with the above specification.
     */
    private Bitmap createSampleImage(String text) {
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(TEXT_SIZE);
        Rect rect = new Rect();
        paint.getTextBounds(text, 0, text.length(), rect);
        int w = 2 * MARGIN + rect.right - rect.left;
        int h = 2 * MARGIN + rect.bottom - rect.top;
        Bitmap dest = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas();
        canvas.setBitmap(dest);
        paint.setColor(Color.WHITE);
        canvas.drawPaint(paint);
        paint.setColor(Color.RED);
        canvas.drawText(text, MARGIN - rect.left, MARGIN - rect.top, paint);
        return dest;
    }

    private Uri createUriForImage(String name, String text) {
        final File file = new File(getFilesDir() + File.separator + "images"
                + File.separator + name);
        file.getParentFile().mkdirs(); //if the folder doesn't exists it is created
        try {
            createSampleImage(text).compress(Bitmap.CompressFormat.JPEG, 100,
                    new FileOutputStream(file));
        } catch (FileNotFoundException e) {
            return null;
        }
        return FileProvider.getUriForFile(this,
                "com.android.cts.verifier.managedprovisioning.fileprovider", file);
    }
}
