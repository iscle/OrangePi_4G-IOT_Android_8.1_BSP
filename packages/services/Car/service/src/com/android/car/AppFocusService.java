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
package com.android.car;

import android.car.CarAppFocusManager;
import android.car.IAppFocus;
import android.car.IAppFocusListener;
import android.car.IAppFocusOwnershipCallback;
import android.content.Context;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * App focus service ensures only one instance of application type is active at a time.
 */
public class AppFocusService extends IAppFocus.Stub implements CarServiceBase,
        BinderInterfaceContainer.BinderEventHandler<IAppFocusOwnershipCallback> {
    private static final boolean DBG = false;
    private static final boolean DBG_EVENT = false;

    private final SystemActivityMonitoringService mSystemActivityMonitoringService;
    private final ClientHolder mAllChangeClients;
    private final OwnershipClientHolder mAllOwnershipClients;
    /** K: appType, V: client owning it */
    private final HashMap<Integer, OwnershipClientInfo> mFocusOwners = new HashMap<>();
    private final Set<Integer> mActiveAppTypes = new HashSet<>();
    private final CopyOnWriteArrayList<FocusOwnershipCallback> mFocusOwnershipCallbacks =
            new CopyOnWriteArrayList<>();
    private final BinderInterfaceContainer.BinderEventHandler<IAppFocusListener>
            mAllBinderEventHandler = bInterface -> { /* nothing to do.*/ };

    private DispatchHandler mDispatchHandler;
    private HandlerThread mHandlerThread;

    public AppFocusService(Context context,
            SystemActivityMonitoringService systemActivityMonitoringService) {
        mSystemActivityMonitoringService = systemActivityMonitoringService;
        mAllChangeClients = new ClientHolder(mAllBinderEventHandler);
        mAllOwnershipClients = new OwnershipClientHolder(this);
    }

    @Override
    public void registerFocusListener(IAppFocusListener listener, int appType) {
        synchronized (this) {
            ClientInfo info = (ClientInfo) mAllChangeClients.getBinderInterface(listener);
            if (info == null) {
                info = new ClientInfo(mAllChangeClients, listener, Binder.getCallingUid(),
                        Binder.getCallingPid(), appType);
                mAllChangeClients.addBinderInterface(info);
            } else {
                info.addAppType(appType);
            }
        }
    }

    @Override
    public void unregisterFocusListener(IAppFocusListener listener, int appType) {
        synchronized (this) {
            ClientInfo info = (ClientInfo) mAllChangeClients.getBinderInterface(listener);
            if (info == null) {
                return;
            }
            info.removeAppType(appType);
            if (info.getAppTypes().isEmpty()) {
                mAllChangeClients.removeBinder(listener);
            }
        }
    }

    @Override
    public int[] getActiveAppTypes() {
        synchronized (this) {
            return toIntArray(mActiveAppTypes);
        }
    }

    @Override
    public boolean isOwningFocus(IAppFocusOwnershipCallback callback, int appType) {
        synchronized (this) {
            OwnershipClientInfo info =
                    (OwnershipClientInfo) mAllOwnershipClients.getBinderInterface(callback);
            if (info == null) {
                return false;
            }
            return info.getOwnedAppTypes().contains(appType);
        }
    }

    @Override
    public int requestAppFocus(IAppFocusOwnershipCallback callback, int appType) {
        synchronized (this) {
            OwnershipClientInfo info =
                    (OwnershipClientInfo) mAllOwnershipClients.getBinderInterface(callback);
            if (info == null) {
                info = new OwnershipClientInfo(mAllOwnershipClients, callback,
                        Binder.getCallingUid(), Binder.getCallingPid());
                mAllOwnershipClients.addBinderInterface(info);
            }
            Set<Integer> alreadyOwnedAppTypes = info.getOwnedAppTypes();
            if (!alreadyOwnedAppTypes.contains(appType)) {
                OwnershipClientInfo ownerInfo = mFocusOwners.get(appType);
                if (ownerInfo != null && ownerInfo != info) {
                    if (mSystemActivityMonitoringService.isInForeground(
                                ownerInfo.getPid(), ownerInfo.getUid()) &&
                        !mSystemActivityMonitoringService.isInForeground(
                                info.getPid(), info.getUid())) {
                        Log.w(CarLog.TAG_APP_FOCUS, "Focus request failed for non-foreground app("
                              + "pid=" + info.getPid() + ", uid=" + info.getUid() + ")."
                              + "Foreground app (pid=" + ownerInfo.getPid() + ", uid="
                              + ownerInfo.getUid() + ") owns it.");
                        return CarAppFocusManager.APP_FOCUS_REQUEST_FAILED;
                    }
                    ownerInfo.removeOwnedAppType(appType);
                    mDispatchHandler.requestAppFocusOwnershipLossDispatch(
                            ownerInfo.binderInterface, appType);
                    if (DBG) {
                        Log.i(CarLog.TAG_APP_FOCUS, "losing app type "
                                + appType + "," + ownerInfo.toString());
                    }
                }
                updateFocusOwner(appType, info);
            }
            info.addOwnedAppType(appType);
            mDispatchHandler.requestAppFocusOwnershipGrantDispatch(
                    info.binderInterface, appType);
            if (mActiveAppTypes.add(appType)) {
                if (DBG) {
                    Log.i(CarLog.TAG_APP_FOCUS, "adding active app type " + appType + ","
                            + info.toString());
                }
                for (BinderInterfaceContainer.BinderInterface<IAppFocusListener> client :
                        mAllChangeClients.getInterfaces()) {
                    ClientInfo clientInfo = (ClientInfo) client;
                    // dispatch events only when there is change after filter and the listener
                    // is not coming from the current caller.
                    if (clientInfo.getAppTypes().contains(appType)) {
                        mDispatchHandler.requestAppFocusChangeDispatch(clientInfo.binderInterface,
                                appType, true);
                    }
                }
            }
        }
        return CarAppFocusManager.APP_FOCUS_REQUEST_SUCCEEDED;
    }

    @Override
    public void abandonAppFocus(IAppFocusOwnershipCallback callback, int appType) {
        synchronized (this) {
            OwnershipClientInfo info =
                    (OwnershipClientInfo) mAllOwnershipClients.getBinderInterface(callback);
            if (info == null) {
                // ignore as this client cannot have owned anything.
                return;
            }
            if (!mActiveAppTypes.contains(appType)) {
                // ignore as none of them are active;
                return;
            }
            Set<Integer> currentlyOwnedAppTypes = info.getOwnedAppTypes();
            if (!currentlyOwnedAppTypes.contains(appType)) {
                // ignore as listener doesn't own focus.
                return;
            }
            if (mFocusOwners.remove(appType) != null) {
                mActiveAppTypes.remove(appType);
                info.removeOwnedAppType(appType);
                if (DBG) {
                    Log.i(CarLog.TAG_APP_FOCUS, "abandoning focus " + appType
                            + "," + info.toString());
                }
                for (FocusOwnershipCallback ownershipCallback : mFocusOwnershipCallbacks) {
                    ownershipCallback.onFocusAbandoned(appType, info.mUid, info.mPid);
                }
                for (BinderInterfaceContainer.BinderInterface<IAppFocusListener> client :
                        mAllChangeClients.getInterfaces()) {
                    ClientInfo clientInfo = (ClientInfo) client;
                    if (clientInfo.getAppTypes().contains(appType)) {
                        mDispatchHandler.requestAppFocusChangeDispatch(clientInfo.binderInterface,
                                appType, false);
                    }
                }
            }
        }
    }

    @Override
    public void init() {
        synchronized (this) {
            mHandlerThread = new HandlerThread(AppFocusService.class.getSimpleName());
            mHandlerThread.start();
            mDispatchHandler = new DispatchHandler(mHandlerThread.getLooper());
        }
    }

    @Override
    public void release() {
        synchronized (this) {
            mHandlerThread.quitSafely();
            try {
                mHandlerThread.join(1000);
            } catch (InterruptedException e) {
                Log.e(CarLog.TAG_APP_FOCUS, "Timeout while waiting for handler thread to join.");
            }
            mDispatchHandler = null;
            mAllChangeClients.clear();
            mAllOwnershipClients.clear();
            mFocusOwners.clear();
            mActiveAppTypes.clear();
        }
    }

    @Override
    public void onBinderDeath(
            BinderInterfaceContainer.BinderInterface<IAppFocusOwnershipCallback> bInterface) {
        OwnershipClientInfo info = (OwnershipClientInfo) bInterface;
        for (Integer appType : info.getOwnedAppTypes()) {
            abandonAppFocus(bInterface.binderInterface, appType);
        }
    }

    @Override
    public void dump(PrintWriter writer) {
        writer.println("**AppFocusService**");
        synchronized (this) {
            writer.println("mActiveAppTypes:" + mActiveAppTypes);
            for (BinderInterfaceContainer.BinderInterface<IAppFocusOwnershipCallback> client :
                    mAllOwnershipClients.getInterfaces()) {
                OwnershipClientInfo clientInfo = (OwnershipClientInfo) client;
                writer.println(clientInfo.toString());
            }
        }
    }

    /**
     * Returns true if process with given uid and pid owns provided focus.
     */
    public boolean isFocusOwner(int uid, int pid, int appType) {
        synchronized (this) {
            if (mFocusOwners.containsKey(appType)) {
                OwnershipClientInfo clientInfo = mFocusOwners.get(appType);
                return clientInfo.getUid() == uid && clientInfo.getPid() == pid;
            }
        }
        return false;
    }

    /**
     * Defines callback functions that will be called when ownership has been changed.
     */
    public interface FocusOwnershipCallback {
        void onFocusAcquired(int appType, int uid, int pid);
        void onFocusAbandoned(int appType, int uid, int pid);
    }

    /**
     * Registers callback.
     *
     * If any focus already acquired it will trigger
     * {@link FocusOwnershipCallback#onFocusAcquired} call immediately in the same thread.
     */
    public void registerContextOwnerChangedCallback(FocusOwnershipCallback callback) {
        mFocusOwnershipCallbacks.add(callback);

        HashSet<Map.Entry<Integer, OwnershipClientInfo>> owners;
        synchronized (this) {
            owners = new HashSet<>(mFocusOwners.entrySet());
        }

        for (Map.Entry<Integer, OwnershipClientInfo> entry : owners) {
            OwnershipClientInfo clientInfo = entry.getValue();
            callback.onFocusAcquired(entry.getKey(), clientInfo.getUid(), clientInfo.getPid());
        }
    }

    /**
     * Unregisters provided callback.
     */
    public void unregisterContextOwnerChangedCallback(FocusOwnershipCallback callback) {
        mFocusOwnershipCallbacks.remove(callback);
    }

    private void updateFocusOwner(int appType, OwnershipClientInfo owner) {
        synchronized (this) {
            mFocusOwners.put(appType, owner);
        }

        CarServiceUtils.runOnMain(() -> {
            for (FocusOwnershipCallback callback : mFocusOwnershipCallbacks) {
                callback.onFocusAcquired(appType, owner.getUid(), owner.getPid());
            }
        });
    }

    private void dispatchAppFocusOwnershipLoss(IAppFocusOwnershipCallback callback, int appType) {
        try {
            callback.onAppFocusOwnershipLost(appType);
        } catch (RemoteException e) {
        }
    }

    private void dispatchAppFocusOwnershipGrant(IAppFocusOwnershipCallback callback, int appType) {
        try {
            callback.onAppFocusOwnershipGranted(appType);
        } catch (RemoteException e) {
        }
    }

    private void dispatchAppFocusChange(IAppFocusListener listener, int appType, boolean active) {
        try {
            listener.onAppFocusChanged(appType, active);
        } catch (RemoteException e) {
        }
    }

    private static class ClientHolder extends BinderInterfaceContainer<IAppFocusListener> {
        private ClientHolder(BinderEventHandler<IAppFocusListener> holder) {
            super(holder);
        }
    }

    private static class OwnershipClientHolder extends
            BinderInterfaceContainer<IAppFocusOwnershipCallback> {
        private OwnershipClientHolder(AppFocusService service) {
            super(service);
        }
    }

    private static class ClientInfo extends
            BinderInterfaceContainer.BinderInterface<IAppFocusListener> {
        private final int mUid;
        private final int mPid;
        private final Set<Integer> mAppTypes = new HashSet<>();

        private ClientInfo(ClientHolder holder, IAppFocusListener binder, int uid, int pid,
                int appType) {
            super(holder, binder);
            this.mUid = uid;
            this.mPid = pid;
            this.mAppTypes.add(appType);
        }

        private synchronized Set<Integer> getAppTypes() {
            return mAppTypes;
        }

        private synchronized boolean addAppType(Integer appType) {
            return mAppTypes.add(appType);
        }

        private synchronized boolean removeAppType(Integer appType) {
            return mAppTypes.remove(appType);
        }

        @Override
        public String toString() {
            synchronized (this) {
                return "ClientInfo{mUid=" + mUid + ",mPid=" + mPid
                        + ",appTypes=" + mAppTypes + "}";
            }
        }
    }

    private static class OwnershipClientInfo extends
            BinderInterfaceContainer.BinderInterface<IAppFocusOwnershipCallback> {
        private final int mUid;
        private final int mPid;
        private final Set<Integer> mOwnedAppTypes = new HashSet<>();

        private OwnershipClientInfo(OwnershipClientHolder holder, IAppFocusOwnershipCallback binder,
                int uid, int pid) {
            super(holder, binder);
            this.mUid = uid;
            this.mPid = pid;
        }

        private synchronized Set<Integer> getOwnedAppTypes() {
            if (DBG_EVENT) {
                Log.i(CarLog.TAG_APP_FOCUS, "getOwnedAppTypes " + mOwnedAppTypes);
            }
            return mOwnedAppTypes;
        }

        private synchronized boolean addOwnedAppType(Integer appType) {
            if (DBG_EVENT) {
                Log.i(CarLog.TAG_APP_FOCUS, "addOwnedAppType " + appType);
            }
            return mOwnedAppTypes.add(appType);
        }

        private synchronized boolean removeOwnedAppType(Integer appType) {
            if (DBG_EVENT) {
                Log.i(CarLog.TAG_APP_FOCUS, "removeOwnedAppType " + appType);
            }
            return mOwnedAppTypes.remove(appType);
        }

        int getUid() {
            return mUid;
        }

        int getPid() {
            return mPid;
        }

        @Override
        public String toString() {
            synchronized (this) {
                return "ClientInfo{mUid=" + mUid + ",mPid=" + mPid
                        + ",owned=" + mOwnedAppTypes + "}";
            }
        }
    }

    private class DispatchHandler extends Handler {
        private static final int MSG_DISPATCH_OWNERSHIP_LOSS = 0;
        private static final int MSG_DISPATCH_OWNERSHIP_GRANT = 1;
        private static final int MSG_DISPATCH_FOCUS_CHANGE = 2;

        private DispatchHandler(Looper looper) {
            super(looper);
        }

        private void requestAppFocusOwnershipLossDispatch(IAppFocusOwnershipCallback callback,
                int appType) {
            Message msg = obtainMessage(MSG_DISPATCH_OWNERSHIP_LOSS, appType, 0, callback);
            sendMessage(msg);
        }

        private void requestAppFocusOwnershipGrantDispatch(IAppFocusOwnershipCallback callback,
                int appType) {
            Message msg = obtainMessage(MSG_DISPATCH_OWNERSHIP_GRANT, appType, 0, callback);
            sendMessage(msg);
        }

        private void requestAppFocusChangeDispatch(IAppFocusListener listener, int appType,
                boolean active) {
            Message msg = obtainMessage(MSG_DISPATCH_FOCUS_CHANGE, appType, active ? 1 : 0,
                    listener);
            sendMessage(msg);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_DISPATCH_OWNERSHIP_LOSS:
                    dispatchAppFocusOwnershipLoss((IAppFocusOwnershipCallback) msg.obj, msg.arg1);
                    break;
                case MSG_DISPATCH_OWNERSHIP_GRANT:
                    dispatchAppFocusOwnershipGrant((IAppFocusOwnershipCallback) msg.obj, msg.arg1);
                    break;
                case MSG_DISPATCH_FOCUS_CHANGE:
                    dispatchAppFocusChange((IAppFocusListener) msg.obj, msg.arg1, msg.arg2 == 1);
                    break;
                default:
                    Log.e(CarLog.TAG_APP_FOCUS, "Can't dispatch message: " + msg);
            }
        }
    }

    private static int[] toIntArray(Set<Integer> intSet) {
        int[] intArr = new int[intSet.size()];
        int index = 0;
        for (Integer value : intSet) {
            intArr[index++] = value;
        }
        return intArr;
    }
}
