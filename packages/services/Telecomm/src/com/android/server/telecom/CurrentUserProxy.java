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
 * limitations under the License
 */

package com.android.server.telecom;

import android.os.UserHandle;

/**
 * Defines common functionality for a class which has knowledge of the currently logged in user.
 * Implemented by {@link CallsManager} and used by {@link VideoProviderProxy} so that it does not
 * have a dependency on the entire CallsManager when all that is required is the ability to find out
 * the handle of the currently logged in user.
 */
public interface CurrentUserProxy {
    UserHandle getCurrentUserHandle();
}
