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

import android.annotation.IntDef;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.car.annotation.FutureFeature;
import android.car.cluster.CarInstrumentClusterManager;
import android.car.content.pm.CarPackageManager;
import android.car.diagnostic.CarDiagnosticManager;
import android.car.hardware.CarSensorManager;
import android.car.hardware.CarVendorExtensionManager;
import android.car.hardware.cabin.CarCabinManager;
import android.car.hardware.hvac.CarHvacManager;
import android.car.hardware.radio.CarRadioManager;
import android.car.media.CarAudioManager;
import android.car.navigation.CarNavigationStatusManager;
import android.car.CarBluetoothManager;
import android.car.test.CarTestManagerBinderWrapper;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;

import com.android.car.internal.FeatureConfiguration;
import com.android.internal.annotations.GuardedBy;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashMap;

/**
 *   Top level car API for embedded Android Auto deployments.
 *   This API works only for devices with {@link PackageManager#FEATURE_AUTOMOTIVE}
 *   Calling this API on a device with no such feature will lead to an exception.
 */
public final class Car {

    /**
     * Represent the version of Car API. This is only updated when there is API change.
     * 1 : N
     * 2 : O
     * 3 : O-MR1
     */
    public static final int VERSION = 3;

    /** Service name for {@link CarSensorManager}, to be used in {@link #getCarManager(String)}. */
    public static final String SENSOR_SERVICE = "sensor";

    /** Service name for {@link CarInfoManager}, to be used in {@link #getCarManager(String)}. */
    public static final String INFO_SERVICE = "info";

    /** Service name for {@link CarAppFocusManager}. */
    public static final String APP_FOCUS_SERVICE = "app_focus";

    /** Service name for {@link CarPackageManager} */
    public static final String PACKAGE_SERVICE = "package";

    /** Service name for {@link CarAudioManager} */
    public static final String AUDIO_SERVICE = "audio";

    /**
     * Service name for {@link CarNavigationStatusManager}
     * @hide
     */
    public static final String CAR_NAVIGATION_SERVICE = "car_navigation_service";
    /**
     * Service name for {@link CarInstrumentClusterManager}
     * @hide
     */
    public static final String CAR_INSTRUMENT_CLUSTER_SERVICE = "cluster_service";

    /**
     * @hide
     */
    @SystemApi
    public static final String CABIN_SERVICE = "cabin";

    /**
     * @hide
     */
    @SystemApi
    public static final String DIAGNOSTIC_SERVICE = "diagnostic";

    /**
     * @hide
     */
    @SystemApi
    public static final String RADIO_SERVICE = "radio";

    /**
     * @hide
     */
    @SystemApi
    public static final String HVAC_SERVICE = "hvac";

    /**
     * @hide
     */
    @SystemApi
    public static final String PROJECTION_SERVICE = "projection";

    /**
     * @hide
     */
    @SystemApi
    public static final String VENDOR_EXTENSION_SERVICE = "vendor_extension";

    /**
     * @hide
     */
    public static final String BLUETOOTH_SERVICE = "car_bluetooth";

    /**
     * Service for testing. This is system app only feature.
     * Service name for {@link CarTestManager}, to be used in {@link #getCarManager(String)}.
     * @hide
     */
    @SystemApi
    public static final String TEST_SERVICE = "car-service-test";

    /** Permission necessary to access car's mileage information. */
    public static final String PERMISSION_MILEAGE = "android.car.permission.CAR_MILEAGE";

    /** Permission necessary to access car's fuel level. */
    public static final String PERMISSION_FUEL = "android.car.permission.CAR_FUEL";

    /** Permission necessary to access car's speed. */
    public static final String PERMISSION_SPEED = "android.car.permission.CAR_SPEED";

    /** Permission necessary to access car's dynamics state. */
    public static final String PERMISSION_VEHICLE_DYNAMICS_STATE =
        "android.car.permission.VEHICLE_DYNAMICS_STATE";

    /**
     * Permission necessary to change car audio volume through {@link CarAudioManager}.
     */
    public static final String PERMISSION_CAR_CONTROL_AUDIO_VOLUME =
            "android.car.permission.CAR_CONTROL_AUDIO_VOLUME";

    /**
     * Permission necessary to change car audio settings through {@link CarAudioManager}.
     * @hide
     */
    public static final String PERMISSION_CAR_CONTROL_AUDIO_SETTINGS =
            "android.car.permission.CAR_CONTROL_AUDIO_SETTINGS";

    /**
     * Permission necessary to use {@link CarNavigationStatusManager}.
     * @hide
     */
    public static final String PERMISSION_CAR_NAVIGATION_MANAGER =
            "android.car.permission.CAR_NAVIGATION_MANAGER";

    /**
     * Permission necessary to start activities in the instrument cluster through
     * {@link CarInstrumentClusterManager}
     *
     * @hide
     */
    public static final String PERMISSION_CAR_INSTRUMENT_CLUSTER_CONTROL =
            "android.car.permission.CAR_INSTRUMENT_CLUSTER_CONTROL";

    /**
     * Application must have this permission in order to be launched in the instrument cluster
     * display.
     *
     * @hide
     */
    public static final String PERMISSION_CAR_DISPLAY_IN_CLUSTER =
            "android.car.permission.CAR_DISPLAY_IN_CLUSTER";

    /**
     * Permission necessary to access car specific communication channel.
     * @hide
     */
    @SystemApi
    public static final String PERMISSION_VENDOR_EXTENSION =
            "android.car.permission.CAR_VENDOR_EXTENSION";

    /**
     * @hide
     */
    @SystemApi
    public static final String PERMISSION_CONTROL_APP_BLOCKING =
            "android.car.permission.CONTROL_APP_BLOCKING";

    /**
     * Permission necessary to access Car Cabin APIs.
     * @hide
     */
    @SystemApi
    public static final String PERMISSION_CAR_CABIN = "android.car.permission.CAR_CABIN";

    /**
     * Permission necessary to access Car HVAC APIs.
     * @hide
     */
    @SystemApi
    public static final String PERMISSION_CAR_HVAC = "android.car.permission.CAR_HVAC";

    /**
     * Permission necessary to access Car RADIO system APIs.
     * @hide
     */
    @SystemApi
    public static final String PERMISSION_CAR_RADIO = "android.car.permission.CAR_RADIO";


    /**
     * Permission necessary to access Car PROJECTION system APIs.
     * @hide
     */
    @SystemApi
    public static final String PERMISSION_CAR_PROJECTION = "android.car.permission.CAR_PROJECTION";

    /**
     * Permission necessary to mock vehicle hal for testing.
     * @hide
     * @deprecated mocking vehicle HAL in car service is no longer supported.
     */
    @SystemApi
    public static final String PERMISSION_MOCK_VEHICLE_HAL =
            "android.car.permission.CAR_MOCK_VEHICLE_HAL";

    /**
     * Permission necessary to access CarTestService.
     * @hide
     */
    @SystemApi
    public static final String PERMISSION_CAR_TEST_SERVICE =
            "android.car.permission.CAR_TEST_SERVICE";

    /**
     * Permissions necessary to read diagnostic information, including vendor-specific bits.
     *
     * @hide
     */
    @SystemApi
    public static final String PERMISSION_CAR_DIAGNOSTIC_READ_ALL =
        "android.car.permission.DIAGNOSTIC_READ_ALL";

    /**
     * Permissions necessary to clear diagnostic information.
     *
     * @hide
     */
    @SystemApi
    public static final String PERMISSION_CAR_DIAGNOSTIC_CLEAR = "android.car.permission.DIAGNOSTIC_CLEAR";

    /** Type of car connection: platform runs directly in car. */
    public static final int CONNECTION_TYPE_EMBEDDED = 5;


    /** @hide */
    @IntDef({CONNECTION_TYPE_EMBEDDED})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ConnectionType {}

    /**
     * CarXyzService throws IllegalStateException with this message is re-thrown as
     * {@link CarNotConnectedException}.
     *
     * @hide
     */
    public static final String CAR_NOT_CONNECTED_EXCEPTION_MSG = "CarNotConnected";

    /**
     * Activity Action: Provide media playing through a media template app.
     * <p>Input: String extra mapped by {@link android.app.SearchManager#QUERY} is the query
     * used to start the media. String extra mapped by {@link #CAR_EXTRA_MEDIA_PACKAGE} is the
     * package name of the media app which user wants to play media on.
     * <p>Output: nothing.
     */
    public static final String CAR_INTENT_ACTION_MEDIA_TEMPLATE =
            "android.car.intent.action.MEDIA_TEMPLATE";

    /**
     * Used as a string extra field with {@link #CAR_INTENT_ACTION_MEDIA_TEMPLATE} to specify the
     * media app that user wants to start the media on. Note: this is not the templated media app.
     */
    public static final String CAR_EXTRA_MEDIA_PACKAGE = "android.car.intent.extra.MEDIA_PACKAGE";

    /** @hide */
    public static final String CAR_SERVICE_INTERFACE_NAME = "android.car.ICar";

    private static final String CAR_SERVICE_PACKAGE = "com.android.car";

    private static final String CAR_SERVICE_CLASS = "com.android.car.CarService";

    private static final long CAR_SERVICE_BIND_RETRY_INTERVAL_MS = 500;
    private static final long CAR_SERVICE_BIND_MAX_RETRY = 20;

    private final Context mContext;
    @GuardedBy("this")
    private ICar mService;
    private final boolean mOwnsService;
    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;
    @GuardedBy("this")
    private int mConnectionState;
    @GuardedBy("this")
    private int mConnectionRetryCount;

    private final Runnable mConnectionRetryRunnable = new Runnable() {
        @Override
        public void run() {
            startCarService();
        }
    };

    private final Runnable mConnectionRetryFailedRunnable = new Runnable() {
        @Override
        public void run() {
            mServiceConnectionListener.onServiceDisconnected(new ComponentName(CAR_SERVICE_PACKAGE,
                    CAR_SERVICE_CLASS));
        }
    };

    private final ServiceConnection mServiceConnectionListener =
            new ServiceConnection () {
        public void onServiceConnected(ComponentName name, IBinder service) {
            synchronized (Car.this) {
                mService = ICar.Stub.asInterface(service);
                mConnectionState = STATE_CONNECTED;
            }
            mServiceConnectionListenerClient.onServiceConnected(name, service);
        }

        public void onServiceDisconnected(ComponentName name) {
            synchronized (Car.this) {
                mService = null;
                if (mConnectionState  == STATE_DISCONNECTED) {
                    return;
                }
                mConnectionState = STATE_DISCONNECTED;
            }
            // unbind explicitly here.
            disconnect();
            mServiceConnectionListenerClient.onServiceDisconnected(name);
        }
    };

    private final ServiceConnection mServiceConnectionListenerClient;
    private final Object mCarManagerLock = new Object();
    @GuardedBy("mCarManagerLock")
    private final HashMap<String, CarManagerBase> mServiceMap = new HashMap<>();

    /** Handler for generic event dispatching. */
    private final Handler mEventHandler;

    private final Handler mMainThreadEventHandler;

    /**
     * A factory method that creates Car instance for all Car API access.
     * @param context
     * @param serviceConnectionListener listener for monitoring service connection.
     * @param handler the handler on which the callback should execute, or null to execute on the
     * service's main thread. Note: the service connection listener will be always on the main
     * thread regardless of the handler given.
     * @return Car instance if system is in car environment and returns {@code null} otherwise.
     */
    public static Car createCar(Context context, ServiceConnection serviceConnectionListener,
            @Nullable Handler handler) {
        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            Log.e(CarLibLog.TAG_CAR, "FEATURE_AUTOMOTIVE not declared while android.car is used");
            return null;
        }
        try {
          return new Car(context, serviceConnectionListener, handler);
        } catch (IllegalArgumentException e) {
          // Expected when car service loader is not available.
        }
        return null;
    }

    /**
     * A factory method that creates Car instance for all Car API access using main thread {@code
     * Looper}.
     *
     * @see #createCar(Context, ServiceConnection, Handler)
     */
    public static Car createCar(Context context, ServiceConnection serviceConnectionListener) {
      return createCar(context, serviceConnectionListener, null);
    }

    private Car(Context context, ServiceConnection serviceConnectionListener,
            @Nullable Handler handler) {
        mContext = context;
        mEventHandler = determineEventHandler(handler);
        mMainThreadEventHandler = determineMainThreadEventHandler(mEventHandler);

        mService = null;
        mOwnsService = true;
        mServiceConnectionListenerClient = serviceConnectionListener;
    }


    /**
     * Car constructor when ICar binder is already available.
     * @hide
     */
    public Car(Context context, ICar service, @Nullable Handler handler) {
        mContext = context;
        mEventHandler = determineEventHandler(handler);
        mMainThreadEventHandler = determineMainThreadEventHandler(mEventHandler);

        mService = service;
        mOwnsService = false;
        mConnectionState = STATE_CONNECTED;
        mServiceConnectionListenerClient = null;
    }

    private static Handler determineMainThreadEventHandler(Handler eventHandler) {
        Looper mainLooper = Looper.getMainLooper();
        return (eventHandler.getLooper() == mainLooper) ? eventHandler : new Handler(mainLooper);
    }

    private static Handler determineEventHandler(@Nullable Handler handler) {
        if (handler == null) {
            Looper looper = Looper.getMainLooper();
            handler = new Handler(looper);
        }
        return handler;
    }

    /**
     * Connect to car service. This can be called while it is disconnected.
     * @throws IllegalStateException If connection is still on-going from previous
     *         connect call or it is already connected
     */
    public void connect() throws IllegalStateException {
        synchronized (this) {
            if (mConnectionState != STATE_DISCONNECTED) {
                throw new IllegalStateException("already connected or connecting");
            }
            mConnectionState = STATE_CONNECTING;
            startCarService();
        }
    }

    /**
     * Disconnect from car service. This can be called while disconnected. Once disconnect is
     * called, all Car*Managers from this instance becomes invalid, and
     * {@link Car#getCarManager(String)} will return different instance if it is connected again.
     */
    public void disconnect() {
        synchronized (this) {
            if (mConnectionState == STATE_DISCONNECTED) {
                return;
            }
            mEventHandler.removeCallbacks(mConnectionRetryRunnable);
            mMainThreadEventHandler.removeCallbacks(mConnectionRetryFailedRunnable);
            mConnectionRetryCount = 0;
            tearDownCarManagers();
            mService = null;
            mConnectionState = STATE_DISCONNECTED;

            if (mOwnsService) {
                mContext.unbindService(mServiceConnectionListener);
            }
        }
    }

    /**
     * Tells if it is connected to the service or not. This will return false if it is still
     * connecting.
     * @return
     */
    public boolean isConnected() {
        synchronized (this) {
            return mService != null;
        }
    }

    /**
     * Tells if this instance is already connecting to car service or not.
     * @return
     */
    public boolean isConnecting() {
        synchronized (this) {
            return mConnectionState == STATE_CONNECTING;
        }
    }

    /**
     * Get car specific service as in {@link Context#getSystemService(String)}. Returned
     * {@link Object} should be type-casted to the desired service.
     * For example, to get sensor service,
     * SensorManagerService sensorManagerService = car.getCarManager(Car.SENSOR_SERVICE);
     * @param serviceName Name of service that should be created like {@link #SENSOR_SERVICE}.
     * @return Matching service manager or null if there is no such service.
     * @throws CarNotConnectedException if the connection to the car service has been lost.
     */
    public Object getCarManager(String serviceName) throws CarNotConnectedException {
        CarManagerBase manager;
        ICar service = getICarOrThrow();
        synchronized (mCarManagerLock) {
            manager = mServiceMap.get(serviceName);
            if (manager == null) {
                try {
                    IBinder binder = service.getCarService(serviceName);
                    if (binder == null) {
                        Log.w(CarLibLog.TAG_CAR, "getCarManager could not get binder for service:" +
                                serviceName);
                        return null;
                    }
                    manager = createCarManager(serviceName, binder);
                    if (manager == null) {
                        Log.w(CarLibLog.TAG_CAR,
                                "getCarManager could not create manager for service:" +
                                        serviceName);
                        return null;
                    }
                    mServiceMap.put(serviceName, manager);
                } catch (RemoteException e) {
                    handleRemoteException(e);
                }
            }
        }
        return manager;
    }

    /**
     * Return the type of currently connected car.
     * @return
     */
    @ConnectionType
    public int getCarConnectionType() {
        return CONNECTION_TYPE_EMBEDDED;
    }

    /**
     * IllegalStateException from XyzCarService with special message is re-thrown as a different
     * exception. If the IllegalStateException is not understood then this message will throw the
     * original exception.
     *
     * @param e exception from XyzCarService.
     * @throws CarNotConnectedException if the connection to the car service has been lost.
     * @hide
     */
    public static void checkCarNotConnectedExceptionFromCarService(
            IllegalStateException e) throws CarNotConnectedException, IllegalStateException {
        String message = e.getMessage();
        if (CAR_NOT_CONNECTED_EXCEPTION_MSG.equals(message)) {
            throw new CarNotConnectedException();
        } else {
            throw e;
        }
    }

    /** @hide */
    public static void hideCarNotConnectedExceptionFromCarService(
            IllegalStateException e) throws IllegalStateException {
        String message = e.getMessage();
        if (CAR_NOT_CONNECTED_EXCEPTION_MSG.equals(message)) {
            return; //ignore
        } else {
            throw e;
        }
    }

    private CarManagerBase createCarManager(String serviceName, IBinder binder)
            throws CarNotConnectedException {
        CarManagerBase manager = null;
        switch (serviceName) {
            case AUDIO_SERVICE:
                manager = new CarAudioManager(binder, mContext, mEventHandler);
                break;
            case SENSOR_SERVICE:
                manager = new CarSensorManager(binder, mContext, mEventHandler);
                break;
            case INFO_SERVICE:
                manager = new CarInfoManager(binder);
                break;
            case APP_FOCUS_SERVICE:
                manager = new CarAppFocusManager(binder, mEventHandler);
                break;
            case PACKAGE_SERVICE:
                manager = new CarPackageManager(binder, mContext);
                break;
            case CAR_NAVIGATION_SERVICE:
                manager = new CarNavigationStatusManager(binder);
                break;
            case CABIN_SERVICE:
                manager = new CarCabinManager(binder, mContext, mEventHandler);
                break;
            case DIAGNOSTIC_SERVICE:
                manager = new CarDiagnosticManager(binder, mContext, mEventHandler);
                break;
            case HVAC_SERVICE:
                manager = new CarHvacManager(binder, mContext, mEventHandler);
                break;
            case PROJECTION_SERVICE:
                manager = new CarProjectionManager(binder, mEventHandler);
                break;
            case RADIO_SERVICE:
                manager = new CarRadioManager(binder, mEventHandler);
                break;
            case VENDOR_EXTENSION_SERVICE:
                manager = new CarVendorExtensionManager(binder, mEventHandler);
                break;
            case CAR_INSTRUMENT_CLUSTER_SERVICE:
                manager = new CarInstrumentClusterManager(binder, mEventHandler);
                break;
            case TEST_SERVICE:
                /* CarTestManager exist in static library. So instead of constructing it here,
                 * only pass binder wrapper so that CarTestManager can be constructed outside. */
                manager = new CarTestManagerBinderWrapper(binder);
                break;
            case BLUETOOTH_SERVICE:
                manager = new CarBluetoothManager(binder, mContext);
        }
        return manager;
    }

    private void startCarService() {
        Intent intent = new Intent();
        intent.setPackage(CAR_SERVICE_PACKAGE);
        intent.setAction(Car.CAR_SERVICE_INTERFACE_NAME);
        boolean bound = mContext.bindServiceAsUser(intent, mServiceConnectionListener,
                Context.BIND_AUTO_CREATE, UserHandle.CURRENT_OR_SELF);
        if (!bound) {
            mConnectionRetryCount++;
            if (mConnectionRetryCount > CAR_SERVICE_BIND_MAX_RETRY) {
                Log.w(CarLibLog.TAG_CAR, "cannot bind to car service after max retry");
                mMainThreadEventHandler.post(mConnectionRetryFailedRunnable);
            } else {
                mEventHandler.postDelayed(mConnectionRetryRunnable,
                        CAR_SERVICE_BIND_RETRY_INTERVAL_MS);
            }
        } else {
            mConnectionRetryCount = 0;
        }
    }

    private synchronized ICar getICarOrThrow() throws IllegalStateException {
        if (mService == null) {
            throw new IllegalStateException("not connected");
        }
        return mService;
    }

    private void handleRemoteException(RemoteException e) {
        Log.w(CarLibLog.TAG_CAR, "RemoteException", e);
        disconnect();
    }

    private void tearDownCarManagers() {
        synchronized (mCarManagerLock) {
            for (CarManagerBase manager: mServiceMap.values()) {
                manager.onCarDisconnected();
            }
            mServiceMap.clear();
        }
    }
}
