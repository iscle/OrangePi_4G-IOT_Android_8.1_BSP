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

package com.android.compatibility.common.generator;

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.kxml2.io.KXmlSerializer;

public class ManifestGenerator {

    private static final String DEFAULT_MIN_SDK = "8";

    private static final String USAGE = "Usage: "
        + "manifest-generator -n NAME -p PACKAGE_NAME -o OUTPUT_FILE -i INSTRUMENT_NAME "
        + "[-s MIN_SDK_VERSION] [-t TARGET_SDK_VERSION] [-r PERMISSION]+ [-a ACTIVITY]+";
    private static final String MANIFEST = "manifest";
    private static final String USES_SDK = "uses-sdk";
    private static final String USES_PERMISSION = "uses-permission";
    private static final String APPLICATION = "application";
    private static final String INSTRUMENTATION = "instrumentation";
    private static final String ACTIVITY = "activity";

    public static void main(String[] args) {
        String pkgName = null;
        String instrumentName = null;
        String minSdk = DEFAULT_MIN_SDK;
        String targetSdk = null;
        List<String> permissions = new ArrayList<>();
        List<String> activities = new ArrayList<>();
        String output = null;

        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals("-p")) {
                pkgName = args[++i];
            } else if (args[i].equals("-a")) {
                activities.add(args[++i]);
            } else if (args[i].equals("-o")) {
                output = args[++i];
            } else if (args[i].equals("-i")) {
                instrumentName = args[++i];
            } else if (args[i].equals("-r")) {
                permissions.add(args[++i]);
            } else if (args[i].equals("-s")) {
                minSdk = args[++i];
            } else if (args[i].equals("-t")) {
                targetSdk = args[++i];
            }
        }

        if (pkgName == null) {
            error("Missing package name");
        } else if (instrumentName == null) {
            error("Missing instrumentation name");
        } else if (activities.isEmpty()) {
            error("No activities");
        } else if (output == null) {
            error("Missing output file");
        }

        FileOutputStream out = null;
        try {
          out = new FileOutputStream(output);
          generate(out, pkgName, instrumentName, minSdk, targetSdk, permissions, activities);
        } catch (Exception e) {
          System.err.println("Couldn't create manifest file");
        } finally {
          if (out != null) {
              try {
                  out.close();
              } catch (Exception e) {
                  // Ignore
              }
          }
        }
    }

    /*package*/ static void generate(OutputStream out, String pkgName, String instrumentName,
            String minSdk, String targetSdk, List<String> permissions, List<String> activities)
            throws Exception {
        final String ns = null;
        KXmlSerializer serializer = new KXmlSerializer();
        serializer.setOutput(out, "UTF-8");
        serializer.startDocument("UTF-8", true);
        serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
        serializer.startTag(ns, MANIFEST);
        serializer.attribute(ns, "xmlns:android", "http://schemas.android.com/apk/res/android");
        serializer.attribute(ns, "package", pkgName);
        serializer.startTag(ns, USES_SDK);
        serializer.attribute(ns, "android:minSdkVersion", minSdk);
        if (targetSdk != null) {
            serializer.attribute(ns, "android:targetSdkVersion", targetSdk);
        }
        serializer.endTag(ns, USES_SDK);
        for (String permission : permissions) {
            serializer.startTag(ns, USES_PERMISSION);
            serializer.attribute(ns, "android:name", permission);
            serializer.endTag(ns, USES_PERMISSION);
        }
        serializer.startTag(ns, APPLICATION);
        for (String activity : activities) {
            serializer.startTag(ns, ACTIVITY);
            serializer.attribute(ns, "android:name", activity);
            serializer.endTag(ns, ACTIVITY);
        }
        serializer.endTag(ns, APPLICATION);
        serializer.startTag(ns, INSTRUMENTATION);
        serializer.attribute(ns, "android:name", instrumentName);
        serializer.attribute(ns, "android:targetPackage", pkgName);
        serializer.endTag(ns, INSTRUMENTATION);
        serializer.endTag(ns, MANIFEST);
        serializer.endDocument();
        out.flush();
    }

    private static void error(String message) {
        System.err.println(message);
        System.err.println(USAGE);
        System.exit(1);
    }
}
