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

package android.platform.test.helpers.exceptions;

/**
 * A UiTimeoutException is an exception specific to UI-driven app helpers. This should be thrown
 * when a specific UI condition is not met due to a timeout that has been exceeded.
 * <p>
 * Examples include (but are not limited to): waiting for the shutter button to be enabled in GCA
 * or long loading times for Gmail. The reason or symptom may be clarified by the included message,
 * but should not speculate if there is any reasonable doubt.
 */
public class UiTimeoutException extends RuntimeException {
    public UiTimeoutException(String msg) {
        super(msg);
    }

    public UiTimeoutException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
