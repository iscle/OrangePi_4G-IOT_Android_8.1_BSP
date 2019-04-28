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
package com.android.documentsui.base;

import android.content.ContentResolver;
import android.os.Bundle;

import com.android.documentsui.queries.CommandInterceptor;

import javax.annotation.Nullable;

/**
 * Shared values that may be set by {@link CommandInterceptor}.
 */
public final class DebugFlags {

    private DebugFlags() {}

    private static String mQvPackage;
    private static boolean sDocumentDetailsEnabled;
    private static int sForcedPageOffset = -1;
    private static int sForcedPageLimit = -1;

    public static void setQuickViewer(@Nullable String qvPackage) {
        mQvPackage = qvPackage;
    }

    public static @Nullable String getQuickViewer() {
        return mQvPackage;
    }

    public static void setDocumentDetailsEnabled(boolean enabled) {
        sDocumentDetailsEnabled = enabled;
    }

    public static boolean getDocumentDetailsEnabled() {
        return sDocumentDetailsEnabled;
    }

    public static void setForcedPaging(int offset, int limit) {
        sForcedPageOffset = offset;
        sForcedPageLimit = limit;
    }

    public static boolean addForcedPagingArgs(Bundle queryArgs) {
        boolean flagsAdded = false;
        if (sForcedPageOffset >= 0) {
            queryArgs.putInt(ContentResolver.QUERY_ARG_OFFSET, sForcedPageOffset);
            flagsAdded |= true;
        }
        if (sForcedPageLimit >= 0) {
            queryArgs.putInt(ContentResolver.QUERY_ARG_LIMIT, sForcedPageLimit);
            flagsAdded |= true;
        }
        return flagsAdded;
    }
}
