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

package com.android.cts.util;

import android.content.Context;
import android.content.Intent;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;

import java.util.ArrayList;

public class TestResult implements Parcelable {
    public static final String EXTRA_TEST_RESULT =
            "com.android.cts.ephemeraltest.EXTRA_TEST_RESULT";
    private static final String ACTION_START_ACTIVITY =
            "com.android.cts.ephemeraltest.START_ACTIVITY";

    private final String mPackageName;
    private final String mComponentName;
    private final String mMethodName;
    private final String mStatus;
    private final String mException;
    private final boolean mInstantAppPackageInfoExposed;

    public String getPackageName() {
        return mPackageName;
    }

    public String getComponentName() {
        return mComponentName;
    }

    public String getMethodName() {
        return mMethodName;
    }

    public String getStatus() {
        return mStatus;
    }

    public String getException() {
        return mException;
    }

    public boolean getEphemeralPackageInfoExposed() {
        return mInstantAppPackageInfoExposed;
    }

    public static Builder getBuilder() {
        return new Builder();
    }

    public void broadcast(Context context) {
        final Intent broadcastIntent = new Intent(ACTION_START_ACTIVITY);
        broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
        broadcastIntent.addFlags(Intent.FLAG_RECEIVER_VISIBLE_TO_INSTANT_APPS);
        broadcastIntent.putExtra(EXTRA_TEST_RESULT, this);
        context.sendBroadcast(broadcastIntent);
    }

    private TestResult(String packageName, String componentName, String methodName,
            String status, String exception, boolean ephemeralPackageInfoExposed) {
        mPackageName = packageName;
        mComponentName = componentName;
        mMethodName = methodName;
        mStatus = status;
        mException = exception;
        mInstantAppPackageInfoExposed = ephemeralPackageInfoExposed;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mPackageName);
        dest.writeString(mComponentName);
        dest.writeString(mMethodName);
        dest.writeString(mStatus);
        dest.writeString(mException);
        dest.writeBoolean(mInstantAppPackageInfoExposed);
    }

    public static final Creator<TestResult> CREATOR = new Creator<TestResult>() {
        public TestResult createFromParcel(Parcel source) {
            return new TestResult(source);
        }
        public TestResult[] newArray(int size) {
            return new TestResult[size];
        }
    };

    private TestResult(Parcel source) {
        mPackageName = source.readString();
        mComponentName = source.readString();
        mMethodName = source.readString();
        mStatus = source.readString();
        mException = source.readString();
        mInstantAppPackageInfoExposed = source.readBoolean();
    }

    public static class Builder {
        private String packageName;
        private String componentName;
        private String methodName;
        private String status;
        private String exception;
        private boolean instantAppPackageInfoExposed;

        private Builder() {
        }
        public Builder setPackageName(String _packageName) {
            packageName = _packageName;
            return this;
        }
        public Builder setComponentName(String _componentName) {
            componentName = _componentName;
            return this;
        }
        public Builder setMethodName(String _methodName) {
            methodName = _methodName;
            return this;
        }
        public Builder setStatus(String _status) {
            status = _status;
            return this;
        }
        public Builder setException(String _exception) {
            exception = _exception;
            return this;
        }
        public Builder setEphemeralPackageInfoExposed(boolean _instantAppPackageInfoExposed) {
            instantAppPackageInfoExposed = _instantAppPackageInfoExposed;
            return this;
        }
        public TestResult build() {
            return new TestResult(packageName, componentName, methodName,
                    status, exception, instantAppPackageInfoExposed);
        }
    }
}
