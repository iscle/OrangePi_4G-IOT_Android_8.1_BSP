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
package com.android.car.vehiclemonitor;

import android.annotation.IntDef;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.ServiceSpecificException;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;

/**
 * System API to access Vehicle monitor. This is only for system services and applications should
 * not use this. All APIs will fail with security error if normal app tries this.
 */
public class VehicleMonitor {
    private static final String TAG = VehicleMonitor.class.getSimpleName();

    private final IVehicleMonitor mService;
    private final VehicleMonitorListener mListener;
    private final IVehicleMonitorListenerImpl mVehicleMonitorListener;
    private final EventHandler mEventHandler;

    private static final int VMS_CONNECT_MAX_RETRY = 10;
    private static final long VMS_RETRY_WAIT_TIME_MS = 1000;

    /**
     * Application priorities used in vehicle monitoring.
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            ApplicationPriority.NONE,
            ApplicationPriority.FOREGROUND
    })
    public @interface ApplicationPriority {
        int NONE = 0;
        int FOREGROUND = 1;
    }

    /**
     * Listener for VMS events.
     */
    public interface VehicleMonitorListener {
        void onAppViolation(int pid, int uid, int action, int violation);
    }

    /**
     * Factory method to create VehicleMonitor
     */
    public static VehicleMonitor createVehicleMonitor(
            VehicleMonitorListener listener, Looper looper) {
        int retryCount = 0;
        IVehicleMonitor service = null;
        while (true) {
            service = IVehicleMonitor.Stub.asInterface(
                    ServiceManager.getService(IVehicleMonitor.class.getCanonicalName()));
            if (service != null) {
                break;
            }
            retryCount++;
            if (retryCount > VMS_CONNECT_MAX_RETRY) {
                break;
            }
            try {
                Thread.sleep(VMS_RETRY_WAIT_TIME_MS);
            } catch (InterruptedException e) {
                //ignore
            }
        }
        if (service == null) {
            throw new RuntimeException("Vehicle monitor service not available:"
                    + IVehicleMonitor.class.getCanonicalName());
        }
        return new VehicleMonitor(service, listener, looper);
    }

    private VehicleMonitor(
            IVehicleMonitor service, VehicleMonitorListener listener, Looper looper) {
        mService = service;
        mListener = listener;
        mEventHandler = new EventHandler(looper);
        mVehicleMonitorListener = new IVehicleMonitorListenerImpl(this);
        try {
            mService.setMonitorListener(mVehicleMonitorListener);
        } catch (RemoteException e) {
            throw new RuntimeException("Vehicle monitor service not working ", e);
        }
    }

    /**
     * Set application priority.
     * <p>
     * This will lead into writing application priority into vehicle monitor.
     */
    public void setAppPriority(int pid, int uid, @ApplicationPriority int priority)
            throws ServiceSpecificException {
        try {
            mService.setAppPriority(pid, uid, priority);
        } catch (RemoteException e) {
            throw new RuntimeException("Vehicle monitor service not working ", e);
        }
    }

    private void handleVehicleMonitorAppViolation(AppViolation appViolation) {
        mListener.onAppViolation(appViolation.mPid, appViolation.mUid, appViolation.mAction,
                appViolation.mViolation);
    }

    private class EventHandler extends Handler {

        private static final int MSG_APP_VIOLATION = 0;

        private EventHandler(Looper looper) {
            super(looper);
        }

        private void notifyAppViolation(int pid, int uid, int action, int violation) {
            AppViolation appViolation = new AppViolation(pid, uid, action, violation);
            Message msg = obtainMessage(MSG_APP_VIOLATION, appViolation);
            sendMessage(msg);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_APP_VIOLATION:
                    AppViolation appViolation = (AppViolation) msg.obj;
                    handleVehicleMonitorAppViolation(appViolation);
                    break;
            }
        }
    }

    private static class IVehicleMonitorListenerImpl extends IVehicleMonitorListener.Stub {

        private final WeakReference<VehicleMonitor> mVehicleMonitor;

        private IVehicleMonitorListenerImpl(VehicleMonitor vehicleNewotk) {
            mVehicleMonitor = new WeakReference<>(vehicleNewotk);
        }

        @Override
        public void onAppViolation(int pid, int uid, int action, int violation) {
            VehicleMonitor vehicleMonitor = mVehicleMonitor.get();
            if (vehicleMonitor != null) {
                vehicleMonitor.mEventHandler.notifyAppViolation(pid, uid, action, violation);
            }
        }
    }

    private static class AppViolation {
        public final int mPid;
        public final int mUid;
        public final int mAction;
        public final int mViolation;

        AppViolation(int pid, int uid, int action, int violation) {
            mPid = pid;
            mUid = uid;
            mAction = action;
            mViolation = violation;
        }
    }
}
