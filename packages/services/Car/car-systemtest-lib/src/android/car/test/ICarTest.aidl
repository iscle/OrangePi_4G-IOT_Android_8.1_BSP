/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.car.test;

import android.os.IBinder;

/** @hide */
interface ICarTest {
    /**
     * Calling this method will effectively call release method for all car services. This make
     * sense for test purpose when it is neccessary to reduce interference between testing and
     * real instances of Car Service. For example changing audio focus in CarAudioService may
     * affect framework's AudioManager listeners. AudioManager has a lot of complex logic which is
     * hard to mock.
     */
    void stopCarService(IBinder token) = 1;

    /** Re initializes car services that was previously released by #releaseCarService method. */
    void startCarService(IBinder token) = 2;
}
