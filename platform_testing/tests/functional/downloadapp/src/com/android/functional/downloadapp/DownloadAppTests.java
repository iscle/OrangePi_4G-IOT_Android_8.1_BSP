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

import android.content.Context;
import android.os.Environment;
import android.os.SystemClock;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.Direction;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.test.InstrumentationTestCase;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;
import android.test.suitebuilder.annotation.Suppress;
import android.util.Log;

import com.android.functional.downloadapp.DownloadAppTestHelper.UIViewType;

import junit.framework.Assert;

import java.util.Random;

public class DownloadAppTests extends InstrumentationTestCase {
    private DownloadAppTestHelper mDLAppHelper = null;
    private UiDevice mDevice = null;
    private Context mContext = null;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mDevice = UiDevice.getInstance(getInstrumentation());
        mContext = getInstrumentation().getContext();
        mDLAppHelper = DownloadAppTestHelper.getInstance(mDevice, mContext);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    @SmallTest
    public void testAddCompletedDownload() throws Exception {
        Random random = new Random();
        Long dlId = mDLAppHelper.addToDownloadContentDB(
                String.format("%s.pdf", DownloadAppTestHelper.randomWord(random.nextInt(8) + 2)),
                String.format("%s Desc", DownloadAppTestHelper.randomWord(random.nextInt(9) + 4)),
                Boolean.FALSE,
                DownloadAppTestHelper.FILE_TYPES[random.nextInt(
                        DownloadAppTestHelper.FILE_TYPES.length)],
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        .getAbsolutePath(),
                random.nextInt(2 * mDLAppHelper.TIMEOUT), Boolean.FALSE);
        assertTrue("Download item <> 1",
                1 == mDLAppHelper.getDownloadItemCountById(new long[] {
                        dlId
                }));
    }

    @MediumTest
    public void testScroll() {
        mDLAppHelper.populateContentInDLApp(20);
        mDLAppHelper.launchApp(DownloadAppTestHelper.PACKAGE_NAME, DownloadAppTestHelper.APP_NAME);
        UiObject2 container = mDevice.wait(Until.findObject(
                By.res("com.android.documentsui:id/container_directory")), mDLAppHelper.TIMEOUT);
        container.scroll(Direction.UP, 1.0f);
        mDevice.waitForIdle();
        container.scroll(Direction.DOWN, 1.0f);
    }

    @MediumTest
    public void testSortByName() throws Exception {
        Log.d(mDLAppHelper.TEST_TAG, String.format("Before sortbyname tests, total count is %d",
                mDLAppHelper.getTotalNumberDownloads()));
        mDLAppHelper.populateContentInDLApp(5);
        mDLAppHelper.launchApp(DownloadAppTestHelper.PACKAGE_NAME, DownloadAppTestHelper.APP_NAME);
        mDLAppHelper.sortByParam("name");
        assertTrue("DL items can't be sorted by name", mDLAppHelper.verifySortedByName());
    }

    @Suppress
    @MediumTest
    public void testSortBySize() {
        mDLAppHelper.populateContentInDLApp(5);
        mDLAppHelper.launchApp(DownloadAppTestHelper.PACKAGE_NAME, DownloadAppTestHelper.APP_NAME);
        mDLAppHelper.sortByParam("size");
        assertTrue("DL items can't be sorted by size", mDLAppHelper.verifySortedBySize());
    }

    @MediumTest
    public void testSortByTime() {
        mDLAppHelper.populateContentInDLApp(5);
        mDLAppHelper.launchApp(DownloadAppTestHelper.PACKAGE_NAME, DownloadAppTestHelper.APP_NAME);
        mDLAppHelper.sortByParam("date modified");
        assertTrue("DL items can't be sorted by time", mDLAppHelper.verifySortedByTime());
    }

    @MediumTest
    public void testToggleViewTypeForDownloadItems() {
        mDLAppHelper.populateContentInDLApp(10);
        mDLAppHelper.launchApp(DownloadAppTestHelper.PACKAGE_NAME, DownloadAppTestHelper.APP_NAME);
        mDevice.wait(Until.findObject(By.res(
                String.format("%s:id/%s", DownloadAppTestHelper.PACKAGE_NAME, UIViewType.LIST))),
                2 * mDLAppHelper.TIMEOUT).click();
        mDLAppHelper.verifyDownloadViewType(UIViewType.GRID);
        mDevice.wait(Until.findObject(By.res(
                String.format("%s:id/%s", DownloadAppTestHelper.PACKAGE_NAME, UIViewType.GRID))),
                2 * mDLAppHelper.TIMEOUT).click();
        mDLAppHelper.verifyDownloadViewType(UIViewType.LIST);
    }

    @MediumTest
    public void testCABMenuShow() {
        mDLAppHelper.populateContentInDLApp(10);
        mDLAppHelper.launchApp(DownloadAppTestHelper.PACKAGE_NAME, DownloadAppTestHelper.APP_NAME);
        mDevice.wait(Until.findObject(By.res("com.android.documentsui:id/dir_list")),
                mDLAppHelper.TIMEOUT).getChildren().get(1).click(1 * 2 * mDLAppHelper.TIMEOUT);
        UiObject2 cabMenuObj = null;
        int counter = 5;
        while ((cabMenuObj = mDevice.wait(Until.findObject(By.res(String.format("%s:id/menu_share",
                DownloadAppTestHelper.PACKAGE_NAME))), mDLAppHelper.TIMEOUT)) == null
                && counter-- > 0);
        Assert.assertNotNull(cabMenuObj);
        counter = 5;
        while ((cabMenuObj = mDevice.wait(Until.findObject(By.res(String.format("%s:id/menu_delete",
                DownloadAppTestHelper.PACKAGE_NAME))), mDLAppHelper.TIMEOUT)) == null
                && counter-- > 0);
        Assert.assertNotNull(cabMenuObj);
        counter = 5;
        while ((cabMenuObj = mDevice.wait(Until.findObject(
                By.desc("More options")), mDLAppHelper.TIMEOUT)) == null && counter-- > 0)
            ;
        Assert.assertNotNull(cabMenuObj);
        while ((cabMenuObj = mDevice.wait(Until.findObject(
                By.desc("Done")), mDLAppHelper.TIMEOUT)) == null && counter-- > 0)
            ;
        Assert.assertNotNull(cabMenuObj);
        cabMenuObj.click();
        SystemClock.sleep(2 * mDLAppHelper.TIMEOUT);
    }

    @MediumTest
    public void testCABMenuDelete() {
        mDLAppHelper.populateContentInDLApp(10);
        mDLAppHelper.launchApp(DownloadAppTestHelper.PACKAGE_NAME, DownloadAppTestHelper.APP_NAME);
        UiObject2 deleteObj = mDevice.wait(Until.findObject(
                By.res("com.android.documentsui:id/dir_list")), mDLAppHelper.TIMEOUT)
                .getChildren().get(1);
        String deleteObjText = deleteObj.getText();
        deleteObj.click(1 * 2 * mDLAppHelper.TIMEOUT);
        int counter = 5;
        UiObject2 cabMenuObj = null;
        while ((cabMenuObj = mDevice.wait(Until.findObject(By.res(String.format("%s:id/menu_delete",
                DownloadAppTestHelper.PACKAGE_NAME))), 2 * mDLAppHelper.TIMEOUT)) == null
                && counter-- > 0);
        cabMenuObj.click();
        UiObject2 deleteBtn = mDevice.wait(Until.findObject(
                By.textContains("Delete")), mDLAppHelper.TIMEOUT);
        if(deleteBtn != null) {
            mDevice.wait(Until.findObject(By.text("OK")), 2 * mDLAppHelper.TIMEOUT).click();
        }
        Assert.assertFalse("", mDLAppHelper.getDownloadItemNames().contains(deleteObjText));
    }
}
