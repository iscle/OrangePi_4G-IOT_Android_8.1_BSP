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
package android.car.apitest;

import android.car.Car;
import android.car.CarApiUtil;
import android.car.CarNotConnectedException;
import android.car.settings.CarSettings;

import junit.framework.TestCase;

public class CarApiUtilTest extends TestCase {

    public void testDecodeGarageTimeSetting() {
        String time = "11:20";
        int[] result = CarApiUtil.decodeGarageTimeSetting(time);
        assertEquals(11, result[0]);
        assertEquals(20, result[1]);

        time = "23:59";
        result = CarApiUtil.decodeGarageTimeSetting(time);
        assertEquals(23, result[0]);
        assertEquals(59, result[1]);

        time = null;
        assertEquals(CarSettings.DEFAULT_GARAGE_MODE_WAKE_UP_TIME,
                CarApiUtil.decodeGarageTimeSetting(time));

        time = "25:10";
        result = CarApiUtil.decodeGarageTimeSetting(time);
        assertEquals(CarSettings.DEFAULT_GARAGE_MODE_WAKE_UP_TIME, result);

        time = "12:99";
        result = CarApiUtil.decodeGarageTimeSetting(time);
        assertEquals(CarSettings.DEFAULT_GARAGE_MODE_WAKE_UP_TIME, result);

        time= "hour:min";
        result = CarApiUtil.decodeGarageTimeSetting(time);
        assertEquals(CarSettings.DEFAULT_GARAGE_MODE_WAKE_UP_TIME, result);
    }

    public void testEncodeGarageModeTime() {
        assertTrue(CarApiUtil.encodeGarageTimeSetting(0, 0).equals("0:0"));
        assertTrue(CarApiUtil.encodeGarageTimeSetting(10, 0).equals("10:0"));
        assertTrue(CarApiUtil.encodeGarageTimeSetting(23, 59).equals("23:59"));
    }

    public void testCheckCarNotConnectedExceptionFromCarService() {
        IllegalStateException e = new IllegalStateException(Car.CAR_NOT_CONNECTED_EXCEPTION_MSG);
        Exception resultException = null;
        try {
            CarApiUtil.checkCarNotConnectedExceptionFromCarService(e);
        } catch (Exception exception) {
            resultException = exception;
        }
        assertTrue(resultException instanceof CarNotConnectedException);

        e = new IllegalStateException("Hello");
        resultException = null;
        try {
            CarApiUtil.checkCarNotConnectedExceptionFromCarService(e);
        } catch (Exception exception) {
            resultException = exception;
        }
        assertEquals(e, resultException);
    }
}
