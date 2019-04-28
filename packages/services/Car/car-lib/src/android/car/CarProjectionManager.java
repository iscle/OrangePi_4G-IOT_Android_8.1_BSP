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

package android.car;

import android.annotation.SystemApi;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;

import java.lang.ref.WeakReference;

/**
 * CarProjectionManager allows applications implementing projection to register/unregister itself
 * with projection manager, listen for voice notification.
 * @hide
 */
@SystemApi
public final class CarProjectionManager implements CarManagerBase {
    /**
     * Listener to get projected notifications.
     *
     * Currently only voice search request is supported.
     */
    public interface CarProjectionListener {
        /**
         * Voice search was requested by the user.
         */
        void onVoiceAssistantRequest(boolean fromLongPress);
    }

    /**
     * Flag for voice search request.
     */
    public static final int PROJECTION_VOICE_SEARCH = 0x1;
    /**
     * Flag for long press voice search request.
     */
    public static final int PROJECTION_LONG_PRESS_VOICE_SEARCH = 0x2;

    private final ICarProjection mService;
    private final Handler mHandler;
    private final ICarProjectionCallbackImpl mBinderListener;

    private CarProjectionListener mListener;
    private int mVoiceSearchFilter;

    /**
     * @hide
     */
    CarProjectionManager(IBinder service, Handler handler) {
        mService = ICarProjection.Stub.asInterface(service);
        mHandler = handler;
        mBinderListener = new ICarProjectionCallbackImpl(this);
    }

    /**
     * Compatibility with previous APIs due to typo
     * @throws CarNotConnectedException if the connection to the car service has been lost.
     * @hide
     */
    public void regsiterProjectionListener(CarProjectionListener listener, int voiceSearchFilter)
            throws CarNotConnectedException {
        registerProjectionListener(listener, voiceSearchFilter);
    }

    /**
     * Register listener to monitor projection. Only one listener can be registered and
     * registering multiple times will lead into only the last listener to be active.
     * @param listener
     * @param voiceSearchFilter Flags of voice search requests to get notification.
     * @throws CarNotConnectedException if the connection to the car service has been lost.
     */
    public void registerProjectionListener(CarProjectionListener listener, int voiceSearchFilter)
            throws CarNotConnectedException {
        if (listener == null) {
            throw new IllegalArgumentException("null listener");
        }
        synchronized (this) {
            if (mListener == null || mVoiceSearchFilter != voiceSearchFilter) {
                try {
                    mService.registerProjectionListener(mBinderListener, voiceSearchFilter);
                } catch (RemoteException e) {
                    throw new CarNotConnectedException(e);
                }
            }
            mListener = listener;
            mVoiceSearchFilter = voiceSearchFilter;
        }
    }

    /**
     * Compatibility with previous APIs due to typo
     * @throws CarNotConnectedException if the connection to the car service has been lost.
     * @hide
     */
    public void unregsiterProjectionListener() {
       unregisterProjectionListener();
    }

    /**
     * Unregister listener and stop listening projection events.
     * @throws CarNotConnectedException if the connection to the car service has been lost.
     */
    public void unregisterProjectionListener() {
        synchronized (this) {
            try {
                mService.unregisterProjectionListener(mBinderListener);
            } catch (RemoteException e) {
                //ignore
            }
            mListener = null;
            mVoiceSearchFilter = 0;
        }
    }

    /**
     * Registers projection runner on projection start with projection service
     * to create reverse binding.
     * @param serviceIntent
     * @throws CarNotConnectedException if the connection to the car service has been lost.
     */
    public void registerProjectionRunner(Intent serviceIntent) throws CarNotConnectedException {
        if (serviceIntent == null) {
            throw new IllegalArgumentException("null serviceIntent");
        }
        synchronized (this) {
            try {
                mService.registerProjectionRunner(serviceIntent);
            } catch (RemoteException e) {
                throw new CarNotConnectedException(e);
            }
        }
    }

    /**
     * Unregisters projection runner on projection stop with projection service to create
     * reverse binding.
     * @param serviceIntent
     * @throws CarNotConnectedException if the connection to the car service has been lost.
     */
    public void unregisterProjectionRunner(Intent serviceIntent) {
        if (serviceIntent == null) {
            throw new IllegalArgumentException("null serviceIntent");
        }
        synchronized (this) {
            try {
                mService.unregisterProjectionRunner(serviceIntent);
            } catch (RemoteException e) {
                //ignore
            }
        }
    }

    @Override
    public void onCarDisconnected() {
        // nothing to do
    }

    private void handleVoiceAssistantRequest(boolean fromLongPress) {
        CarProjectionListener listener;
        synchronized (this) {
            if (mListener == null) {
                return;
            }
            listener = mListener;
        }
        listener.onVoiceAssistantRequest(fromLongPress);
    }

    private static class ICarProjectionCallbackImpl extends ICarProjectionCallback.Stub {

        private final WeakReference<CarProjectionManager> mManager;

        private ICarProjectionCallbackImpl(CarProjectionManager manager) {
            mManager = new WeakReference<>(manager);
        }

        @Override
        public void onVoiceAssistantRequest(final boolean fromLongPress) {
            final CarProjectionManager manager = mManager.get();
            if (manager == null) {
                return;
            }
            manager.mHandler.post(new Runnable() {
                @Override
                public void run() {
                    manager.handleVoiceAssistantRequest(fromLongPress);
                }
            });
        }
    }
}
