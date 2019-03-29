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
package com.android.cts.verifier.sensors.sixdof.Utils.Path;

/**
 * Class with the implementation for the Accuracy path
 */
public class AccuracyPath extends Path {
    /**
     * Implementation of the abstract class in path but left empty because there is nothing extra to
     * check for.
     *
     * @param coordinates the coordinates for the waypoint
     */
    @Override
    public void additionalChecks(float[] coordinates) {
        // No additional checks required in this test.
    }
}
