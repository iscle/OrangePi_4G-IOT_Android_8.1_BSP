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

package android.car.diagnostic;

import android.annotation.IntDef;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.car.Car;
import android.car.CarApiUtil;
import android.car.CarLibLog;
import android.car.CarManagerBase;
import android.car.CarNotConnectedException;
import android.car.diagnostic.ICarDiagnosticEventListener.Stub;
import android.content.Context;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.util.SparseArray;

import com.android.car.internal.CarPermission;
import com.android.car.internal.CarRatedListeners;
import com.android.car.internal.SingleMessageHandler;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * API for monitoring car diagnostic data.
 *
 * @hide
 */
@SystemApi
public final class CarDiagnosticManager implements CarManagerBase {
    public static final int FRAME_TYPE_LIVE = 0;
    public static final int FRAME_TYPE_FREEZE = 1;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({FRAME_TYPE_LIVE, FRAME_TYPE_FREEZE})
    /** @hide */
    public @interface FrameType {}

    /** @hide */
    public static final @FrameType int FRAME_TYPES[] = {
        FRAME_TYPE_LIVE,
        FRAME_TYPE_FREEZE
    };

    private static final int MSG_DIAGNOSTIC_EVENTS = 0;

    private final ICarDiagnostic mService;
    private final SparseArray<CarDiagnosticListeners> mActiveListeners = new SparseArray<>();

    /** Handles call back into clients. */
    private final SingleMessageHandler<CarDiagnosticEvent> mHandlerCallback;

    private CarDiagnosticEventListenerToService mListenerToService;

    private final CarPermission mVendorExtensionPermission;

    /** @hide */
    public CarDiagnosticManager(IBinder service, Context context, Handler handler) {
        mService = ICarDiagnostic.Stub.asInterface(service);
        mHandlerCallback = new SingleMessageHandler<CarDiagnosticEvent>(handler.getLooper(),
            MSG_DIAGNOSTIC_EVENTS) {
            @Override
            protected void handleEvent(CarDiagnosticEvent event) {
                CarDiagnosticListeners listeners;
                synchronized (mActiveListeners) {
                    listeners = mActiveListeners.get(event.frameType);
                }
                if (listeners != null) {
                    listeners.onDiagnosticEvent(event);
                }
            }
        };
        mVendorExtensionPermission = new CarPermission(context, Car.PERMISSION_VENDOR_EXTENSION);
    }

    @Override
    /** @hide */
    public void onCarDisconnected() {
        synchronized(mActiveListeners) {
            mActiveListeners.clear();
            mListenerToService = null;
        }
    }

    /** Listener for diagnostic events. Callbacks are called in the Looper context. */
    public interface OnDiagnosticEventListener {
        /**
         * Called when there is a diagnostic event from the car.
         *
         * @param carDiagnosticEvent
         */
        void onDiagnosticEvent(final CarDiagnosticEvent carDiagnosticEvent);
    }

    // OnDiagnosticEventListener registration

    private void assertFrameType(@FrameType int frameType) {
        switch(frameType) {
            case FRAME_TYPE_FREEZE:
            case FRAME_TYPE_LIVE:
                return;
            default:
                throw new IllegalArgumentException(String.format(
                            "%d is not a valid diagnostic frame type", frameType));
        }
    }

    /**
     * Register a new listener for events of a given frame type and rate.
     * @param listener
     * @param frameType
     * @param rate
     * @return true if the registration was successful; false otherwise
     * @throws CarNotConnectedException
     * @throws IllegalArgumentException
     */
    public boolean registerListener(OnDiagnosticEventListener listener,
            @FrameType int frameType,
            int rate)
                throws CarNotConnectedException, IllegalArgumentException {
        assertFrameType(frameType);
        synchronized(mActiveListeners) {
            if (null == mListenerToService) {
                mListenerToService = new CarDiagnosticEventListenerToService(this);
            }
            boolean needsServerUpdate = false;
            CarDiagnosticListeners listeners = mActiveListeners.get(frameType);
            if (listeners == null) {
                listeners = new CarDiagnosticListeners(rate);
                mActiveListeners.put(frameType, listeners);
                needsServerUpdate = true;
            }
            if (listeners.addAndUpdateRate(listener, rate)) {
                needsServerUpdate = true;
            }
            if (needsServerUpdate) {
                if (!registerOrUpdateDiagnosticListener(frameType, rate)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Unregister a listener, causing it to stop receiving all diagnostic events.
     * @param listener
     */
    public void unregisterListener(OnDiagnosticEventListener listener) {
        synchronized(mActiveListeners) {
            for(@FrameType int frameType : FRAME_TYPES) {
                doUnregisterListenerLocked(listener, frameType);
            }
        }
    }

    private void doUnregisterListenerLocked(OnDiagnosticEventListener listener,
            @FrameType int frameType) {
        CarDiagnosticListeners listeners = mActiveListeners.get(frameType);
        if (listeners != null) {
            boolean needsServerUpdate = false;
            if (listeners.contains(listener)) {
                needsServerUpdate = listeners.remove(listener);
            }
            if (listeners.isEmpty()) {
                try {
                    mService.unregisterDiagnosticListener(frameType,
                        mListenerToService);
                } catch (RemoteException e) {
                    //ignore
                }
                mActiveListeners.remove(frameType);
            } else if (needsServerUpdate) {
                try {
                    registerOrUpdateDiagnosticListener(frameType, listeners.getRate());
                } catch (CarNotConnectedException e) {
                    // ignore
                }
            }
        }
    }

    private boolean registerOrUpdateDiagnosticListener(@FrameType int frameType, int rate)
        throws CarNotConnectedException {
        try {
            return mService.registerOrUpdateDiagnosticListener(frameType, rate, mListenerToService);
        } catch (IllegalStateException e) {
            CarApiUtil.checkCarNotConnectedExceptionFromCarService(e);
        } catch (RemoteException e) {
            throw new CarNotConnectedException();
        }
        return false;
    }

    // ICarDiagnostic forwards

    /**
     * Retrieve the most-recently acquired live frame data from the car.
     * @return A CarDiagnostic event for the most recently known live frame if one is present.
     *         null if no live frame has been recorded by the vehicle.
     * @throws CarNotConnectedException
     */
    public @Nullable
    CarDiagnosticEvent getLatestLiveFrame() throws CarNotConnectedException {
        try {
            return mService.getLatestLiveFrame();
        } catch (IllegalStateException e) {
            CarApiUtil.checkCarNotConnectedExceptionFromCarService(e);
        } catch (RemoteException e) {
            throw new CarNotConnectedException();
        }
        return null;
    }

    /**
     * Return the list of the timestamps for which a freeze frame is currently stored.
     * @return An array containing timestamps at which, at the current time, the vehicle has
     *         a freeze frame stored. If no freeze frames are currently stored, an empty
     *         array will be returned.
     * Because vehicles might have a limited amount of storage for frames, clients cannot
     * assume that a timestamp obtained via this call will be indefinitely valid for retrieval
     * of the actual diagnostic data, and must be prepared to handle a missing frame.
     * @throws CarNotConnectedException
     */
    public long[] getFreezeFrameTimestamps() throws CarNotConnectedException {
        try {
            return mService.getFreezeFrameTimestamps();
        } catch (IllegalStateException e) {
            CarApiUtil.checkCarNotConnectedExceptionFromCarService(e);
        } catch (RemoteException e) {
            throw new CarNotConnectedException();
        }
        return new long[]{};
    }

    /**
     * Retrieve the freeze frame event data for a given timestamp, if available.
     * @param timestamp
     * @return A CarDiagnostic event for the frame at the given timestamp, if one is
     *         available. null is returned otherwise.
     * Storage constraints might cause frames to be deleted from vehicle memory.
     * For this reason it cannot be assumed that a timestamp will yield a valid frame,
     * even if it was initially obtained via a call to getFreezeFrameTimestamps().
     * @throws CarNotConnectedException
     */
    public @Nullable
    CarDiagnosticEvent getFreezeFrame(long timestamp)
        throws CarNotConnectedException {
        try {
            return mService.getFreezeFrame(timestamp);
        } catch (IllegalStateException e) {
            CarApiUtil.checkCarNotConnectedExceptionFromCarService(e);
        } catch (RemoteException e) {
            throw new CarNotConnectedException();
        }
        return null;
    }

    /**
     * Clear the freeze frame information from vehicle memory at the given timestamps.
     * @param timestamps A list of timestamps to delete freeze frames at, or an empty array
     *                   to delete all freeze frames from vehicle memory.
     * @return true if all the required frames were deleted (including if no timestamps are
     *         provided and all frames were cleared); false otherwise.
     * Due to storage constraints, timestamps cannot be assumed to be indefinitely valid, and
     * a false return from this method should be used by the client as cause for invalidating
     * its local knowledge of the vehicle diagnostic state.
     * @throws CarNotConnectedException
     */
    public boolean clearFreezeFrames(long... timestamps) throws CarNotConnectedException {
        try {
            return mService.clearFreezeFrames(timestamps);
        } catch (IllegalStateException e) {
            CarApiUtil.checkCarNotConnectedExceptionFromCarService(e);
        } catch (RemoteException e) {
            throw new CarNotConnectedException();
        }
        return false;
    }

    /**
     * Returns true if this vehicle supports sending live frame information.
     * @return
     * @throws CarNotConnectedException
     */
    public boolean isLiveFrameSupported() throws CarNotConnectedException {
        try {
            return mService.isLiveFrameSupported();
        } catch (IllegalStateException e) {
            CarApiUtil.checkCarNotConnectedExceptionFromCarService(e);
        } catch (RemoteException e) {
            throw new CarNotConnectedException();
        }
        return false;
    }

    /**
     * Returns true if this vehicle supports supports sending notifications to
     * registered listeners when new freeze frames happen.
     * @throws CarNotConnectedException
     */
    public boolean isFreezeFrameNotificationSupported() throws CarNotConnectedException {
        try {
            return mService.isFreezeFrameNotificationSupported();
        } catch (IllegalStateException e) {
            CarApiUtil.checkCarNotConnectedExceptionFromCarService(e);
        } catch (RemoteException e) {
            throw new CarNotConnectedException();
        }
        return false;
    }

    /**
     * Returns whether the underlying HAL supports retrieving freeze frames
     * stored in vehicle memory using timestamp.
     * @throws CarNotConnectedException
     */
    public boolean isGetFreezeFrameSupported() throws CarNotConnectedException {
        try {
            return mService.isGetFreezeFrameSupported();
        } catch (IllegalStateException e) {
            CarApiUtil.checkCarNotConnectedExceptionFromCarService(e);
        } catch (RemoteException e) {
            throw new CarNotConnectedException();
        }
        return false;
    }

    /**
     * Returns true if this vehicle supports clearing freeze frame timestamps.
     * This is only meaningful if freeze frame data is also supported.
     * @return
     * @throws CarNotConnectedException
     */
    public boolean isClearFreezeFramesSupported() throws CarNotConnectedException {
        try {
            return mService.isClearFreezeFramesSupported();
        } catch (IllegalStateException e) {
            CarApiUtil.checkCarNotConnectedExceptionFromCarService(e);
        } catch (RemoteException e) {
            throw new CarNotConnectedException();
        }
        return false;
    }

    private static class CarDiagnosticEventListenerToService
            extends Stub {
        private final WeakReference<CarDiagnosticManager> mManager;

        public CarDiagnosticEventListenerToService(CarDiagnosticManager manager) {
            mManager = new WeakReference<>(manager);
        }

        private void handleOnDiagnosticEvents(CarDiagnosticManager manager,
            List<CarDiagnosticEvent> events) {
            manager.mHandlerCallback.sendEvents(events);
        }

        @Override
        public void onDiagnosticEvents(List<CarDiagnosticEvent> events) {
            CarDiagnosticManager manager = mManager.get();
            if (manager != null) {
                handleOnDiagnosticEvents(manager, events);
            }
        }
    }

    private class CarDiagnosticListeners extends CarRatedListeners<OnDiagnosticEventListener> {
        CarDiagnosticListeners(int rate) {
            super(rate);
        }

        void onDiagnosticEvent(final CarDiagnosticEvent event) {
            // throw away old data as oneway binder call can change order.
            long updateTime = event.timestamp;
            if (updateTime < mLastUpdateTime) {
                Log.w(CarLibLog.TAG_DIAGNOSTIC, "dropping old data");
                return;
            }
            mLastUpdateTime = updateTime;
            final boolean hasVendorExtensionPermission = mVendorExtensionPermission.checkGranted();
            final CarDiagnosticEvent eventToDispatch = hasVendorExtensionPermission ?
                    event :
                    event.withVendorSensorsRemoved();
            List<OnDiagnosticEventListener> listeners;
            synchronized (mActiveListeners) {
                listeners = new ArrayList<>(getListeners());
            }
            listeners.forEach(new Consumer<OnDiagnosticEventListener>() {

                @Override
                public void accept(OnDiagnosticEventListener listener) {
                    listener.onDiagnosticEvent(eventToDispatch);
                }
            });
        }
    }
}
