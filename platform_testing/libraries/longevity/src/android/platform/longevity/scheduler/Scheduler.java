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

import java.util.List;
import java.util.function.BiFunction;

import org.junit.runner.Runner;

/**
 * A {@code BiFunction} for modifying the execution of {@code LongevitySuite} {@code Runner}s.
 */
@FunctionalInterface
public interface Scheduler extends BiFunction<Bundle, List<Runner>, List<Runner>> {
    default Scheduler andThen(Scheduler next) {
        return (bundle, list) -> next.apply(bundle, this.apply(bundle, list));
    }
}
