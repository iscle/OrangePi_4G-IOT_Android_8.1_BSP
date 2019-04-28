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

package android.support.car;

import android.content.Context;
import android.os.Handler;

/**
 * CarServiceLoader is the abstraction for loading different types of car service.
 * @hide
 */
public abstract class CarServiceLoader {

    private final Context mContext;
    private final CarConnectionCallbackProxy mCallback;
    private final Handler mEventHandler;

    public CarServiceLoader(Context context, CarConnectionCallbackProxy callback, Handler handler) {
        mContext = context;
        mCallback = callback;
        mEventHandler = handler;
    }

    public abstract void connect() throws IllegalStateException;
    public abstract void disconnect();
    public abstract boolean isConnected();

    @Car.ConnectionType
    public abstract int getCarConnectionType() throws CarNotConnectedException;

    /**
     * Retrieves a manager object for a specified Car*Manager.
     * @param serviceName One of the android.car.Car#*_SERVICE constants.
     * @return An instance of the request manager.  Null if the manager is not supported on the
     * current vehicle.
     * @throws CarNotConnectedException if the connection to the car service has been lost.
     */
    public abstract Object getCarManager(String serviceName) throws CarNotConnectedException;

    protected Context getContext() {
        return mContext;
    }

    protected CarConnectionCallbackProxy getConnectionCallback() {
        return mCallback;
    }

    protected Handler getEventHandler() {
        return mEventHandler;
    }

    /**
     * Wrapper for CarConnectionCallback which does not return a {@link android.support.car.Car}
     * object.
     */
    public abstract static class CarConnectionCallbackProxy {

        /**
         * Called when the Car has been connected. Does not guarantee the car is still connected
         * while this callback is running, so {@link CarNotConnectedException}s may still be
         * thrown from {@link Car} method calls.
         */
        public abstract void onConnected();
        /**
         * Called when the Car has been disconnected.
         */
        public abstract void onDisconnected();
    }
}
