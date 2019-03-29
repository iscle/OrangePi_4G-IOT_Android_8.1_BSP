/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.hardware.cts.helpers.reporting;

import android.hardware.cts.helpers.SensorTestPlatformException;

/**
 * Interface that represents a node in a hierarchy built by the sensor test platform.
 */
// TODO: this is an intermediate state to introduce a full-blown centralized recorder data produced
//       by sensor tests
public interface ISensorTestNode {

    /**
     * Provides a name (tag) that can be used to identify the current node.
     */
    String getName() throws SensorTestPlatformException;
}
