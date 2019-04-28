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

package com.android.car.obd2.commands;

import com.android.car.obd2.IntegerArrayStream;
import com.android.car.obd2.Obd2Command;
import java.util.Optional;

public class FuelTankLevel implements Obd2Command.OutputSemanticHandler<Float> {
    @Override
    public int getPid() {
        return 0x2F;
    }

    @Override
    public Optional<Float> consume(IntegerArrayStream data) {
        return data.hasAtLeast(
                1,
                theData -> Optional.of(theData.consume() * (100.0f / 255.0f)),
                theData -> Optional.<Float>empty());
    }
}
