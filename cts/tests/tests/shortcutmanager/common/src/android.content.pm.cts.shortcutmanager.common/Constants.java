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
package android.content.pm.cts.shortcutmanager.common;

import java.security.SecureRandom;

public class Constants {
    public static final String ACTION_THROTTLING_TEST =
            "android.content.pm.cts.shortcutmanager.ACTION_THROTTLING_TEST";
    public static final String ACTION_REPLY =
            "android.content.pm.cts.shortcutmanager.ACTION_REPLY";

    public static final String EXTRA_METHOD = "method";
    public static final String EXTRA_REPLY_ACTION = "reply_action";

    public static final String TEST_SET_DYNAMIC_SHORTCUTS = "testSetDynamicShortcuts";
    public static final String TEST_ADD_DYNAMIC_SHORTCUTS = "testAddDynamicShortcuts";
    public static final String TEST_UPDATE_SHORTCUTS = "testUpdateShortcuts";

    public static final String TEST_ACTIVITY_UNTHROTTLED = "testActivityUnthrottled";
    public static final String TEST_FG_SERVICE_UNTHROTTLED = "testFgServiceUnthrottled";
    public static final String TEST_BG_SERVICE_THROTTLED = "testBgServiceThrottled";

    public static final String TEST_INLINE_REPLY_SHOW = "testInlineReplyShow";
    public static final String TEST_INLINE_REPLY_CHECK = "testInlineReplyCheck";

    public static final String INLINE_REPLY_TITLE = "InlineReplyTestTitle";
    public static final String INLINE_REPLY_REMOTE_INPUT_CAPTION = "__INLINE_REPLY_REMOTE_INPUT__";

    public static final String IGNORE = "IGNORE";
    public static final String REQUEST_IGNORED_MESSAGE = "REQUEST_IGNORED_MESSAGE";

    public static final String ALREADY_PINNED = "ALREADY_PINNED";

    public static final String HAS_ICON = "HAS_ICON";
    public static final String LABEL = "LABEL";

    public static final SecureRandom sRandom = new SecureRandom();
}
