/*
 * Copyright 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.managedprovisioning.preprovisioning.terms;

import static com.android.internal.util.Preconditions.checkStringNotEmpty;

import com.android.managedprovisioning.common.ProvisionLogger;

/**
 * Class responsible for storing disclaimers
 */
public final class TermsDocument {
    private final String mHeading;
    private final String mContent;

    /**
     * Creates a {@link TermsDocument} instance.
     *
     * @param heading non-empty {@link String}
     * @param content non-empty {@link String}
     * @return null if either of the invocation arguments is an empty string
     */
    public static TermsDocument createInstance(String heading, String content) {
        try {
            return new TermsDocument(heading, content);
        } catch (IllegalArgumentException e) {
            ProvisionLogger.loge("Failed to parse a disclaimer.", e);
            return null;
        }
    }

    private TermsDocument(String heading, String content) {
        mHeading = checkStringNotEmpty(heading);
        mContent = checkStringNotEmpty(content);
    }

    /** @return Document heading */
    public String getHeading() {
        return mHeading;
    }

    /** @return Document raw HTML content */
    public String getContent() {
        return mContent;
    }
}