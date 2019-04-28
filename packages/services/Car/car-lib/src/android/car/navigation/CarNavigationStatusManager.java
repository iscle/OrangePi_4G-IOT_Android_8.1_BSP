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
package android.car.navigation;

import android.annotation.IntDef;
import android.car.CarApiUtil;
import android.car.CarLibLog;
import android.car.CarManagerBase;
import android.car.CarNotConnectedException;
import android.car.cluster.renderer.IInstrumentClusterNavigation;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * API for providing navigation status for instrument cluster.
 * @hide
 */
public final class CarNavigationStatusManager implements CarManagerBase {

    /** Navigation status */
    public static final int STATUS_UNAVAILABLE = 0;
    public static final int STATUS_ACTIVE = 1;
    public static final int STATUS_INACTIVE = 2;

    /** @hide */
    @IntDef({
        STATUS_UNAVAILABLE,
        STATUS_ACTIVE,
        STATUS_INACTIVE
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Status {}

    /* Turn Types */
    /** Turn is of an unknown type.*/
    public static final int TURN_UNKNOWN = 0;
    /** Starting point of the navigation. */
    public static final int TURN_DEPART = 1;
    /** No turn, but the street name changes. */
    public static final int TURN_NAME_CHANGE = 2;
    /** Slight turn. */
    public static final int TURN_SLIGHT_TURN = 3;
    /** Regular turn. */
    public static final int TURN_TURN = 4;
    /** Sharp turn. */
    public static final int TURN_SHARP_TURN = 5;
    /** U-turn. */
    public static final int TURN_U_TURN = 6;
    /** On ramp. */
    public static final int TURN_ON_RAMP = 7;
    /** Off ramp. */
    public static final int TURN_OFF_RAMP = 8;
    /** Road forks (diverges). */
    public static final int TURN_FORK = 9;
    /** Road merges. */
    public static final int TURN_MERGE = 10;
    /** Roundabout entrance on which the route ends. Instruction says "Enter roundabout". */
    public static final int TURN_ROUNDABOUT_ENTER = 11;
    /** Roundabout exit. */
    public static final int TURN_ROUNDABOUT_EXIT = 12;
    /**
     * Roundabout entrance and exit. For example, "At the roundabout, take Nth exit." Be sure to
     * specify the "turnNumber" parameter when using this type.
     */
    public static final int TURN_ROUNDABOUT_ENTER_AND_EXIT = 13;
    /** Potentially confusing intersection where the user should steer straight. */
    public static final int TURN_STRAIGHT = 14;
    /** You're on a boat! */
    public static final int TURN_FERRY_BOAT = 16;
    /** Train ferries for vehicles. */
    public static final int TURN_FERRY_TRAIN = 17;
    /** You have arrived. */
    public static final int TURN_DESTINATION = 19;

    /** @hide */
    @IntDef({
        TURN_UNKNOWN,
        TURN_DEPART,
        TURN_NAME_CHANGE,
        TURN_SLIGHT_TURN,
        TURN_TURN,
        TURN_SHARP_TURN,
        TURN_U_TURN,
        TURN_ON_RAMP,
        TURN_OFF_RAMP,
        TURN_FORK,
        TURN_MERGE,
        TURN_ROUNDABOUT_ENTER,
        TURN_ROUNDABOUT_EXIT,
        TURN_ROUNDABOUT_ENTER_AND_EXIT,
        TURN_STRAIGHT,
        TURN_FERRY_BOAT,
        TURN_FERRY_TRAIN,
        TURN_DESTINATION
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface TurnEvent {}

    /**
     * Event type that holds information about next maneuver.
     * @hide
     */
    public static final int EVENT_TYPE_NEXT_MANEUVER_INFO = 1;
    /**
     * Event type that holds information regarding distance/time to the next maneuver.
     * @hide
     */
    public static final int EVENT_TYPE_NEXT_MANEUVER_COUNTDOWN = 2;
    /**
     * All custom (vendor-specific) event types should be equal or greater than this constant.
     * @hide
     */
    public static final int EVENT_TYPE_VENDOR_FIRST = 1024;

    /* Turn Side */
    /** Turn is on the left side of the vehicle. */
    public static final int TURN_SIDE_LEFT = 1;
    /** Turn is on the right side of the vehicle. */
    public static final int TURN_SIDE_RIGHT = 2;
    /** Turn side is unspecified. */
    public static final int TURN_SIDE_UNSPECIFIED = 3;

    /** @hide */
    @IntDef({
        TURN_SIDE_LEFT,
        TURN_SIDE_RIGHT,
        TURN_SIDE_UNSPECIFIED
    })
    public @interface TurnSide {}

    private static final int START = 1;
    private static final int STOP = 2;

    /**
     * Distance units for use in {@link #sendNavigationTurnDistanceEvent(int, int, int, int)}.
     */
    /** Distance is specified in meters. */
    public static final int DISTANCE_METERS = 1;
    /** Distance is specified in kilometers. */
    public static final int DISTANCE_KILOMETERS = 2;
    /** Distance is specified in miles. */
    public static final int DISTANCE_MILES = 3;
    /** Distance is specified in feet. */
    public static final int DISTANCE_FEET = 4;
    /** Distance is specified in yards. */
    public static final int DISTANCE_YARDS = 5;

    /** @hide */
    @IntDef({
        DISTANCE_METERS,
        DISTANCE_KILOMETERS,
        DISTANCE_MILES,
        DISTANCE_FEET,
        DISTANCE_YARDS
    })
    public @interface DistanceUnit {}

    private static final String TAG = CarLibLog.TAG_NAV;

    private final IInstrumentClusterNavigation mService;


    /**
     * Only for CarServiceLoader
     * @hide
     */
    public CarNavigationStatusManager(IBinder service) {
        mService = IInstrumentClusterNavigation.Stub.asInterface(service);
    }

    /**
     * @param status new instrument cluster navigation status.
     * @throws CarNotConnectedException if the connection to the car service has been lost.
     */
    public void sendNavigationStatus(@Status int status) throws CarNotConnectedException {
        try {
            if (status == STATUS_ACTIVE) {
                mService.onStartNavigation();
            } else {
                mService.onStopNavigation();
            }
        } catch (IllegalStateException e) {
            CarApiUtil.checkCarNotConnectedExceptionFromCarService(e);
        } catch (RemoteException e) {
            handleCarServiceRemoteExceptionAndThrow(e);
        }
    }

    /**
     * Sends a Navigation Next Step event to the car.
     * <p>
     * Note: For an example of a roundabout: if a roundabout has 4 exits, spaced evenly, then the
     * first exit will have turnNumber=1, turnAngle=90; the second will have turnNumber=2,
     * turnAngle=180; the third will have turnNumber=3, turnAngle=270.  turnNumber and turnAngle are
     * counted in the direction of travel around the roundabout (clockwise for roads where the car
     * drives on the left-hand side of the road, such as Australia; anti-clockwise for roads where
     * the car drives on the right, such as the USA).
     *
     * @param turnEvent turn event like ({@link #TURN_TURN}, {@link #TURN_U_TURN},
     *        {@link #TURN_ROUNDABOUT_ENTER_AND_EXIT}, etc).
     * @param eventName Name of the turn event like road name to turn. For example "Charleston road"
     *        in "Turn right to Charleston road"
     * @param turnAngle turn angle in degrees between the roundabout entry and exit (0..359).  Only
     *        used for event type {@link #TURN_ROUNDABOUT_ENTER_AND_EXIT}.  -1 if unused.
     * @param turnNumber turn number, counting around from the roundabout entry to the exit.  Only
     *        used for event type {@link #TURN_ROUNDABOUT_ENTER_AND_EXIT}.  -1 if unused.
     * @param image image to be shown in the instrument cluster.  Null if instrument
     *        cluster type doesn't support images.
     * @param turnSide turn side ({@link #TURN_SIDE_LEFT}, {@link #TURN_SIDE_RIGHT} or
     *        {@link #TURN_SIDE_UNSPECIFIED}).
     * @throws CarNotConnectedException if the connection to the car service has been lost.
     *
     * @deprecated Use {@link #sendEvent(int, Bundle)} instead.
     */
    public void sendNavigationTurnEvent(@TurnEvent int turnEvent, CharSequence eventName,
            int turnAngle, int turnNumber, Bitmap image, @TurnSide int turnSide)
                    throws CarNotConnectedException {
        try {
            mService.onNextManeuverChanged(turnEvent, eventName, turnAngle, turnNumber, image,
                    turnSide);
        } catch (IllegalStateException e) {
            CarApiUtil.checkCarNotConnectedExceptionFromCarService(e);
        } catch (RemoteException e) {
            handleCarServiceRemoteExceptionAndThrow(e);
        }
    }

    /**
     * Sends a Navigation Next Step Distance event to the car.
     *
     * @param distanceMeters Distance to next event in meters.
     * @param timeSeconds Time to next event in seconds.
     * @param displayDistanceMillis Distance to the next event. This is exactly the same distance
     * that navigation app is displaying. Use it when you want to display distance, it has
     * appropriate rounding function and units are in sync with navigation app. This parameter is
     * in {@code displayDistanceUnit * 1000}.
     * @param displayDistanceUnit units for {@param displayDistanceMillis} param.
     * See {@link DistanceUnit} for acceptable values.
     * @throws CarNotConnectedException if the connection to the car service has been lost.
     *
     * @deprecated Use {@link #sendEvent(int, Bundle)} instead.
     */
    public void sendNavigationTurnDistanceEvent(int distanceMeters, int timeSeconds,
            int displayDistanceMillis, @DistanceUnit int displayDistanceUnit)
            throws CarNotConnectedException {
        try {
            mService.onNextManeuverDistanceChanged(distanceMeters, timeSeconds,
                    displayDistanceMillis, displayDistanceUnit);
        } catch (IllegalStateException e) {
            CarApiUtil.checkCarNotConnectedExceptionFromCarService(e);
        } catch (RemoteException e) {
            handleCarServiceRemoteExceptionAndThrow(e);
        }
    }

    /**
     * Sends events from navigation app to instrument cluster.
     *
     * @param eventType event type
     * @param bundle object that holds data about the event
     * @throws CarNotConnectedException if the connection to the car service has been lost.
     *
     * @see #EVENT_TYPE_NEXT_MANEUVER_INFO
     * @see #EVENT_TYPE_NEXT_MANEUVER_COUNTDOWN
     *
     * @hide
     */
    public void sendEvent(int eventType, Bundle bundle) throws CarNotConnectedException {
        try {
            mService.onEvent(eventType, bundle);
        } catch (IllegalStateException e) {
            CarApiUtil.checkCarNotConnectedExceptionFromCarService(e);
        } catch (RemoteException e) {
            handleCarServiceRemoteExceptionAndThrow(e);
        }
    }

    @Override
    public void onCarDisconnected() {
        Log.d(TAG, "onCarDisconnected");
    }

    /** Returns navigation features of instrument cluster */
    public CarNavigationInstrumentCluster getInstrumentClusterInfo()
            throws CarNotConnectedException {
        try {
            return mService.getInstrumentClusterInfo();
        } catch (RemoteException e) {
            handleCarServiceRemoteExceptionAndThrow(e);
        }
        return null;
    }

    private void handleCarServiceRemoteExceptionAndThrow(RemoteException e)
            throws CarNotConnectedException {
        handleCarServiceRemoteException(e);
        throw new CarNotConnectedException();
    }

    private void handleCarServiceRemoteException(RemoteException e) {
        Log.w(TAG, "RemoteException from car service:" + e.getMessage());
        // nothing to do for now
    }
}
