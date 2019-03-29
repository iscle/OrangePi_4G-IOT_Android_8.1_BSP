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
 * limitations under the License
 */

package android.content.cts;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.AbstractWindowedCursor;
import android.database.Cursor;
import android.database.CursorWindow;
import android.net.Uri;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.util.Log;

/**
 * Content provider that uses a custom {@link CursorWindow} to inject file descriptor
 * pointing to another ashmem region having window slots with references outside of allowed ranges.
 *
 * <p>Used in {@link ContentProviderCursorWindowTest}
 */
public class CursorWindowContentProvider extends ContentProvider {
    private static final String TAG = "CursorWindowContentProvider";
    static {
        System.loadLibrary("nativecursorwindow_jni");
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        AbstractWindowedCursor cursor = new AbstractWindowedCursor() {
            @Override
            public int getCount() {
                return 1;
            }

            @Override
            public String[] getColumnNames() {
                return new String[] {"a"};
            }
        };
        cursor.setWindow(new InjectingCursorWindow("TmpWindow"));
        return cursor;
    }

    class InjectingCursorWindow extends CursorWindow {
        InjectingCursorWindow(String name) {
            super(name);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            Parcel tmp = Parcel.obtain();

            super.writeToParcel(tmp, flags);
            tmp.setDataPosition(0);
            // Find location of file descriptor
            int fdPos = -1;
            while (tmp.dataAvail() > 0) {
                fdPos = tmp.dataPosition();
                int frameworkFdMarker = tmp.readInt();
                if (frameworkFdMarker == 0x66642a85 /* BINDER_TYPE_FD */) {
                    break;
                }
            }
            if (fdPos == -1) {
                tmp.recycle();
                throw new IllegalStateException("File descriptor not found in the output of "
                        + "CursorWindow.writeToParcel");
            }
            // Write reply with replaced file descriptor
            ParcelFileDescriptor evilFd = ParcelFileDescriptor
                    .adoptFd(makeNativeCursorWindowFd(1000, 1000, true));
            dest.appendFrom(tmp, 0, fdPos);
            dest.writeFileDescriptor(evilFd.getFileDescriptor());
            tmp.setDataPosition(dest.dataPosition());
            dest.appendFrom(tmp, dest.dataPosition(), tmp.dataAvail());
            tmp.recycle();
        }
    }

    private native static int makeNativeCursorWindowFd(int offset, int size, boolean isBlob);

    // Stubs
    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        Log.e(TAG, "delete() not implemented");
        return 0;
    }

    @Override
    public String getType(Uri uri) {
        Log.e(TAG, "getType() not implemented");
        return "";
    }

    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        Log.e(TAG, "insert() not implemented");
        return null;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
            String[] selectionArgs) {
        Log.e(TAG, "update() not implemented");
        return 0;
    }

}
