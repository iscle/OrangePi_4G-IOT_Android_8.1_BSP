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
package android.platform.longevity.scheduler;

import android.os.Bundle;

import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.junit.runner.Runner;

/**
 * A {@link Scheduler} for shuffling the order of test execution.
 */
public class Shuffle implements Scheduler {
    static final String SHUFFLE_OPTION_NAME = "shuffle";
    private static final boolean SHUFFLE_DEFAULT_VALUE = false;
    static final String SEED_OPTION_NAME = "seed";

    @Override
    public List<Runner> apply(Bundle args, List<Runner> input) {
        boolean shuffle = Boolean.parseBoolean(
                args.getString(SHUFFLE_OPTION_NAME, String.valueOf(SHUFFLE_DEFAULT_VALUE)));
        long seed = Long.parseLong(
                args.getString(SEED_OPTION_NAME, String.valueOf(new Random().nextLong())));
        // TODO: Log the options selected.
        if (shuffle) {
            Collections.shuffle(input, new Random(seed));
        }
        return input;
    }
}
