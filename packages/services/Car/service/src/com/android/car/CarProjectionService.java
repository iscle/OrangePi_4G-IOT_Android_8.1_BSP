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
package com.android.car;

import android.car.CarProjectionManager;
import android.car.ICarProjection;
import android.car.ICarProjectionCallback;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;
import android.view.KeyEvent;

import java.io.PrintWriter;

/**
 * Car projection service allows to bound to projected app to boost it prioirity.
 * It also enables proejcted applications to handle voice action requests.
 */
class CarProjectionService extends ICarProjection.Stub implements CarServiceBase,
        BinderInterfaceContainer.BinderEventHandler<ICarProjectionCallback> {
    private final ListenerHolder mAllListeners;
    private final CarInputService mCarInputService;
    private final Context mContext;

    private final CarInputService.KeyEventListener mVoiceAssistantKeyListener =
            new CarInputService.KeyEventListener() {
                @Override
                public boolean onKeyEvent(KeyEvent event) {
                    handleVoiceAssitantRequest(false);
                    return true;
                }
            };

    private final CarInputService.KeyEventListener mLongVoiceAssistantKeyListener =
            new CarInputService.KeyEventListener() {
                @Override
                public boolean onKeyEvent(KeyEvent event) {
                    handleVoiceAssitantRequest(true);
                    return true;
                }
            };

    private final ServiceConnection mConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName className, IBinder service) {
                synchronized (CarProjectionService.this) {
                    mBound = true;
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName className) {
                // Service has crashed.
                Log.w(CarLog.TAG_PROJECTION, "Service disconnected: " + className);
                synchronized (CarProjectionService.this) {
                    mRegisteredService = null;
                }
                unbindServiceIfBound();
            }
        };

    private boolean mBound;
    private Intent mRegisteredService;

    CarProjectionService(Context context, CarInputService carInputService) {
        mContext = context;
        mCarInputService = carInputService;
        mAllListeners = new ListenerHolder(this);
    }

    @Override
    public void registerProjectionRunner(Intent serviceIntent) {
        // We assume one active projection app running in the system at one time.
        synchronized (this) {
            if (serviceIntent.filterEquals(mRegisteredService)) {
                return;
            }
            if (mRegisteredService != null) {
                Log.w(CarLog.TAG_PROJECTION, "Registering new service[" + serviceIntent
                        + "] while old service[" + mRegisteredService + "] is still running");
            }
            unbindServiceIfBound();
        }
        bindToService(serviceIntent);
    }

    @Override
    public void unregisterProjectionRunner(Intent serviceIntent) {
        synchronized (this) {
            if (!serviceIntent.filterEquals(mRegisteredService)) {
                Log.w(CarLog.TAG_PROJECTION, "Request to unbind unregistered service["
                        + serviceIntent + "]. Registered service[" + mRegisteredService + "]");
                return;
            }
            mRegisteredService = null;
        }
        unbindServiceIfBound();
    }

    private void bindToService(Intent serviceIntent) {
        synchronized (this) {
            mRegisteredService = serviceIntent;
        }
        UserHandle userHandle = UserHandle.getUserHandleForUid(Binder.getCallingUid());
        mContext.bindServiceAsUser(serviceIntent, mConnection, Context.BIND_AUTO_CREATE,
                userHandle);
    }

    private void unbindServiceIfBound() {
        synchronized (this) {
            if (!mBound) {
                return;
            }
            mBound = false;
        }
        mContext.unbindService(mConnection);
    }

    private synchronized void handleVoiceAssitantRequest(boolean isTriggeredByLongPress) {
        for (BinderInterfaceContainer.BinderInterface<ICarProjectionCallback> listener :
                 mAllListeners.getInterfaces()) {
            ListenerInfo listenerInfo = (ListenerInfo) listener;
            if ((listenerInfo.hasFilter(CarProjectionManager.PROJECTION_LONG_PRESS_VOICE_SEARCH)
                    && isTriggeredByLongPress)
                    || (listenerInfo.hasFilter(CarProjectionManager.PROJECTION_VOICE_SEARCH)
                    && !isTriggeredByLongPress)) {
                dispatchVoiceAssistantRequest(listenerInfo.binderInterface, isTriggeredByLongPress);
            }
        }
    }

    @Override
    public void registerProjectionListener(ICarProjectionCallback listener, int filter) {
        synchronized (this) {
            ListenerInfo info = (ListenerInfo) mAllListeners.getBinderInterface(listener);
            if (info == null) {
                info = new ListenerInfo(mAllListeners, listener, filter);
                mAllListeners.addBinderInterface(info);
            } else {
                info.setFilter(filter);
            }
        }
        updateCarInputServiceListeners();
    }

    @Override
    public void unregisterProjectionListener(ICarProjectionCallback listener) {
        synchronized (this) {
            mAllListeners.removeBinder(listener);
        }
        updateCarInputServiceListeners();
    }

    private void updateCarInputServiceListeners() {
        boolean listenShortPress = false;
        boolean listenLongPress = false;
        synchronized (this) {
            for (BinderInterfaceContainer.BinderInterface<ICarProjectionCallback> listener :
                         mAllListeners.getInterfaces()) {
                ListenerInfo listenerInfo = (ListenerInfo) listener;
                listenShortPress |= listenerInfo.hasFilter(
                        CarProjectionManager.PROJECTION_VOICE_SEARCH);
                listenLongPress |= listenerInfo.hasFilter(
                        CarProjectionManager.PROJECTION_LONG_PRESS_VOICE_SEARCH);
            }
        }
        mCarInputService.setVoiceAssistantKeyListener(listenShortPress
                ? mVoiceAssistantKeyListener : null);
        mCarInputService.setLongVoiceAssistantKeyListener(listenLongPress
                ? mLongVoiceAssistantKeyListener : null);
    }

    @Override
    public void init() {
        // nothing to do
    }

    @Override
    public void release() {
        synchronized (this) {
            mAllListeners.clear();
        }
    }

    @Override
    public void onBinderDeath(
            BinderInterfaceContainer.BinderInterface<ICarProjectionCallback> bInterface) {
        unregisterProjectionListener(bInterface.binderInterface);
    }

    @Override
    public void dump(PrintWriter writer) {
        writer.println("**CarProjectionService**");
        synchronized (this) {
            for (BinderInterfaceContainer.BinderInterface<ICarProjectionCallback> listener :
                         mAllListeners.getInterfaces()) {
                ListenerInfo listenerInfo = (ListenerInfo) listener;
                writer.println(listenerInfo.toString());
            }
        }
    }

    private void dispatchVoiceAssistantRequest(ICarProjectionCallback listener,
            boolean fromLongPress) {
        try {
            listener.onVoiceAssistantRequest(fromLongPress);
        } catch (RemoteException e) {
        }
    }

    private static class ListenerHolder extends BinderInterfaceContainer<ICarProjectionCallback> {
        private ListenerHolder(CarProjectionService service) {
            super(service);
        }
    }

    private static class ListenerInfo extends
            BinderInterfaceContainer.BinderInterface<ICarProjectionCallback> {
        private int mFilter;

        private ListenerInfo(ListenerHolder holder, ICarProjectionCallback binder, int filter) {
            super(holder, binder);
            this.mFilter = filter;
        }

        private synchronized int getFilter() {
            return mFilter;
        }

        private boolean hasFilter(int filter) {
            return (getFilter() & filter) != 0;
        }

        private synchronized void setFilter(int filter) {
            mFilter = filter;
        }

        @Override
        public String toString() {
            synchronized (this) {
                return "ListenerInfo{filter=" + Integer.toHexString(mFilter) + "}";
            }
        }
    }
}
