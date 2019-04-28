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

package com.android.car;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.car.Car;
import android.car.diagnostic.CarDiagnosticEvent;
import android.car.diagnostic.CarDiagnosticManager;
import android.car.diagnostic.ICarDiagnostic;
import android.car.diagnostic.ICarDiagnosticEventListener;
import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.ArrayMap;
import android.util.Log;
import com.android.car.hal.DiagnosticHalService.DiagnosticCapabilities;
import com.android.car.internal.CarPermission;
import com.android.car.Listeners.ClientWithRate;
import com.android.car.hal.DiagnosticHalService;
import com.android.internal.annotations.GuardedBy;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/** @hide */
public class CarDiagnosticService extends ICarDiagnostic.Stub
        implements CarServiceBase, DiagnosticHalService.DiagnosticListener {
    /** lock to access diagnostic structures */
    private final ReentrantLock mDiagnosticLock = new ReentrantLock();
    /** hold clients callback */
    @GuardedBy("mDiagnosticLock")
    private final LinkedList<DiagnosticClient> mClients = new LinkedList<>();

    /** key: diagnostic type. */
    @GuardedBy("mDiagnosticLock")
    private final HashMap<Integer, Listeners<DiagnosticClient>> mDiagnosticListeners =
        new HashMap<>();

    /** the latest live frame data. */
    @GuardedBy("mDiagnosticLock")
    private final LiveFrameRecord mLiveFrameDiagnosticRecord = new LiveFrameRecord(mDiagnosticLock);

    /** the latest freeze frame data (key: DTC) */
    @GuardedBy("mDiagnosticLock")
    private final FreezeFrameRecord mFreezeFrameDiagnosticRecords = new FreezeFrameRecord(
        mDiagnosticLock);

    private final DiagnosticHalService mDiagnosticHal;

    private final Context mContext;

    private final CarPermission mDiagnosticReadPermission;

    private final CarPermission mDiagnosticClearPermission;

    public CarDiagnosticService(Context context, DiagnosticHalService diagnosticHal) {
        mContext = context;
        mDiagnosticHal = diagnosticHal;
        mDiagnosticReadPermission = new CarPermission(mContext,
                Car.PERMISSION_CAR_DIAGNOSTIC_READ_ALL);
        mDiagnosticClearPermission = new CarPermission(mContext,
                Car.PERMISSION_CAR_DIAGNOSTIC_CLEAR);
    }

    @Override
    public void init() {
        mDiagnosticLock.lock();
        try {
            mDiagnosticHal.setDiagnosticListener(this);
            setInitialLiveFrame();
            setInitialFreezeFrames();
        } finally {
            mDiagnosticLock.unlock();
        }
    }

    @Nullable
    private CarDiagnosticEvent setInitialLiveFrame() {
        CarDiagnosticEvent liveFrame = null;
        if(mDiagnosticHal.getDiagnosticCapabilities().isLiveFrameSupported()) {
            liveFrame = setRecentmostLiveFrame(mDiagnosticHal.getCurrentLiveFrame());
        }
        return liveFrame;
    }

    private void setInitialFreezeFrames() {
        if(mDiagnosticHal.getDiagnosticCapabilities().isFreezeFrameSupported() &&
            mDiagnosticHal.getDiagnosticCapabilities().isFreezeFrameInfoSupported()) {
            long[] timestamps = mDiagnosticHal.getFreezeFrameTimestamps();
            if (timestamps != null) {
                for (long timestamp : timestamps) {
                    setRecentmostFreezeFrame(mDiagnosticHal.getFreezeFrame(timestamp));
                }
            }
        }
    }

    @Nullable
    private CarDiagnosticEvent setRecentmostLiveFrame(final CarDiagnosticEvent event) {
        if (event != null) {
            return mLiveFrameDiagnosticRecord.update(event.checkLiveFrame());
        }
        return null;
    }

    @Nullable
    private CarDiagnosticEvent setRecentmostFreezeFrame(final CarDiagnosticEvent event) {
        if (event != null) {
            return mFreezeFrameDiagnosticRecords.update(event.checkFreezeFrame());
        }
        return null;
    }

    @Override
    public void release() {
        mDiagnosticLock.lock();
        try {
            mDiagnosticListeners.forEach(
                    (Integer frameType, Listeners diagnosticListeners) ->
                            diagnosticListeners.release());
            mDiagnosticListeners.clear();
            mLiveFrameDiagnosticRecord.disableIfNeeded();
            mFreezeFrameDiagnosticRecords.disableIfNeeded();
            mClients.clear();
        } finally {
            mDiagnosticLock.unlock();
        }
    }

    private void processDiagnosticData(List<CarDiagnosticEvent> events) {
        ArrayMap<CarDiagnosticService.DiagnosticClient, List<CarDiagnosticEvent>> eventsByClient =
                new ArrayMap<>();

        Listeners<DiagnosticClient> listeners = null;

        mDiagnosticLock.lock();
        for (CarDiagnosticEvent event : events) {
            if (event.isLiveFrame()) {
                // record recent-most live frame information
                setRecentmostLiveFrame(event);
                listeners = mDiagnosticListeners.get(CarDiagnosticManager.FRAME_TYPE_LIVE);
            } else if (event.isFreezeFrame()) {
                setRecentmostFreezeFrame(event);
                listeners = mDiagnosticListeners.get(CarDiagnosticManager.FRAME_TYPE_FREEZE);
            } else {
                Log.w(
                        CarLog.TAG_DIAGNOSTIC,
                        String.format("received unknown diagnostic event: %s", event));
                continue;
            }

            if (null != listeners) {
                for (ClientWithRate<DiagnosticClient> clientWithRate : listeners.getClients()) {
                    DiagnosticClient client = clientWithRate.getClient();
                    List<CarDiagnosticEvent> clientEvents = eventsByClient.computeIfAbsent(client,
                            (DiagnosticClient diagnosticClient) -> new LinkedList<>());
                    clientEvents.add(event);
                }
            }
        }
        mDiagnosticLock.unlock();

        for (ArrayMap.Entry<CarDiagnosticService.DiagnosticClient, List<CarDiagnosticEvent>> entry :
                eventsByClient.entrySet()) {
            CarDiagnosticService.DiagnosticClient client = entry.getKey();
            List<CarDiagnosticEvent> clientEvents = entry.getValue();

            client.dispatchDiagnosticUpdate(clientEvents);
        }
    }

    /** Received diagnostic data from car. */
    @Override
    public void onDiagnosticEvents(List<CarDiagnosticEvent> events) {
        processDiagnosticData(events);
    }

    @Override
    public boolean registerOrUpdateDiagnosticListener(int frameType, int rate,
                ICarDiagnosticEventListener listener) {
        boolean shouldStartDiagnostics = false;
        CarDiagnosticService.DiagnosticClient diagnosticClient = null;
        Integer oldRate = null;
        Listeners<DiagnosticClient> diagnosticListeners = null;
        mDiagnosticLock.lock();
        try {
            mDiagnosticReadPermission.assertGranted();
            diagnosticClient = findDiagnosticClientLocked(listener);
            Listeners.ClientWithRate<DiagnosticClient> diagnosticClientWithRate = null;
            if (diagnosticClient == null) {
                diagnosticClient = new DiagnosticClient(listener);
                try {
                    listener.asBinder().linkToDeath(diagnosticClient, 0);
                } catch (RemoteException e) {
                    Log.w(
                            CarLog.TAG_DIAGNOSTIC,
                            String.format(
                                    "received RemoteException trying to register listener for %s",
                                    frameType));
                    return false;
                }
                mClients.add(diagnosticClient);
            }
            diagnosticListeners = mDiagnosticListeners.get(frameType);
            if (diagnosticListeners == null) {
                diagnosticListeners = new Listeners<>(rate);
                mDiagnosticListeners.put(frameType, diagnosticListeners);
                shouldStartDiagnostics = true;
            } else {
                oldRate = diagnosticListeners.getRate();
                diagnosticClientWithRate =
                        diagnosticListeners.findClientWithRate(diagnosticClient);
            }
            if (diagnosticClientWithRate == null) {
                diagnosticClientWithRate =
                        new ClientWithRate<>(diagnosticClient, rate);
                diagnosticListeners.addClientWithRate(diagnosticClientWithRate);
            } else {
                diagnosticClientWithRate.setRate(rate);
            }
            if (diagnosticListeners.getRate() > rate) {
                diagnosticListeners.setRate(rate);
                shouldStartDiagnostics = true;
            }
            diagnosticClient.addDiagnostic(frameType);
        } finally {
            mDiagnosticLock.unlock();
        }
        Log.i(
                CarLog.TAG_DIAGNOSTIC,
                String.format(
                        "shouldStartDiagnostics = %s for %s at rate %d",
                        shouldStartDiagnostics, frameType, rate));
        // start diagnostic outside lock as it can take time.
        if (shouldStartDiagnostics) {
            if (!startDiagnostic(frameType, rate)) {
                // failed. so remove from active diagnostic list.
                Log.w(CarLog.TAG_DIAGNOSTIC, "startDiagnostic failed");
                mDiagnosticLock.lock();
                try {
                    diagnosticClient.removeDiagnostic(frameType);
                    if (oldRate != null) {
                        diagnosticListeners.setRate(oldRate);
                    } else {
                        mDiagnosticListeners.remove(frameType);
                    }
                } finally {
                    mDiagnosticLock.unlock();
                }
                return false;
            }
        }
        return true;
    }

    private boolean startDiagnostic(int frameType, int rate) {
        Log.i(CarLog.TAG_DIAGNOSTIC, String.format("starting diagnostic %s at rate %d",
                frameType, rate));
        DiagnosticHalService diagnosticHal = getDiagnosticHal();
        if (diagnosticHal != null) {
            if (!diagnosticHal.isReady()) {
                Log.w(CarLog.TAG_DIAGNOSTIC, "diagnosticHal not ready");
                return false;
            }
            switch (frameType) {
                case CarDiagnosticManager.FRAME_TYPE_LIVE:
                    if (mLiveFrameDiagnosticRecord.isEnabled()) {
                        return true;
                    }
                    if (diagnosticHal.requestSensorStart(CarDiagnosticManager.FRAME_TYPE_LIVE,
                            rate)) {
                        mLiveFrameDiagnosticRecord.enable();
                        return true;
                    }
                    break;
                case CarDiagnosticManager.FRAME_TYPE_FREEZE:
                    if (mFreezeFrameDiagnosticRecords.isEnabled()) {
                        return true;
                    }
                    if (diagnosticHal.requestSensorStart(CarDiagnosticManager.FRAME_TYPE_FREEZE,
                            rate)) {
                        mFreezeFrameDiagnosticRecords.enable();
                        return true;
                    }
                    break;
            }
        }
        return false;
    }

    @Override
    public void unregisterDiagnosticListener(
            int frameType, ICarDiagnosticEventListener listener) {
        boolean shouldStopDiagnostic = false;
        boolean shouldRestartDiagnostic = false;
        int newRate = 0;
        mDiagnosticLock.lock();
        try {
            DiagnosticClient diagnosticClient = findDiagnosticClientLocked(listener);
            if (diagnosticClient == null) {
                Log.i(
                        CarLog.TAG_DIAGNOSTIC,
                        String.format(
                                "trying to unregister diagnostic client %s for %s which is not registered",
                                listener, frameType));
                // never registered or already unregistered.
                return;
            }
            diagnosticClient.removeDiagnostic(frameType);
            if (diagnosticClient.getNumberOfActiveDiagnostic() == 0) {
                diagnosticClient.release();
                mClients.remove(diagnosticClient);
            }
            Listeners<DiagnosticClient> diagnosticListeners = mDiagnosticListeners.get(frameType);
            if (diagnosticListeners == null) {
                // diagnostic not active
                return;
            }
            ClientWithRate<DiagnosticClient> clientWithRate =
                    diagnosticListeners.findClientWithRate(diagnosticClient);
            if (clientWithRate == null) {
                return;
            }
            diagnosticListeners.removeClientWithRate(clientWithRate);
            if (diagnosticListeners.getNumberOfClients() == 0) {
                shouldStopDiagnostic = true;
                mDiagnosticListeners.remove(frameType);
            } else if (diagnosticListeners.updateRate()) { // rate changed
                newRate = diagnosticListeners.getRate();
                shouldRestartDiagnostic = true;
            }
        } finally {
            mDiagnosticLock.unlock();
        }
        Log.i(
                CarLog.TAG_DIAGNOSTIC,
                String.format(
                        "shouldStopDiagnostic = %s, shouldRestartDiagnostic = %s for type %s",
                        shouldStopDiagnostic, shouldRestartDiagnostic, frameType));
        if (shouldStopDiagnostic) {
            stopDiagnostic(frameType);
        } else if (shouldRestartDiagnostic) {
            startDiagnostic(frameType, newRate);
        }
    }

    private void stopDiagnostic(int frameType) {
        DiagnosticHalService diagnosticHal = getDiagnosticHal();
        if (diagnosticHal == null || !diagnosticHal.isReady()) {
            Log.w(CarLog.TAG_DIAGNOSTIC, "diagnosticHal not ready");
            return;
        }
        switch (frameType) {
            case CarDiagnosticManager.FRAME_TYPE_LIVE:
                if (mLiveFrameDiagnosticRecord.disableIfNeeded())
                    diagnosticHal.requestSensorStop(CarDiagnosticManager.FRAME_TYPE_LIVE);
                break;
            case CarDiagnosticManager.FRAME_TYPE_FREEZE:
                if (mFreezeFrameDiagnosticRecords.disableIfNeeded())
                    diagnosticHal.requestSensorStop(CarDiagnosticManager.FRAME_TYPE_FREEZE);
                break;
        }
    }

    private DiagnosticHalService getDiagnosticHal() {
        return mDiagnosticHal;
    }

    // Expose DiagnosticCapabilities
    public boolean isLiveFrameSupported() {
        return getDiagnosticHal().getDiagnosticCapabilities().isLiveFrameSupported();
    }

    public boolean isFreezeFrameNotificationSupported() {
        return getDiagnosticHal().getDiagnosticCapabilities().isFreezeFrameSupported();
    }

    public boolean isGetFreezeFrameSupported() {
        DiagnosticCapabilities diagnosticCapabilities =
                getDiagnosticHal().getDiagnosticCapabilities();
        return diagnosticCapabilities.isFreezeFrameInfoSupported() &&
                diagnosticCapabilities.isFreezeFrameSupported();
    }

    public boolean isClearFreezeFramesSupported() {
        DiagnosticCapabilities diagnosticCapabilities =
            getDiagnosticHal().getDiagnosticCapabilities();
        return diagnosticCapabilities.isFreezeFrameClearSupported() &&
            diagnosticCapabilities.isFreezeFrameSupported();
    }

    // ICarDiagnostic implementations

    @Override
    public CarDiagnosticEvent getLatestLiveFrame() {
        mLiveFrameDiagnosticRecord.lock();
        CarDiagnosticEvent liveFrame = mLiveFrameDiagnosticRecord.getLastEvent();
        mLiveFrameDiagnosticRecord.unlock();
        return liveFrame;
    }

    @Override
    public long[] getFreezeFrameTimestamps() {
        mFreezeFrameDiagnosticRecords.lock();
        long[] timestamps = mFreezeFrameDiagnosticRecords.getFreezeFrameTimestamps();
        mFreezeFrameDiagnosticRecords.unlock();
        return timestamps;
    }

    @Override
    @Nullable
    public CarDiagnosticEvent getFreezeFrame(long timestamp) {
        mFreezeFrameDiagnosticRecords.lock();
        CarDiagnosticEvent freezeFrame = mFreezeFrameDiagnosticRecords.getEvent(timestamp);
        mFreezeFrameDiagnosticRecords.unlock();
        return freezeFrame;
    }

    @Override
    public boolean clearFreezeFrames(long... timestamps) {
        mDiagnosticClearPermission.assertGranted();
        if (mDiagnosticHal.getDiagnosticCapabilities().isFreezeFrameClearSupported()) {
            mFreezeFrameDiagnosticRecords.lock();
            mDiagnosticHal.clearFreezeFrames(timestamps);
            mFreezeFrameDiagnosticRecords.clearEvents();
            mFreezeFrameDiagnosticRecords.unlock();
            return true;
        }
        return false;
    }

    /**
     * Find DiagnosticClient from client list and return it. This should be called with mClients
     * locked.
     *
     * @param listener
     * @return null if not found.
     */
    private CarDiagnosticService.DiagnosticClient findDiagnosticClientLocked(
            ICarDiagnosticEventListener listener) {
        IBinder binder = listener.asBinder();
        for (DiagnosticClient diagnosticClient : mClients) {
            if (diagnosticClient.isHoldingListenerBinder(binder)) {
                return diagnosticClient;
            }
        }
        return null;
    }

    private void removeClient(DiagnosticClient diagnosticClient) {
        mDiagnosticLock.lock();
        try {
            for (int diagnostic : diagnosticClient.getDiagnosticArray()) {
                unregisterDiagnosticListener(
                        diagnostic, diagnosticClient.getICarDiagnosticEventListener());
            }
            mClients.remove(diagnosticClient);
        } finally {
            mDiagnosticLock.unlock();
        }
    }

    /** internal instance for pending client request */
    private class DiagnosticClient implements Listeners.IListener {
        /** callback for diagnostic events */
        private final ICarDiagnosticEventListener mListener;

        private final Set<Integer> mActiveDiagnostics = new HashSet<>();

        /** when false, it is already released */
        private volatile boolean mActive = true;

        DiagnosticClient(ICarDiagnosticEventListener listener) {
            this.mListener = listener;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof DiagnosticClient
                && mListener.asBinder()
                == ((DiagnosticClient) o).mListener.asBinder();
        }

        boolean isHoldingListenerBinder(IBinder listenerBinder) {
            return mListener.asBinder() == listenerBinder;
        }

        void addDiagnostic(int frameType) {
            mActiveDiagnostics.add(frameType);
        }

        void removeDiagnostic(int frameType) {
            mActiveDiagnostics.remove(frameType);
        }

        int getNumberOfActiveDiagnostic() {
            return mActiveDiagnostics.size();
        }

        int[] getDiagnosticArray() {
            return mActiveDiagnostics.stream().mapToInt(Integer::intValue).toArray();
        }

        ICarDiagnosticEventListener getICarDiagnosticEventListener() {
            return mListener;
        }

        /** Client dead. should remove all diagnostic requests from client */
        @Override
        public void binderDied() {
            mListener.asBinder().unlinkToDeath(this, 0);
            removeClient(this);
        }

        void dispatchDiagnosticUpdate(List<CarDiagnosticEvent> events) {
            if (events.size() != 0 && mActive) {
                try {
                    mListener.onDiagnosticEvents(events);
                } catch (RemoteException e) {
                    //ignore. crash will be handled by death handler
                }
            }
        }

        @Override
        public void release() {
            if (mActive) {
                mListener.asBinder().unlinkToDeath(this, 0);
                mActiveDiagnostics.clear();
                mActive = false;
            }
        }
    }

    private static abstract class DiagnosticRecord {
        private final ReentrantLock mLock;
        protected boolean mEnabled = false;

        DiagnosticRecord(ReentrantLock lock) {
            mLock = lock;
        }

        void lock() {
            mLock.lock();
        }

        void unlock() {
            mLock.unlock();
        }

        boolean isEnabled() {
            return mEnabled;
        }

        void enable() {
            mEnabled = true;
        }

        abstract boolean disableIfNeeded();
        abstract CarDiagnosticEvent update(CarDiagnosticEvent newEvent);
    }

    private static class LiveFrameRecord extends DiagnosticRecord {
        /** Store the most recent live-frame. */
        CarDiagnosticEvent mLastEvent = null;

        LiveFrameRecord(ReentrantLock lock) {
            super(lock);
        }

        @Override
        boolean disableIfNeeded() {
            if (!mEnabled) return false;
            mEnabled = false;
            mLastEvent = null;
            return true;
        }

        @Override
        CarDiagnosticEvent update(@NonNull CarDiagnosticEvent newEvent) {
            newEvent = Objects.requireNonNull(newEvent);
            if((null == mLastEvent) || mLastEvent.isEarlierThan(newEvent))
                mLastEvent = newEvent;
            return mLastEvent;
        }

        CarDiagnosticEvent getLastEvent() {
            return mLastEvent;
        }
    }

    private static class FreezeFrameRecord extends DiagnosticRecord {
        /** Store the timestamp --> freeze frame mapping. */
        HashMap<Long, CarDiagnosticEvent> mEvents = new HashMap<>();

        FreezeFrameRecord(ReentrantLock lock) {
            super(lock);
        }

        @Override
        boolean disableIfNeeded() {
            if (!mEnabled) return false;
            mEnabled = false;
            clearEvents();
            return true;
        }

        void clearEvents() {
            mEvents.clear();
        }

        @Override
        CarDiagnosticEvent update(@NonNull CarDiagnosticEvent newEvent) {
            mEvents.put(newEvent.timestamp, newEvent);
            return newEvent;
        }

        long[] getFreezeFrameTimestamps() {
            return mEvents.keySet().stream().mapToLong(Long::longValue).toArray();
        }

        CarDiagnosticEvent getEvent(long timestamp) {
            return mEvents.get(timestamp);
        }

        Iterable<CarDiagnosticEvent> getEvents() {
            return mEvents.values();
        }
    }

    @Override
    public void dump(PrintWriter writer) {
        writer.println("*CarDiagnosticService*");
        writer.println("**last events for diagnostics**");
        if (null != mLiveFrameDiagnosticRecord.getLastEvent()) {
            writer.println("last live frame event: ");
            writer.println(mLiveFrameDiagnosticRecord.getLastEvent());
        }
        writer.println("freeze frame events: ");
        mFreezeFrameDiagnosticRecords.getEvents().forEach(writer::println);
        writer.println("**clients**");
        try {
            for (DiagnosticClient client : mClients) {
                if (client != null) {
                    try {
                        writer.println(
                                "binder:"
                                        + client.mListener
                                        + " active diagnostics:"
                                        + Arrays.toString(client.getDiagnosticArray()));
                    } catch (ConcurrentModificationException e) {
                        writer.println("concurrent modification happened");
                    }
                } else {
                    writer.println("null client");
                }
            }
        } catch (ConcurrentModificationException e) {
            writer.println("concurrent modification happened");
        }
        writer.println("**diagnostic listeners**");
        try {
            for (int diagnostic : mDiagnosticListeners.keySet()) {
                Listeners diagnosticListeners = mDiagnosticListeners.get(diagnostic);
                if (diagnosticListeners != null) {
                    writer.println(
                            " Diagnostic:"
                                    + diagnostic
                                    + " num client:"
                                    + diagnosticListeners.getNumberOfClients()
                                    + " rate:"
                                    + diagnosticListeners.getRate());
                }
            }
        } catch (ConcurrentModificationException e) {
            writer.println("concurrent modification happened");
        }
    }
}
