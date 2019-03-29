/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.media.session.cts;

import static android.media.cts.MediaSessionTestHelperConstants.MEDIA_SESSION_TEST_HELPER_PKG;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.ComponentName;
import android.content.Context;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.service.notification.NotificationListenerService;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

/**
 * Tests {@link MediaSessionManager} with the multi-user environment.
 * <p>Don't run tests here directly. They aren't stand-alone tests and each test will be run
 * indirectly by the host-side test CtsMediaHostTestCases after the proper device setup.
 */
@SmallTest
public class MediaSessionManagerTest extends NotificationListenerService {
    private ComponentName mComponentName;
    private MediaSessionManager mMediaSessionManager;

    @Before
    public void setUp() throws Exception {
        Context context = InstrumentationRegistry.getTargetContext();
        mMediaSessionManager = (MediaSessionManager) context.getSystemService(
                Context.MEDIA_SESSION_SERVICE);
        mComponentName = new ComponentName(context, MediaSessionManagerTest.class);
    }

    /**
     * Tests if the MediaSessionTestHelper doesn't have an active media session.
     */
    @Test
    public void testGetActiveSessions_noMediaSessionFromMediaSessionTestHelper() throws Exception {
        List<MediaController> controllers = mMediaSessionManager.getActiveSessions(mComponentName);
        for (MediaController controller : controllers) {
            if (controller.getPackageName().equals(MEDIA_SESSION_TEST_HELPER_PKG)) {
                fail("Media session for the media session app shouldn't be available");
                return;
            }
        }
    }

    /**
     * Tests if the MediaSessionTestHelper has an active media session.
     */
    @Test
    public void testGetActiveSessions_hasMediaSessionFromMediaSessionTestHelper() throws Exception {
        List<MediaController> controllers = mMediaSessionManager.getActiveSessions(mComponentName);
        for (MediaController controller : controllers) {
            if (controller.getPackageName().equals(MEDIA_SESSION_TEST_HELPER_PKG)) {
                // Test success
                return;
            }
        }
        fail("Media session for the media session app is expected");
    }

    /**
     * Tests if there's no media session.
     */
    @Test
    public void testGetActiveSessions_noMediaSession() throws Exception {
        List<MediaController> controllers = mMediaSessionManager.getActiveSessions(mComponentName);
        assertTrue(controllers.isEmpty());
    }
}
