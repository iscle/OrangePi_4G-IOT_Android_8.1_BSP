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
 * An UnknownUiException is an exception specific to UI-driven app helpers. This should be thrown
 * when specific UI conditions, generally post-conditions, are not met for some unknown reason.
 * <p>
 * Examples include (but are not limited to): opening an e-mail and not finding any open message,
 * loading a website and not seeing any content, being on GCA in camera mode without a flash button.
 * <p>
 * These exceptions are likely a manifestation of unhandled conditions or UI updates, but cannot
 * explicitly say so without further diagnosis.
 */
public class UnknownUiException extends RuntimeException {
    public UnknownUiException(String msg) {
        super(msg);
    }

    public UnknownUiException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
