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
import android.os.Looper;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.car.content.pm.CarPackageManager;
import android.support.car.hardware.CarSensorManager;
import android.support.car.media.CarAudioManager;
import android.support.car.navigation.CarNavigationStatusManager;
import android.util.Log;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Top-level car API that provides access to all car services and data available in the platform.
 * <p/>
 * Use one of the createCar methods to create a new instance of the Car api.  The
 * {@link CarConnectionCallback} will respond with an {@link CarConnectionCallback#onConnected(Car)}
 * or {@link CarConnectionCallback#onDisconnected(Car)} message.  Nothing can be done with the
 * car until onConnected is called.  When the car disconnects then reconnects you may still use
 * the Car object but any manages retrieved from it should be considered invalid and will need to
 * be retried. Also, you must call {@link #disconnect} before an instance of Car goes out of scope
 * to avoid leaking resources.
 *
 * <p/>
 * Once connected, {@link #getCarManager(String)} or {@link #getCarManager(Class)} can be used to
 * retrieve a manager.  This is patterned after how one would retrieve a service from
 * {@link Context#getSystemService(String)} or {@link Context#getSystemService(Class)}.  Once
 * again if the car is disconnected you'll want to get new versions of these managers.
 */
public class Car {

    private static final String TAG = "CAR.SUPPORT.LIB.CAR";
    /**
     * Service name for {@link CarSensorManager}, to be used in {@link #getCarManager(String)}.
     */
    public static final String SENSOR_SERVICE = "sensor";

    /**
     * Service name for {@link CarInfoManager}, to be used in {@link #getCarManager(String)}.
     */
    public static final String INFO_SERVICE = "info";

    /**
     * Service name for {@link CarAppFocusManager}.
     */
    public static final String APP_FOCUS_SERVICE = "app_focus";

    /**
     * Service name for {@link CarPackageManager}.
     * @hide
     */
    public static final String PACKAGE_SERVICE = "package";

    /**
     * Service name for {@link CarAudioManager}.
     */
    public static final String AUDIO_SERVICE = "audio";
    /**
     * Service name for {@link CarNavigationStatusManager}.
     * @hide
     */
    public static final String CAR_NAVIGATION_SERVICE = "car_navigation_service";
    /**
     * Service name for {@link CarNavigationStatusManager}.
     */
    public static final String NAVIGATION_STATUS_SERVICE = "car_navigation_service";

    // TODO(jthol) move into a more robust registry implementation
    private static final Map<Class, String> CLASS_TO_SERVICE_NAME;
    static{
        Map<Class, String> mapping = new HashMap<>();
        mapping.put(CarSensorManager.class, SENSOR_SERVICE);
        mapping.put(CarInfoManager.class, INFO_SERVICE);
        mapping.put(CarAppFocusManager.class, APP_FOCUS_SERVICE);
        mapping.put(CarPackageManager.class, PACKAGE_SERVICE);
        mapping.put(CarAudioManager.class, AUDIO_SERVICE);
        mapping.put(CarNavigationStatusManager.class, NAVIGATION_STATUS_SERVICE);

        CLASS_TO_SERVICE_NAME = Collections.unmodifiableMap(mapping);
    }


    /**
     * Type of car connection: car emulator, no physical connection.
     * @hide
     */
    public static final int CONNECTION_TYPE_EMULATOR = 0;
    /**
     * Type of car connection: connected to a car via USB.
     * @hide
     */
    public static final int CONNECTION_TYPE_USB = 1;
    /**
     * Type of car connection: connected to a car via Wi-Fi.
     * @hide
     */
    public static final int CONNECTION_TYPE_WIFI = 2;
    /**
     * Type of car connection: on-device car emulator, for development (such as Local Head Unit).
     * @hide
     */
    public static final int CONNECTION_TYPE_ON_DEVICE_EMULATOR = 3;
    /**
     * Type of car connection: car emulator, connected over ADB (such as Desktop Head Unit).
     * @hide
     */
    public static final int CONNECTION_TYPE_ADB_EMULATOR = 4;
    /**
     * Type of car connection: platform runs directly in car.
     * @hide
     */
    public static final int CONNECTION_TYPE_EMBEDDED = 5;

    /**
     * Unknown type (the support lib is likely out-of-date).
     * @hide
     */
    public static final int CONNECTION_TYPE_UNKNOWN = -1;

    private static final Set<Integer> CONNECTION_TYPES = new HashSet<>();
    static {
        CONNECTION_TYPES.add(CONNECTION_TYPE_ADB_EMULATOR);
        CONNECTION_TYPES.add(CONNECTION_TYPE_USB);
        CONNECTION_TYPES.add(CONNECTION_TYPE_WIFI);
        CONNECTION_TYPES.add(CONNECTION_TYPE_ON_DEVICE_EMULATOR);
        CONNECTION_TYPES.add(CONNECTION_TYPE_ADB_EMULATOR);
        CONNECTION_TYPES.add(CONNECTION_TYPE_EMBEDDED);
    }

    /** @hide */
    @IntDef({CONNECTION_TYPE_EMULATOR, CONNECTION_TYPE_USB, CONNECTION_TYPE_WIFI,
            CONNECTION_TYPE_ON_DEVICE_EMULATOR, CONNECTION_TYPE_ADB_EMULATOR,
            CONNECTION_TYPE_EMBEDDED, CONNECTION_TYPE_UNKNOWN})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ConnectionType {
    }

    /**
     * Permission necessary to access car mileage information.
     * @hide
     */
    public static final String PERMISSION_MILEAGE = "android.car.permission.CAR_MILEAGE";
    /**
     * Permission necessary to access car fuel level.
     * @hide
     */
    public static final String PERMISSION_FUEL = "android.car.permission.CAR_FUEL";
    /**
     * Permission necessary to access car speed.
     * @hide
     */
    public static final String PERMISSION_SPEED = "android.car.permission.CAR_SPEED";
    /**
     * Permission necessary to access car dynamics state.
     * @hide
     */
    public static final String PERMISSION_VEHICLE_DYNAMICS_STATE =
            "android.car.permission.VEHICLE_DYNAMICS_STATE";
    /**
     * Permission necessary to access a car-specific communication channel.
     */
    public static final String PERMISSION_VENDOR_EXTENSION =
            "android.car.permission.CAR_VENDOR_EXTENSION";
    /**
     * Permission necessary to use {@link android.car.navigation.CarNavigationStatusManager}.
     */
    public static final String PERMISSION_CAR_NAVIGATION_MANAGER =
            "android.car.permission.PERMISSION_CAR_NAVIGATION_MANAGER";


    /**
     * PackageManager.FEATURE_AUTOMOTIVE from M. But redefine here to support L.
     */
    private static final String FEATURE_AUTOMOTIVE = "android.hardware.type.automotive";

    /**
     * {@link CarServiceLoader} implementation for projected mode. Available only when the
     * projected client library is linked.
     */
    private static final String PROJECTED_CAR_SERVICE_LOADER =
            "com.google.android.apps.auto.sdk.service.CarServiceLoaderGms";
    /**
     * Permission necessary to change car audio volume through {@link CarAudioManager}.
     * @hide
     */
    public static final String PERMISSION_CAR_CONTROL_AUDIO_VOLUME =
            "android.car.permission.CAR_CONTROL_AUDIO_VOLUME";

    private final Context mContext;
    private final Handler mEventHandler;
    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;
    // @GuardedBy("this")
    private int mConnectionState;

    private final CarServiceLoader.CarConnectionCallbackProxy mCarConnectionCallbackProxy =
            new CarServiceLoader.CarConnectionCallbackProxy() {
                @Override
                public void onConnected() {
                    synchronized (Car.this) {
                        mConnectionState = STATE_CONNECTED;
                    }
                    mCarConnectionCallback.onConnected(Car.this);
                }

                @Override
                public void onDisconnected() {
                    synchronized (Car.this) {
                        if (mConnectionState == STATE_DISCONNECTED) {
                            return;
                        }
                        mConnectionState = STATE_DISCONNECTED;
                    }
                    mCarConnectionCallback.onDisconnected(Car.this);
                }
            };

    private final CarConnectionCallback mCarConnectionCallback;
    private final Object mCarManagerLock = new Object();
    //@GuardedBy("mCarManagerLock")
    private final HashMap<String, CarManagerBase> mServiceMap = new HashMap<>();
    private final CarServiceLoader mCarServiceLoader;


    /**
     * A factory method that creates a Car instance with the given {@code Looper}.
     *
     * @param context The current app context.
     * @param carConnectionCallback Receives information when the Car Service is started and
     * stopped.
     * @param handler The handler on which the callback should execute, or null to execute on the
     * service's main thread. Note the service connection listener is always on the main
     * thread regardless of the handler given.
     * @return Car instance if system is in car environment; returns {@code null} otherwise.
     */
    public static Car createCar(Context context,
            CarConnectionCallback carConnectionCallback, @Nullable Handler handler) {
        try {
            return new Car(context, carConnectionCallback, handler);
        } catch (IllegalArgumentException e) {
            // Expected when Car Service loader is not available.
            Log.w(TAG, "Car failed to be created", e);
        }
        return null;
    }

    /**
     * A factory method that creates Car instance using the main thread {@link Handler}.
     *
     * @see #createCar(Context, CarConnectionCallback, Handler)
     */
    public static Car createCar(Context context,
            CarConnectionCallback carConnectionCallback) {
        return createCar(context, carConnectionCallback, null);
    }

    private Car(Context context, CarConnectionCallback carConnectionCallback,
            @Nullable Handler handler) {
        mContext = context;
        mCarConnectionCallback = carConnectionCallback;
        if (handler == null) {
            Looper looper = Looper.getMainLooper();
            handler = new Handler(looper);
        }
        mEventHandler = handler;

        if (mContext.getPackageManager().hasSystemFeature(FEATURE_AUTOMOTIVE)) {
            mCarServiceLoader =
                    new CarServiceLoaderEmbedded(context, mCarConnectionCallbackProxy,
                            mEventHandler);
        } else {
            mCarServiceLoader = loadCarServiceLoader(PROJECTED_CAR_SERVICE_LOADER, context,
                    mCarConnectionCallbackProxy, mEventHandler);
        }
    }

    private CarServiceLoader loadCarServiceLoader(String carServiceLoaderClassName, Context context,
            CarServiceLoader.CarConnectionCallbackProxy carConnectionCallbackProxy,
            Handler eventHandler) throws IllegalArgumentException {
        Class<? extends CarServiceLoader> carServiceLoaderClass = null;
        try {
            carServiceLoaderClass =
                    Class.forName(carServiceLoaderClassName).asSubclass(CarServiceLoader.class);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(
                    "Cannot find CarServiceLoader implementation:" + carServiceLoaderClassName, e);
        }
        Constructor<? extends CarServiceLoader> ctor;
        try {
            ctor = carServiceLoaderClass.getDeclaredConstructor(Context.class,
                    CarServiceLoader.CarConnectionCallbackProxy.class, Handler.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Cannot construct CarServiceLoader, no constructor: "
                    + carServiceLoaderClassName, e);
        }
        try {
            return ctor.newInstance(context, carConnectionCallbackProxy, eventHandler);
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                | InvocationTargetException e) {
            throw new IllegalArgumentException(
                    "Cannot construct CarServiceLoader, constructor failed for "
                            + carServiceLoaderClass.getName(), e);
        }
    }

    /**
     * Car constructor when CarServiceLoader is already available.
     *
     * @param serviceLoader must be non-null and connected or {@link CarNotConnectedException} will
     * be thrown.
     * @hide
     */
    public Car(@NonNull CarServiceLoader serviceLoader) throws CarNotConnectedException {
        if (!serviceLoader.isConnected()) {
            throw new CarNotConnectedException();
        }
        mCarServiceLoader = serviceLoader;
        mEventHandler = serviceLoader.getEventHandler();
        mContext = serviceLoader.getContext();

        mConnectionState = STATE_CONNECTED;
        mCarConnectionCallback = null;
    }

    /**
     * Connect to Car Service. Can be called while disconnected.
     *
     * @throws IllegalStateException if the car is connected or still trying to connect
     * from previous calls.
     */
    public void connect() throws IllegalStateException {
        synchronized (this) {
            if (mConnectionState != STATE_DISCONNECTED) {
                throw new IllegalStateException("already connected or connecting");
            }
            mConnectionState = STATE_CONNECTING;
            mCarServiceLoader.connect();
        }
    }

    /**
     * Disconnect from Car Service. Can be called while disconnected. After disconnect is
     * called, all Car*Managers from this instance become invalid, and {@link
     * Car#getCarManager(String)} returns a different instance if connected again.
     */
    public void disconnect() {
        synchronized (this) {
            tearDownCarManagers();
            mConnectionState = STATE_DISCONNECTED;
            mCarServiceLoader.disconnect();
        }
    }

    /**
     * @return Returns {@code true} if this object is connected to the service; {@code false}
     * otherwise.
     */
    public boolean isConnected() {
        synchronized (this) {
            return mConnectionState == STATE_CONNECTED;
        }
    }

    /**
     * @return Returns {@code true} if this object is still connecting to the service.
     */
    public boolean isConnecting() {
        synchronized (this) {
            return mConnectionState == STATE_CONNECTING;
        }
    }

    /**
     * Get a car-specific manager. This is modeled after {@link Context#getSystemService(String)}.
     * The returned {@link Object} should be type cast to the desired manager. For example,
     * to get the sensor service, use the following:
     * <pre>{@code CarSensorManager sensorManager =
     *     (CarSensorManager) car.getCarManager(Car.SENSOR_SERVICE);}</pre>
     *
     * @param serviceName Name of service to create, for example {@link #SENSOR_SERVICE}.
     * @return The requested service manager or null if the service is not available.
     */
    public Object getCarManager(String serviceName)
            throws CarNotConnectedException {
        Object manager = null;
        synchronized (mCarManagerLock) {
            manager = mServiceMap.get(serviceName);
            if (manager == null) {
                manager = mCarServiceLoader.getCarManager(serviceName);
            }
            // do not store if it is not CarManagerBase. This can happen when system version
            // is retrieved from this call.
            if (manager != null && manager instanceof CarManagerBase) {
                mServiceMap.put(serviceName, (CarManagerBase) manager);
            }
        }
        return manager;
    }

    /**
     * Get a car-specific manager. This is modeled after {@link Context#getSystemService(Class)}.
     * The returned service will be type cast to the desired manager. For example,
     * to get the sensor service, use the following:
     * <pre>{@code CarSensorManager sensorManager = car.getCarManager(CarSensorManager.class);
     * }</pre>
     *
     * @param serviceClass Class: The class of the desired service. For
     * example {@link CarSensorManager}.
     * @return The service or null if the class is not a supported car service.
     */
    public <T> T getCarManager(Class<T> serviceClass) throws CarNotConnectedException {
        // TODO(jthol) port to a more robust registry implementation
        String serviceName = CLASS_TO_SERVICE_NAME.get(serviceClass);
        return (serviceName == null) ? null : (T) getCarManager(serviceName);
    }

    /**
     * Return the type of currently connected car. This should only be used for testing scenarios
     *
     * @return One of {@link #CONNECTION_TYPE_USB}, {@link #CONNECTION_TYPE_WIFI},
     * {@link #CONNECTION_TYPE_EMBEDDED}, {@link #CONNECTION_TYPE_ON_DEVICE_EMULATOR},
     * {@link #CONNECTION_TYPE_ADB_EMULATOR},
     * {@link #CONNECTION_TYPE_UNKNOWN}.
     * @throws CarNotConnectedException if the connection to the car service has been lost.
     * @hide
     */
    @ConnectionType
    public int getCarConnectionType() throws CarNotConnectedException {
        int carConnectionType = mCarServiceLoader.getCarConnectionType();
        if (!CONNECTION_TYPES.contains(carConnectionType)){
            return CONNECTION_TYPE_UNKNOWN;
        }
        return carConnectionType;
    }

    private void tearDownCarManagers() {
        synchronized (mCarManagerLock) {
            for (CarManagerBase manager : mServiceMap.values()) {
                manager.onCarDisconnected();
            }
            mServiceMap.clear();
        }
    }
}
