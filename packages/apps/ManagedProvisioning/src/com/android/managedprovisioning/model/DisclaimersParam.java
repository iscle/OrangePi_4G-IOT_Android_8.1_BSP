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
package com.android.managedprovisioning.model;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.PersistableBundle;
import com.android.internal.annotations.Immutable;
import com.android.managedprovisioning.common.PersistableBundlable;
import com.android.managedprovisioning.common.StoreUtils;
import java.io.File;
import java.io.IOException;

/**
 * Stores disclaimers information.
 */
@Immutable
public class DisclaimersParam extends PersistableBundlable {
    private static final String HEADER_KEY = "HEADER_KEY";
    private static final String CONTENT_PATH_KEY = "CONTENT_PATH_KEY";

    public static final Parcelable.Creator<DisclaimersParam> CREATOR
            = new Parcelable.Creator<DisclaimersParam>() {
        @Override
        public DisclaimersParam createFromParcel(Parcel in) {
            return new DisclaimersParam(in);
        }

        @Override
        public DisclaimersParam[] newArray(int size) {
            return new DisclaimersParam[size];
        }
    };

    public final Disclaimer[] mDisclaimers;

    private DisclaimersParam(Builder builder) {
        mDisclaimers = builder.mDisclaimers;
    }

    private DisclaimersParam(Parcel in) {
        this(createBuilderFromPersistableBundle(
                PersistableBundlable.getPersistableBundleFromParcel(in)));
    }

    public static DisclaimersParam fromPersistableBundle(PersistableBundle bundle) {
        return createBuilderFromPersistableBundle(bundle).build();
    }

    private static Builder createBuilderFromPersistableBundle(PersistableBundle bundle) {
        String[] headers = bundle.getStringArray(HEADER_KEY);
        String[] contentPaths = bundle.getStringArray(CONTENT_PATH_KEY);
        Builder builder = new Builder();
        if (headers != null) {
            // assume headers.length == contentPaths.length
            Disclaimer[] disclaimers = new Disclaimer[headers.length];
            for (int i = 0; i < headers.length; i++) {
                disclaimers[i] = new Disclaimer(headers[i], contentPaths[i]);
            }
            builder.setDisclaimers(disclaimers);
        }
        return builder;
    }

    public void cleanUp() {
        if (mDisclaimers != null) {
            for(Disclaimer disclaimer : mDisclaimers) {
                new File(disclaimer.mContentFilePath).delete();
            }
        }
    }

    @Override
    public PersistableBundle toPersistableBundle() {
        final PersistableBundle bundle = new PersistableBundle();
        if (mDisclaimers != null) {
            String[] headers = new String[mDisclaimers.length];
            String[] contentPaths = new String[mDisclaimers.length];
            for (int i = 0; i < mDisclaimers.length; i++) {
                headers[i] = mDisclaimers[i].mHeader;
                contentPaths[i] = mDisclaimers[i].mContentFilePath;
            }
            bundle.putStringArray(HEADER_KEY, headers);
            bundle.putStringArray(CONTENT_PATH_KEY, contentPaths);
        }
        return bundle;
    }

    @Immutable
    public static class Disclaimer {
        public final String mHeader;
        public final String mContentFilePath;
        public Disclaimer(String header, String contentFilePath) {
            mHeader = header;
            mContentFilePath = contentFilePath;
        }
    }

    public final static class Builder {
        private Disclaimer[] mDisclaimers;

        public Builder setDisclaimers(Disclaimer[] disclaimers) {
            mDisclaimers = disclaimers;
            return this;
        }

        public DisclaimersParam build() {
            return new DisclaimersParam(this);
        }
    }
}
