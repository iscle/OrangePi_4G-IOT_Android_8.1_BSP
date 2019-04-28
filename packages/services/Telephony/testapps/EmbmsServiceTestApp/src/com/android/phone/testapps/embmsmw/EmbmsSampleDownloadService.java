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

import android.app.Activity;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.telephony.MbmsDownloadSession;
import android.telephony.mbms.DownloadRequest;
import android.telephony.mbms.DownloadStateCallback;
import android.telephony.mbms.FileInfo;
import android.telephony.mbms.FileServiceInfo;
import android.telephony.mbms.MbmsDownloadSessionCallback;
import android.telephony.mbms.MbmsErrors;
import android.telephony.mbms.UriPathPair;
import android.telephony.mbms.vendor.IMbmsDownloadService;
import android.telephony.mbms.vendor.MbmsDownloadServiceBase;
import android.telephony.mbms.vendor.VendorUtils;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class EmbmsSampleDownloadService extends Service {
    private static final Set<String> ALLOWED_PACKAGES = new HashSet<String>() {{
        add("com.android.phone.testapps.embmsdownload");
    }};

    private static final String LOG_TAG = "EmbmsSampleDownload";
    private static final long INITIALIZATION_DELAY = 200;
    private static final long SEND_FILE_SERVICE_INFO_DELAY = 500;
    private static final long DOWNLOAD_DELAY_MS = 1000;
    private static final long FILE_SEPARATION_DELAY = 500;

    private final IMbmsDownloadService mBinder = new MbmsDownloadServiceBase() {
        @Override
        public int initialize(int subId, MbmsDownloadSessionCallback callback) {
            int packageUid = Binder.getCallingUid();
            String[] packageNames = getPackageManager().getPackagesForUid(packageUid);
            if (packageNames == null) {
                return MbmsErrors.InitializationErrors.ERROR_APP_PERMISSIONS_NOT_GRANTED;
            }
            boolean isUidAllowed = Arrays.stream(packageNames).anyMatch(ALLOWED_PACKAGES::contains);
            if (!isUidAllowed) {
                return MbmsErrors.InitializationErrors.ERROR_APP_PERMISSIONS_NOT_GRANTED;
            }

            // Do initialization with a bit of a delay to simulate work being done.
            mHandler.postDelayed(() -> {
                FrontendAppIdentifier appKey = new FrontendAppIdentifier(packageUid, subId);
                if (!mAppCallbacks.containsKey(appKey)) {
                    mAppCallbacks.put(appKey, callback);
                    ComponentName appReceiver = VendorUtils.getAppReceiverFromPackageName(
                            EmbmsSampleDownloadService.this,
                            getPackageManager().getNameForUid(packageUid));
                    mAppReceivers.put(appKey, appReceiver);
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
        public int requestUpdateFileServices(int subscriptionId,
                List<String> serviceClasses) throws RemoteException {
            FrontendAppIdentifier appKey =
                    new FrontendAppIdentifier(Binder.getCallingUid(), subscriptionId);
            checkInitialized(appKey);

            List<FileServiceInfo> serviceInfos =
                    FileServiceRepository.getInstance(EmbmsSampleDownloadService.this)
                    .getFileServicesForClasses(serviceClasses);

            mHandler.postDelayed(() -> {
                MbmsDownloadSessionCallback appCallback = mAppCallbacks.get(appKey);
                appCallback.onFileServicesUpdated(serviceInfos);
            }, SEND_FILE_SERVICE_INFO_DELAY);
            return MbmsErrors.SUCCESS;
        }

        @Override
        public int setTempFileRootDirectory(int subscriptionId,
                String rootDirectoryPath) throws RemoteException {
            FrontendAppIdentifier appKey =
                    new FrontendAppIdentifier(Binder.getCallingUid(), subscriptionId);
            checkInitialized(appKey);

            if (mActiveDownloadRequests.getOrDefault(appKey, Collections.emptySet()).size() > 0) {
                return MbmsErrors.DownloadErrors.ERROR_CANNOT_CHANGE_TEMP_FILE_ROOT;
            }
            mAppTempFileRoots.put(appKey, rootDirectoryPath);
            return MbmsErrors.SUCCESS;
        }

        @Override
        public int download(DownloadRequest downloadRequest) {
            FrontendAppIdentifier appKey = new FrontendAppIdentifier(
                    Binder.getCallingUid(), downloadRequest.getSubscriptionId());
            checkInitialized(appKey);

            mHandler.post(() -> sendFdRequest(downloadRequest, appKey));
            return MbmsErrors.SUCCESS;
        }

        @Override
        public int registerStateCallback(DownloadRequest downloadRequest,
                DownloadStateCallback callback) throws RemoteException {
            mDownloadStateCallbacks.put(downloadRequest, callback);
            return MbmsErrors.SUCCESS;
        }

        @Override
        public int cancelDownload(DownloadRequest downloadRequest) {
            FrontendAppIdentifier appKey = new FrontendAppIdentifier(
                    Binder.getCallingUid(), downloadRequest.getSubscriptionId());
            checkInitialized(appKey);
            if (!mActiveDownloadRequests.getOrDefault(
                    appKey, Collections.emptySet()).contains(downloadRequest)) {
                return MbmsErrors.DownloadErrors.ERROR_UNKNOWN_DOWNLOAD_REQUEST;
            }
            mActiveDownloadRequests.get(appKey).remove(downloadRequest);
            return MbmsErrors.SUCCESS;
        }

        @Override
        public void onAppCallbackDied(int uid, int subscriptionId) {
            FrontendAppIdentifier appKey = new FrontendAppIdentifier(uid, subscriptionId);

            Log.i(LOG_TAG, "Disposing app " + appKey + " due to binder death");
            mAppCallbacks.remove(appKey);
            // TODO: call dispose
        }
    };

    private static EmbmsSampleDownloadService sInstance = null;

    private final Map<FrontendAppIdentifier, MbmsDownloadSessionCallback> mAppCallbacks =
            new HashMap<>();
    private final Map<FrontendAppIdentifier, ComponentName> mAppReceivers = new HashMap<>();
    private final Map<FrontendAppIdentifier, String> mAppTempFileRoots = new HashMap<>();
    private final Map<FrontendAppIdentifier, Set<DownloadRequest>> mActiveDownloadRequests =
            new ConcurrentHashMap<>();
    // A map of app-identifiers to (maps of service-ids to sets of temp file uris in use)
    private final Map<FrontendAppIdentifier, Map<String, Set<Uri>>> mTempFilesInUse =
            new ConcurrentHashMap<>();
    private final Map<DownloadRequest, DownloadStateCallback> mDownloadStateCallbacks =
            new ConcurrentHashMap<>();

    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private int mDownloadDelayFactor = 1;

    @Override
    public IBinder onBind(Intent intent) {
        mHandlerThread = new HandlerThread("EmbmsTestDownloadServiceWorker");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
        sInstance = this;
        return mBinder.asBinder();
    }

    public static EmbmsSampleDownloadService getInstance() {
        return sInstance;
    }

    public void requestCleanup() {
        // Assume that there's only one app, and do it for all the services.
        FrontendAppIdentifier registeredAppId = mAppReceivers.keySet().iterator().next();
        ComponentName appReceiver = mAppReceivers.values().iterator().next();
        for (FileServiceInfo fileServiceInfo :
                FileServiceRepository.getInstance(this).getAllFileServices()) {
            Intent cleanupIntent = new Intent(VendorUtils.ACTION_CLEANUP);
            cleanupIntent.setComponent(appReceiver);
            cleanupIntent.putExtra(VendorUtils.EXTRA_SERVICE_ID, fileServiceInfo.getServiceId());
            cleanupIntent.putExtra(VendorUtils.EXTRA_TEMP_FILE_ROOT,
                    mAppTempFileRoots.get(registeredAppId));
            Set<Uri> tempFilesInUse =
                    mTempFilesInUse.getOrDefault(registeredAppId, Collections.emptyMap())
                            .getOrDefault(fileServiceInfo.getServiceId(), Collections.emptySet());
            cleanupIntent.putExtra(VendorUtils.EXTRA_TEMP_FILES_IN_USE,
                    new ArrayList<>(tempFilesInUse));
            sendBroadcast(cleanupIntent);
        }
    }

    public void requestExtraTempFiles(FileServiceInfo serviceInfo) {
        // Assume one app, and do it for the specified service.
        FrontendAppIdentifier registeredAppId = mAppReceivers.keySet().iterator().next();
        ComponentName appReceiver = mAppReceivers.values().iterator().next();
        Intent fdRequestIntent = new Intent(VendorUtils.ACTION_FILE_DESCRIPTOR_REQUEST);
        fdRequestIntent.putExtra(VendorUtils.EXTRA_SERVICE_ID, serviceInfo.getServiceId());
        fdRequestIntent.putExtra(VendorUtils.EXTRA_FD_COUNT, 10);
        fdRequestIntent.putExtra(VendorUtils.EXTRA_TEMP_FILE_ROOT,
                mAppTempFileRoots.get(registeredAppId));
        fdRequestIntent.setComponent(appReceiver);

        sendOrderedBroadcast(fdRequestIntent,
                null, // receiverPermission
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        int result = getResultCode();
                        Bundle extras = getResultExtras(false);
                        Log.i(LOG_TAG, "Received extra temp files. Result " + result);
                        if (extras != null) {
                            Log.i(LOG_TAG, "Got "
                                    + extras.getParcelableArrayList(
                                    VendorUtils.EXTRA_FREE_URI_LIST).size()
                                    + " fds");
                        }
                    }
                },
                null, // scheduler
                Activity.RESULT_OK,
                null, // initialData
                null /* initialExtras */);
    }

    public void delayDownloads(int factor) {
        mDownloadDelayFactor = factor;
    }

    private void sendFdRequest(DownloadRequest request, FrontendAppIdentifier appKey) {
        int numFds = getNumFdsNeededForRequest(request);
        // Compose the FILE_DESCRIPTOR_REQUEST_INTENT
        Intent requestIntent = new Intent(VendorUtils.ACTION_FILE_DESCRIPTOR_REQUEST);
        requestIntent.putExtra(VendorUtils.EXTRA_SERVICE_ID, request.getFileServiceId());
        requestIntent.putExtra(VendorUtils.EXTRA_FD_COUNT, numFds);
        requestIntent.putExtra(VendorUtils.EXTRA_TEMP_FILE_ROOT,
                mAppTempFileRoots.get(appKey));
        requestIntent.setComponent(mAppReceivers.get(appKey));

        // Send as an ordered broadcast, using a BroadcastReceiver to capture the result
        // containing UriPathPairs.
        sendOrderedBroadcast(requestIntent,
                null, // receiverPermission
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        Bundle resultExtras = getResultExtras(false);
                        // This delay is to emulate the time it'd usually take to fetch the file
                        // off the network.
                        mHandler.postDelayed(
                                () -> performDownload(request, appKey, resultExtras),
                                DOWNLOAD_DELAY_MS);
                    }
                },
                null, // scheduler
                Activity.RESULT_OK,
                null, // initialData
                null /* initialExtras */);
    }

    private void performDownload(DownloadRequest request, FrontendAppIdentifier appKey,
            Bundle extras) {
        List<UriPathPair> tempFiles = extras.getParcelableArrayList(
                VendorUtils.EXTRA_FREE_URI_LIST);
        List<FileInfo> filesToDownload = FileServiceRepository.getInstance(this)
                .getFileServiceInfoForId(request.getFileServiceId())
                .getFiles();

        if (tempFiles.size() != filesToDownload.size()) {
            Log.w(LOG_TAG, "Different numbers of temp files and files to download...");
        }

        if (!mActiveDownloadRequests.containsKey(appKey)) {
            mActiveDownloadRequests.put(appKey, Collections.synchronizedSet(new HashSet<>()));
        }
        mActiveDownloadRequests.get(appKey).add(request);

        // Go through the files one-by-one and send them to the frontend app with a delay between
        // each one.
        for (int i = 0; i < tempFiles.size(); i++) {
            if (i >= filesToDownload.size()) {
                break;
            }
            UriPathPair tempFile = tempFiles.get(i);
            addTempFileInUse(appKey, request.getFileServiceId(),
                    tempFile.getFilePathUri());
            FileInfo fileToDownload = filesToDownload.get(i);
            mHandler.postDelayed(() -> {
                if (mActiveDownloadRequests.get(appKey) == null ||
                        !mActiveDownloadRequests.get(appKey).contains(request)) {
                    return;
                }
                downloadSingleFile(appKey, request, tempFile, fileToDownload);
                removeTempFileInUse(appKey, request.getFileServiceId(),
                        tempFile.getFilePathUri());
            }, FILE_SEPARATION_DELAY * i * mDownloadDelayFactor);
        }
    }

    private void downloadSingleFile(FrontendAppIdentifier appKey, DownloadRequest request,
            UriPathPair tempFile, FileInfo fileToDownload) {
        int result = MbmsDownloadSession.RESULT_SUCCESSFUL;
        // Test Callback
        DownloadStateCallback c = mDownloadStateCallbacks.get(request);
        if (c != null) {
            c.onProgressUpdated(request, fileToDownload, 0, 10, 0, 10);
        }
        // Test Callback
        if (c != null) {
            c.onStateUpdated(request, fileToDownload,
                    MbmsDownloadSession.STATUS_ACTIVELY_DOWNLOADING);
        }
        try {
            // Get the ParcelFileDescriptor for the single temp file we requested
            ParcelFileDescriptor tempFileFd = getContentResolver().openFileDescriptor(
                    tempFile.getContentUri(), "rw");
            OutputStream destinationStream =
                    new ParcelFileDescriptor.AutoCloseOutputStream(tempFileFd);

            // This is how you get the native fd
            Log.i(LOG_TAG, "Native fd: " + tempFileFd.getFd());

            int resourceId = FileServiceRepository.getInstance(this)
                    .getResourceForFileUri(fileToDownload.getUri());
            // Open the picture we have in our res/raw directory
            InputStream image = getResources().openRawResource(resourceId);

            // Copy it into the temp file in the app's file space (crudely)
            byte[] imageBuffer = new byte[image.available()];
            image.read(imageBuffer);
            destinationStream.write(imageBuffer);
            destinationStream.flush();
        } catch (IOException e) {
            result = MbmsDownloadSession.RESULT_CANCELLED;
        }
        // Test Callback
        if (c != null) {
            c.onProgressUpdated(request, fileToDownload, 10, 10, 10, 10);
        }
        // Take a round-trip through the download request serialization to exercise it
        DownloadRequest request1 = new DownloadRequest.Builder(request.getSourceUri())
                .setSubscriptionId(request.getSubscriptionId())
                .setServiceId(request.getFileServiceId())
                .setOpaqueData(request.getOpaqueData())
                .build();

        Intent downloadResultIntent =
                new Intent(VendorUtils.ACTION_DOWNLOAD_RESULT_INTERNAL);
        downloadResultIntent.putExtra(MbmsDownloadSession.EXTRA_MBMS_DOWNLOAD_REQUEST, request1);
        downloadResultIntent.putExtra(VendorUtils.EXTRA_FINAL_URI,
                tempFile.getFilePathUri());
        downloadResultIntent.putExtra(MbmsDownloadSession.EXTRA_MBMS_FILE_INFO, fileToDownload);
        downloadResultIntent.putExtra(VendorUtils.EXTRA_TEMP_FILE_ROOT,
                mAppTempFileRoots.get(appKey));
        ArrayList<Uri> tempFileList = new ArrayList<>(1);
        tempFileList.add(tempFile.getFilePathUri());
        downloadResultIntent.getExtras().putParcelableArrayList(
                VendorUtils.EXTRA_TEMP_LIST, tempFileList);
        downloadResultIntent.putExtra(MbmsDownloadSession.EXTRA_MBMS_DOWNLOAD_RESULT, result);
        downloadResultIntent.setComponent(mAppReceivers.get(appKey));

        sendOrderedBroadcast(downloadResultIntent,
                null, // receiverPermission
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        int resultCode = getResultCode();
                        Log.i(LOG_TAG, "Download result ack: " + resultCode);
                    }
                },
                null, // scheduler
                Activity.RESULT_OK,
                null, // initialData
                null /* initialExtras */);
    }

    private void checkInitialized(FrontendAppIdentifier appKey) {
        if (!mAppCallbacks.containsKey(appKey)) {
            throw new IllegalStateException("Not yet initialized");
        }
    }

    private int getNumFdsNeededForRequest(DownloadRequest request) {
        return FileServiceRepository.getInstance(this)
                .getFileServiceInfoForId(request.getFileServiceId()).getFiles().size();
    }

    private void addTempFileInUse(FrontendAppIdentifier appKey, String serviceId, Uri tempFileUri) {
        Map<String, Set<Uri>> tempFileByService = mTempFilesInUse.get(appKey);
        if (tempFileByService == null) {
            tempFileByService = new ConcurrentHashMap<>();
            mTempFilesInUse.put(appKey, tempFileByService);
        }
        Set<Uri> tempFilesInUse = tempFileByService.get(serviceId);
        if (tempFilesInUse == null) {
            tempFilesInUse = ConcurrentHashMap.newKeySet();
            tempFileByService.put(serviceId, tempFilesInUse);
        }
        tempFilesInUse.add(tempFileUri);
    }

    private void removeTempFileInUse(FrontendAppIdentifier appKey, String serviceId,
            Uri tempFileUri) {
        Set<Uri> tempFilesInUse = mTempFilesInUse.getOrDefault(appKey, Collections.emptyMap())
                .getOrDefault(serviceId, Collections.emptySet());
        if (tempFilesInUse.contains(tempFileUri)) {
            tempFilesInUse.remove(tempFileUri);
        } else {
            Log.w(LOG_TAG, "Trying to remove unknown temp file in use " + tempFileUri + " for app" +
                    appKey + " and service id " + serviceId);
        }
    }
}
