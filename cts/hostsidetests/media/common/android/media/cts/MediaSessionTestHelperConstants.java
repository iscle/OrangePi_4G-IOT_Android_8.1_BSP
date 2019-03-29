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

package android.media.cts;

/**
 * Defines constants for controlling the media session test helper app, which can start media
 * playback and create the media session for test.
 * <p>Any change in these constants should also be applied to the media session test helper app.
 * <p>Don't add Android specific imports because this will be used by the both host-side and
 * device-side.
 */
public class MediaSessionTestHelperConstants {
    /**
     * Package name of the media session test helper.
     */
    public static final String MEDIA_SESSION_TEST_HELPER_PKG =
            "android.media.app.media_session_test_helper";
    /**
     * Package binary file name of the media session test helper`.
     */
    public static final String MEDIA_SESSION_TEST_HELPER_APK = "CtsMediaSessionTestHelper.apk";

    /**
     * Intent action name to control media sesion test helper.
     */
    public static final String ACTION_CONTROL =
            "android.media.app.media_session_test_helper.ACTION_CONTROL";
    /**
     * Intent extra key name to control media session test helper.
     */
    public static final String EXTRA_CONTROL_COMMAND =
            "android.media.app.media_session_test_helper.EXTRA_CONTROL_COMMAND";

    /**
     * Intent extra value for the key {@link #EXTRA_CONTROL_COMMAND} to create the media session
     * if it doesn't exist.
     * @see buildControlCommand
     */
    public static final int FLAG_CREATE_MEDIA_SESSION = 0x01;
    /**
     * Intent extra value for the key {@link #EXTRA_CONTROL_COMMAND} to set the media session active
     * if it exists.
     * @see buildControlCommand
     */
    public static final int FLAG_SET_MEDIA_SESSION_ACTIVE = 0x02;
    /**
     * Intent extra value for the key {@link #EXTRA_CONTROL_COMMAND} to set the media session
     * inactive if it exists.
     * @see buildControlCommand
     */
    public static final int FLAG_SET_MEDIA_SESSION_INACTIVE = 0x04;
    /**
     * Intent extra value for the key {@link #EXTRA_CONTROL_COMMAND} to release the media session
     * if it was created.
     * @see buildControlCommand
     */
    public static final int FLAG_RELEASE_MEDIA_SESSION = 0x08;

    private MediaSessionTestHelperConstants() {
        // Prevent from the instantiation.
    }

    /**
     * Builds the control command for the media session test helper app.
     *
     * @param userId user id to send the command
     * @param flag bit masked flag among {@link #FLAG_CREATE_MEDIA_SESSION},
     *            {@link #FLAG_SET_MEDIA_SESSION_ACTIVE}, {@link #FLAG_SET_MEDIA_SESSION_INACTIVE},
     *            and {@link #FLAG_RELEASE_MEDIA_SESSION}. If multiple flags are specificed,
     *            operations will be exceuted in order.
     **/
    public static String buildControlCommand(int userId, int flag) {
        return "am start-foreground-service --user " + userId + " -a " + ACTION_CONTROL + " --ei "
                + EXTRA_CONTROL_COMMAND + " " + flag + " " + MEDIA_SESSION_TEST_HELPER_PKG;
    }
}
