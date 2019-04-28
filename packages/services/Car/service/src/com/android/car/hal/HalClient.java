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

package com.android.car.hal;

import static android.os.SystemClock.elapsedRealtime;

import android.hardware.automotive.vehicle.V2_0.IVehicle;
import android.hardware.automotive.vehicle.V2_0.IVehicleCallback;
import android.hardware.automotive.vehicle.V2_0.StatusCode;
import android.hardware.automotive.vehicle.V2_0.SubscribeOptions;
import android.hardware.automotive.vehicle.V2_0.VehiclePropConfig;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;

import com.android.car.CarLog;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Vehicle HAL client. Interacts directly with Vehicle HAL interface {@link IVehicle}. Contains
 * some logic for retriable properties, redirects Vehicle notifications into given looper thread.
 */
class  HalClient {
    /**
     * If call to vehicle HAL returns StatusCode.TRY_AGAIN, than {@link HalClient} will retry to
     * invoke that method again for this amount of milliseconds.
     */
    private static final int WAIT_CAP_FOR_RETRIABLE_RESULT_MS = 2000;

    private static final int SLEEP_BETWEEN_RETRIABLE_INVOKES_MS = 50;

    private final IVehicle mVehicle;

    private final IVehicleCallback mInternalCallback;

    /**
     * Create HalClient object
     *
     * @param vehicle interface to the vehicle HAL
     * @param looper looper that will be used to propagate notifications from vehicle HAL
     * @param callback to propagate notifications from Vehicle HAL in the provided looper thread
     */
    HalClient(IVehicle vehicle, Looper looper, IVehicleCallback callback) {
        mVehicle = vehicle;
        Handler handler = new CallbackHandler(looper, callback);
        mInternalCallback = new VehicleCallback(handler);
    }

    ArrayList<VehiclePropConfig> getAllPropConfigs() throws RemoteException {
        return mVehicle.getAllPropConfigs();
    }

    public void subscribe(SubscribeOptions... options) throws RemoteException {
        mVehicle.subscribe(mInternalCallback, new ArrayList<>(Arrays.asList(options)));
    }

    public void unsubscribe(int prop) throws RemoteException {
        mVehicle.unsubscribe(mInternalCallback, prop);
    }

    public void setValue(VehiclePropValue propValue) throws PropertyTimeoutException {
        int status = invokeRetriable(() -> {
            try {
                return mVehicle.set(propValue);
            } catch (RemoteException e) {
                Log.e(CarLog.TAG_HAL, "Failed to set value", e);
                return StatusCode.TRY_AGAIN;
            }
        }, WAIT_CAP_FOR_RETRIABLE_RESULT_MS, SLEEP_BETWEEN_RETRIABLE_INVOKES_MS);

        if (StatusCode.INVALID_ARG == status) {
            throw new IllegalArgumentException(
                    String.format("Failed to set value for: 0x%x, areaId: 0x%x",
                            propValue.prop, propValue.areaId));
        }

        if (StatusCode.TRY_AGAIN == status) {
            throw new PropertyTimeoutException(propValue.prop);
        }

        if (StatusCode.OK != status) {
            throw new IllegalStateException(
                    String.format("Failed to set property: 0x%x, areaId: 0x%x, "
                            + "code: %d", propValue.prop, propValue.areaId, status));
        }
    }

    VehiclePropValue getValue(VehiclePropValue requestedPropValue) throws PropertyTimeoutException {
        final ObjectWrapper<VehiclePropValue> valueWrapper = new ObjectWrapper<>();
        int status = invokeRetriable(() -> {
            ValueResult res = internalGet(requestedPropValue);
            valueWrapper.object = res.propValue;
            return res.status;
        }, WAIT_CAP_FOR_RETRIABLE_RESULT_MS, SLEEP_BETWEEN_RETRIABLE_INVOKES_MS);

        int propId = requestedPropValue.prop;
        int areaId = requestedPropValue.areaId;
        if (StatusCode.INVALID_ARG == status) {
            throw new IllegalArgumentException(
                    String.format("Failed to get value for: 0x%x, areaId: 0x%x", propId, areaId));
        }

        if (StatusCode.TRY_AGAIN == status) {
            throw new PropertyTimeoutException(propId);
        }

        if (StatusCode.OK != status || valueWrapper.object == null) {
            throw new IllegalStateException(
                    String.format("Failed to get property: 0x%x, areaId: 0x%x, "
                            + "code: %d", propId, areaId, status));
        }

        return valueWrapper.object;
    }

    private ValueResult internalGet(VehiclePropValue requestedPropValue) {
        final ValueResult result = new ValueResult();
        try {
            mVehicle.get(requestedPropValue,
                    (status, propValue) -> {
                        result.status = status;
                        result.propValue = propValue;
                    });
        } catch (RemoteException e) {
            Log.e(CarLog.TAG_HAL, "Failed to get value from vehicle HAL", e);
            result.status = StatusCode.TRY_AGAIN;
        }

        return result;
    }

    interface RetriableCallback {
        /** Returns {@link StatusCode} */
        int action();
    }

    private static int invokeRetriable(RetriableCallback callback, long timeoutMs, long sleepMs) {
        int status = callback.action();
        long startTime = elapsedRealtime();
        while (StatusCode.TRY_AGAIN == status && (elapsedRealtime() - startTime) < timeoutMs) {
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException e) {
                Log.e(CarLog.TAG_HAL, "Thread was interrupted while waiting for vehicle HAL.", e);
                break;
            }

            status = callback.action();
        }
        return status;
    }

    private static class ObjectWrapper<T> {
        T object;
    }

    private static class ValueResult {
        int status;
        VehiclePropValue propValue;
    }

    private static class PropertySetError {
        final int errorCode;
        final int propId;
        final int areaId;

        PropertySetError(int errorCode, int propId, int areaId) {
            this.errorCode = errorCode;
            this.propId = propId;
            this.areaId = areaId;
        }
    }

    private static class CallbackHandler extends Handler {
        private static final int MSG_ON_PROPERTY_SET = 1;
        private static final int MSG_ON_PROPERTY_EVENT = 2;
        private static final int MSG_ON_SET_ERROR = 3;

        private final IVehicleCallback mCallback;

        CallbackHandler(Looper looper, IVehicleCallback callback) {
            super(looper);
            mCallback = callback;
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);

            try {
                switch (msg.what) {
                    case MSG_ON_PROPERTY_EVENT:
                        mCallback.onPropertyEvent((ArrayList<VehiclePropValue>) msg.obj);
                        break;
                    case MSG_ON_PROPERTY_SET:
                        mCallback.onPropertySet((VehiclePropValue) msg.obj);
                        break;
                    case MSG_ON_SET_ERROR:
                        PropertySetError obj = (PropertySetError) msg.obj;
                        mCallback.onPropertySetError(obj.errorCode, obj.propId, obj.areaId);
                        break;
                    default:
                        Log.e(CarLog.TAG_HAL, "Unexpected message: " + msg.what);
                }
            } catch (RemoteException e) {
                Log.e(CarLog.TAG_HAL, "Message failed: " + msg.what);
            }
        }
    }

    private static class VehicleCallback extends IVehicleCallback.Stub {
        private Handler mHandler;

        VehicleCallback(Handler handler) {
            mHandler = handler;
        }

        @Override
        public void onPropertyEvent(ArrayList<VehiclePropValue> propValues) {
            mHandler.sendMessage(Message.obtain(
                    mHandler, CallbackHandler.MSG_ON_PROPERTY_EVENT, propValues));
        }

        @Override
        public void onPropertySet(VehiclePropValue propValue) {
            mHandler.sendMessage(Message.obtain(
                    mHandler, CallbackHandler.MSG_ON_PROPERTY_SET, propValue));
        }

        @Override
        public void onPropertySetError(int errorCode, int propId, int areaId) {
            mHandler.sendMessage(Message.obtain(
                    mHandler, CallbackHandler.MSG_ON_SET_ERROR,
                    new PropertySetError(errorCode, propId, areaId)));
        }
    }
}
