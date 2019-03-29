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

package com.android.compatibility.common.tradefed.result;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.json.stream.JsonWriter;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionCopier;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.IShardableListener;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Map;

/**
 * Write test metadata to the result/metadata folder.
 */
public class MetadataReporter implements IShardableListener {

    @Option(name = "include-failure-time", description = "Include timing about tests that failed.")
    private boolean mIncludeFailures = false;

    @Option(name = "min-test-duration", description = "Ignore test durations less than this.",
            isTimeVal = true)
    private long mMinTestDuration = 2 * 1000;

    private static final String METADATA_DIR = "metadata";
    private CompatibilityBuildHelper mBuildHelper;
    private File mMetadataDir;
    private long mStartTime;
    private String mCurrentModule;
    private boolean mTestFailed;
    private Collection<TestMetadata> mTestMetadata = new LinkedList<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public IShardableListener clone() {
        MetadataReporter clone = new MetadataReporter();
        OptionCopier.copyOptionsNoThrow(this, clone);
        return clone;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationStarted(IInvocationContext context) {
        IBuildInfo buildInfo = context.getBuildInfos().get(0);
        synchronized(this) {
            if (mBuildHelper == null) {
                mBuildHelper = new CompatibilityBuildHelper(buildInfo);
                try {
                    mMetadataDir = new File(mBuildHelper.getResultDir(), METADATA_DIR);
                } catch (FileNotFoundException e) {
                    throw new RuntimeException("Metadata Directory was not created: " +
                            mMetadataDir.getAbsolutePath());
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testRunStarted(String id, int numTests) {
        this.mCurrentModule = id;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testStarted(TestIdentifier test) {
        mStartTime = System.currentTimeMillis();
        mTestFailed = false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testFailed(TestIdentifier test, String trace) {
        mTestFailed = true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testIgnored(TestIdentifier test) {
        mTestFailed = true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testAssumptionFailure(TestIdentifier test, String trace) {
        mTestFailed = true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testEnded(TestIdentifier test, Map<String, String> testMetrics) {
        long duration = System.currentTimeMillis() - mStartTime;
        if (mTestFailed && !mIncludeFailures) {
            return;
        }
        if (duration < mMinTestDuration) {
            return;
        }

        TestMetadata metadata = new TestMetadata();
        metadata.testId = buildTestId(test);
        metadata.seconds = duration / 1000; // convert to second for reporting
        mTestMetadata.add(metadata);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testRunEnded(long elapsedTime, Map<String, String> metrics) {
        if (!mTestMetadata.isEmpty()) {
            tryWriteToFile(mBuildHelper, mCurrentModule, mMetadataDir, mTestMetadata);
        }
        mTestMetadata.clear();
    }

    /** Information about a test's execution. */
    public static class TestMetadata {
        // The id of the test
        String testId;
        // The duration of the test.
        long seconds;
    }

    private static String buildTestId(TestIdentifier test) {
        return String.format("%s.%s", test.getClassName(), test.getTestName());
    }

    private static void tryWriteToFile(
            CompatibilityBuildHelper compatibilityBuildHelper,
            String moduleName,
            File metadataDir,
            Collection<TestMetadata> metadatas) {

        metadataDir.mkdirs();

        String moduleFileName = moduleName + "." + System.currentTimeMillis() + ".json";
        File metadataFile = new File(metadataDir, moduleFileName);
        Map<String, String> buildAttributes =
                compatibilityBuildHelper.getBuildInfo().getBuildAttributes();
        try (JsonWriter writer = new JsonWriter(new PrintWriter(metadataFile))) {
            writer.beginObject();

            writer.name("fingerprint");
            writer.value(buildAttributes.get("cts:build_fingerprint"));

            writer.name("product");
            writer.value(buildAttributes.get("cts:build_product"));

            writer.name("build_id");
            writer.value(buildAttributes.get("cts:build_id"));

            writer.name("suite_version");
            writer.value(compatibilityBuildHelper.getSuiteVersion());

            writer.name("suite_name");
            writer.value(compatibilityBuildHelper.getSuiteName());

            writer.name("suite_build");
            writer.value(compatibilityBuildHelper.getSuiteBuild());

            writer.name("module_id");
            writer.value(moduleName);

            writer.name("test");
            writer.beginArray();
            for (TestMetadata metadata : metadatas) {
                writer.beginObject();
                writer.name("id");
                writer.value(metadata.testId);
                writer.name("sec");
                writer.value(metadata.seconds);
                writer.endObject();
            }
            writer.endArray();

            writer.endObject();
        } catch (IOException e) {
            CLog.e("[%s] While saving metadata.", metadataFile.getAbsolutePath());
            CLog.e(e);
        }
    }

    protected Collection<TestMetadata> getTestMetadata() {
        return Collections.unmodifiableCollection(mTestMetadata);
    }
}
