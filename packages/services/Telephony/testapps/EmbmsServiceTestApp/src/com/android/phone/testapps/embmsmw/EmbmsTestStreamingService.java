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
 * limitations under the License
 */

package com.android.phone.testapps.embmsmw;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.telephony.mbms.MbmsErrors;
import android.telephony.mbms.MbmsStreamingSessionCallback;
import android.telephony.mbms.StreamingService;
import android.telephony.mbms.StreamingServiceCallback;
import android.telephony.mbms.StreamingServiceInfo;
import android.telephony.mbms.vendor.MbmsStreamingServiceBase;
import android.util.Log;

import com.android.internal.os.SomeArgs;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EmbmsTestStreamingService extends Service {
    private static final Set<String> ALLOWED_PACKAGES = new HashSet<String>() {{
        add("com.android.phone.testapps.embmsfrontend");
    }};

    private static final String TAG = "EmbmsTestStreaming";

    private static final long INITIALIZATION_DELAY = 200;
    private static final long SEND_SERVICE_LIST_DELAY = 300;
    private static final long START_STREAMING_DELAY = 500;

    private static final int SEND_STREAMING_SERVICES_LIST = 1;

    private final Map<FrontendAppIdentifier, MbmsStreamingSessionCallback> mAppCallbacks =
            new HashMap<>();

    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private Handler.Callback mWorkerCallback = (msg) -> {
        switch (msg.what) {
            case SEND_STREAMING_SERVICES_LIST:
                SomeArgs args = (SomeArgs) msg.obj;
                FrontendAppIdentifier appKey = (FrontendAppIdentifier) args.arg1;
                List<StreamingServiceInfo> services = (List) args.arg2;
                MbmsStreamingSessionCallback appCallback = mAppCallbacks.get(appKey);
                if (appCallback != null) {
                    appCallback.onStreamingServicesUpdated(services);
                }
                break;
        }
        return true;
    };

    private final MbmsStreamingServiceBase mBinder = new MbmsStreamingServiceBase() {
        @Override
        public int initialize(MbmsStreamingSessionCallback callback, int subId) {
            int packageUid = Binder.getCallingUid();
            String[] packageNames = getPackageManager().getPackagesForUid(packageUid);
            if (packageNames == null) {
                return MbmsErrors.InitializationErrors.ERROR_APP_PERMISSIONS_NOT_GRANTED;
            }
            boolean isUidAllowed = Arrays.stream(packageNames).anyMatch(ALLOWED_PACKAGES::contains);
            if (!isUidAllowed) {
                return MbmsErrors.InitializationErrors.ERROR_APP_PERMISSIONS_NOT_GRANTED;
            }

            mHandler.postDelayed(() -> {
                FrontendAppIdentifier appKey = new FrontendAppIdentifier(packageUid, subId);
                if (!mAppCallbacks.containsKey(appKey)) {
                    mAppCallbacks.put(appKey, callback);
                } else {
                    callback.onError(
                            MbmsErrors.InitializationErrors.ERROR_DUPLICATE_INITIALIZE, "");
                    return;
                }
                callback.onMiddlewareReady();
            }, INITIALIZATION_DELAY);
            return MbmsErrors.SUCCESS;
        }

        @Override
        public int requestUpdateStreamingServices(int subscriptionId, List<String> serviceClasses) {
            FrontendAppIdentifier appKey =
                    new FrontendAppIdentifier(Binder.getCallingUid(), subscriptionId);
            checkInitialized(appKey);

            List<StreamingServiceInfo> serviceInfos =
                    StreamingServiceRepository.getStreamingServicesForClasses(serviceClasses);

            SomeArgs args = SomeArgs.obtain();
            args.arg1 = appKey;
            args.arg2 = serviceInfos;

            mHandler.removeMessages(SEND_STREAMING_SERVICES_LIST);
            mHandler.sendMessageDelayed(
                    mHandler.obtainMessage(SEND_STREAMING_SERVICES_LIST, args),
                    SEND_SERVICE_LIST_DELAY);
            return MbmsErrors.SUCCESS;
        }

        @Override
        public int startStreaming(int subscriptionId, String serviceId,
                StreamingServiceCallback callback) {
            FrontendAppIdentifier appKey =
                    new FrontendAppIdentifier(Binder.getCallingUid(), subscriptionId);
            checkInitialized(appKey);
            checkServiceExists(serviceId);

            if (StreamStateTracker.getStreamingState(appKey, serviceId) ==
                    StreamingService.STATE_STARTED) {
                return MbmsErrors.StreamingErrors.ERROR_DUPLICATE_START_STREAM;
            }

            mHandler.postDelayed(
                    () -> StreamStateTracker.startStreaming(appKey, serviceId, callback,
                            StreamingService.REASON_BY_USER_REQUEST),
                    START_STREAMING_DELAY);
            return MbmsErrors.SUCCESS;
        }

        @Override
        public Uri getPlaybackUri(int subscriptionId, String serviceId) {
            FrontendAppIdentifier appKey =
                    new FrontendAppIdentifier(Binder.getCallingUid(), subscriptionId);
            checkInitialized(appKey);
            checkServiceExists(serviceId);

            Uri streamingUri = StreamingServiceRepository.getUriForService(serviceId);
            if (streamingUri == null) {
                throw new IllegalArgumentException("Invalid service ID");
            }
            return streamingUri;
        }

        @Override
        public void stopStreaming(int subscriptionId, String serviceId) {
            FrontendAppIdentifier appKey =
                    new FrontendAppIdentifier(Binder.getCallingUid(), subscriptionId);
            checkInitialized(appKey);
            checkServiceExists(serviceId);

            mHandler.post(() -> StreamStateTracker.stopStreaming(appKey, serviceId,
                    StreamingService.REASON_BY_USER_REQUEST));
            StreamStateTracker.dispose(appKey, serviceId);
        }

        @Override
        public void dispose(int subscriptionId) {
            FrontendAppIdentifier appKey =
                    new FrontendAppIdentifier(Binder.getCallingUid(), subscriptionId);
            checkInitialized(appKey);

            Log.i(TAG, "Disposing app with uid " + Binder.getCallingUid());
            StreamStateTracker.disposeAll(appKey);
            mAppCallbacks.remove(appKey);
        }

        @Override
        public void onAppCallbackDied(int uid, int subscriptionId) {
            FrontendAppIdentifier appKey = new FrontendAppIdentifier(uid, subscriptionId);

            Log.i(TAG, "Disposing app " + appKey + " due to binder death");
            StreamStateTracker.disposeAll(appKey);
            mAppCallbacks.remove(appKey);
        }
    };

    @Override
    public void onDestroy() {
        super.onCreate();
        mHandlerThread.quitSafely();
        logd("EmbmsTestStreamingService onDestroy");
    }

    @Override
    public IBinder onBind(Intent intent) {
        logd("EmbmsTestStreamingService onBind");
        mHandlerThread = new HandlerThread("EmbmsTestStreamingServiceWorker");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper(), mWorkerCallback);
        return mBinder;
    }

    private static void logd(String s) {
        Log.d(TAG, s);
    }

    private void checkInitialized(FrontendAppIdentifier appKey) {
        if (!mAppCallbacks.containsKey(appKey)) {
            throw new IllegalStateException("Not yet initialized");
        }
    }

    private void checkServiceExists(String serviceId) {
        if (StreamingServiceRepository.getStreamingServiceInfoForId(serviceId) == null) {
            throw new IllegalArgumentException("Invalid service ID");
        }
    }
}
