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

package android.location.cts;

import android.location.GnssNavigationMessage;

public class GnssNavigationMessageCallbackTest extends GnssTestCase {
    private static class MockCallback extends GnssNavigationMessage.Callback {
    }

    public void testAllMethodsExist() {
        GnssNavigationMessage.Callback callback = new MockCallback();
        GnssNavigationMessage message = new GnssNavigationMessage();
        GnssNavigationMessage event = message;
        callback.onGnssNavigationMessageReceived(event);
        callback.onStatusChanged(GnssNavigationMessage.Callback.STATUS_READY);
    }
}
