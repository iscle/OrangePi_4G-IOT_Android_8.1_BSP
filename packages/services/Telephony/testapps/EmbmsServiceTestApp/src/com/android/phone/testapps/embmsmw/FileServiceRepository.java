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

package com.android.phone.testapps.embmsmw;

import android.content.Context;
import android.net.Uri;
import android.telephony.mbms.FileInfo;
import android.telephony.mbms.FileServiceInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class FileServiceRepository {
    private int sServiceIdCounter = 0;
    private final Map<String, FileServiceInfo> mIdToServiceInfo = new HashMap<>();
    private final Map<Uri, Integer> mFileUriToResource = new HashMap<>();

    private static final String FILE_DOWNLOAD_SCHEME = "filedownload";
    private static final String FILE_AUTHORITY = "com.android.phone.testapps";

    private static FileServiceRepository sInstance;
    public static FileServiceRepository getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new FileServiceRepository(context);
        }
        return sInstance;
    }

    private final Context mContext;

    private FileServiceRepository(Context context) {
        mContext = context;
        Uri sunAndTree = initFile("sunAndTree.png", R.raw.suntree);
        Uri snake = initFile("animals/snake.png", R.raw.snake);
        Uri unicorn = initFile("animals/unicorn.png", R.raw.unicorn);
        Uri sheep = initFile("animals/sheep.png", R.raw.sheep);

        createFileService("Class1", sunAndTree);
        createFileService("Class1", snake, unicorn, sheep);
    }

    public List<FileServiceInfo> getFileServicesForClasses(
            List<String> serviceClasses) {
        return mIdToServiceInfo.values().stream()
                .filter((info) -> serviceClasses.contains(info.getServiceClassName()))
                .collect(Collectors.toList());
    }

    public List<FileServiceInfo> getAllFileServices() {
        return new ArrayList<>(mIdToServiceInfo.values());
    }

    public FileServiceInfo getFileServiceInfoForId(String serviceId) {
        return mIdToServiceInfo.getOrDefault(serviceId, null);
    }

    public int getResourceForFileUri(Uri uri) {
        return mFileUriToResource.getOrDefault(uri, 0);
    }

    private void createFileService(String className, Uri... filesIncluded) {
        sServiceIdCounter++;
        String id = "FileServiceId[" + sServiceIdCounter + "]";
        List<Locale> locales = new ArrayList<Locale>(2) {{
            add(Locale.US);
            add(Locale.UK);
        }};
        Map<Locale, String> localeDict = new HashMap<Locale, String>() {{
            put(Locale.US, "File Source " + sServiceIdCounter);
            put(Locale.UK, "File Source with extra vowels " + sServiceIdCounter);
        }};
        List<FileInfo> fileInfos = Arrays.stream(filesIncluded)
                .map(this::getFileInfoForUri)
                .collect(Collectors.toList());
        FileServiceInfo info = new FileServiceInfo(localeDict, className, locales,
                id, new Date(System.currentTimeMillis() - 10000),
                new Date(System.currentTimeMillis() + 10000),
                fileInfos);
        mIdToServiceInfo.put(id, info);
    }

    private Uri initFile(String relPath, int resource) {
        Uri uri = new Uri.Builder()
                .scheme(FILE_DOWNLOAD_SCHEME)
                .authority(FILE_AUTHORITY)
                .path(relPath)
                .build();
        mFileUriToResource.put(uri, resource);
        return uri;
    }

    private FileInfo getFileInfoForUri(Uri uri) {
        if (!mFileUriToResource.containsKey(uri)) {
            return null;
        }

        return new FileInfo(uri, "application/octet-stream");
    }
}
