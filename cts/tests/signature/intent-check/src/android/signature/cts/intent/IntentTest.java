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
package android.signature.cts.intent;

import static android.signature.cts.CurrentApi.CURRENT_API_FILE;
import static android.signature.cts.CurrentApi.SYSTEM_CURRENT_API_FILE;
import static android.signature.cts.CurrentApi.SYSTEM_REMOVED_API_FILE;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.signature.cts.ApiDocumentParser;
import android.signature.cts.JDiffClassDescription;
import android.signature.cts.JDiffClassDescription.JDiffField;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import com.android.compatibility.common.util.DynamicConfigDeviceSide;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validate that the android intents used by APKs on this device are part of the
 * platform.
 */
@RunWith(AndroidJUnit4.class)
public class IntentTest {
    private static final String TAG = IntentTest.class.getSimpleName();

    private static final File SIGNATURE_TEST_PACKGES =
            new File("/data/local/tmp/signature-test-packages");
    private static final String ANDROID_INTENT_PREFIX = "android.intent.action";
    private static final String ACTION_LINE_PREFIX = "          Action: ";
    private static final String MODULE_NAME = "CtsIntentSignatureTestCases";

    private PackageManager mPackageManager;
    private Set<String> intentWhitelist;

    @Before
    public void setupPackageManager() throws Exception {
      mPackageManager = InstrumentationRegistry.getContext().getPackageManager();
      intentWhitelist = getIntentWhitelist();
    }

    @Test
    public void shouldNotFindUnexpectedIntents() throws Exception {
        Set<String> platformIntents = lookupPlatformIntents();
        platformIntents.addAll(intentWhitelist);

        Set<String> allInvalidIntents = new HashSet<>();

        Set<String> errors = new HashSet<>();
        List<ApplicationInfo> packages =
            mPackageManager.getInstalledApplications(PackageManager.GET_META_DATA);
        for (ApplicationInfo appInfo : packages) {
            if (!isSystemApp(appInfo) && !isUpdatedSystemApp(appInfo)) {
                // Only examine system apps
                continue;
            }
            Set<String> invalidIntents = new HashSet<>();
            Set<String> activeIntents = lookupActiveIntents(appInfo.packageName);

            for (String activeIntent : activeIntents) {
              String intent = activeIntent.trim();
              if (!platformIntents.contains(intent) &&
                    intent.startsWith(ANDROID_INTENT_PREFIX)) {
                  invalidIntents.add(activeIntent);
                  allInvalidIntents.add(activeIntent);
              }
            }

            String error = String.format("Package: %s Invalid Intent: %s",
                  appInfo.packageName, invalidIntents);
            if (!invalidIntents.isEmpty()) {
                errors.add(error);
            }
        }

        // Log the whitelist line to make it easy to update.
        for (String intent : allInvalidIntents) {
           Log.d(TAG, String.format("whitelist.add(\"%s\");", intent));
        }

        Assert.assertTrue(errors.toString(), errors.isEmpty());
    }

    private Set<String> lookupPlatformIntents() {
        try {
            Set<String> intents = new HashSet<>();
            intents.addAll(parse(CURRENT_API_FILE));
            intents.addAll(parse(SYSTEM_CURRENT_API_FILE));
            intents.addAll(parse(SYSTEM_REMOVED_API_FILE));
            return intents;
        } catch (XmlPullParserException | IOException e) {
            throw new RuntimeException("failed to parse", e);
        }
    }

    private static Set<String> parse(String apiFileName)
            throws XmlPullParserException, IOException {

        Set<String> androidIntents = new HashSet<>();

        ApiDocumentParser apiDocumentParser = new ApiDocumentParser(TAG,
                new ApiDocumentParser.Listener() {
                    @Override
                    public void completedClass(JDiffClassDescription classDescription) {
                        for (JDiffField diffField : classDescription.getFieldList()) {
                            String fieldValue = diffField.getValueString();
                            if (fieldValue != null) {
                                fieldValue = fieldValue.replace("\"", "");
                                if (fieldValue.startsWith(ANDROID_INTENT_PREFIX)) {
                                    androidIntents.add(fieldValue);
                                }
                            }
                        }

                    }
                });

        apiDocumentParser.parse(new FileInputStream(new File(apiFileName)));

        return androidIntents;
    }

    private static boolean isSystemApp(ApplicationInfo applicationInfo) {
        return (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
    }

    private static boolean isUpdatedSystemApp(ApplicationInfo applicationInfo) {
        return (applicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
    }

    private static Set<String> lookupActiveIntents(String packageName) {
        HashSet<String> activeIntents = new HashSet<>();
        File dumpsysPackage = new File(SIGNATURE_TEST_PACKGES, packageName + ".txt");
        if (!dumpsysPackage.exists() || dumpsysPackage.length() == 0) {
          throw new RuntimeException("Missing package info: " + dumpsysPackage.getAbsolutePath());
        }
        try (
            BufferedReader in = new BufferedReader(
                  new InputStreamReader(new FileInputStream(dumpsysPackage)))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith(ACTION_LINE_PREFIX)) {
                    String intent = line.substring(
                          ACTION_LINE_PREFIX.length(), line.length() - 1);
                    activeIntents.add(intent.replace("\"", ""));
                }
            }
            return activeIntents;
        } catch (Exception e) {
          throw new RuntimeException("While retrieving dumpsys", e);
        }
    }

    private static Set<String> getIntentWhitelist() throws Exception {
        Set<String> whitelist = new HashSet<>();

        DynamicConfigDeviceSide dcds = new DynamicConfigDeviceSide(MODULE_NAME);
        List<String> intentWhitelist = dcds.getValues("intent_whitelist");

        // Log the whitelist Intent
        for (String intent : intentWhitelist) {
           Log.d(TAG, String.format("whitelist add: %s", intent));
           whitelist.add(intent);
        }

        return whitelist;
    }
}
