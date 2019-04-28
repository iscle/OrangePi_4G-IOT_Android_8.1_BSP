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

package com.android.timezone.data;

import com.android.timezone.distro.DistroException;
import com.android.timezone.distro.DistroVersion;
import com.android.timezone.distro.TimeZoneDistro;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.res.AssetManager;
import android.database.AbstractCursor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.TimeZoneRulesDataContract;
import android.provider.TimeZoneRulesDataContract.Operation;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static android.content.res.AssetManager.ACCESS_STREAMING;

/**
 * A basic implementation of a time zone data provider that can be used by OEMs to implement
 * an APK asset-based solution for time zone updates.
 */
public final class TimeZoneRulesDataProvider extends ContentProvider {

    static final String TAG = "TimeZoneRulesDataProvider";

    private static final String METADATA_KEY_OPERATION = "android.timezoneprovider.OPERATION";

    private static final Set<String> KNOWN_COLUMN_NAMES;
    private static final Map<String, Class<?>> KNOWN_COLUMN_TYPES;

    static {
        Set<String> columnNames = new HashSet<>();
        columnNames.add(Operation.COLUMN_TYPE);
        columnNames.add(Operation.COLUMN_DISTRO_MAJOR_VERSION);
        columnNames.add(Operation.COLUMN_DISTRO_MINOR_VERSION);
        columnNames.add(Operation.COLUMN_RULES_VERSION);
        columnNames.add(Operation.COLUMN_REVISION);
        KNOWN_COLUMN_NAMES = Collections.unmodifiableSet(columnNames);

        Map<String, Class<?>> columnTypes = new HashMap<>();
        columnTypes.put(Operation.COLUMN_TYPE, String.class);
        columnTypes.put(Operation.COLUMN_DISTRO_MAJOR_VERSION, Integer.class);
        columnTypes.put(Operation.COLUMN_DISTRO_MINOR_VERSION, Integer.class);
        columnTypes.put(Operation.COLUMN_RULES_VERSION, String.class);
        columnTypes.put(Operation.COLUMN_REVISION, Integer.class);
        KNOWN_COLUMN_TYPES = Collections.unmodifiableMap(columnTypes);
    }

    private final Map<String, Object> mColumnData = new HashMap<>();

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public void attachInfo(Context context, ProviderInfo info) {
        super.attachInfo(context, info);

        // Sanity check our security
        if (!TimeZoneRulesDataContract.AUTHORITY.equals(info.authority)) {
            // The authority looked for by the time zone updater is fixed.
            throw new SecurityException(
                    "android:authorities must be \"" + TimeZoneRulesDataContract.AUTHORITY + "\"");
        }
        if (!info.grantUriPermissions) {
            throw new SecurityException("Provider must grant uri permissions");
        }
        if (!info.exported) {
            // The content provider is accessed directly so must be exported.
            throw new SecurityException("android:exported must be \"true\"");
        }
        if (info.pathPermissions != null || info.writePermission != null) {
            // Use readPermission only to implement permissions.
            throw new SecurityException("Use android:readPermission only");
        }
        if (!android.Manifest.permission.UPDATE_TIME_ZONE_RULES.equals(info.readPermission)) {
            // Writing is not supported.
            throw new SecurityException("android:readPermission must be set to \""
                    + android.Manifest.permission.UPDATE_TIME_ZONE_RULES
                    + "\" is: " + info.readPermission);
        }

        // info.metadata is not filled in by default. Must ask for it again.
        final ProviderInfo infoWithMetadata = context.getPackageManager()
                .resolveContentProvider(info.authority, PackageManager.GET_META_DATA);
        Bundle metaData = infoWithMetadata.metaData;
        if (metaData == null) {
            throw new SecurityException("meta-data must be set");
        }

        // Work out what the operation type is.
        String type;
        try {
            type = getMandatoryMetaDataString(metaData, METADATA_KEY_OPERATION);
            mColumnData.put(Operation.COLUMN_TYPE, type);
        } catch (IllegalArgumentException e) {
            throw new SecurityException(METADATA_KEY_OPERATION + " meta-data not set.");
        }

        // Fill in version information if this is an install operation.
        if (Operation.TYPE_INSTALL.equals(type)) {
            // Extract the version information from the distro.
            InputStream distroBytesInputStream;
            try {
                distroBytesInputStream = context.getAssets().open(TimeZoneDistro.FILE_NAME);
            } catch (IOException e) {
                throw new SecurityException(
                        "Unable to open asset: " + TimeZoneDistro.FILE_NAME, e);
            }
            TimeZoneDistro distro = new TimeZoneDistro(distroBytesInputStream);
            try {
                DistroVersion distroVersion = distro.getDistroVersion();
                mColumnData.put(Operation.COLUMN_DISTRO_MAJOR_VERSION,
                        distroVersion.formatMajorVersion);
                mColumnData.put(Operation.COLUMN_DISTRO_MINOR_VERSION,
                        distroVersion.formatMinorVersion);
                mColumnData.put(Operation.COLUMN_RULES_VERSION, distroVersion.rulesVersion);
                mColumnData.put(Operation.COLUMN_REVISION, distroVersion.revision);
            } catch (IOException | DistroException e) {
                throw new SecurityException("Invalid asset: " + TimeZoneDistro.FILE_NAME, e);
            }

        }
    }

    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
            @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        if (!Operation.CONTENT_URI.equals(uri)) {
            return null;
        }
        final List<String> projectionList = Arrays.asList(projection);
        if (projection != null && !KNOWN_COLUMN_NAMES.containsAll(projectionList)) {
            throw new UnsupportedOperationException(
                    "Only " + KNOWN_COLUMN_NAMES + " columns supported.");
        }

        return new AbstractCursor() {
            @Override
            public int getCount() {
                return 1;
            }

            @Override
            public String[] getColumnNames() {
                return projectionList.toArray(new String[0]);
            }

            @Override
            public int getType(int column) {
                String columnName = projectionList.get(column);
                Class<?> columnJavaType = KNOWN_COLUMN_TYPES.get(columnName);
                if (columnJavaType == String.class) {
                    return Cursor.FIELD_TYPE_STRING;
                } else if (columnJavaType == Integer.class) {
                    return Cursor.FIELD_TYPE_INTEGER;
                } else {
                    throw new UnsupportedOperationException(
                            "Unsupported type: " + columnJavaType + " for " + columnName);
                }
            }

            @Override
            public String getString(int column) {
                checkPosition();
                String columnName = projectionList.get(column);
                if (KNOWN_COLUMN_TYPES.get(columnName) != String.class) {
                    throw new UnsupportedOperationException();
                }
                return (String) mColumnData.get(columnName);
            }

            @Override
            public short getShort(int column) {
                checkPosition();
                throw new UnsupportedOperationException();
            }

            @Override
            public int getInt(int column) {
                checkPosition();
                String columnName = projectionList.get(column);
                if (KNOWN_COLUMN_TYPES.get(columnName) != Integer.class) {
                    throw new UnsupportedOperationException();
                }
                return (Integer) mColumnData.get(columnName);
            }

            @Override
            public long getLong(int column) {
                return getInt(column);
            }

            @Override
            public float getFloat(int column) {
                throw new UnsupportedOperationException();
            }

            @Override
            public double getDouble(int column) {
                checkPosition();
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isNull(int column) {
                checkPosition();
                return column != 0;
            }
        };
    }

    @Override
    public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode)
            throws FileNotFoundException {
        if (!Operation.CONTENT_URI.equals(uri)) {
            throw new FileNotFoundException("Unknown URI: " + uri);
        }
        if (!"r".equals(mode)) {
            throw new FileNotFoundException("Only read-only access supported.");
        }

        // We cannot return the asset ParcelFileDescriptor from
        // assets.openFd(name).getParcelFileDescriptor() here as the receiver in the reading
        // process gets a ParcelFileDescriptor pointing at the whole .apk. Instead, we extract
        // the asset file we want to storage then wrap that in a ParcelFileDescriptor.
        File distroFile = null;
        try {
            distroFile = File.createTempFile("distro", null, getContext().getFilesDir());

            AssetManager assets = getContext().getAssets();
            try (InputStream is = assets.open(TimeZoneDistro.FILE_NAME, ACCESS_STREAMING);
                 FileOutputStream fos = new FileOutputStream(distroFile, false /* append */)) {
                copy(is, fos);
            }

            return ParcelFileDescriptor.open(distroFile, ParcelFileDescriptor.MODE_READ_ONLY);
        } catch (IOException e) {
            throw new RuntimeException("Unable to copy distro asset file", e);
        } finally {
            if (distroFile != null) {
                // Even if we have an open file descriptor pointing at the file it should be safe to
                // delete because of normal Unix file behavior. Deleting here avoids leaking any
                // storage.
                distroFile.delete();
            }
        }
    }

    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection,
            @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection,
            @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    private static String getMandatoryMetaDataString(Bundle metaData, String key) {
        if (!metaData.containsKey(key)) {
            throw new SecurityException("No metadata with key " + key + " found.");
        }
        return metaData.getString(key);
    }

    /**
     * Copies all of the bytes from {@code in} to {@code out}. Neither stream is closed.
     */
    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int c;
        while ((c = in.read(buffer)) != -1) {
            out.write(buffer, 0, c);
        }
    }
}
