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

import android.car.settings.CarSettings;

/**
 * Internal helper utilities
 * @hide
 */
public final class CarApiUtil {

    /**
     * CarService throws IllegalStateException with this message is re-thrown as
     * {@link CarNotConnectedException}.
     *
     * @hide
     */
    public static final String CAR_NOT_CONNECTED_EXCEPTION_MSG = "CarNotConnected";

    /**
     * Re-throw IllegalStateException from CarService with
     * {@link #CAR_NOT_CONNECTED_EXCEPTION_MSG} message as {@link CarNotConnectedException}.
     * exception.
     *
     * @param e exception from CarService
     * @throws CarNotConnectedException if the connection to the car service has been lost.
     * @hide
     */
    public static void checkCarNotConnectedExceptionFromCarService(IllegalStateException e)
            throws CarNotConnectedException {
        if (e.getMessage().equals(CAR_NOT_CONNECTED_EXCEPTION_MSG)) {
            throw new CarNotConnectedException();
        } else {
            throw e;
        }
    }

    /** do not use */
    private CarApiUtil() {};

    /**
     * Return an integer array of {hour, minute} from the String presentation of the garage mode
     * time.
     *
     * @hide
     */
    public static int[] decodeGarageTimeSetting(String time) {
        int[] result = CarSettings.DEFAULT_GARAGE_MODE_WAKE_UP_TIME;
        if (time == null) {
            return result;
        }

        String[] tokens = time.split(":");
        if (tokens.length != 2) {
            return result;
        }
        try {
            result[0] = Integer.valueOf(tokens[0]);
            result[1] = Integer.valueOf(tokens[1]);
        } catch (NumberFormatException e) {
            return CarSettings.DEFAULT_GARAGE_MODE_WAKE_UP_TIME;
        }
        if (result[0] >= 0 && result[0] <= 23 && result[1] >= 0 && result[1] <= 59) {
            return result;
        } else {
            return CarSettings.DEFAULT_GARAGE_MODE_WAKE_UP_TIME;
        }
    }

    /**
     * Return a String presentation of the garage mode "hour:minute".
     *
     * @hide
     */
    public static String encodeGarageTimeSetting(int hour, int min) {
        return hour + ":" + min;
    }
}
