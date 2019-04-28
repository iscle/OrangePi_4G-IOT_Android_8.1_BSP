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

package com.android.car.radio;

/**
 * An interface for a Fragment that has the ability to fade in and out its content view. This
 * interface is for fragments that will have a manual tuner displayed on top of it. Since the
 * manual tuner does not cover the entire screen, the underlying fragment should not peek over
 * the top of the tuner.
 */
public interface FragmentWithFade {
    /**
     * Fade out the main contents of the fragment.
     */
    void fadeOutContent();

    /**
     * Fade in the main contents of the fragment.
     */
    void fadeInContent();
}

