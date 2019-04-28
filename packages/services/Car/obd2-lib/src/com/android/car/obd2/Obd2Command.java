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

package com.android.car.obd2;

import com.android.car.obd2.commands.AmbientAirTemperature;
import com.android.car.obd2.commands.CalculatedEngineLoad;
import com.android.car.obd2.commands.EngineCoolantTemperature;
import com.android.car.obd2.commands.EngineOilTemperature;
import com.android.car.obd2.commands.EngineRuntime;
import com.android.car.obd2.commands.FuelGaugePressure;
import com.android.car.obd2.commands.FuelSystemStatus;
import com.android.car.obd2.commands.FuelTankLevel;
import com.android.car.obd2.commands.FuelTrimCommand.Bank1LongTermFuelTrimCommand;
import com.android.car.obd2.commands.FuelTrimCommand.Bank1ShortTermFuelTrimCommand;
import com.android.car.obd2.commands.FuelTrimCommand.Bank2LongTermFuelTrimCommand;
import com.android.car.obd2.commands.FuelTrimCommand.Bank2ShortTermFuelTrimCommand;
import com.android.car.obd2.commands.RPM;
import com.android.car.obd2.commands.Speed;
import com.android.car.obd2.commands.ThrottlePosition;
import java.io.IOException;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Base class of OBD2 command objects that query a "vehicle" and return an individual data point
 * represented as a Java type.
 *
 * @param <ValueType> The Java type that represents the value of this command's output.
 */
public abstract class Obd2Command<ValueType> {

    /**
     * Abstract representation of an object whose job it is to receive the bytes read from the OBD2
     * connection and return a Java representation of a command's value.
     *
     * @param <ValueType>
     */
    public interface OutputSemanticHandler<ValueType> {
        int getPid();

        Optional<ValueType> consume(IntegerArrayStream data);
    }

    public static final int LIVE_FRAME = 1;
    public static final int FREEZE_FRAME = 2;

    private static final HashMap<Integer, OutputSemanticHandler<Integer>>
            SUPPORTED_INTEGER_COMMANDS = new HashMap<>();
    private static final HashMap<Integer, OutputSemanticHandler<Float>> SUPPORTED_FLOAT_COMMANDS =
            new HashMap<>();

    private static void addSupportedIntegerCommands(
            OutputSemanticHandler<Integer>... integerOutputSemanticHandlers) {
        for (OutputSemanticHandler<Integer> integerOutputSemanticHandler :
                integerOutputSemanticHandlers) {
            SUPPORTED_INTEGER_COMMANDS.put(
                    integerOutputSemanticHandler.getPid(), integerOutputSemanticHandler);
        }
    }

    private static void addSupportedFloatCommands(
            OutputSemanticHandler<Float>... floatOutputSemanticHandlers) {
        for (OutputSemanticHandler<Float> floatOutputSemanticHandler :
                floatOutputSemanticHandlers) {
            SUPPORTED_FLOAT_COMMANDS.put(
                    floatOutputSemanticHandler.getPid(), floatOutputSemanticHandler);
        }
    }

    public static Set<Integer> getSupportedIntegerCommands() {
        return SUPPORTED_INTEGER_COMMANDS.keySet();
    }

    public static Set<Integer> getSupportedFloatCommands() {
        return SUPPORTED_FLOAT_COMMANDS.keySet();
    }

    public static OutputSemanticHandler<Integer> getIntegerCommand(int pid) {
        return SUPPORTED_INTEGER_COMMANDS.get(pid);
    }

    public static OutputSemanticHandler<Float> getFloatCommand(int pid) {
        return SUPPORTED_FLOAT_COMMANDS.get(pid);
    }

    static {
        addSupportedFloatCommands(
                new AmbientAirTemperature(),
                new CalculatedEngineLoad(),
                new FuelTankLevel(),
                new Bank2ShortTermFuelTrimCommand(),
                new Bank2LongTermFuelTrimCommand(),
                new Bank1LongTermFuelTrimCommand(),
                new Bank1ShortTermFuelTrimCommand(),
                new ThrottlePosition());
        addSupportedIntegerCommands(
                new EngineOilTemperature(),
                new EngineCoolantTemperature(),
                new FuelGaugePressure(),
                new FuelSystemStatus(),
                new RPM(),
                new EngineRuntime(),
                new Speed());
    }

    protected final int mMode;
    protected final OutputSemanticHandler<ValueType> mSemanticHandler;

    Obd2Command(int mode, OutputSemanticHandler<ValueType> semanticHandler) {
        mMode = mode;
        mSemanticHandler = Objects.requireNonNull(semanticHandler);
    }

    public abstract Optional<ValueType> run(Obd2Connection connection) throws Exception;

    public int getPid() {
        return mSemanticHandler.getPid();
    }

    public static final <T> LiveFrameCommand<T> getLiveFrameCommand(OutputSemanticHandler handler) {
        return new LiveFrameCommand<>(handler);
    }

    public static final <T> FreezeFrameCommand<T> getFreezeFrameCommand(
            OutputSemanticHandler handler, int frameId) {
        return new FreezeFrameCommand<>(handler, frameId);
    }

    /**
     * An OBD2 command that returns live frame data.
     *
     * @param <ValueType> The Java type that represents the command's result type.
     */
    public static class LiveFrameCommand<ValueType> extends Obd2Command<ValueType> {
        private static final int RESPONSE_MARKER = 0x41;

        LiveFrameCommand(OutputSemanticHandler<ValueType> semanticHandler) {
            super(LIVE_FRAME, semanticHandler);
        }

        public Optional<ValueType> run(Obd2Connection connection)
                throws IOException, InterruptedException {
            String command = String.format("%02X%02X", mMode, mSemanticHandler.getPid());
            int[] data = connection.run(command);
            IntegerArrayStream stream = new IntegerArrayStream(data);
            if (stream.expect(RESPONSE_MARKER, mSemanticHandler.getPid())) {
                return mSemanticHandler.consume(stream);
            }
            return Optional.empty();
        }
    }

    /**
     * An OBD2 command that returns freeze frame data.
     *
     * @param <ValueType> The Java type that represents the command's result type.
     */
    public static class FreezeFrameCommand<ValueType> extends Obd2Command<ValueType> {
        private static final int RESPONSE_MARKER = 0x42;

        private int mFrameId;

        FreezeFrameCommand(OutputSemanticHandler<ValueType> semanticHandler, int frameId) {
            super(FREEZE_FRAME, semanticHandler);
            mFrameId = frameId;
        }

        public Optional<ValueType> run(Obd2Connection connection)
                throws IOException, InterruptedException {
            String command =
                    String.format("%02X%02X %02X", mMode, mSemanticHandler.getPid(), mFrameId);
            int[] data = connection.run(command);
            IntegerArrayStream stream = new IntegerArrayStream(data);
            if (stream.expect(RESPONSE_MARKER, mSemanticHandler.getPid(), mFrameId)) {
                return mSemanticHandler.consume(stream);
            }
            return Optional.empty();
        }
    }
}
