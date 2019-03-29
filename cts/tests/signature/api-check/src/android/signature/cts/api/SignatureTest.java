/*
 * Copyright (C) 2011 The Android Open Source Project
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

package android.signature.cts.api;

import android.os.Bundle;
import android.signature.cts.ApiDocumentParser;
import android.signature.cts.ApiComplianceChecker;
import android.signature.cts.FailureType;
import android.signature.cts.JDiffClassDescription;
import android.signature.cts.ReflectionHelper;
import android.signature.cts.ResultObserver;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import org.xmlpull.v1.XmlPullParserException;
import repackaged.android.test.InstrumentationTestCase;
import repackaged.android.test.InstrumentationTestRunner;

import static android.signature.cts.CurrentApi.API_FILE_DIRECTORY;

/**
 * Performs the signature check via a JUnit test.
 */
public class SignatureTest extends InstrumentationTestCase {

    private static final String TAG = SignatureTest.class.getSimpleName();

    /**
     * A set of class names that are inaccessible for some reason.
     */
    private static final Set<String> KNOWN_INACCESSIBLE_CLASSES = new HashSet<>();

    static {
        // TODO(b/63383787) - These classes, which are nested annotations with @Retention(SOURCE)
        // are removed from framework.dex for an as yet unknown reason.
        KNOWN_INACCESSIBLE_CLASSES.add("android.content.pm.PackageManager.PermissionFlags");
        KNOWN_INACCESSIBLE_CLASSES.add("android.hardware.radio.ProgramSelector.IdentifierType");
        KNOWN_INACCESSIBLE_CLASSES.add("android.hardware.radio.ProgramSelector.ProgramType");
        KNOWN_INACCESSIBLE_CLASSES.add("android.hardware.radio.RadioManager.Band");
        KNOWN_INACCESSIBLE_CLASSES.add("android.os.UserManager.UserRestrictionSource");
        KNOWN_INACCESSIBLE_CLASSES.add(
                "android.service.persistentdata.PersistentDataBlockManager.FlashLockState");
    }

    private TestResultObserver mResultObserver;

    private String[] expectedApiFiles;
    private String[] unexpectedApiFiles;

    private class TestResultObserver implements ResultObserver {

        boolean mDidFail = false;

        StringBuilder mErrorString = new StringBuilder();

        @Override
        public void notifyFailure(FailureType type, String name, String errorMessage) {
            mDidFail = true;
            mErrorString.append("\n");
            mErrorString.append(type.toString().toLowerCase());
            mErrorString.append(":\t");
            mErrorString.append(name);
            mErrorString.append("\tError: ");
            mErrorString.append(errorMessage);
        }
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mResultObserver = new TestResultObserver();

        // Get the arguments passed to the instrumentation.
        Bundle instrumentationArgs =
                ((InstrumentationTestRunner) getInstrumentation()).getArguments();

        expectedApiFiles = getCommaSeparatedList(instrumentationArgs, "expected-api-files");
        unexpectedApiFiles = getCommaSeparatedList(instrumentationArgs, "unexpected-api-files");
    }

    private String[] getCommaSeparatedList(Bundle instrumentationArgs, String key) {
        String argument = instrumentationArgs.getString(key);
        if (argument == null) {
            return new String[0];
        }
        return argument.split(",");
    }

    /**
     * Tests that the device's API matches the expected set defined in xml.
     * <p/>
     * Will check the entire API, and then report the complete list of failures
     */
    public void testSignature() {
        try {
            Set<JDiffClassDescription> unexpectedClasses = loadUnexpectedClasses();
            for (JDiffClassDescription classDescription : unexpectedClasses) {
                Class<?> unexpectedClass = findUnexpectedClass(classDescription);
                if (unexpectedClass != null) {
                    mResultObserver.notifyFailure(
                            FailureType.UNEXPECTED_CLASS,
                            classDescription.getAbsoluteClassName(),
                            "Class should not be accessible to this APK");
                }
            }

            ApiComplianceChecker complianceChecker = new ApiComplianceChecker(mResultObserver);
            ApiDocumentParser apiDocumentParser = new ApiDocumentParser(
                    TAG, new ApiDocumentParser.Listener() {
                @Override
                public void completedClass(JDiffClassDescription classDescription) {
                    // Ignore classes that are known to be inaccessible.
                    if (KNOWN_INACCESSIBLE_CLASSES.contains(classDescription.getAbsoluteClassName())) {
                        return;
                    }

                    // Ignore unexpected classes that are in the API definition.
                    if (!unexpectedClasses.contains(classDescription)) {
                        complianceChecker.checkSignatureCompliance(classDescription);
                    }
                }
            });

            for (String expectedApiFile : expectedApiFiles) {
                File file = new File(API_FILE_DIRECTORY + "/" + expectedApiFile);
                apiDocumentParser.parse(new FileInputStream(file));
            }
        } catch (Exception e) {
            mResultObserver.notifyFailure(FailureType.CAUGHT_EXCEPTION, e.getMessage(),
                    e.getMessage());
        }
        if (mResultObserver.mDidFail) {
            StringBuilder errorString = mResultObserver.mErrorString;
            ClassLoader classLoader = getClass().getClassLoader();
            errorString.append("\nClassLoader hierarchy\n");
            while (classLoader != null) {
                errorString.append("    ").append(classLoader).append("\n");
                classLoader = classLoader.getParent();
            }
            fail(errorString.toString());
        }
    }

    private Class<?> findUnexpectedClass(JDiffClassDescription classDescription) {
        try {
            return ReflectionHelper.findMatchingClass(classDescription);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private Set<JDiffClassDescription> loadUnexpectedClasses()
            throws IOException, XmlPullParserException {

        Set<JDiffClassDescription> unexpectedClasses = new TreeSet<>(
                Comparator.comparing(JDiffClassDescription::getAbsoluteClassName));
        ApiDocumentParser apiDocumentParser = new ApiDocumentParser(TAG,
                new ApiDocumentParser.Listener() {
                    @Override
                    public void completedClass(JDiffClassDescription classDescription) {
                        unexpectedClasses.add(classDescription);
                    }
                });
        for (String expectedApiFile : unexpectedApiFiles) {
            File file = new File(API_FILE_DIRECTORY + "/" + expectedApiFile);
            apiDocumentParser.parse(new FileInputStream(file));
        }
        return unexpectedClasses;
    }
}
