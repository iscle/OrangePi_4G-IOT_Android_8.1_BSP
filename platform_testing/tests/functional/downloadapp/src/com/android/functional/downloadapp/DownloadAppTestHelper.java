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

package com.android.functional.downloadapp;

import android.app.AlarmManager;
import android.app.DownloadManager;
import android.app.DownloadManager.Query;
import android.app.DownloadManager.Request;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.icu.util.Calendar;
import android.net.Uri;
import android.os.Environment;
import android.support.test.launcherhelper.ILauncherStrategy;
import android.support.test.launcherhelper.LauncherStrategyFactory;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.util.Log;

import junit.framework.Assert;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Random;

public class DownloadAppTestHelper {
    private static DownloadAppTestHelper mInstance = null;
    public static final String[] FILE_TYPES = new String[] {
            "pdf", "jpg", "jpeg", "doc", "xls", "txt", "rtf", "ppt", "gif", "png"
    };

    public static final String PACKAGE_NAME = "com.android.documentsui";
    public static final String APP_NAME = "Downloads";
    public static final String TEST_TAG = "DownloadAppTest";
    public final int TIMEOUT = 500;
    public final int MIN_FILENAME_LEN = 4;
    private Context mContext = null;
    private UiDevice mDevice = null;
    private DownloadManager mDownloadMgr;
    private Hashtable<String, DlObjSizeTimePair> mDownloadedItems =
            new Hashtable<String, DlObjSizeTimePair>();
    public ILauncherStrategy mLauncherStrategy;

    private DownloadAppTestHelper(UiDevice device, Context context) {
        mDevice = device;
        mContext = context;
        mLauncherStrategy = LauncherStrategyFactory.getInstance(mDevice).getLauncherStrategy();
    }

    public static DownloadAppTestHelper getInstance(UiDevice device, Context context) {
        if (mInstance == null) {
            mInstance = new DownloadAppTestHelper(device, context);
        }
        return mInstance;
    }

    public DownloadManager getDLManager() {
        return (DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);
    }

    public void launchApp(String packageName, String appName) {
        if (!mDevice.hasObject(By.pkg(packageName).depth(0))) {
            mLauncherStrategy.launch(appName, packageName);
        }
    }

    /** sort items in Download app by name, size, time */
    public void sortByParam(String sortby) {
        UiObject2 sortMenu = mDevice
                .wait(Until.findObject(By.res(PACKAGE_NAME, "menu_sort")), 200);
        if (sortMenu == null) {
            mDevice.wait(Until.findObject(By.desc("More options")), 200).click();
            sortMenu = mDevice.wait(Until.findObject(By.res("android:id/submenuarrow")), 200);
        }
        sortMenu.click();
        mDevice.wait(Until.findObject(By.text(String.format("By %s", sortby))), 200).click();
        mDevice.waitForIdle();
    }

    /** returns text list of items in Download app */
    public List<String> getDownloadItemNames() {
        List<UiObject2> itmesList = mDevice.wait(Until.findObjects(By.res("android:id/title")),
                TIMEOUT);
        List<String> nameList = new ArrayList<String>();
        for (UiObject2 item : itmesList) {
            nameList.add(item.getText());
        }
        return nameList;
    }

    /** verifies items in DownloadApp UI are sorted by name */
    public Boolean verifySortedByName() {
        List<String> nameList = getDownloadItemNames();
        for (int i = 0; i < (nameList.size() - 1); ++i) {
            if (nameList.get(i).compareToIgnoreCase(nameList.get(i + 1)) > 0) {
                return false;
            }
        }
        return true;
    }

    /** verifies items in DownloadApp UI are sorted by size */
    public Boolean verifySortedBySize() {
        List<String> nameList = getDownloadItemNames();
        for (int i = 0; i < (nameList.size() - 1); ++i) {
            DlObjSizeTimePair firstItem = mDownloadedItems.get(nameList.get(i));
            DlObjSizeTimePair secondItem = mDownloadedItems.get(nameList.get(i + 1));
            if (firstItem != null && secondItem != null
                    && firstItem.dlObjSize < secondItem.dlObjSize) {
                return false;
            }
        }
        return true;
    }

    /** verifies items in DownloadApp UI are sorted by time */
    public Boolean verifySortedByTime() {
        List<String> nameList = getDownloadItemNames();
        for (int i = 0; i < (nameList.size() - 1); ++i) {
            DlObjSizeTimePair firstItem = mDownloadedItems.get(nameList.get(i));
            DlObjSizeTimePair secondItem = mDownloadedItems.get(nameList.get(i + 1));
            if (firstItem != null && secondItem != null
                    && firstItem.dlObjTimeInMilliSec < secondItem.dlObjTimeInMilliSec) {
                return false;
            }
        }
        return true;
    }

    public void verifyDownloadViewType(UIViewType view) {
        int counter = 5;
        UiObject2 viewTypeObj = null;
        while ((viewTypeObj = mDevice.wait(
                Until.findObject(By.res(String.format("%s:id/%s",
                        DownloadAppTestHelper.PACKAGE_NAME,view.toString()))),200)) == null
                        && counter-- > 0);
        Assert.assertNotNull(viewTypeObj);
    }

    /**
     * Create word of random assortment of lower/upper case letters
     */
    /** set system time to random n[0..29] days earlier */
    public long changeSystemTime(long timeToSet) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(timeToSet);
        AlarmManager am = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        am.setTime(c.getTimeInMillis());
        return c.getTimeInMillis();
    }

    /** add some content to download DB using DownloadManager.addCompletedDownload api */
    public void populateContentInDLApp(int count) {
        int totalDownloaded = getTotalNumberDownloads();
        if (totalDownloaded >= count) {
            return;
        }
        Random random = new Random();
        long currentTime = System.currentTimeMillis();
        for (int i = 0; i < (count - totalDownloaded); ++i) {
            String fileName = String.format("%s.%s",
                    DownloadAppTestHelper.randomWord(random.nextInt(8) + MIN_FILENAME_LEN),
                    DownloadAppTestHelper.FILE_TYPES[random.nextInt(FILE_TYPES.length)]);
            int size = random.nextInt(1000);
            // changing system time to simulate the usecase "downloaded items over a period of time"
            long timeInMiliSec = changeSystemTime(
                    System.currentTimeMillis() - random.nextInt(30 * 24) * (60 * 60 * 1000));
            long dlId = -1;

            dlId = getDLManager().addCompletedDownload(
                    fileName,
                    String.format("%s Desc",
                            DownloadAppTestHelper.randomWord(random.nextInt(8) + MIN_FILENAME_LEN)),
                    Boolean.FALSE,
                    DownloadAppTestHelper.FILE_TYPES[random.nextInt(FILE_TYPES.length)],
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                            .getAbsolutePath(),
                    size, Boolean.FALSE);
            Assert.assertFalse("Add to DonwloadDB has failed!", dlId == -1);
            Log.d(TEST_TAG, String.format("Adding Name = %s, size = %d, time = %d", fileName,
                    size, timeInMiliSec));
            mDownloadedItems.put(fileName, new DlObjSizeTimePair(size, timeInMiliSec));
        }
        changeSystemTime(currentTime);
    }

    /** add some content to download DB directly in hacky way to bypass addCompletedDownload Api*/
    public long addToDownloadContentDB(String title, String description,
            boolean isMediaScannerScannable, String mimeType, String path, long length,
            boolean showNotification) {

        boolean allowWrite = Boolean.FALSE;
        Uri uri = Uri.parse("http://blah-blah"); // just put something in url format
        Uri referer = null;
        Request request;
        request = new Request(uri);
        ContentValues values = new ContentValues();
        /**
         * a hacky way to insert into the Download DB direct bypassing the api with minimal data
         * Constants have been taken from
         * /platform/frameworks/base/+/master/core/java/android/provider/Downloads.java
         */
        values.put("title", title);
        values.put("description", description);
        values.put("mimetype", mimeType);
        values.put("is_public_api", true);
        values.put("scanned", isMediaScannerScannable);
        values.put("is_visible_in_downloads_ui", Boolean.TRUE);
        values.put("destination", 6); // 6: show the download item in app
        values.put("_data", path); // location to save the downloaded file
        values.put("status", 200); // 200 : STATUS_SUCCESS
        values.put("total_bytes", length);
        values.put("visibility", (showNotification)
                ? Request.VISIBILITY_VISIBLE_NOTIFY_ONLY_COMPLETION : Request.VISIBILITY_HIDDEN);
        Uri downloadUri = mContext.getContentResolver()
                .insert(Uri.parse("content://downloads/my_downloads"), values);
        if (downloadUri == null) {
            return -1;
        }
        return Long.parseLong(downloadUri.getLastPathSegment());
    }

    /** remove downloads from download content db */
    public void removeContentInDLApp() {
        Cursor cursor = null;
        try {
            Query query = new Query();
            cursor = getDLManager().query(query);
            int columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_ID);
            long[] removeIds = new long[cursor.getCount() - 1];
            Log.d(TEST_TAG, String.format("Remove Size is = %d", cursor.getCount()));
            for (int i = 0; i < (cursor.getCount() - 1); i++) {
                cursor.moveToNext();
                removeIds[i] = cursor.getLong(columnIndex);
            }
            if (removeIds.length > 0) {
                Assert.assertEquals(removeIds.length, getDLManager().remove(removeIds));
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public int getTotalNumberDownloads() {
        Cursor cursor = null;
        try {
            Query query = new Query();
            cursor = getDLManager().query(query);
            return cursor.getCount();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public int getDownloadItemCountById(long[] downloadId) {
        Cursor cursor = null;
        int total = 0;
        try {
            Query query = new Query().setFilterById(downloadId);
            cursor = getDLManager().query(query);
            total = cursor.getCount();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return total;
    }

    public static String randomWord(int length) {
        Random random = new Random();
        StringBuilder result = new StringBuilder();
        for (int j = 0; j < length; j++) {
            int base = random.nextInt(2) == 0 ? 'A' : 'a';
            result.append((char) (random.nextInt(26) + base));
        }
        return result.toString();
    }

    /**
     * Class to hold size and time info on downloaded items
     */
    class DlObjSizeTimePair {
        int dlObjSize;
        long dlObjTimeInMilliSec;

        public DlObjSizeTimePair(int size, long time) {
            this.dlObjSize = size;
            this.dlObjTimeInMilliSec = time;
        }
    }

    public enum UIViewType {
        LIST {
            public String toString() {
                return "menu_list";
            }
        },
        GRID {
            public String toString() {
                return "menu_grid";
            }
        }
    };
}
