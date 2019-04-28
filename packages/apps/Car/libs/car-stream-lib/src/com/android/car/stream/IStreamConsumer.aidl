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
package com.android.car.stream;

import com.android.car.stream.StreamCard;

/* Interface to be implemented by apps that need to listen for data produced by StreamService */
oneway interface IStreamConsumer {
    /** Called when a new StreamCard is added */
    void onStreamCardAdded(in StreamCard card) = 0;
    /** Called when a StreamCard is removed */
    void onStreamCardRemoved(in StreamCard card) = 1;
    /** Called when an existing StreamCard is updated */
    void onStreamCardChanged(in StreamCard newStreamCard) = 2;
}