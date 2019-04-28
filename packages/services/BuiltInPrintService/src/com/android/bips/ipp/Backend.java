/*
 * Copyright (C) 2016 The Android Open Source Project
 * Copyright (C) 2016 Mopria Alliance, Inc.
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

package com.android.bips.ipp;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.printservice.PrintJob;
import android.text.TextUtils;
import android.util.Log;

import com.android.bips.R;
import com.android.bips.jni.BackendConstants;
import com.android.bips.jni.JobCallback;
import com.android.bips.jni.JobCallbackParams;
import com.android.bips.jni.LocalJobParams;
import com.android.bips.jni.LocalPrinterCapabilities;
import com.android.bips.jni.PdfRender;
import com.android.bips.util.FileUtils;

import java.io.File;
import java.util.Locale;
import java.util.function.Consumer;

public class Backend implements JobCallback {
    private static final String TAG = Backend.class.getSimpleName();
    private static final boolean DEBUG = false;

    static final String TEMP_JOB_FOLDER = "jobs";

    // Error codes strictly to be in negative number
    static final int ERROR_FILE = -1;
    static final int ERROR_CANCEL = -2;
    static final int ERROR_UNKNOWN = -3;

    private static final String VERSION_UNKNOWN = "(unknown)";

    private final Handler mMainHandler;
    private final Context mContext;
    private JobStatus mCurrentJobStatus;
    private Consumer<JobStatus> mJobStatusListener;
    private AsyncTask<Void, Void, Integer> mStartTask;

    public Backend(Context context) {
        if (DEBUG) Log.d(TAG, "Backend()");

        mContext = context;
        mMainHandler = new Handler(context.getMainLooper());
        PdfRender.getInstance(mContext);

        // Load required JNI libraries
        System.loadLibrary(BackendConstants.WPRINT_LIBRARY_PREFIX);

        // Create and initialize JNI layer
        nativeInit(this, context.getApplicationInfo().dataDir, Build.VERSION.SDK_INT);
        nativeSetSourceInfo(context.getString(R.string.app_name).toLowerCase(Locale.US),
                getApplicationVersion(context).toLowerCase(Locale.US),
                BackendConstants.WPRINT_APPLICATION_ID.toLowerCase(Locale.US));
    }

    /** Return the current application version or VERISON_UNKNOWN */
    private String getApplicationVersion(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return VERSION_UNKNOWN;
        }
    }

    /** Asynchronously get printer capabilities, returning results or null to a callback */
    public AsyncTask<?, ?, ?> getCapabilities(Uri uri, long timeout,
            final Consumer<LocalPrinterCapabilities> capabilitiesConsumer) {
        if (DEBUG) Log.d(TAG, "getCapabilities()");

        return new GetCapabilitiesTask(this, uri, timeout) {
            @Override
            protected void onPostExecute(LocalPrinterCapabilities result) {
                capabilitiesConsumer.accept(result);
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * Start a print job. Results will be notified to the listener. Do not start more than
     * one job at a time.
     */
    public void print(Uri uri, PrintJob printJob, LocalPrinterCapabilities capabilities,
            Consumer<JobStatus> listener) {
        if (DEBUG) Log.d(TAG, "print()");

        mJobStatusListener = listener;
        mCurrentJobStatus = new JobStatus();

        mStartTask = new StartJobTask(mContext, this, uri, printJob, capabilities) {
            @Override
            public void onCancelled(Integer result) {
                if (DEBUG) Log.d(TAG, "StartJobTask::onCancelled " + result);
                onPostExecute(ERROR_CANCEL);
            }

            @Override
            protected void onPostExecute(Integer result) {
                if (DEBUG) Log.d(TAG, "StartJobTask::onPostExecute " + result);
                mStartTask = null;
                if (result > 0) {
                    mCurrentJobStatus = new JobStatus.Builder(mCurrentJobStatus).setId(result)
                            .build();
                } else if (mJobStatusListener != null) {
                    String jobResult = BackendConstants.JOB_DONE_ERROR;
                    if (result == ERROR_CANCEL) {
                        jobResult = BackendConstants.JOB_DONE_CANCELLED;
                    } else if (result == ERROR_FILE) {
                        jobResult = BackendConstants.JOB_DONE_CORRUPT;
                    }

                    // If the start attempt failed and we are still listening, notify and be done
                    mCurrentJobStatus = new JobStatus.Builder()
                            .setJobState(BackendConstants.JOB_STATE_DONE)
                            .setJobResult(jobResult).build();
                    mJobStatusListener.accept(mCurrentJobStatus);
                    mJobStatusListener = null;
                }
            }
        };
        mStartTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /** Attempt to cancel the current job */
    public void cancel() {
        if (DEBUG) Log.d(TAG, "cancel()");

        if (mStartTask != null) {
            if (DEBUG) Log.d(TAG, "cancelling start task");
            mStartTask.cancel(true);
        } else if (mCurrentJobStatus != null && mCurrentJobStatus.getId() != JobStatus.ID_UNKNOWN) {
            if (DEBUG) Log.d(TAG, "cancelling job via new task");
            new CancelJobTask(this, mCurrentJobStatus.getId())
                    .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } else {
            if (DEBUG) Log.d(TAG, "Nothing to cancel in backend, ignoring");
        }
    }

    /**
     * Call when it is safe to release document-centric resources related to a print job
     */
    public void closeDocument() {
        // Tell the renderer it may release resources for the document
        PdfRender.getInstance(mContext).closeDocument();
    }

    /**
     * Call when service is shutting down, nothing else is happening, and this object
     * is no longer required. After closing this object it should be discarded.
     */
    public void close() {
        nativeExit();
        PdfRender.getInstance(mContext).close();
    }

    /** Called by JNI */
    @Override
    public void jobCallback(final int jobId, final JobCallbackParams params) {
        mMainHandler.post(() -> {
            if (DEBUG) Log.d(TAG, "jobCallback() jobId=" + jobId + ", params=" + params);

            JobStatus.Builder builder = new JobStatus.Builder(mCurrentJobStatus);

            builder.setId(params.jobId);

            if (!TextUtils.isEmpty(params.printerState)) {
                updateBlockedReasons(builder, params);
            } else if (!TextUtils.isEmpty(params.jobState)) {
                builder.setJobState(params.jobState);
                if (!TextUtils.isEmpty(params.jobDoneResult)) {
                    builder.setJobResult(params.jobDoneResult);
                }
                updateBlockedReasons(builder, params);
            }
            mCurrentJobStatus = builder.build();

            if (mJobStatusListener != null) {
                mJobStatusListener.accept(mCurrentJobStatus);
            }

            if (mCurrentJobStatus.isJobDone()) {
                nativeEndJob(jobId);
                // Reset status for next job.
                mCurrentJobStatus = new JobStatus();
                mJobStatusListener = null;

                FileUtils.deleteAll(new File(mContext.getFilesDir(), Backend.TEMP_JOB_FOLDER));
            }
        });
    }

    /** Update the blocked reason list with non-empty strings */
    private void updateBlockedReasons(JobStatus.Builder builder, JobCallbackParams params) {
        if ((params.blockedReasons != null) && (params.blockedReasons.length > 0)) {
            builder.clearBlockedReasons();
            for (String reason : params.blockedReasons) {
                if (!TextUtils.isEmpty(reason)) {
                    builder.addBlockedReason(reason);
                }
            }
        }
    }

    /**
     * Extracts the ip portion of x.x.x.x/y/z
     *
     * @param address any string in the format xxx/yyy/zzz
     * @return the part before the "/" or "xxx" in this case
     */
    static String getIp(String address) {
        int i = address.indexOf('/');
        return i == -1 ? address : address.substring(0, i);
    }

    /**
     * Initialize the lower layer.
     *
     * @param jobCallback job callback to use whenever job updates arrive
     * @param dataDir directory to use for temporary files
     * @param apiVersion local system API version to be supplied to printers
     * @return {@link BackendConstants#STATUS_OK} or an error code.
     */
    native int nativeInit(JobCallback jobCallback, String dataDir, int apiVersion);

    /**
     * Supply additional information about the source of jobs.
     *
     * @param appName human-readable name of application providing data to the printer
     * @param version version of delivering application
     * @param appId identifier for the delivering application
     */
    native void nativeSetSourceInfo(String appName, String version, String appId);

    /**
     * Request capabilities from a printer.
     *
     * @param address IP address or hostname (e.g. "192.168.1.2")
     * @param port port to use (e.g. 631)
     * @param httpResource path of print resource on host (e.g. "/ipp/print")
     * @param uriScheme scheme (e.g. "ipp")
     * @param timeout milliseconds to wait before giving up on request
     * @param capabilities target object to be filled with printer capabilities, if successful
     * @return {@link BackendConstants#STATUS_OK} or an error code.
     */
    native int nativeGetCapabilities(String address, int port, String httpResource,
            String uriScheme, long timeout, LocalPrinterCapabilities capabilities);

    /**
     * Determine initial parameters to be used for jobs
     *
     * @param jobParams object to be filled with default parameters
     * @return {@link BackendConstants#STATUS_OK} or an error code.
     */
    native int nativeGetDefaultJobParameters(LocalJobParams jobParams);

    /**
     * Update job parameters to align with known printer capabilities
     *
     * @param jobParams on input, contains requested job parameters; on output contains final
     *                  job parameter selections.
     * @param capabilities printer capabilities to be used when finalizing job parameters
     * @return {@link BackendConstants#STATUS_OK} or an error code.
     */
    native int nativeGetFinalJobParameters(LocalJobParams jobParams,
            LocalPrinterCapabilities capabilities);

    /**
     * Begin job delivery to a target printer. Updates on the job will be sent to the registered
     * {@link JobCallback}.
     *
     * @param address IP address or hostname (e.g. "192.168.1.2")
     * @param port port to use (e.g. 631)
     * @param mime_type MIME type of data being sent
     * @param jobParams job parameters to use when providing the job to the printer
     * @param capabilities printer capabilities for the printer being used
     * @param fileList list of files to be provided of the given MIME type
     * @param debugDir directory to receive debugging information, if any
     * @param scheme URI scheme (e.g. ipp/ipps)
     * @return {@link BackendConstants#STATUS_OK} or an error code.
     */
    native int nativeStartJob(String address, int port, String mime_type, LocalJobParams jobParams,
            LocalPrinterCapabilities capabilities, String[] fileList, String debugDir, String scheme);

    /**
     * Request cancellation of the identified job.
     *
     * @param jobId identifier of the job to cancel
     * @return {@link BackendConstants#STATUS_OK} or an error code.
     */
    native int nativeCancelJob(int jobId);

    /**
     * Finalizes a job after it is ends for any reason
     *
     * @param jobId identifier of the job to end
     * @return {@link BackendConstants#STATUS_OK} or an error code.
     */
    native int nativeEndJob(int jobId);

    /**
     * Shut down and clean up resources in the JNI layer on system exit
     *
     * @return {@link BackendConstants#STATUS_OK} or an error code.
     */
    native int nativeExit();
}
