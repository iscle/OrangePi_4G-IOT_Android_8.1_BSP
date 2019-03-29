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
package android.content.pm.cts.shortcutmanager.packages;

import android.app.Activity;
import android.content.pm.LauncherApps;
import android.content.pm.LauncherApps.PinItemRequest;
import android.content.pm.ShortcutInfo;
import android.content.pm.cts.shortcutmanager.common.Constants;
import android.content.pm.cts.shortcutmanager.common.ReplyUtil;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;

import java.util.Objects;

/**
 * Activity that receives a "pin shortcut" request, and accepts automatically.
 */
public class ShortcutConfirmPin extends Activity {
    private static final String TAG = "ShortcutConfirmPin";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.i(TAG, "ShortcutConfirmPin.onCreate");

        String replyAction = null;
        try {
            final LauncherApps launcherApps = getSystemService(LauncherApps.class);
            final PinItemRequest request = launcherApps.getPinItemRequest(getIntent());

            // This really must be non-null; otherwise we can't send a reply.
            final ShortcutInfo shortcut = request.getShortcutInfo();
            if (shortcut == null) {
                Log.e(TAG, "request.getShortcutInfo() NOT expected to be NULL");
                return;
            }

            replyAction = shortcut.getExtras().getString(Constants.EXTRA_REPLY_ACTION);

            if (!request.isValid()) {
                ReplyUtil.sendReply(this, replyAction, "request.isValid() expected to be TRUE");
                return;
            }
            if (request.getRequestType() != PinItemRequest.REQUEST_TYPE_SHORTCUT) {
                ReplyUtil.sendReply(this, replyAction,
                        "request.getRequestType() expected to be REQUEST_TYPE_SHORTCUT");
                return;
            }

            if (shortcut.getExtras().getBoolean(Constants.IGNORE)) {
                // Send a reply so that the caller can tell if the request has been sent,
                // and ignored.
                ReplyUtil.sendReply(this, replyAction, Constants.REQUEST_IGNORED_MESSAGE);
                return;
            }

            // Check the shortcut's fields.
            final boolean expectPinned = shortcut.getExtras().getBoolean(Constants.ALREADY_PINNED);
            if (shortcut.isPinned() != expectPinned) {
                ReplyUtil.sendReply(this, replyAction, "isPinned() expected to be " + expectPinned);
                return;
            }

            final String expectLabel = shortcut.getExtras().getString(Constants.LABEL);
            if (!Objects.equals(expectLabel, shortcut.getShortLabel())) {
                ReplyUtil.sendReply(this, replyAction,
                        "getShortLabel() expected to be '" + expectLabel + "', but was '"
                        + shortcut.getShortLabel() + "'");
                return;
            }
            final Drawable icon = launcherApps.getShortcutBadgedIconDrawable(
                    shortcut, DisplayMetrics.DENSITY_DEFAULT);
            if (shortcut.getExtras().getBoolean(Constants.HAS_ICON)) {
                // Send a reply so that the caller can tell if the request has been sent,
                // and ignored.
                if (icon == null) {
                    ReplyUtil.sendReply(this, replyAction, "Expected to have icon");
                    return;
                }
            } else {
                if (icon != null) {
                    ReplyUtil.sendReply(this, replyAction, "Not expected to have icon");
                    return;
                }
            }

            request.accept();
            if (request.isValid()) {
                ReplyUtil.sendReply(this, replyAction,
                        "request.isValid() expected to be FALSE after accept()");
                return;
            }
            ReplyUtil.sendSuccessReply(this, replyAction);
        } catch (Exception e) {
            Log.e(TAG, "Caught exception", e);
            if (replyAction != null) {
                ReplyUtil.sendReply(this, replyAction, "Caught exception: " + e);
            }
        } finally {
            finish();
        }
    }
}
