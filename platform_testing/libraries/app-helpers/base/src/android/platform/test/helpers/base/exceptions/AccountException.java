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
 * An AccountException is an exception specific to UI-driven app helpers. This should be thrown
 * under two circumstances:
 * <p>
 * 1. When an account is explicitly required to complete an action, but one is not logged in.
 * <p>
 * 2. When no account is explicitly required to complete an action, but one is logged in.
 */
public class AccountException extends RuntimeException {
    public AccountException(String msg) {
        super(msg);
    }

    public AccountException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
