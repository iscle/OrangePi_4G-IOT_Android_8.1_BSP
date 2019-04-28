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

package com.android.car.test;

import android.test.suitebuilder.annotation.MediumTest;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

/**
 * Validates that diagnostic constants in CarService and Vehicle HAL have the same value
 * This is an important assumption to validate because we do not perform any mapping between
 * the two layers, instead relying on the constants on both sides having identical values.
 */
@MediumTest
public class CarDiagnosticConstantsTest extends TestCase {
    static final String TAG = CarDiagnosticConstantsTest.class.getSimpleName();

    static class MismatchException extends Exception {
        private static String dumpClass(Class<?> clazz) {
            StringBuilder builder = new StringBuilder(clazz.getName() + "{\n");
            Arrays.stream(clazz.getFields()).forEach((Field field) -> {
                builder.append('\t').append(field.toString()).append('\n');
            });
            return builder.append('}').toString();
        }

        private static void logClasses(Class<?> clazz1, Class<?> clazz2) {
            Log.d(TAG, "MismatchException. class1: " + dumpClass(clazz1));
            Log.d(TAG, "MismatchException. class2: " + dumpClass(clazz2));
        }

        MismatchException(String message) {
            super(message);
        }

        static MismatchException fieldValueMismatch(Class<?> clazz1, Class<?> clazz2, String name,
                int value1, int value2) {
            logClasses(clazz1, clazz2);
            return new MismatchException("In comparison of " + clazz1 + " and " + clazz2 +
                " field " + name  + " had different values " + value1 + " vs. " + value2);
        }

        static MismatchException fieldsOnlyInClass1(Class<?> clazz1, Class<?> clazz2,
                Map<String, Integer> fields) {
            logClasses(clazz1, clazz2);
            return new MismatchException("In comparison of " + clazz1 + " and " + clazz2 +
                " some fields were only found in the first class:\n" +
                fields.keySet().stream().reduce("",
                    (String s, String t) -> s + "\n" + t));
        }

        static MismatchException fieldOnlyInClass2(Class<?> clazz1, Class<?> clazz2, String field) {
            logClasses(clazz1, clazz2);
            return new MismatchException("In comparison of " + clazz1 + " and " + clazz2 +
                " field " + field + " was not found in both classes");
        }
    }

    static boolean isPublicStaticFinalInt(Field field) {
        final int modifiers = field.getModifiers();
        final boolean isPublic = (modifiers & Modifier.PUBLIC) == Modifier.PUBLIC;
        final boolean isStatic = (modifiers & Modifier.STATIC) == Modifier.STATIC;
        final boolean isFinal = (modifiers & Modifier.FINAL) == Modifier.FINAL;
        if (isPublic && isStatic && isFinal) {
            return field.getType() == int.class;
        }
        return false;
    }

    static void validateMatch(Class<?> clazz1, Class<?> clazz2) throws Exception {
        Map<String, Integer> fields = new HashMap<>();

        // add all the fields in the first class to a map
        Arrays.stream(clazz1.getFields()).filter(
            CarDiagnosticConstantsTest::isPublicStaticFinalInt).forEach( (Field field) -> {
                final String name = field.getName();
                try {
                    fields.put(name, field.getInt(null));
                } catch (IllegalAccessException e) {
                    // this will practically never happen because we checked that it is a
                    // public static final field before reading from it
                    Log.wtf(TAG, String.format("attempt to access field %s threw exception",
                        field.toString()), e);
                }
            });

        // check for all fields in the second class, and remove matches from the map
        for (Field field2 : clazz2.getFields()) {
            if (isPublicStaticFinalInt(field2)) {
                final String name = field2.getName();
                if (fields.containsKey(name)) {
                    try {
                        final int value2 = field2.getInt(null);
                        final int value1 = fields.getOrDefault(name, value2+1);
                        if (value2 != value1) {
                            throw MismatchException.fieldValueMismatch(clazz1, clazz2,
                                field2.getName(), value1, value2);
                        }
                        fields.remove(name);
                    } catch (IllegalAccessException e) {
                        // this will practically never happen because we checked that it is a
                        // public static final field before reading from it
                        Log.wtf(TAG, String.format("attempt to access field %s threw exception",
                            field2.toString()), e);
                        throw e;
                    }
                } else {
                    throw MismatchException.fieldOnlyInClass2(clazz1, clazz2, name);
                }
            }
        }

        // if anything is left, we didn't find some fields in the second class
        if (!fields.isEmpty()) {
            throw MismatchException.fieldsOnlyInClass1(clazz1, clazz2, fields);
        }
    }

    public void testFuelSystemStatus() throws Exception {
        validateMatch(android.hardware.automotive.vehicle.V2_0.Obd2FuelSystemStatus.class,
            android.car.diagnostic.CarDiagnosticEvent.FuelSystemStatus.class);
    }

    public void testFuelType() throws Exception {
        validateMatch(android.hardware.automotive.vehicle.V2_0.Obd2FuelType.class,
            android.car.diagnostic.CarDiagnosticEvent.FuelType.class);
    }

    public void testSecondaryAirStatus() throws Exception {
        validateMatch(android.hardware.automotive.vehicle.V2_0.Obd2SecondaryAirStatus.class,
            android.car.diagnostic.CarDiagnosticEvent.SecondaryAirStatus.class);
    }

    public void testIgnitionMonitors() throws Exception {
        validateMatch(android.hardware.automotive.vehicle.V2_0.Obd2CommonIgnitionMonitors.class,
            android.car.diagnostic.CarDiagnosticEvent.CommonIgnitionMonitors.class);

        validateMatch(android.hardware.automotive.vehicle.V2_0.Obd2CompressionIgnitionMonitors.class,
            android.car.diagnostic.CarDiagnosticEvent.CompressionIgnitionMonitors.class);

        validateMatch(android.hardware.automotive.vehicle.V2_0.Obd2SparkIgnitionMonitors.class,
            android.car.diagnostic.CarDiagnosticEvent.SparkIgnitionMonitors.class);
    }
}
