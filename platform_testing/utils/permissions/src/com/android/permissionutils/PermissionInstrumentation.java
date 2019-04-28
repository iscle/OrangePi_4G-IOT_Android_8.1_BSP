/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.permissionutils;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import com.android.permissionutils.GrantPermissionUtil;

/**
 * A utility to dump or grant all revoked runtime permissions
 */
public class PermissionInstrumentation extends Instrumentation {
    private static final String PARAM_COMMAND = "command";
    private static final String COMMAND_DUMP = "dump";
    private static final String COMMAND_GRANTALL = "grant-all";

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        String command = arguments.getString(PARAM_COMMAND);
        if (command == null) {
            throw new IllegalArgumentException("missing command parameter");
        }
        if (COMMAND_DUMP.equals(command)) {
            GrantPermissionUtil.dumpMissingPermissions(getContext());
        } else if (COMMAND_GRANTALL.equals(command)) {
            GrantPermissionUtil.grantAllPermissions(getContext());
        } else {
            throw new IllegalArgumentException(
                    String.format("unrecognized command \"%s\"", command));
        }
        finish(Activity.RESULT_OK, new Bundle());
    }
}
