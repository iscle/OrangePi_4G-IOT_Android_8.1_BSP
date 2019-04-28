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

package com.android.managedprovisioning.task.nonrequiredapps;

import static com.android.internal.util.Preconditions.checkNotNull;

import android.annotation.Nullable;
import android.app.AppGlobals;
import android.content.Context;
import android.content.pm.IPackageManager;
import android.util.Xml;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.FastXmlSerializer;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

/**
 * Stores and retrieves the system apps that were on the device during provisioning and on
 * subsequent OTAs.
 */
public class SystemAppsSnapshot {
    private static final String TAG_SYSTEM_APPS = "system-apps";
    private static final String TAG_PACKAGE_LIST_ITEM = "item";
    private static final String ATTR_VALUE = "value";

    private final Context mContext;
    private final IPackageManager mIPackageManager;
    private final Utils mUtils;

    public SystemAppsSnapshot(Context context) {
        this(context, AppGlobals.getPackageManager(), new Utils());
    }

    @VisibleForTesting
    SystemAppsSnapshot(
            Context context,
            IPackageManager iPackageManager,
            Utils utils) {
        mContext = checkNotNull(context);
        mIPackageManager = checkNotNull(iPackageManager);
        mUtils = checkNotNull(utils);
    }

    /**
     * Returns whether currently a snapshot exists for the given user.
     *
     * @param userId the user id for which the snapshot is requested.
     */
    public boolean hasSnapshot(int userId) {
        return getSystemAppsFile(mContext, userId).exists();
    }

    /**
     * Returns the last stored snapshot for the given user.
     *
     * @param userId the user id for which the snapshot is requested.
     */
    public Set<String> getSnapshot(int userId) {
        return readSystemApps(getSystemAppsFile(mContext, userId));
    }

    /**
     * Call this method to take a snapshot of the current set of system apps.
     *
     * @param userId the user id for which the snapshot should be taken.
     */
    public void takeNewSnapshot(int userId) {
        final File systemAppsFile = getSystemAppsFile(mContext, userId);
        systemAppsFile.getParentFile().mkdirs(); // Creating the folder if it does not exist
        writeSystemApps(mUtils.getCurrentSystemApps(mIPackageManager, userId), systemAppsFile);
    }

    private void writeSystemApps(Set<String> packageNames, File systemAppsFile) {
        try {
            FileOutputStream stream = new FileOutputStream(systemAppsFile, false);
            XmlSerializer serializer = new FastXmlSerializer();
            serializer.setOutput(stream, "utf-8");
            serializer.startDocument(null, true);
            serializer.startTag(null, TAG_SYSTEM_APPS);
            for (String packageName : packageNames) {
                serializer.startTag(null, TAG_PACKAGE_LIST_ITEM);
                serializer.attribute(null, ATTR_VALUE, packageName);
                serializer.endTag(null, TAG_PACKAGE_LIST_ITEM);
            }
            serializer.endTag(null, TAG_SYSTEM_APPS);
            serializer.endDocument();
            stream.close();
        } catch (IOException e) {
            ProvisionLogger.loge("IOException trying to write the system apps", e);
        }
    }

    private Set<String> readSystemApps(File systemAppsFile) {
        Set<String> result = new HashSet<>();
        if (!systemAppsFile.exists()) {
            return result;
        }
        try {
            FileInputStream stream = new FileInputStream(systemAppsFile);

            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(stream, null);
            parser.next();

            int type;
            int outerDepth = parser.getDepth();
            while ((type=parser.next()) != XmlPullParser.END_DOCUMENT
                    && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
                if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                    continue;
                }
                String tag = parser.getName();
                if (tag.equals(TAG_PACKAGE_LIST_ITEM)) {
                    result.add(parser.getAttributeValue(null, ATTR_VALUE));
                } else {
                    ProvisionLogger.loge("Unknown tag: " + tag);
                }
            }
            stream.close();
        } catch (IOException e) {
            ProvisionLogger.loge("IOException trying to read the system apps", e);
        } catch (XmlPullParserException e) {
            ProvisionLogger.loge("XmlPullParserException trying to read the system apps", e);
        }
        return result;
    }

    @VisibleForTesting
    static File getSystemAppsFile(Context context, int userId) {
        return new File(context.getFilesDir() + File.separator + "system_apps"
                + File.separator + "user" + userId + ".xml");
    }
}
