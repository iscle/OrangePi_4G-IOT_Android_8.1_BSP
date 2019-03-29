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

package android.os.cts;

import android.app.PendingIntent;
import android.app.AuthenticationRequiredException;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;

import java.io.FileDescriptor;
import java.io.FileNotFoundException;

public class CrossProcessExceptionService extends Service {
    public static class CustomException extends IllegalArgumentException implements Parcelable {
        public CustomException(String message) {
            super(message);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
        }

        public static final Creator<CustomException> CREATOR = new Creator<CustomException>() {
            @Override
            public CustomException createFromParcel(Parcel source) {
                return new CustomException("REMOTE");
            }

            @Override
            public CustomException[] newArray(int size) {
                return new CustomException[size];
            }
        };

    }

    @Override
    public IBinder onBind(Intent intent) {
        return new Binder() {
            @Override
            public void dump(FileDescriptor fd, String[] args) {
                switch (args[0]) {
                    case "none":
                        return;
                    case "SE":
                        throw new SecurityException("SE");
                    case "ARE":
                        final PendingIntent pi = PendingIntent.getActivity(
                                CrossProcessExceptionService.this, 12, new Intent(),
                                PendingIntent.FLAG_CANCEL_CURRENT);
                        throw new AuthenticationRequiredException(new FileNotFoundException("FNFE"), pi);
                    case "RE":
                        throw new RuntimeException("RE");
                    case "custom":
                        throw new CustomException("LOCAL");
                }
            }
        };
    }
}
