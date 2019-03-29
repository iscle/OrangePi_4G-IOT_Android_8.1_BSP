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

package com.android.compatibility.common.util;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.security.DigestException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;

/***
 * Calculate and store checksum values for files and test results
 */
public final class ChecksumReporter implements Serializable {

    public static final String NAME = "checksum.data";
    public static final String PREV_NAME = "checksum.previous.data";

    private static final double DEFAULT_FPP = 0.05;
    private static final String SEPARATOR = "/";
    private static final String ID_SEPARATOR = "@";
    private static final String NAME_SEPARATOR = ".";

    private static final short CURRENT_VERSION = 1;
    // Serialized format Id (ie magic number) used to identify serialized data.
    static final short SERIALIZED_FORMAT_CODE = 650;

    private final BloomFilter<CharSequence> mResultChecksum;
    private final HashMap<String, byte[]> mFileChecksum;
    private final short mVersion;

    /***
     * Calculate checksum of test results and files in result directory and write to disk
     * @param dir test results directory
     * @param result test results
     * @return true if successful, false if unable to calculate or store the checksum
     */
    public static boolean tryCreateChecksum(File dir, IInvocationResult result) {
        try {
            int totalCount = countTestResults(result);
            ChecksumReporter checksumReporter =
                    new ChecksumReporter(totalCount, DEFAULT_FPP, CURRENT_VERSION);
            checksumReporter.addInvocation(result);
            checksumReporter.addDirectory(dir);
            checksumReporter.saveToFile(dir);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    /***
     * Create Checksum Reporter from data saved on disk
     * @param directory
     * @return
     * @throws ChecksumValidationException
     */
    public static ChecksumReporter load(File directory) throws ChecksumValidationException {
        ChecksumReporter reporter = new ChecksumReporter(directory);
        if (reporter.getCapacity() > 1.1) {
            throw new ChecksumValidationException("Capacity exceeded.");
        }
        return reporter;
    }

    /***
     * Deserialize checksum from file
     * @param directory the parent directory containing the checksum file
     * @throws ChecksumValidationException
     */
    public ChecksumReporter(File directory) throws ChecksumValidationException {
        File file = new File(directory, ChecksumReporter.NAME);
        try (FileInputStream fileStream = new FileInputStream(file);
            InputStream outputStream = new BufferedInputStream(fileStream);
            ObjectInput objectInput = new ObjectInputStream(outputStream)) {
            short magicNumber = objectInput.readShort();
            switch (magicNumber) {
                case SERIALIZED_FORMAT_CODE:
                   mVersion = objectInput.readShort();
                    mResultChecksum = (BloomFilter<CharSequence>) objectInput.readObject();
                    mFileChecksum = (HashMap<String, byte[]>) objectInput.readObject();
                    break;
                default:
                    throw new ChecksumValidationException("Unknown format of serialized data.");
            }
        } catch (Exception e) {
            throw new ChecksumValidationException("Unable to load checksum from file", e);
        }
        if (mVersion > CURRENT_VERSION) {
            throw new ChecksumValidationException(
                    "File contains a newer version of ChecksumReporter");
        }
    }

    /***
     * Create new instance of ChecksumReporter
     * @param testCount the number of test results that will be stored
     * @param fpp the false positive percentage for result lookup misses
     */
    public ChecksumReporter(int testCount, double fpp, short version) {
        mResultChecksum = BloomFilter.create(Funnels.unencodedCharsFunnel(),
                testCount, fpp);
        mFileChecksum = new HashMap<>();
        mVersion = version;
    }

    /***
     * Add each test result from each module and test case
     */
    public void addInvocation(IInvocationResult invocationResult) {
        for (IModuleResult module : invocationResult.getModules()) {
            String buildFingerprint = invocationResult.getBuildFingerprint();
            addModuleResult(module, buildFingerprint);
            for (ICaseResult caseResult : module.getResults()) {
                for (ITestResult testResult : caseResult.getResults()) {
                    addTestResult(testResult, module, buildFingerprint);
                }
            }
        }
    }

    /***
     * Calculate CRC of file and store the result
     * @param file crc calculated on this file
     * @param path part of the key to identify the files crc
     */
    public void addFile(File file, String path) {
        byte[] crc;
        try {
            crc = calculateFileChecksum(file);
        } catch (ChecksumValidationException e) {
            crc = new byte[0];
        }
        String key = path + SEPARATOR + file.getName();
        mFileChecksum.put(key, crc);
    }

    @VisibleForTesting
    public boolean containsFile(File file, String path) {
        String key = path + SEPARATOR + file.getName();
        if (mFileChecksum.containsKey(key))
        {
            try {
                byte[] crc = calculateFileChecksum(file);
                return Arrays.equals(mFileChecksum.get(key), crc);
            } catch (ChecksumValidationException e) {
                return false;
            }
        }
        return false;
    }

    /***
     * Adds all child files recursively through all sub directories
     * @param directory target that is deeply searched for files
     */
    public void addDirectory(File directory) {
        addDirectory(directory, directory.getName());
    }

    /***
     * @param path the relative path to the current directory from the base directory
     */
    private void addDirectory(File directory, String path) {
        for(String childName : directory.list()) {
            File child = new File(directory, childName);
            if (child.isDirectory()) {
                addDirectory(child, path + SEPARATOR + child.getName());
            } else {
                addFile(child, path);
            }
        }
    }

    /***
     * Calculate checksum of test result and store the value
     * @param testResult the target of the checksum
     * @param moduleResult the module that contains the test result
     * @param buildFingerprint the fingerprint the test execution is running against
     */
    public void addTestResult(
        ITestResult testResult, IModuleResult moduleResult, String buildFingerprint) {

        String signature = generateTestResultSignature(testResult, moduleResult, buildFingerprint);
        mResultChecksum.put(signature);
    }

    @VisibleForTesting
    public boolean containsTestResult(
            ITestResult testResult, IModuleResult moduleResult, String buildFingerprint) {

        String signature = generateTestResultSignature(testResult, moduleResult, buildFingerprint);
        return mResultChecksum.mightContain(signature);
    }

    /***
     * Calculate checksm of module result and store value
     * @param moduleResult  the target of the checksum
     * @param buildFingerprint the fingerprint the test execution is running against
     */
    public void addModuleResult(IModuleResult moduleResult, String buildFingerprint) {
        mResultChecksum.put(
                generateModuleResultSignature(moduleResult, buildFingerprint));
        mResultChecksum.put(
                generateModuleSummarySignature(moduleResult, buildFingerprint));
    }

    @VisibleForTesting
    public Boolean containsModuleResult(IModuleResult moduleResult, String buildFingerprint) {
        return mResultChecksum.mightContain(
                generateModuleResultSignature(moduleResult, buildFingerprint));
    }

    /***
     * Write the checksum data to disk.
     * Overwrites existing file
     * @param directory
     * @throws IOException
     */
    public void saveToFile(File directory) throws IOException {
        File file = new File(directory, NAME);

        try (FileOutputStream fileStream = new FileOutputStream(file, false);
             OutputStream outputStream = new BufferedOutputStream(fileStream);
             ObjectOutput objectOutput = new ObjectOutputStream(outputStream)) {
            objectOutput.writeShort(SERIALIZED_FORMAT_CODE);
            objectOutput.writeShort(mVersion);
            objectOutput.writeObject(mResultChecksum);
            objectOutput.writeObject(mFileChecksum);
        }
    }

    @VisibleForTesting
    double getCapacity() {
        // If default FPP changes:
        // increment the CURRENT_VERSION and set the denominator based on this.mVersion
        return mResultChecksum.expectedFpp() / DEFAULT_FPP;
    }

    static String generateTestResultSignature(ITestResult testResult, IModuleResult module,
            String buildFingerprint) {
        StringBuilder sb = new StringBuilder();
        String stacktrace = testResult.getStackTrace();

        stacktrace = stacktrace == null ? "" : stacktrace.trim();
        // Line endings for stacktraces are somewhat unpredictable and there is no need to
        // actually read the result they are all removed for consistency.
        stacktrace = stacktrace.replaceAll("\\r?\\n|\\r", "");
        sb.append(buildFingerprint).append(SEPARATOR)
                .append(module.getId()).append(SEPARATOR)
                .append(testResult.getFullName()).append(SEPARATOR)
                .append(testResult.getResultStatus().getValue()).append(SEPARATOR)
                .append(stacktrace).append(SEPARATOR);
        return sb.toString();
    }

    static String generateTestResultSignature(
            String packageName, String suiteName, String caseName, String testName, String abi,
            String status,
            String stacktrace,
            String buildFingerprint) {

        String testId = buildTestId(suiteName, caseName, testName, abi);
        StringBuilder sb = new StringBuilder();

        stacktrace = stacktrace == null ? "" : stacktrace.trim();
        // Line endings for stacktraces are somewhat unpredictable and there is no need to
        // actually read the result they are all removed for consistency.
        stacktrace = stacktrace.replaceAll("\\r?\\n|\\r", "");
        sb.append(buildFingerprint)
                .append(SEPARATOR)
                .append(packageName)
                .append(SEPARATOR)
                .append(testId)
                .append(SEPARATOR)
                .append(status)
                .append(SEPARATOR)
                .append(stacktrace)
                .append(SEPARATOR);
        return sb.toString();
    }

    private static String buildTestId(
            String suiteName, String caseName, String testName, String abi) {
        String name = Joiner.on(NAME_SEPARATOR).skipNulls().join(
                Strings.emptyToNull(suiteName),
                Strings.emptyToNull(caseName),
                Strings.emptyToNull(testName));
        return Joiner.on(ID_SEPARATOR).skipNulls().join(
                Strings.emptyToNull(name),
                Strings.emptyToNull(abi));
    }


    private static String generateModuleResultSignature(IModuleResult module,
            String buildFingerprint) {
        StringBuilder sb = new StringBuilder();
        sb.append(buildFingerprint).append(SEPARATOR)
                .append(module.getId()).append(SEPARATOR)
                .append(module.isDone()).append(SEPARATOR)
                .append(module.countResults(TestStatus.FAIL));
        return sb.toString();
    }

    private static String generateModuleSummarySignature(IModuleResult module,
            String buildFingerprint) {
        StringBuilder sb = new StringBuilder();
        sb.append(buildFingerprint).append(SEPARATOR)
                .append(module.getId()).append(SEPARATOR)
                .append(module.countResults(TestStatus.FAIL));
        return sb.toString();
    }

    static byte[] calculateFileChecksum(File file) throws ChecksumValidationException {

        try (FileInputStream fis = new FileInputStream(file);
             InputStream inputStream = new BufferedInputStream(fis)) {
            MessageDigest hashSum = MessageDigest.getInstance("SHA-256");
            int cnt;
            int bufferSize = 8192;
            byte [] buffer = new byte[bufferSize];
            while ((cnt = inputStream.read(buffer)) != -1) {
                hashSum.update(buffer, 0, cnt);
            }

            byte[] partialHash = new byte[32];
            hashSum.digest(partialHash, 0, 32);
            return partialHash;
        } catch (NoSuchAlgorithmException e) {
            throw new ChecksumValidationException("Unable to hash file.", e);
        } catch (IOException e) {
            throw new ChecksumValidationException("Unable to hash file.", e);
        } catch (DigestException e) {
            throw new ChecksumValidationException("Unable to hash file.", e);
        }
    }


    private static int countTestResults(IInvocationResult invocation) {
        int count = 0;
        for (IModuleResult module : invocation.getModules()) {
            // Two entries per module (result & summary)
            count += 2;
            for (ICaseResult caseResult : module.getResults()) {
                count += caseResult.getResults().size();
            }
        }
        return count;
    }

    public static class ChecksumValidationException extends Exception {
        public ChecksumValidationException(String detailMessage) {
            super(detailMessage);
        }

        public ChecksumValidationException(String detailMessage, Throwable throwable) {
            super(detailMessage, throwable);
        }
    }
}
