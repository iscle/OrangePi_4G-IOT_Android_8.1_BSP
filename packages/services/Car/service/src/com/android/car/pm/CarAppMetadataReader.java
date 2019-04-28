/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.car.pm;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.util.Log;

import com.android.car.CarLog;

import java.io.IOException;
import java.util.LinkedList;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * Read meta data containing car application info.
 */
public class CarAppMetadataReader {
    /** Name of the meta-data attribute for the automotive application XML resource */
    private static final String METADATA_ATTRIBUTE = "android.car.application";
    /** Name of the tag to declare automotive usage */
    private static final String USES_TAG = "uses";
    /** Name of the attribute to name the usage type */
    private static final String NAME_ATTRIBUTE = "name";

    private static final String NAME_ATTR_TYPE_SERVICE = "service";
    private static final String NAME_ATTR_TYPE_ACTIVITY = "activity";

    private static final String CLASS_ATTRIBUTE = "class";

    public static CarAppMetadataInfo parseMetadata(Context context, String packageName) {
        int metadataId = 0;
        Resources resources = null;
        try {
            metadataId = getMetadataId(context, packageName);
            if (metadataId == 0) {
                return null;
            }
            PackageManager pm = context.getPackageManager();
            resources = pm.getResourcesForApplication(packageName);
        } catch (NameNotFoundException e) {
            Log.w(CarLog.TAG_PACKAGE, "Cannot read mta data, package:" + packageName, e);
            return null;
        }

        // Try to open the XML resource
        XmlResourceParser parser = null;
        try {
            parser = resources.getXml(metadataId);
        } catch (Resources.NotFoundException e) {
            Log.w(CarLog.TAG_PACKAGE, "Resource not found [" + packageName + "]");
            return null;
        }

        boolean useService = false;
        boolean useAllActivities = false;
        LinkedList<String> activities = null;
        // Now, read the XML and pull out the appropriate "uses" attribute
        int eventType;
        try {
            eventType = parser.next();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && USES_TAG.equals(parser.getName())) {
                    String nameAttribute =
                            parser.getAttributeValue(null /*namespace*/, NAME_ATTRIBUTE);
                    if (nameAttribute == null) {
                        Log.w(CarLog.TAG_PACKAGE, "Bad metadata," + METADATA_ATTRIBUTE +
                                " for pavkage:" + packageName);
                        return null;
                    }
                    switch (nameAttribute) {
                        case NAME_ATTR_TYPE_SERVICE:
                            useService = true;
                            break;
                        case NAME_ATTR_TYPE_ACTIVITY: {
                            String classAttribute = parser.getAttributeValue(null /*namespace*/,
                                    CLASS_ATTRIBUTE);
                            if (classAttribute == null) { // all activities
                                useAllActivities = true;
                            } else {
                                if (activities == null) {
                                    activities = new LinkedList<>();
                                }
                                activities.add(classAttribute);
                            }
                        } break;
                    }
                }
                eventType = parser.next();
            }
        } catch (XmlPullParserException | IOException e) {
            Log.w(CarLog.TAG_PACKAGE, "Resource not parsable [" + packageName + "]");
            return null;
        }
        String[] activityStrings = null;
        if (activities != null) {
            activityStrings = activities.toArray(new String[activities.size()]);
        }
        return new CarAppMetadataInfo(useService, useAllActivities, activityStrings);
    }

    /**
     * Returns the resource ID of the automotive application XML
     * @throws NameNotFoundException
     */
    private static int getMetadataId(Context context, String packageName)
            throws NameNotFoundException {
        final PackageManager pm = context.getPackageManager();
        ApplicationInfo appInfo;
        appInfo = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA /*flags*/);
        if (appInfo.metaData == null) {
            return 0;
        }
        return appInfo.metaData.getInt(METADATA_ATTRIBUTE, 0);
    }

    /**
     * App's metada for car application
     */
    public static class CarAppMetadataInfo {
        /** has "service" use */
        public final boolean useService;
        /** has "activity" use with no specific class */
        public final boolean useAllActivities;
        /** has "activity" uses with specific classes */
        public String[] activities;

        private CarAppMetadataInfo(boolean useService, boolean useAllActivities,
                String[] activities) {
            this.useService = useService;
            this.useAllActivities = useAllActivities;
            this.activities = activities;
        }
    }
}
