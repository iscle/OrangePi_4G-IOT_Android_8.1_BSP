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
package com.android.documentsui.queries;

import static com.android.documentsui.base.Shared.DEBUG;

import android.content.Context;
import android.support.annotation.VisibleForTesting;
import android.text.TextUtils;
import android.util.Log;

import com.android.documentsui.DocumentsApplication;
import com.android.documentsui.R;
import com.android.documentsui.base.DebugFlags;
import com.android.documentsui.base.EventHandler;
import com.android.documentsui.base.Features;

import java.util.ArrayList;
import java.util.List;

public final class CommandInterceptor implements EventHandler<String> {

    @VisibleForTesting
    static final String COMMAND_PREFIX = ":";

    private static final String TAG = "CommandInterceptor";

    private final List<EventHandler<String[]>> mCommands = new ArrayList<>();

    private Features mFeatures;

    public CommandInterceptor(Features features) {
        mFeatures = features;

        mCommands.add(this::quickViewer);
        mCommands.add(this::gestureScale);
        mCommands.add(this::jobProgressDialog);
        mCommands.add(this::archiveCreation);
        mCommands.add(this::docInspector);
        mCommands.add(this::docDetails);
        mCommands.add(this::forcePaging);
    }

    public void add(EventHandler<String[]> handler) {
        mCommands.add(handler);
    }

    @Override
    public boolean accept(String query) {
        if (!mFeatures.isDebugSupportEnabled()) {
            return false;
        }

        if (!mFeatures.isCommandInterceptorEnabled()) {
            if (DEBUG) Log.v(TAG, "Skipping input, command interceptor disabled.");
            return false;
        }

        if (query.length() > COMMAND_PREFIX.length() && query.startsWith(COMMAND_PREFIX)) {
            String[] tokens = query.substring(COMMAND_PREFIX.length()).split("\\s+");
            for (EventHandler<String[]> command : mCommands) {
                if (command.accept(tokens)) {
                    return true;
                }
            }
            Log.d(TAG, "Unrecognized debug command: " + query);
        }
        return false;
    }

    private boolean quickViewer(String[] tokens) {
        if ("qv".equals(tokens[0])) {
            if (tokens.length == 2 && !TextUtils.isEmpty(tokens[1])) {
                DebugFlags.setQuickViewer(tokens[1]);
                Log.i(TAG, "Set quick viewer to: " + tokens[1]);
                return true;
            } else {
                Log.w(TAG, "Invalid command structure: " + TextUtils.join(" ", tokens));
            }
        } else if ("deqv".equals(tokens[0])) {
            Log.i(TAG, "Unset quick viewer");
            DebugFlags.setQuickViewer(null);
            return true;
        }
        return false;
    }

    private boolean gestureScale(String[] tokens) {
        if ("gs".equals(tokens[0])) {
            if (tokens.length == 2 && !TextUtils.isEmpty(tokens[1])) {
                boolean enabled = asBool(tokens[1]);
                mFeatures.forceFeature(R.bool.feature_gesture_scale, enabled);
                Log.i(TAG, "Set gesture scale enabled to: " + enabled);
                return true;
            }
            Log.w(TAG, "Invalid command structure: " + TextUtils.join(" ", tokens));
        }
        return false;
    }

    private boolean jobProgressDialog(String[] tokens) {
        if ("jpd".equals(tokens[0])) {
            if (tokens.length == 2 && !TextUtils.isEmpty(tokens[1])) {
                boolean enabled = asBool(tokens[1]);
                mFeatures.forceFeature(R.bool.feature_job_progress_dialog, enabled);
                Log.i(TAG, "Set job progress dialog enabled to: " + enabled);
                return true;
            }
            Log.w(TAG, "Invalid command structure: " + TextUtils.join(" ", tokens));
        }
        return false;
    }

    private boolean archiveCreation(String[] tokens) {
        if ("zip".equals(tokens[0])) {
            if (tokens.length == 2 && !TextUtils.isEmpty(tokens[1])) {
                boolean enabled = asBool(tokens[1]);
                mFeatures.forceFeature(R.bool.feature_archive_creation, enabled);
                Log.i(TAG, "Set gesture scale enabled to: " + enabled);
                return true;
            }
            Log.w(TAG, "Invalid command structure: " + TextUtils.join(" ", tokens));
        }
        return false;
    }

    private boolean docInspector(String[] tokens) {
        if ("inspect".equals(tokens[0])) {
            if (tokens.length == 2 && !TextUtils.isEmpty(tokens[1])) {
                boolean enabled = asBool(tokens[1]);
                mFeatures.forceFeature(R.bool.feature_inspector, enabled);
                Log.i(TAG, "Set doc inspector enabled to: " + enabled);
                return true;
            }
            Log.w(TAG, "Invalid command structure: " + TextUtils.join(" ", tokens));
        }
        return false;
    }

    private boolean docDetails(String[] tokens) {
        if ("docinfo".equals(tokens[0])) {
            if (tokens.length == 2 && !TextUtils.isEmpty(tokens[1])) {
                boolean enabled = asBool(tokens[1]);
                DebugFlags.setDocumentDetailsEnabled(enabled);
                Log.i(TAG, "Set doc details enabled to: " + enabled);
                return true;
            }
            Log.w(TAG, "Invalid command structure: " + TextUtils.join(" ", tokens));
        }
        return false;
    }

    private boolean forcePaging(String[] tokens) {
        if ("page".equals(tokens[0])) {
            if (tokens.length >= 2) {
                try {
                    int offset = Integer.parseInt(tokens[1]);
                    int limit = (tokens.length == 3) ? Integer.parseInt(tokens[2]) : -1;
                    DebugFlags.setForcedPaging(offset, limit);
                    Log.i(TAG, "Set forced paging to offset: " + offset + ", limit: " + limit);
                    return true;
                } catch (NumberFormatException e) {
                    Log.w(TAG, "Command input does not contain valid numbers: "
                            + TextUtils.join(" ", tokens));
                    return false;
                }
            } else {
                Log.w(TAG, "Invalid command structure: " + TextUtils.join(" ", tokens));
            }
        } else if ("deqv".equals(tokens[0])) {
            Log.i(TAG, "Unset quick viewer");
            DebugFlags.setQuickViewer(null);
            return true;
        }
        return false;
    }

    private final boolean asBool(String val) {
        if (val == null || val.equals("0")) {
            return false;
        }
        if (val.equals("1")) {
            return true;
        }
        return Boolean.valueOf(val);
    }

    public static final class DumpRootsCacheHandler implements EventHandler<String[]> {
        private final Context mContext;

        public DumpRootsCacheHandler(Context context) {
            mContext = context;
        }

        @Override
        public boolean accept(String[] tokens) {
            if ("dumpCache".equals(tokens[0])) {
                DocumentsApplication.getProvidersCache(mContext).logCache();
                return true;
            }
            return false;
        }
    }

    /**
     * Wraps {@link CommandInterceptor} in a tiny decorator that adds support for
     * enabling CommandInterceptor feature based on some magic query input.
     *
     * <p>It's like super meta, maaaannn.
     */
    public static final EventHandler<String> createDebugModeFlipper(
            Features features,
            Runnable debugFlipper,
            CommandInterceptor interceptor) {

        if (!features.isDebugSupportEnabled()) {
            return interceptor;
        }

        String magicString1 = COMMAND_PREFIX + "wwssadadba";
        String magicString2 = "up up down down left right left right b a";

        return new EventHandler<String>() {
            @Override
            public boolean accept(String query) {
                assert(features.isDebugSupportEnabled());

                if (magicString1.equals(query) || magicString2.equals(query)) {
                    debugFlipper.run();
                }
                return interceptor.accept(query);
            }
        };
    }
}
