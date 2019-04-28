/**
 * Copyright (c) 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.car.radio.service;

import com.android.car.radio.service.RadioStation;
import com.android.car.radio.service.RadioRds;

/**
 * Interface for applications to listen for changes in the current radio state.
 */
oneway interface IRadioCallback {
    /**
     * Called upon successful completion of a switch in radio stations.
     *
     * @param station A {@link RadioStation} object that contains the data for the now current radio
     *                station.
     */
    void onRadioStationChanged(in RadioStation station);

    /**
     * Called when only the metadata for the current station has changed.
     *
     * @param radioRds A {@link RadioRds} object that contains the updated metadata.
     */
    void onRadioMetadataChanged(in RadioRds radioRds);

    /**
     * Called when the current radio band has changed.
     *
     * @param radioBand A radio band value from {@link RadioManager}.
     */
    void onRadioBandChanged(int radioBand);

    /**
     * Called when the mute state of the radio has changed.
     *
     * @param isMuted {@code true} if the radio is muted.
     */
    void onRadioMuteChanged(boolean isMuted);

    /**
     * Called when the radio has encountered an error.
     *
     * @param status One of the error states in {@link RadioManager}. For example,
     *               {@link RadioManager#ERROR_HARDWARE_FAILURE}.
     */
    void onError(int status);
}
