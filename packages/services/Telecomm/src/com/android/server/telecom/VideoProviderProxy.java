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
 * limitations under the License
 */

package com.android.server.telecom;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.Context;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.UserHandle;
import android.telecom.Connection;
import android.telecom.InCallService;
import android.telecom.Log;
import android.telecom.VideoProfile;
import android.text.TextUtils;
import android.view.Surface;

import com.android.internal.telecom.IVideoCallback;
import com.android.internal.telecom.IVideoProvider;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static android.Manifest.permission.CALL_PHONE;

/**
 * Proxies video provider messages from {@link InCallService.VideoCall}
 * implementations to the underlying {@link Connection.VideoProvider} implementation.  Also proxies
 * callbacks from the {@link Connection.VideoProvider} to {@link InCallService.VideoCall}
 * implementations.
 *
 * Also provides a means for Telecom to send and receive these messages.
 */
public class VideoProviderProxy extends Connection.VideoProvider {

    /**
     * Listener for Telecom components interested in callbacks from the video provider.
     */
    interface Listener {
        void onSessionModifyRequestReceived(Call call, VideoProfile videoProfile);
    }

    /**
     * Set of listeners on this VideoProviderProxy.
     *
     * ConcurrentHashMap constructor params: 8 is initial table size, 0.9f is
     * load factor before resizing, 1 means we only expect a single thread to
     * access the map so make only a single shard
     */
    private final Set<Listener> mListeners = Collections.newSetFromMap(
            new ConcurrentHashMap<Listener, Boolean>(8, 0.9f, 1));

    /** The TelecomSystem SyncRoot used for synchronized operations. */
    private final TelecomSystem.SyncRoot mLock;

    /**
     * The {@link android.telecom.Connection.VideoProvider} implementation residing with the
     * {@link android.telecom.ConnectionService} which is being wrapped by this
     * {@link VideoProviderProxy}.
     */
    private final IVideoProvider mConectionServiceVideoProvider;

    /**
     * Binder used to bind to the {@link android.telecom.ConnectionService}'s
     * {@link com.android.internal.telecom.IVideoCallback}.
     */
    private final VideoCallListenerBinder mVideoCallListenerBinder;

    /**
     * The Telecom {@link Call} this {@link VideoProviderProxy} is associated with.
     */
    private Call mCall;

    /**
     * Interface providing access to the currently logged in user.
     */
    private CurrentUserProxy mCurrentUserProxy;

    private IBinder.DeathRecipient mDeathRecipient = new IBinder.DeathRecipient() {
        @Override
        public void binderDied() {
            mConectionServiceVideoProvider.asBinder().unlinkToDeath(this, 0);
        }
    };

    /**
     * Creates a new instance of the {@link VideoProviderProxy}, binding it to the passed in
     * {@code videoProvider} residing with the {@link android.telecom.ConnectionService}.
     *
     *
     * @param lock
     * @param videoProvider The {@link android.telecom.ConnectionService}'s video provider.
     * @param call The current call.
     * @throws RemoteException Remote exception.
     */
    VideoProviderProxy(TelecomSystem.SyncRoot lock,
            IVideoProvider videoProvider, Call call, CurrentUserProxy currentUserProxy)
            throws RemoteException {

        super(Looper.getMainLooper());

        mLock = lock;

        mConectionServiceVideoProvider = videoProvider;
        mConectionServiceVideoProvider.asBinder().linkToDeath(mDeathRecipient, 0);

        mVideoCallListenerBinder = new VideoCallListenerBinder();
        mConectionServiceVideoProvider.addVideoCallback(mVideoCallListenerBinder);
        mCall = call;
        mCurrentUserProxy = currentUserProxy;
    }

    /**
     * IVideoCallback stub implementation.  An instance of this class receives callbacks from the
     * {@code ConnectionService}'s video provider.
     */
    private final class VideoCallListenerBinder extends IVideoCallback.Stub {
        /**
         * Proxies a request from the {@link #mConectionServiceVideoProvider} to the
         * {@link InCallService} when a session modification request is received.
         *
         * @param videoProfile The requested video profile.
         */
        @Override
        public void receiveSessionModifyRequest(VideoProfile videoProfile) {
            try {
                Log.startSession("VPP.rSMR");
                synchronized (mLock) {
                    logFromVideoProvider("receiveSessionModifyRequest: " + videoProfile);
                    Log.addEvent(mCall, LogUtils.Events.RECEIVE_VIDEO_REQUEST,
                            VideoProfile.videoStateToString(videoProfile.getVideoState()));

                    mCall.getAnalytics().addVideoEvent(
                            Analytics.RECEIVE_REMOTE_SESSION_MODIFY_REQUEST,
                            videoProfile.getVideoState());

                    if (!mCall.isVideoCallingSupported() &&
                            VideoProfile.isVideo(videoProfile.getVideoState())) {
                        // If video calling is not supported by the phone account, and we receive
                        // a request to upgrade to video, automatically reject it without informing
                        // the InCallService.

                        Log.addEvent(mCall, LogUtils.Events.SEND_VIDEO_RESPONSE, "video not supported");
                        VideoProfile responseProfile = new VideoProfile(
                                VideoProfile.STATE_AUDIO_ONLY);
                        try {
                            mConectionServiceVideoProvider.sendSessionModifyResponse(
                                    responseProfile);
                        } catch (RemoteException e) {
                        }

                        // Don't want to inform listeners of the request as we've just rejected it.
                        return;
                    }

                    // Inform other Telecom components of the session modification request.
                    for (Listener listener : mListeners) {
                        listener.onSessionModifyRequestReceived(mCall, videoProfile);
                    }

                    VideoProviderProxy.this.receiveSessionModifyRequest(videoProfile);
                }
            } finally {
                Log.endSession();
            }
        }

        /**
         * Proxies a request from the {@link #mConectionServiceVideoProvider} to the
         * {@link InCallService} when a session modification response is received.
         *
         * @param status The status of the response.
         * @param requestProfile The requested video profile.
         * @param responseProfile The response video profile.
         */
        @Override
        public void receiveSessionModifyResponse(int status, VideoProfile requestProfile,
                VideoProfile responseProfile) {
            logFromVideoProvider("receiveSessionModifyResponse: status=" + status +
                    " requestProfile=" + requestProfile + " responseProfile=" + responseProfile);
            String eventMessage = "Status Code : " + status + " Video State: " +
                    (responseProfile != null ? responseProfile.getVideoState() : "null");
            Log.addEvent(mCall, LogUtils.Events.RECEIVE_VIDEO_RESPONSE, eventMessage);
            synchronized (mLock) {
                if (status == Connection.VideoProvider.SESSION_MODIFY_REQUEST_SUCCESS) {
                    mCall.getAnalytics().addVideoEvent(
                            Analytics.RECEIVE_REMOTE_SESSION_MODIFY_RESPONSE,
                            responseProfile == null ?
                                    VideoProfile.STATE_AUDIO_ONLY :
                                    responseProfile.getVideoState());
                }
                VideoProviderProxy.this.receiveSessionModifyResponse(status, requestProfile,
                        responseProfile);
            }
        }

        /**
         * Proxies a request from the {@link #mConectionServiceVideoProvider} to the
         * {@link InCallService} when a call session event occurs.
         *
         * @param event The call session event.
         */
        @Override
        public void handleCallSessionEvent(int event) {
            synchronized (mLock) {
                logFromVideoProvider("handleCallSessionEvent: " +
                        Connection.VideoProvider.sessionEventToString(event));
                VideoProviderProxy.this.handleCallSessionEvent(event);
            }
        }

        /**
         * Proxies a request from the {@link #mConectionServiceVideoProvider} to the
         * {@link InCallService} when the peer dimensions change.
         *
         * @param width The width of the peer's video.
         * @param height The height of the peer's video.
         */
        @Override
        public void changePeerDimensions(int width, int height) {
            synchronized (mLock) {
                logFromVideoProvider("changePeerDimensions: width=" + width + " height=" +
                        height);
                VideoProviderProxy.this.changePeerDimensions(width, height);
            }
        }

        /**
         * Proxies a request from the {@link #mConectionServiceVideoProvider} to the
         * {@link InCallService} when the video quality changes.
         *
         * @param videoQuality The video quality.
         */
        @Override
        public void changeVideoQuality(int videoQuality) {
            synchronized (mLock) {
                logFromVideoProvider("changeVideoQuality: " + videoQuality);
                VideoProviderProxy.this.changeVideoQuality(videoQuality);
            }
        }

        /**
         * Proxies a request from the {@link #mConectionServiceVideoProvider} to the
         * {@link InCallService} when the call data usage changes.
         *
         * Also tracks the current call data usage on the {@link Call} for use when writing to the
         * call log.
         *
         * @param dataUsage The data usage.
         */
        @Override
        public void changeCallDataUsage(long dataUsage) {
            synchronized (mLock) {
                logFromVideoProvider("changeCallDataUsage: " + dataUsage);
                VideoProviderProxy.this.setCallDataUsage(dataUsage);
                mCall.setCallDataUsage(dataUsage);
            }
        }

        /**
         * Proxies a request from the {@link #mConectionServiceVideoProvider} to the
         * {@link InCallService} when the camera capabilities change.
         *
         * @param cameraCapabilities The camera capabilities.
         */
        @Override
        public void changeCameraCapabilities(VideoProfile.CameraCapabilities cameraCapabilities) {
            synchronized (mLock) {
                logFromVideoProvider("changeCameraCapabilities: " + cameraCapabilities);
                VideoProviderProxy.this.changeCameraCapabilities(cameraCapabilities);
            }
        }
    }

    @Override
    public void onSetCamera(String cameraId) {
        // No-op.  We implement the other prototype of onSetCamera so that we can use the calling
        // package, uid and pid to verify permission.
    }

    /**
     * Proxies a request from the {@link InCallService} to the
     * {@link #mConectionServiceVideoProvider} to change the camera.
     *
     * @param cameraId The id of the camera.
     * @param callingPackage The package calling in.
     * @param callingUid The UID of the caller.
     * @param callingPid The PID of the caller.
     * @param targetSdkVersion The target SDK version of the calling InCallService where the camera
     *      request originated.
     */
    @Override
    public void onSetCamera(String cameraId, String callingPackage, int callingUid,
            int callingPid, int targetSdkVersion) {
        synchronized (mLock) {
            logFromInCall("setCamera: " + cameraId + " callingPackage=" + callingPackage +
                    "; callingUid=" + callingUid);

            if (!TextUtils.isEmpty(cameraId)) {
                if (!canUseCamera(mCall.getContext(), callingPackage, callingUid, callingPid)) {
                    // Calling app is not permitted to use the camera.  Ignore the request and send
                    // back a call session event indicating the error.
                    Log.i(this, "onSetCamera: camera permission denied; package=%s, uid=%d, "
                            + "pid=%d, targetSdkVersion=%d",
                            callingPackage, callingUid, callingPid, targetSdkVersion);

                    // API 26 introduces a new camera permission error we can use here since the
                    // caller supports that API version.
                    if (targetSdkVersion > Build.VERSION_CODES.N_MR1) {
                        VideoProviderProxy.this.handleCallSessionEvent(
                                Connection.VideoProvider.SESSION_EVENT_CAMERA_PERMISSION_ERROR);
                    } else {
                        VideoProviderProxy.this.handleCallSessionEvent(
                                Connection.VideoProvider.SESSION_EVENT_CAMERA_FAILURE);
                    }
                    return;
                }
            }
            try {
                mConectionServiceVideoProvider.setCamera(cameraId, callingPackage,
                        targetSdkVersion);
            } catch (RemoteException e) {
                VideoProviderProxy.this.handleCallSessionEvent(
                        Connection.VideoProvider.SESSION_EVENT_CAMERA_FAILURE);
            }
        }
    }

    /**
     * Proxies a request from the {@link InCallService} to the
     * {@link #mConectionServiceVideoProvider} to set the preview surface.
     *
     * @param surface The surface.
     */
    @Override
    public void onSetPreviewSurface(Surface surface) {
        synchronized (mLock) {
            logFromInCall("setPreviewSurface");
            try {
                mConectionServiceVideoProvider.setPreviewSurface(surface);
            } catch (RemoteException e) {
            }
        }
    }

    /**
     * Proxies a request from the {@link InCallService} to the
     * {@link #mConectionServiceVideoProvider} to change the display surface.
     *
     * @param surface The surface.
     */
    @Override
    public void onSetDisplaySurface(Surface surface) {
        synchronized (mLock) {
            logFromInCall("setDisplaySurface");
            try {
                mConectionServiceVideoProvider.setDisplaySurface(surface);
            } catch (RemoteException e) {
            }
        }
    }

    /**
     * Proxies a request from the {@link InCallService} to the
     * {@link #mConectionServiceVideoProvider} to change the device orientation.
     *
     * @param rotation The device orientation, in degrees.
     */
    @Override
    public void onSetDeviceOrientation(int rotation) {
        synchronized (mLock) {
            logFromInCall("setDeviceOrientation: " + rotation);
            try {
                mConectionServiceVideoProvider.setDeviceOrientation(rotation);
            } catch (RemoteException e) {
            }
        }
    }

    /**
     * Proxies a request from the {@link InCallService} to the
     * {@link #mConectionServiceVideoProvider} to change the camera zoom ratio.
     *
     * @param value The camera zoom ratio.
     */
    @Override
    public void onSetZoom(float value) {
        synchronized (mLock) {
            logFromInCall("setZoom: " + value);
            try {
                mConectionServiceVideoProvider.setZoom(value);
            } catch (RemoteException e) {
            }
        }
    }

    /**
     * Proxies a request from the {@link InCallService} to the
     * {@link #mConectionServiceVideoProvider} to provide a response to a session modification
     * request.
     *
     * @param fromProfile The video properties prior to the request.
     * @param toProfile The video properties with the requested changes made.
     */
    @Override
    public void onSendSessionModifyRequest(VideoProfile fromProfile, VideoProfile toProfile) {
        synchronized (mLock) {
            logFromInCall("sendSessionModifyRequest: from=" + fromProfile + " to=" + toProfile);
            Log.addEvent(mCall, LogUtils.Events.SEND_VIDEO_REQUEST,
                    VideoProfile.videoStateToString(toProfile.getVideoState()));
            mCall.getAnalytics().addVideoEvent(
                    Analytics.SEND_LOCAL_SESSION_MODIFY_REQUEST,
                    toProfile.getVideoState());
            try {
                mConectionServiceVideoProvider.sendSessionModifyRequest(fromProfile, toProfile);
            } catch (RemoteException e) {
            }
        }
    }

    /**
     * Proxies a request from the {@link InCallService} to the
     * {@link #mConectionServiceVideoProvider} to send a session modification request.
     *
     * @param responseProfile The response connection video properties.
     */
    @Override
    public void onSendSessionModifyResponse(VideoProfile responseProfile) {
        synchronized (mLock) {
            logFromInCall("sendSessionModifyResponse: " + responseProfile);
            Log.addEvent(mCall, LogUtils.Events.SEND_VIDEO_RESPONSE,
                    VideoProfile.videoStateToString(responseProfile.getVideoState()));
            mCall.getAnalytics().addVideoEvent(
                    Analytics.SEND_LOCAL_SESSION_MODIFY_RESPONSE,
                    responseProfile.getVideoState());
            try {
                mConectionServiceVideoProvider.sendSessionModifyResponse(responseProfile);
            } catch (RemoteException e) {
            }
        }
    }

    /**
     * Proxies a request from the {@link InCallService} to the
     * {@link #mConectionServiceVideoProvider} to request the camera capabilities.
     */
    @Override
    public void onRequestCameraCapabilities() {
        synchronized (mLock) {
            logFromInCall("requestCameraCapabilities");
            try {
                mConectionServiceVideoProvider.requestCameraCapabilities();
            } catch (RemoteException e) {
            }
        }
    }

    /**
     * Proxies a request from the {@link InCallService} to the
     * {@link #mConectionServiceVideoProvider} to request the connection data usage.
     */
    @Override
    public void onRequestConnectionDataUsage() {
        synchronized (mLock) {
            logFromInCall("requestCallDataUsage");
            try {
                mConectionServiceVideoProvider.requestCallDataUsage();
            } catch (RemoteException e) {
            }
        }
    }

    /**
     * Proxies a request from the {@link InCallService} to the
     * {@link #mConectionServiceVideoProvider} to set the pause image.
     *
     * @param uri URI of image to display.
     */
    @Override
    public void onSetPauseImage(Uri uri) {
        synchronized (mLock) {
            logFromInCall("setPauseImage: " + uri);
            try {
                mConectionServiceVideoProvider.setPauseImage(uri);
            } catch (RemoteException e) {
            }
        }
    }

    /**
     * Add a listener to this {@link VideoProviderProxy}.
     *
     * @param listener The listener.
     */
    public void addListener(Listener listener) {
        mListeners.add(listener);
    }

    /**
     * Remove a listener from this {@link VideoProviderProxy}.
     *
     * @param listener The listener.
     */
    public void removeListener(Listener listener) {
        if (listener != null) {
            mListeners.remove(listener);
        }
    }

    /**
     * Logs a message originating from the {@link InCallService}.
     *
     * @param toLog The message to log.
     */
    private void logFromInCall(String toLog) {
        Log.i(this, "IC->VP (callId=" + (mCall == null ? "?" : mCall.getId()) + "): " + toLog);
    }

    /**
     * Logs a message originating from the {@link android.telecom.ConnectionService}'s
     * {@link Connection.VideoProvider}.
     *
     * @param toLog The message to log.
     */
    private void logFromVideoProvider(String toLog) {
        Log.i(this, "VP->IC (callId=" + (mCall == null ? "?" : mCall.getId()) + "): " + toLog);
    }

    /**
     * Determines if the caller has permission to use the camera.
     *
     * @param context The context.
     * @param callingPackage The package name of the caller (i.e. Dialer).
     * @param callingUid The UID of the caller.
     * @param callingPid The PID of the caller.
     * @return {@code true} if the calling uid and package can use the camera, {@code false}
     *      otherwise.
     */
    private boolean canUseCamera(Context context, String callingPackage, int callingUid,
            int callingPid) {

        UserHandle callingUser = UserHandle.getUserHandleForUid(callingUid);
        UserHandle currentUserHandle = mCurrentUserProxy.getCurrentUserHandle();
        if (currentUserHandle != null && !currentUserHandle.equals(callingUser)) {
            Log.w(this, "canUseCamera attempt to user camera by background user.");
            return false;
        }

        try {
            context.enforcePermission(Manifest.permission.CAMERA, callingPid, callingUid,
                    "Camera permission required.");
        } catch (SecurityException se) {
            return false;
        }

        AppOpsManager appOpsManager = (AppOpsManager) context.getSystemService(
                Context.APP_OPS_SERVICE);

        try {
            // Some apps that have the permission can be restricted via app ops.
            return appOpsManager != null && appOpsManager.noteOp(AppOpsManager.OP_CAMERA,
                    callingUid, callingPackage) == AppOpsManager.MODE_ALLOWED;
        } catch (SecurityException se) {
            Log.w(this, "canUseCamera got appOpps Exception " + se.toString());
            return false;
        }
    }

}
