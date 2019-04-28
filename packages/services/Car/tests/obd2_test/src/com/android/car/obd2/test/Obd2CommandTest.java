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

package com.android.car.obd2.test;

import static com.android.car.obd2.test.Utils.concatIntArrays;
import static com.android.car.obd2.test.Utils.stringsToIntArray;
import static org.junit.Assert.*;

import com.android.car.obd2.Obd2Command;
import com.android.car.obd2.Obd2Connection;
import java.util.Optional;
import org.junit.Test;

public class Obd2CommandTest {
    private static final String[] EXPECTED_INIT_COMMANDS =
            new String[] {
                "ATD\r", "ATZ\r", "AT E0\r", "AT L0\r", "AT S0\r", "AT H0\r", "AT SP 0\r"
            };

    private static final String OBD2_PROMPT = ">";

    private static final String[] EXPECTED_INIT_RESPONSES =
            new String[] {
                OBD2_PROMPT,
                OBD2_PROMPT,
                OBD2_PROMPT,
                OBD2_PROMPT,
                OBD2_PROMPT,
                OBD2_PROMPT,
                OBD2_PROMPT
            };

    private static final float FLOAT_EQUALITY_DELTA = 0.0001f;

    void checkLiveFrameIntCommand(int pid, String responseBytes, int expectedResponse) {
        String[] commandToSend = new String[] {String.format("01%02X\r", pid)};

        String[] responseToGet =
                new String[] {String.format("41 %02X %s", pid, responseBytes), OBD2_PROMPT};

        MockObd2UnderlyingTransport transport =
                new MockObd2UnderlyingTransport(
                        concatIntArrays(
                                stringsToIntArray(EXPECTED_INIT_COMMANDS),
                                stringsToIntArray(commandToSend)),
                        concatIntArrays(
                                stringsToIntArray(EXPECTED_INIT_RESPONSES),
                                stringsToIntArray(responseToGet)));
        Obd2Connection obd2Connection = new Obd2Connection(transport);
        Obd2Command<Integer> commandObject =
                Obd2Command.getLiveFrameCommand(Obd2Command.getIntegerCommand(pid));
        assertNotNull(commandObject);
        Optional<Integer> receivedResponse = Optional.empty();
        try {
            receivedResponse = commandObject.run(obd2Connection);
        } catch (Exception e) {
            assertTrue("live frame command " + pid + " caused an exception: " + e, false);
        }
        assertTrue("live frame contains a response", receivedResponse.isPresent());
        assertEquals(expectedResponse, (int) receivedResponse.get());
    }

    void checkLiveFrameFloatCommand(int pid, String responseBytes, float expectedResponse) {
        String[] commandToSend = new String[] {String.format("01%02X\r", pid)};

        String[] responseToGet =
                new String[] {String.format("41 %02X %s", pid, responseBytes), OBD2_PROMPT};

        MockObd2UnderlyingTransport transport =
                new MockObd2UnderlyingTransport(
                        concatIntArrays(
                                stringsToIntArray(EXPECTED_INIT_COMMANDS),
                                stringsToIntArray(commandToSend)),
                        concatIntArrays(
                                stringsToIntArray(EXPECTED_INIT_RESPONSES),
                                stringsToIntArray(responseToGet)));
        Obd2Connection obd2Connection = new Obd2Connection(transport);
        Obd2Command<Float> commandObject =
                Obd2Command.getLiveFrameCommand(Obd2Command.getFloatCommand(pid));
        assertNotNull(commandObject);
        Optional<Float> receivedResponse = Optional.empty();
        try {
            receivedResponse = commandObject.run(obd2Connection);
        } catch (Exception e) {
            assertTrue("live frame command " + pid + " caused an exception: " + e, false);
        }
        assertTrue("live frame contains a response", receivedResponse.isPresent());
        assertEquals(expectedResponse, (float) receivedResponse.get(), FLOAT_EQUALITY_DELTA);
    }

    void checkFreezeFrameIntCommand(int pid, String responseBytes, int expectedResponse) {
        String[] commandToSend = new String[] {String.format("02%02X 01\r", pid)};

        String[] responseToGet =
                new String[] {String.format("42 %02X 01 %s", pid, responseBytes), OBD2_PROMPT};

        MockObd2UnderlyingTransport transport =
                new MockObd2UnderlyingTransport(
                        concatIntArrays(
                                stringsToIntArray(EXPECTED_INIT_COMMANDS),
                                stringsToIntArray(commandToSend)),
                        concatIntArrays(
                                stringsToIntArray(EXPECTED_INIT_RESPONSES),
                                stringsToIntArray(responseToGet)));
        Obd2Connection obd2Connection = new Obd2Connection(transport);
        Obd2Command<Integer> commandObject =
                Obd2Command.getFreezeFrameCommand(Obd2Command.getIntegerCommand(pid), 0x1);
        assertNotNull(commandObject);
        Optional<Integer> receivedResponse = Optional.empty();
        try {
            receivedResponse = commandObject.run(obd2Connection);
        } catch (Exception e) {
            assertTrue("freeze frame command " + pid + " caused an exception: " + e, false);
        }
        assertTrue("freeze frame contains a response", receivedResponse.isPresent());
        assertEquals(expectedResponse, (int) receivedResponse.get());
    }

    void checkFreezeFrameFloatCommand(int pid, String responseBytes, float expectedResponse) {
        String[] commandToSend = new String[] {String.format("02%02X 01\r", pid)};

        String[] responseToGet =
                new String[] {String.format("42 %02X 01 %s", pid, responseBytes), OBD2_PROMPT};

        MockObd2UnderlyingTransport transport =
                new MockObd2UnderlyingTransport(
                        concatIntArrays(
                                stringsToIntArray(EXPECTED_INIT_COMMANDS),
                                stringsToIntArray(commandToSend)),
                        concatIntArrays(
                                stringsToIntArray(EXPECTED_INIT_RESPONSES),
                                stringsToIntArray(responseToGet)));
        Obd2Connection obd2Connection = new Obd2Connection(transport);
        Obd2Command<Float> commandObject =
                Obd2Command.getFreezeFrameCommand(Obd2Command.getFloatCommand(pid), 0x1);
        assertNotNull(commandObject);
        Optional<Float> receivedResponse = Optional.empty();
        try {
            receivedResponse = commandObject.run(obd2Connection);
        } catch (Exception e) {
            assertTrue("freeze frame command " + pid + " caused an exception: " + e, false);
        }
        assertTrue("freeze frame contains a response", receivedResponse.isPresent());
        assertEquals(expectedResponse, (float) receivedResponse.get(), FLOAT_EQUALITY_DELTA);
    }

    void checkCommand(int pid, String responseBytes, int expectedResponse) {
        checkLiveFrameIntCommand(pid, responseBytes, expectedResponse);
        checkFreezeFrameIntCommand(pid, responseBytes, expectedResponse);
    }

    void checkCommand(int pid, String responseBytes, float expectedResponse) {
        checkLiveFrameFloatCommand(pid, responseBytes, expectedResponse);
        checkFreezeFrameFloatCommand(pid, responseBytes, expectedResponse);
    }

    @Test
    public void testEngineOilTemperature() {
        checkCommand(0x5C, "87", 95);
    }

    @Test
    public void testAmbientAirTemperature() {
        checkCommand(0x46, "A1", 63.137257f);
    }

    @Test
    public void testCalculatedEngineLoad() {
        checkCommand(0x04, "34", 23.1111f);
    }

    @Test
    public void testEngineCoolantTemperature() {
        checkCommand(0x05, "63", 59);
    }

    @Test
    public void testFuelGaugePressure() {
        checkCommand(0x0A, "12", 54);
    }

    @Test
    public void testFuelSystemStatus() {
        checkCommand(0x03, "08", 8);
    }

    @Test
    public void testFuelTankLevel() {
        checkCommand(0x2F, "5F", 37.2549f);
    }

    @Test
    public void testFuelTrim() {
        checkCommand(0x06, "42", 54.6875f);
        checkCommand(0x07, "42", 54.6875f);
        checkCommand(0x08, "42", 54.6875f);
        checkCommand(0x09, "42", 54.6875f);
    }

    @Test
    public void testRpm() {
        checkCommand(0x0C, "12 0F", 1155);
    }

    @Test
    public void testEngineRuntime() {
        checkCommand(0x1F, "04 10", 1040);
    }

    @Test
    public void testSpeed() {
        checkCommand(0x0D, "82", 130);
    }

    @Test
    public void testThrottlePosition() {
        checkCommand(0x11, "3D", 23.921576f);
    }
}
