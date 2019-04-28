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

package com.android.documentsui.services;

import static android.os.SystemClock.elapsedRealtime;
import static android.provider.DocumentsContract.buildChildDocumentsUri;
import static android.provider.DocumentsContract.buildDocumentUri;
import static android.provider.DocumentsContract.getDocumentId;
import static android.provider.DocumentsContract.isChildDocument;

import static com.android.documentsui.OperationDialogFragment.DIALOG_TYPE_CONVERTED;
import static com.android.documentsui.base.DocumentInfo.getCursorLong;
import static com.android.documentsui.base.DocumentInfo.getCursorString;
import static com.android.documentsui.base.Shared.DEBUG;
import static com.android.documentsui.services.FileOperationService.EXTRA_DIALOG_TYPE;
import static com.android.documentsui.services.FileOperationService.EXTRA_FAILED_DOCS;
import static com.android.documentsui.services.FileOperationService.EXTRA_OPERATION_TYPE;
import static com.android.documentsui.services.FileOperationService.MESSAGE_FINISH;
import static com.android.documentsui.services.FileOperationService.MESSAGE_PROGRESS;
import static com.android.documentsui.services.FileOperationService.OPERATION_COPY;

import android.annotation.StringRes;
import android.app.Notification;
import android.app.Notification.Builder;
import android.app.PendingIntent;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.storage.StorageManager;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.text.format.DateUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;

import com.android.documentsui.DocumentsApplication;
import com.android.documentsui.Metrics;
import com.android.documentsui.R;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.Features;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.clipping.UrisSupplier;
import com.android.documentsui.roots.ProvidersCache;
import com.android.documentsui.services.FileOperationService.OpType;

import libcore.io.IoUtils;

import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.SyncFailedException;
import java.text.NumberFormat;
import java.util.ArrayList;

class CopyJob extends ResolvedResourcesJob {

    private static final String TAG = "CopyJob";

    private static final long LOADING_TIMEOUT = 60000; // 1 min

    final ArrayList<DocumentInfo> convertedFiles = new ArrayList<>();
    DocumentInfo mDstInfo;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Messenger mMessenger;

    private long mStartTime = -1;
    private long mBytesRequired;
    private volatile long mBytesCopied;

    // Speed estimation.
    private long mBytesCopiedSample;
    private long mSampleTime;
    private long mSpeed;
    private long mRemainingTime;

    /**
     * @see @link {@link Job} constructor for most param descriptions.
     */
    CopyJob(Context service, Listener listener, String id, DocumentStack destination,
            UrisSupplier srcs, Messenger messenger, Features features) {
        this(service, listener, id, OPERATION_COPY, destination, srcs, messenger, features);
    }

    CopyJob(Context service, Listener listener, String id, @OpType int opType,
            DocumentStack destination, UrisSupplier srcs, Messenger messenger, Features features) {
        super(service, listener, id, opType, destination, srcs, features);
        mDstInfo = destination.peek();
        mMessenger = messenger;

        assert(srcs.getItemCount() > 0);
    }

    @Override
    Builder createProgressBuilder() {
        return super.createProgressBuilder(
                service.getString(R.string.copy_notification_title),
                R.drawable.ic_menu_copy,
                service.getString(android.R.string.cancel),
                R.drawable.ic_cab_cancel);
    }

    @Override
    public Notification getSetupNotification() {
        return getSetupNotification(service.getString(R.string.copy_preparing));
    }

    Notification getProgressNotification(@StringRes int msgId) {
        updateRemainingTimeEstimate();

        if (mBytesRequired >= 0) {
            double completed = (double) this.mBytesCopied / mBytesRequired;
            mProgressBuilder.setProgress(100, (int) (completed * 100), false);
            mProgressBuilder.setSubText(
                    NumberFormat.getPercentInstance().format(completed));
        } else {
            // If the total file size failed to compute on some files, then show
            // an indeterminate spinner. CopyJob would most likely fail on those
            // files while copying, but would continue with another files.
            // Also, if the total size is 0 bytes, show an indeterminate spinner.
            mProgressBuilder.setProgress(0, 0, true);
        }

        if (mRemainingTime > 0) {
            mProgressBuilder.setContentText(service.getString(msgId,
                    DateUtils.formatDuration(mRemainingTime)));
        } else {
            mProgressBuilder.setContentText(null);
        }

        return mProgressBuilder.build();
    }

    @Override
    public Notification getProgressNotification() {
        return getProgressNotification(R.string.copy_remaining);
    }

    void onBytesCopied(long numBytes) {
        this.mBytesCopied += numBytes;
    }

    @Override
    void finish() {
        try {
            mMessenger.send(Message.obtain(mHandler, MESSAGE_FINISH, 0, 0));
        } catch (RemoteException e) {
            // Ignore. Most likely the frontend was killed.
        }
        super.finish();
    }

    /**
     * Generates an estimate of the remaining time in the copy.
     */
    private void updateRemainingTimeEstimate() {
        long elapsedTime = elapsedRealtime() - mStartTime;

        // mBytesCopied is modified in worker thread, but this method is called in monitor thread,
        // so take a snapshot of mBytesCopied to make sure the updated estimate is consistent.
        final long bytesCopied = mBytesCopied;
        final long sampleDuration = Math.max(elapsedTime - mSampleTime, 1L); // avoid dividing 0
        final long sampleSpeed = ((bytesCopied - mBytesCopiedSample) * 1000) / sampleDuration;
        if (mSpeed == 0) {
            mSpeed = sampleSpeed;
        } else {
            mSpeed = ((3 * mSpeed) + sampleSpeed) / 4;
        }

        if (mSampleTime > 0 && mSpeed > 0) {
            mRemainingTime = ((mBytesRequired - bytesCopied) * 1000) / mSpeed;
        } else {
            mRemainingTime = 0;
        }

        mSampleTime = elapsedTime;
        mBytesCopiedSample = bytesCopied;
    }

    @Override
    Notification getFailureNotification() {
        return getFailureNotification(
                R.plurals.copy_error_notification_title, R.drawable.ic_menu_copy);
    }

    @Override
    Notification getWarningNotification() {
        final Intent navigateIntent = buildNavigateIntent(INTENT_TAG_WARNING);
        navigateIntent.putExtra(EXTRA_DIALOG_TYPE, DIALOG_TYPE_CONVERTED);
        navigateIntent.putExtra(EXTRA_OPERATION_TYPE, operationType);

        navigateIntent.putParcelableArrayListExtra(EXTRA_FAILED_DOCS, convertedFiles);

        // TODO: Consider adding a dialog on tapping the notification with a list of
        // converted files.
        final Notification.Builder warningBuilder = createNotificationBuilder()
                .setContentTitle(service.getResources().getString(
                        R.string.notification_copy_files_converted_title))
                .setContentText(service.getString(
                        R.string.notification_touch_for_details))
                .setContentIntent(PendingIntent.getActivity(appContext, 0, navigateIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_ONE_SHOT))
                .setCategory(Notification.CATEGORY_ERROR)
                .setSmallIcon(R.drawable.ic_menu_copy)
                .setAutoCancel(true);
        return warningBuilder.build();
    }

    @Override
    boolean setUp() {
        if (!super.setUp()) {
            return false;
        }

        // Check if user has canceled this task.
        if (isCanceled()) {
            return false;
        }

        try {
            mBytesRequired = calculateBytesRequired();
        } catch (ResourceException e) {
            Log.w(TAG, "Failed to calculate total size. Copying without progress.", e);
            mBytesRequired = -1;
        }

        // Check if user has canceled this task. We should check it again here as user cancels
        // tasks in main thread, but this is running in a worker thread. calculateSize() may
        // take a long time during which user can cancel this task, and we don't want to waste
        // resources doing useless large chunk of work.
        if (isCanceled()) {
            return false;
        }

        return checkSpace();
    }

    @Override
    void start() {
        mStartTime = elapsedRealtime();
        DocumentInfo srcInfo;
        for (int i = 0; i < mResolvedDocs.size() && !isCanceled(); ++i) {
            srcInfo = mResolvedDocs.get(i);

            if (DEBUG) Log.d(TAG,
                    "Copying " + srcInfo.displayName + " (" + srcInfo.derivedUri + ")"
                    + " to " + mDstInfo.displayName + " (" + mDstInfo.derivedUri + ")");

            try {
                // Copying recursively to itself or one of descendants is not allowed.
                if (mDstInfo.equals(srcInfo) || isDescendentOf(srcInfo, mDstInfo)) {
                    Log.e(TAG, "Skipping recursive copy of " + srcInfo.derivedUri);
                    onFileFailed(srcInfo);
                } else {
                    processDocument(srcInfo, null, mDstInfo);
                }
            } catch (ResourceException e) {
                Log.e(TAG, "Failed to copy " + srcInfo.derivedUri, e);
                onFileFailed(srcInfo);
            }
        }

        Metrics.logFileOperation(service, operationType, mResolvedDocs, mDstInfo);
    }

    /**
     * Checks whether the destination folder has enough space to take all source files.
     * @return true if the root has enough space or doesn't provide free space info; otherwise false
     */
    boolean checkSpace() {
        return verifySpaceAvailable(mBytesRequired);
    }

    /**
     * Checks whether the destination folder has enough space to take files of batchSize
     * @param batchSize the total size of files
     * @return true if the root has enough space or doesn't provide free space info; otherwise false
     */
    final boolean verifySpaceAvailable(long batchSize) {
        // Default to be true because if batchSize or available space is invalid, we still let the
        // copy start anyway.
        boolean available = true;
        if (batchSize >= 0) {
            ProvidersCache cache = DocumentsApplication.getProvidersCache(appContext);

            RootInfo root = stack.getRoot();
            // Query root info here instead of using stack.root because the number there may be
            // stale.
            root = cache.getRootOneshot(root.authority, root.rootId, true);
            if (root.availableBytes >= 0) {
                available = (batchSize <= root.availableBytes);
            } else {
                Log.w(TAG, root.toString() + " doesn't provide available bytes.");
            }
        }

        if (!available) {
            failureCount = mResolvedDocs.size();
            failedDocs.addAll(mResolvedDocs);
        }

        return available;
    }

    @Override
    boolean hasWarnings() {
        return !convertedFiles.isEmpty();
    }

    /**
     * Logs progress on the current copy operation. Displays/Updates the progress notification.
     *
     * @param bytesCopied
     */
    private void makeCopyProgress(long bytesCopied) {
        final int completed =
            mBytesRequired >= 0 ? (int) (100.0 * this.mBytesCopied / mBytesRequired) : -1;
        try {
            mMessenger.send(Message.obtain(mHandler, MESSAGE_PROGRESS,
                    completed, (int) mRemainingTime));
        } catch (RemoteException e) {
            // Ignore. The frontend may be gone.
        }
        onBytesCopied(bytesCopied);
    }

    /**
     * Copies a the given document to the given location.
     *
     * @param src DocumentInfos for the documents to copy.
     * @param srcParent DocumentInfo for the parent of the document to process.
     * @param dstDirInfo The destination directory.
     * @throws ResourceException
     *
     * TODO: Stop passing srcParent, as it's not used for copy, but for move only.
     */
    void processDocument(DocumentInfo src, DocumentInfo srcParent,
            DocumentInfo dstDirInfo) throws ResourceException {

        // TODO: When optimized copy kicks in, we'll not making any progress updates.
        // For now. Local storage isn't using optimized copy.

        // When copying within the same provider, try to use optimized copying.
        // If not supported, then fallback to byte-by-byte copy/move.
        if (src.authority.equals(dstDirInfo.authority)) {
            if ((src.flags & Document.FLAG_SUPPORTS_COPY) != 0) {
                try {
                    if (DocumentsContract.copyDocument(getClient(src), src.derivedUri,
                            dstDirInfo.derivedUri) != null) {
                        Metrics.logFileOperated(
                                appContext, operationType, Metrics.OPMODE_PROVIDER);
                        return;
                    }
                } catch (RemoteException | RuntimeException e) {
                    Log.e(TAG, "Provider side copy failed for: " + src.derivedUri
                            + " due to an exception.", e);
                    Metrics.logFileOperationFailure(
                            appContext, Metrics.SUBFILEOP_QUICK_COPY, src.derivedUri);
                }

                // If optimized copy fails, then fallback to byte-by-byte copy.
                if (DEBUG) Log.d(TAG, "Fallback to byte-by-byte copy for: " + src.derivedUri);
            }
        }

        // If we couldn't do an optimized copy...we fall back to vanilla byte copy.
        byteCopyDocument(src, dstDirInfo);
    }

    void byteCopyDocument(DocumentInfo src, DocumentInfo dest) throws ResourceException {
        final String dstMimeType;
        final String dstDisplayName;

        if (DEBUG) Log.d(TAG, "Doing byte copy of document: " + src);
        // If the file is virtual, but can be converted to another format, then try to copy it
        // as such format. Also, append an extension for the target mime type (if known).
        if (src.isVirtual()) {
            String[] streamTypes = null;
            try {
                streamTypes = getContentResolver().getStreamTypes(src.derivedUri, "*/*");
            } catch (RuntimeException e) {
                Metrics.logFileOperationFailure(
                        appContext, Metrics.SUBFILEOP_OBTAIN_STREAM_TYPE, src.derivedUri);
                throw new ResourceException(
                        "Failed to obtain streamable types for %s due to an exception.",
                        src.derivedUri, e);
            }
            if (streamTypes != null && streamTypes.length > 0) {
                dstMimeType = streamTypes[0];
                final String extension = MimeTypeMap.getSingleton().
                        getExtensionFromMimeType(dstMimeType);
                dstDisplayName = src.displayName +
                        (extension != null ? "." + extension : src.displayName);
            } else {
                Metrics.logFileOperationFailure(
                        appContext, Metrics.SUBFILEOP_OBTAIN_STREAM_TYPE, src.derivedUri);
                throw new ResourceException("Cannot copy virtual file %s. No streamable formats "
                        + "available.", src.derivedUri);
            }
        } else {
            dstMimeType = src.mimeType;
            dstDisplayName = src.displayName;
        }

        // Create the target document (either a file or a directory), then copy recursively the
        // contents (bytes or children).
        Uri dstUri = null;
        try {
            dstUri = DocumentsContract.createDocument(
                    getClient(dest), dest.derivedUri, dstMimeType, dstDisplayName);
        } catch (RemoteException | RuntimeException e) {
            Metrics.logFileOperationFailure(
                    appContext, Metrics.SUBFILEOP_CREATE_DOCUMENT, dest.derivedUri);
            throw new ResourceException(
                    "Couldn't create destination document " + dstDisplayName + " in directory %s "
                    + "due to an exception.", dest.derivedUri, e);
        }
        if (dstUri == null) {
            // If this is a directory, the entire subdir will not be copied over.
            Metrics.logFileOperationFailure(
                    appContext, Metrics.SUBFILEOP_CREATE_DOCUMENT, dest.derivedUri);
            throw new ResourceException(
                    "Couldn't create destination document " + dstDisplayName + " in directory %s.",
                    dest.derivedUri);
        }

        DocumentInfo dstInfo = null;
        try {
            dstInfo = DocumentInfo.fromUri(getContentResolver(), dstUri);
        } catch (FileNotFoundException | RuntimeException e) {
            Metrics.logFileOperationFailure(
                    appContext, Metrics.SUBFILEOP_QUERY_DOCUMENT, dstUri);
            throw new ResourceException("Could not load DocumentInfo for newly created file %s.",
                    dstUri);
        }

        if (Document.MIME_TYPE_DIR.equals(src.mimeType)) {
            copyDirectoryHelper(src, dstInfo);
        } else {
            copyFileHelper(src, dstInfo, dest, dstMimeType);
        }
    }

    /**
     * Handles recursion into a directory and copying its contents. Note that in linux terms, this
     * does the equivalent of "cp src/* dst", not "cp -r src dst".
     *
     * @param srcDir Info of the directory to copy from. The routine will copy the directory's
     *            contents, not the directory itself.
     * @param destDir Info of the directory to copy to. Must be created beforehand.
     * @throws ResourceException
     */
    private void copyDirectoryHelper(DocumentInfo srcDir, DocumentInfo destDir)
            throws ResourceException {
        // Recurse into directories. Copy children into the new subdirectory.
        final String queryColumns[] = new String[] {
                Document.COLUMN_DISPLAY_NAME,
                Document.COLUMN_DOCUMENT_ID,
                Document.COLUMN_MIME_TYPE,
                Document.COLUMN_SIZE,
                Document.COLUMN_FLAGS
        };
        Cursor cursor = null;
        boolean success = true;
        // Iterate over srcs in the directory; copy to the destination directory.
        try {
            try {
                cursor = queryChildren(srcDir, queryColumns);
            } catch (RemoteException | RuntimeException e) {
                Metrics.logFileOperationFailure(
                        appContext, Metrics.SUBFILEOP_QUERY_CHILDREN, srcDir.derivedUri);
                throw new ResourceException("Failed to query children of %s due to an exception.",
                        srcDir.derivedUri, e);
            }

            DocumentInfo src;
            while (cursor.moveToNext() && !isCanceled()) {
                try {
                    src = DocumentInfo.fromCursor(cursor, srcDir.authority);
                    processDocument(src, srcDir, destDir);
                } catch (RuntimeException e) {
                    Log.e(TAG, String.format(
                            "Failed to recursively process a file %s due to an exception.",
                            srcDir.derivedUri.toString()), e);
                    success = false;
                }
            }
        } catch (RuntimeException e) {
            Log.e(TAG, String.format(
                    "Failed to copy a file %s to %s. ",
                    srcDir.derivedUri.toString(), destDir.derivedUri.toString()), e);
            success = false;
        } finally {
            IoUtils.closeQuietly(cursor);
        }

        if (!success) {
            throw new RuntimeException("Some files failed to copy during a recursive "
                    + "directory copy.");
        }
    }

    /**
     * Handles copying a single file.
     *
     * @param src Info of the file to copy from.
     * @param dest Info of the *file* to copy to. Must be created beforehand.
     * @param destParent Info of the parent of the destination.
     * @param mimeType Mime type for the target. Can be different than source for virtual files.
     * @throws ResourceException
     */
    private void copyFileHelper(DocumentInfo src, DocumentInfo dest, DocumentInfo destParent,
            String mimeType) throws ResourceException {
        CancellationSignal canceller = new CancellationSignal();
        AssetFileDescriptor srcFileAsAsset = null;
        ParcelFileDescriptor srcFile = null;
        ParcelFileDescriptor dstFile = null;
        InputStream in = null;
        ParcelFileDescriptor.AutoCloseOutputStream out = null;
        boolean success = false;

        try {
            // If the file is virtual, but can be converted to another format, then try to copy it
            // as such format.
            if (src.isVirtual()) {
                try {
                    srcFileAsAsset = getClient(src).openTypedAssetFileDescriptor(
                                src.derivedUri, mimeType, null, canceller);
                } catch (FileNotFoundException | RemoteException | RuntimeException e) {
                    Metrics.logFileOperationFailure(
                            appContext, Metrics.SUBFILEOP_OPEN_FILE, src.derivedUri);
                    throw new ResourceException("Failed to open a file as asset for %s due to an "
                            + "exception.", src.derivedUri, e);
                }
                srcFile = srcFileAsAsset.getParcelFileDescriptor();
                try {
                    in = new AssetFileDescriptor.AutoCloseInputStream(srcFileAsAsset);
                } catch (IOException e) {
                    Metrics.logFileOperationFailure(
                            appContext, Metrics.SUBFILEOP_OPEN_FILE, src.derivedUri);
                    throw new ResourceException("Failed to open a file input stream for %s due "
                            + "an exception.", src.derivedUri, e);
                }

                Metrics.logFileOperated(
                        appContext, operationType, Metrics.OPMODE_CONVERTED);
            } else {
                try {
                    srcFile = getClient(src).openFile(src.derivedUri, "r", canceller);
                } catch (FileNotFoundException | RemoteException | RuntimeException e) {
                    Metrics.logFileOperationFailure(
                            appContext, Metrics.SUBFILEOP_OPEN_FILE, src.derivedUri);
                    throw new ResourceException(
                            "Failed to open a file for %s due to an exception.", src.derivedUri, e);
                }
                in = new ParcelFileDescriptor.AutoCloseInputStream(srcFile);

                Metrics.logFileOperated(
                        appContext, operationType, Metrics.OPMODE_CONVENTIONAL);
            }

            try {
                dstFile = getClient(dest).openFile(dest.derivedUri, "w", canceller);
            } catch (FileNotFoundException | RemoteException | RuntimeException e) {
                Metrics.logFileOperationFailure(
                        appContext, Metrics.SUBFILEOP_OPEN_FILE, dest.derivedUri);
                throw new ResourceException("Failed to open the destination file %s for writing "
                        + "due to an exception.", dest.derivedUri, e);
            }
            out = new ParcelFileDescriptor.AutoCloseOutputStream(dstFile);

            byte[] buffer = new byte[32 * 1024];
            int len;
            boolean reading = true;
            try {
                // If we know the source size, and the destination supports disk
                // space allocation, then allocate the space we'll need. This
                // uses fallocate() under the hood to optimize on-disk layout
                // and prevent us from running out of space during large copies.
                final StorageManager sm = service.getSystemService(StorageManager.class);
                final long srcSize = srcFile.getStatSize();
                final FileDescriptor dstFd = dstFile.getFileDescriptor();
                if (srcSize > 0 && sm.isAllocationSupported(dstFd)) {
                    sm.allocateBytes(dstFd, srcSize);
                }

                while ((len = in.read(buffer)) != -1) {
                    if (isCanceled()) {
                        if (DEBUG) Log.d(TAG, "Canceled copy mid-copy of: " + src.derivedUri);
                        return;
                    }
                    reading = false;
                    out.write(buffer, 0, len);
                    makeCopyProgress(len);
                    reading = true;
                }

                reading = false;
                // Need to invoke Os#fsync to ensure the file is written to the storage device.
                try {
                    Os.fsync(dstFile.getFileDescriptor());
                } catch (ErrnoException error) {
                    // fsync will fail with fd of pipes and return EROFS or EINVAL.
                    if (error.errno != OsConstants.EROFS && error.errno != OsConstants.EINVAL) {
                        throw new SyncFailedException(
                                "Failed to sync bytes after copying a file.");
                    }
                }

                // Need to invoke IoUtils.close explicitly to avoid from ignoring errors at flush.
                IoUtils.close(dstFile.getFileDescriptor());
                srcFile.checkError();
            } catch (IOException e) {
                Metrics.logFileOperationFailure(
                        appContext,
                        reading ? Metrics.SUBFILEOP_READ_FILE : Metrics.SUBFILEOP_WRITE_FILE,
                        reading ? src.derivedUri: dest.derivedUri);
                throw new ResourceException(
                        "Failed to copy bytes from %s to %s due to an IO exception.",
                        src.derivedUri, dest.derivedUri, e);
            }

            if (src.isVirtual()) {
               convertedFiles.add(src);
            }

            success = true;
        } finally {
            if (!success) {
                if (dstFile != null) {
                    try {
                        dstFile.closeWithError("Error copying bytes.");
                    } catch (IOException closeError) {
                        Log.w(TAG, "Error closing destination.", closeError);
                    }
                }

                if (DEBUG) Log.d(TAG, "Cleaning up failed operation leftovers.");
                canceller.cancel();
                try {
                    deleteDocument(dest, destParent);
                } catch (ResourceException e) {
                    Log.w(TAG, "Failed to cleanup after copy error: " + src.derivedUri, e);
                }
            }

            // This also ensures the file descriptors are closed.
            IoUtils.closeQuietly(in);
            IoUtils.closeQuietly(out);
        }
    }

    /**
     * Calculates the cumulative size of all the documents in the list. Directories are recursed
     * into and totaled up.
     *
     * @return Size in bytes.
     * @throws ResourceException
     */
    private long calculateBytesRequired() throws ResourceException {
        long result = 0;

        for (DocumentInfo src : mResolvedDocs) {
            if (src.isDirectory()) {
                // Directories need to be recursed into.
                try {
                    result += calculateFileSizesRecursively(getClient(src), src.derivedUri);
                } catch (RemoteException e) {
                    throw new ResourceException("Failed to obtain the client for %s.",
                            src.derivedUri, e);
                }
            } else {
                result += src.size;
            }

            if (isCanceled()) {
                return result;
            }
        }
        return result;
    }

    /**
     * Calculates (recursively) the cumulative size of all the files under the given directory.
     *
     * @throws ResourceException
     */
    long calculateFileSizesRecursively(
            ContentProviderClient client, Uri uri) throws ResourceException {
        final String authority = uri.getAuthority();
        final String queryColumns[] = new String[] {
                Document.COLUMN_DOCUMENT_ID,
                Document.COLUMN_MIME_TYPE,
                Document.COLUMN_SIZE
        };

        long result = 0;
        Cursor cursor = null;
        try {
            cursor = queryChildren(client, uri, queryColumns);
            while (cursor.moveToNext() && !isCanceled()) {
                if (Document.MIME_TYPE_DIR.equals(
                        getCursorString(cursor, Document.COLUMN_MIME_TYPE))) {
                    // Recurse into directories.
                    final Uri dirUri = buildDocumentUri(authority,
                            getCursorString(cursor, Document.COLUMN_DOCUMENT_ID));
                    result += calculateFileSizesRecursively(client, dirUri);
                } else {
                    // This may return -1 if the size isn't defined. Ignore those cases.
                    long size = getCursorLong(cursor, Document.COLUMN_SIZE);
                    result += size > 0 ? size : 0;
                }
            }
        } catch (RemoteException | RuntimeException e) {
            throw new ResourceException(
                    "Failed to calculate size for %s due to an exception.", uri, e);
        } finally {
            IoUtils.closeQuietly(cursor);
        }

        return result;
    }

    /**
     * Queries children documents.
     *
     * SAF allows {@link DocumentsContract#EXTRA_LOADING} in {@link Cursor#getExtras()} to indicate
     * there are more data to be loaded. Wait until {@link DocumentsContract#EXTRA_LOADING} is
     * false and then return the cursor.
     *
     * @param srcDir the directory whose children are being loading
     * @param queryColumns columns of metadata to load
     * @return cursor of all children documents
     * @throws RemoteException when the remote throws or waiting for update times out
     */
    private Cursor queryChildren(DocumentInfo srcDir, String[] queryColumns)
            throws RemoteException {
        try (final ContentProviderClient client = getClient(srcDir)) {
            return queryChildren(client, srcDir.derivedUri, queryColumns);
        }
    }

    /**
     * Queries children documents.
     *
     * SAF allows {@link DocumentsContract#EXTRA_LOADING} in {@link Cursor#getExtras()} to indicate
     * there are more data to be loaded. Wait until {@link DocumentsContract#EXTRA_LOADING} is
     * false and then return the cursor.
     *
     * @param client the {@link ContentProviderClient} to use to query children
     * @param dirDocUri the document Uri of the directory whose children are being loaded
     * @param queryColumns columns of metadata to load
     * @return cursor of all children documents
     * @throws RemoteException when the remote throws or waiting for update times out
     */
    private Cursor queryChildren(ContentProviderClient client, Uri dirDocUri, String[] queryColumns)
            throws RemoteException {
        // TODO (b/34459983): Optimize this performance by processing partial result first while provider is loading
        // more data. Note we need to skip size calculation to achieve it.
        final Uri queryUri = buildChildDocumentsUri(dirDocUri.getAuthority(), getDocumentId(dirDocUri));
        Cursor cursor = client.query(
                queryUri, queryColumns, (String) null, null, null);
        while (cursor.getExtras().getBoolean(DocumentsContract.EXTRA_LOADING)) {
            cursor.registerContentObserver(new DirectoryChildrenObserver(queryUri));
            try {
                long start = System.currentTimeMillis();
                synchronized (queryUri) {
                    queryUri.wait(LOADING_TIMEOUT);
                }
                if (System.currentTimeMillis() - start > LOADING_TIMEOUT) {
                    // Timed out
                    throw new RemoteException("Timed out waiting on update for " + queryUri);
                }
            } catch (InterruptedException e) {
                // Should never happen
                throw new RuntimeException(e);
            }

            // Make another query
            cursor = client.query(
                    queryUri, queryColumns, (String) null, null, null);
        }

        return cursor;
    }

    /**
     * Returns true if {@code doc} is a descendant of {@code parentDoc}.
     * @throws ResourceException
     */
    boolean isDescendentOf(DocumentInfo doc, DocumentInfo parent)
            throws ResourceException {
        if (parent.isDirectory() && doc.authority.equals(parent.authority)) {
            try {
                return isChildDocument(getClient(doc), doc.derivedUri, parent.derivedUri);
            } catch (RemoteException | RuntimeException e) {
                throw new ResourceException(
                        "Failed to check if %s is a child of %s due to an exception.",
                        doc.derivedUri, parent.derivedUri, e);
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return new StringBuilder()
                .append("CopyJob")
                .append("{")
                .append("id=" + id)
                .append(", uris=" + mResourceUris)
                .append(", docs=" + mResolvedDocs)
                .append(", destination=" + stack)
                .append("}")
                .toString();
    }

    private static class DirectoryChildrenObserver extends ContentObserver {

        private final Object mNotifier;

        private DirectoryChildrenObserver(Object notifier) {
            super(new Handler(Looper.getMainLooper()));
            assert(notifier != null);
            mNotifier = notifier;
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            synchronized (mNotifier) {
                mNotifier.notify();
            }
        }
    }
}
