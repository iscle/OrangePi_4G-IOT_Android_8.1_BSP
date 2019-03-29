/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.autofillservice.cts;

import static android.autofillservice.cts.CannedFillResponse.ResponseType.NULL;

import static com.google.common.truth.Truth.assertWithMessage;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.assist.AssistStructure;
import android.autofillservice.cts.CannedFillResponse.CannedDataset;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.util.Log;
import android.util.SparseArray;
import android.view.autofill.AutofillManager;

import com.google.common.base.Preconditions;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * This class simulates authentication at the dataset at reponse level
 */
public class AuthenticationActivity extends AbstractAutoFillActivity {

    private static final String TAG = "AuthenticationActivity";
    private static final String EXTRA_DATASET_ID = "dataset_id";
    private static final String EXTRA_RESPONSE_ID = "response_id";

    private static final int MSG_WAIT_FOR_LATCH = 1;

    private static Bundle sData;
    private static final SparseArray<CannedDataset> sDatasets = new SparseArray<>();
    private static final SparseArray<CannedFillResponse> sResponses = new SparseArray<>();
    private static final ArrayList<PendingIntent> sPendingIntents = new ArrayList<>();

    private static Object sLock = new Object();

    // Guarded by sLock
    private static int sResultCode;

    // Guarded by sLock
    // Used to block response until it's counted down.
    private static CountDownLatch sResponseLatch;

    private Handler mHandler;

    static void resetStaticState() {
        setResultCode(RESULT_OK);
        sDatasets.clear();
        sResponses.clear();
        for (int i = 0; i < sPendingIntents.size(); i++) {
            final PendingIntent pendingIntent = sPendingIntents.get(i);
            Log.d(TAG, "Cancelling " + pendingIntent);
            pendingIntent.cancel();
        }
    }

    /**
     * Creates an {@link IntentSender} with the given unique id for the given dataset.
     */
    public static IntentSender createSender(Context context, int id,
            CannedDataset dataset) {
        Preconditions.checkArgument(id > 0, "id must be positive");
        Preconditions.checkState(sDatasets.get(id) == null, "already have id");
        sDatasets.put(id, dataset);
        return createSender(context, EXTRA_DATASET_ID, id);
    }

    /**
     * Creates an {@link IntentSender} with the given unique id for the given fill response.
     */
    public static IntentSender createSender(Context context, int id,
            CannedFillResponse response) {
        Preconditions.checkArgument(id > 0, "id must be positive");
        Preconditions.checkState(sResponses.get(id) == null, "already have id");
        sResponses.put(id, response);
        return createSender(context, EXTRA_RESPONSE_ID, id);
    }

    private static IntentSender createSender(Context context, String extraName, int id) {
        final Intent intent = new Intent(context, AuthenticationActivity.class);
        intent.putExtra(extraName, id);
        final PendingIntent pendingIntent = PendingIntent.getActivity(context, id, intent, 0);
        sPendingIntents.add(pendingIntent);
        return pendingIntent.getIntentSender();
    }

    /**
     * Creates an {@link IntentSender} with the given unique id.
     */
    public static IntentSender createSender(Context context, int id) {
        Preconditions.checkArgument(id > 0, "id must be positive");
        return PendingIntent
                .getActivity(context, id, new Intent(context, AuthenticationActivity.class), 0)
                .getIntentSender();
    }

    public static Bundle getData() {
        final Bundle data = sData;
        sData = null;
        return data;
    }

    /**
     * Sets the value that's passed to {@link Activity#setResult(int, Intent)} when on
     * {@link Activity#onCreate(Bundle)}.
     */
    public static void setResultCode(int resultCode) {
        synchronized (sLock) {
            sResultCode = resultCode;
        }
    }

    /**
     * Sets the value that's passed to {@link Activity#setResult(int, Intent)}, but only calls it
     * after the {@code latch}'s countdown reaches {@code 0}.
     */
    public static void setResultCode(CountDownLatch latch, int resultCode) {
        synchronized (sLock) {
            sResponseLatch = latch;
            sResultCode = resultCode;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mHandler = new Handler(Looper.getMainLooper(), (m) -> {
            switch (m.what) {
                case MSG_WAIT_FOR_LATCH:
                    waitForLatchAndDoIt();
                    break;
                default:
                    throw new IllegalArgumentException("invalid message: " + m);
            }
            return true;
        });

        if (sResponseLatch != null) {
            Log.d(TAG, "Delaying message until latch is counted down");
            mHandler.dispatchMessage(mHandler.obtainMessage(MSG_WAIT_FOR_LATCH));
        } else {
            doIt();
        }
    }

    private void waitForLatchAndDoIt() {
        try {
            final boolean called = sResponseLatch.await(5, TimeUnit.SECONDS);
            if (!called) {
                throw new IllegalStateException("latch not called in 5 seconds");
            }
            doIt();
        } catch (InterruptedException e) {
            Thread.interrupted();
            throw new IllegalStateException("interrupted");
        }
    }

    private void doIt() {
        // We should get the assist structure...
        final AssistStructure structure = getIntent().getParcelableExtra(
                AutofillManager.EXTRA_ASSIST_STRUCTURE);
        assertWithMessage("structure not called").that(structure).isNotNull();

        // and the bundle
        sData = getIntent().getBundleExtra(AutofillManager.EXTRA_CLIENT_STATE);
        final CannedFillResponse response =
                sResponses.get(getIntent().getIntExtra(EXTRA_RESPONSE_ID, 0));
        final CannedDataset dataset =
                sDatasets.get(getIntent().getIntExtra(EXTRA_DATASET_ID, 0));

        final Parcelable result;

        if (response != null) {
            if (response.getResponseType() == NULL) {
                result = null;
            } else {
                result = response
                        .asFillResponse((id) -> Helper.findNodeByResourceId(structure, id));
            }
        } else if (dataset != null) {
            result = dataset.asDataset((id) -> Helper.findNodeByResourceId(structure, id));
        } else {
            throw new IllegalStateException("no dataset or response");
        }

        // Pass on the auth result
        final Intent intent = new Intent();
        intent.putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, result);
        final int resultCode;
        synchronized (sLock) {
            resultCode = sResultCode;
        }
        Log.d(TAG, "Returning code " + resultCode);
        setResult(resultCode, intent);

        // Done
        finish();
    }
}
