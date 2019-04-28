/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.documentsui;

import static com.android.documentsui.base.Shared.DEBUG;
import static com.android.documentsui.base.Shared.TAG;

import android.app.ActivityManager;
import android.content.AsyncTaskLoader;
import android.content.ContentProviderClient;
import android.content.Context;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.database.MatrixCursor;
import android.database.MergeCursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.text.format.DateUtils;
import android.util.Log;

import com.android.documentsui.base.Features;
import com.android.documentsui.base.FilteringCursorWrapper;
import com.android.documentsui.base.Lookup;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.State;
import com.android.documentsui.roots.ProvidersAccess;
import com.android.documentsui.roots.RootCursorWrapper;
import com.android.internal.annotations.GuardedBy;

import com.google.common.util.concurrent.AbstractFuture;

import libcore.io.IoUtils;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class RecentsLoader extends AsyncTaskLoader<DirectoryResult> {
    // TODO: clean up cursor ownership so background thread doesn't traverse
    // previously returned cursors for filtering/sorting; this currently races
    // with the UI thread.

    private static final int MAX_OUTSTANDING_RECENTS = 4;
    private static final int MAX_OUTSTANDING_RECENTS_SVELTE = 2;

    /**
     * Time to wait for first pass to complete before returning partial results.
     */
    private static final int MAX_FIRST_PASS_WAIT_MILLIS = 500;

    /** Maximum documents from a single root. */
    private static final int MAX_DOCS_FROM_ROOT = 64;

    /** Ignore documents older than this age. */
    private static final long REJECT_OLDER_THAN = 45 * DateUtils.DAY_IN_MILLIS;

    /** MIME types that should always be excluded from recents. */
    private static final String[] RECENT_REJECT_MIMES = new String[] { Document.MIME_TYPE_DIR };

    private final Semaphore mQueryPermits;

    private final ProvidersAccess mProviders;
    private final State mState;
    private final Features mFeatures;
    private final Lookup<String, Executor> mExecutors;
    private final Lookup<String, String> mFileTypeMap;

    @GuardedBy("mTasks")
    /** A authority -> RecentsTask map */
    private final Map<String, RecentsTask> mTasks = new HashMap<>();

    private CountDownLatch mFirstPassLatch;
    private volatile boolean mFirstPassDone;

    private DirectoryResult mResult;

    public RecentsLoader(Context context, ProvidersAccess providers, State state, Features features,
            Lookup<String, Executor> executors, Lookup<String, String> fileTypeMap) {

        super(context);
        mProviders = providers;
        mState = state;
        mFeatures = features;
        mExecutors = executors;
        mFileTypeMap = fileTypeMap;

        // Keep clients around on high-RAM devices, since we'd be spinning them
        // up moments later to fetch thumbnails anyway.
        final ActivityManager am = (ActivityManager) getContext().getSystemService(
                Context.ACTIVITY_SERVICE);
        mQueryPermits = new Semaphore(
                am.isLowRamDevice() ? MAX_OUTSTANDING_RECENTS_SVELTE : MAX_OUTSTANDING_RECENTS);
    }

    @Override
    public DirectoryResult loadInBackground() {
        synchronized (mTasks) {
            return loadInBackgroundLocked();
        }
    }

    private DirectoryResult loadInBackgroundLocked() {
        if (mFirstPassLatch == null) {
            // First time through we kick off all the recent tasks, and wait
            // around to see if everyone finishes quickly.
            Map<String, List<String>> rootsIndex = indexRecentsRoots();

            for (String authority : rootsIndex.keySet()) {
                mTasks.put(authority, new RecentsTask(authority, rootsIndex.get(authority)));
            }

            mFirstPassLatch = new CountDownLatch(mTasks.size());
            for (RecentsTask task : mTasks.values()) {
                mExecutors.lookup(task.authority).execute(task);
            }

            try {
                mFirstPassLatch.await(MAX_FIRST_PASS_WAIT_MILLIS, TimeUnit.MILLISECONDS);
                mFirstPassDone = true;
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        final long rejectBefore = System.currentTimeMillis() - REJECT_OLDER_THAN;

        // Collect all finished tasks
        boolean allDone = true;
        int totalQuerySize = 0;
        List<Cursor> cursors = new ArrayList<>(mTasks.size());
        for (RecentsTask task : mTasks.values()) {
            if (task.isDone()) {
                try {
                    final Cursor[] taskCursors = task.get();
                    if (taskCursors == null || taskCursors.length == 0) continue;

                    totalQuerySize += taskCursors.length;
                    for (Cursor cursor : taskCursors) {
                        if (cursor == null) {
                            // It's possible given an authority, some roots fail to return a cursor
                            // after a query.
                            continue;
                        }
                        final FilteringCursorWrapper filtered = new FilteringCursorWrapper(
                                cursor, mState.acceptMimes, RECENT_REJECT_MIMES, rejectBefore) {
                            @Override
                            public void close() {
                                // Ignored, since we manage cursor lifecycle internally
                            }
                        };
                        cursors.add(filtered);
                    }

                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } catch (ExecutionException e) {
                    // We already logged on other side
                } catch (Exception e) {
                    // Catch exceptions thrown when we read the cursor.
                    Log.e(TAG, "Failed to query Recents for authority: " + task.authority
                            + ". Skip this authority in Recents.", e);
                }
            } else {
                allDone = false;
            }
        }

        if (DEBUG) {
            Log.d(TAG,
                    "Found " + cursors.size() + " of " + totalQuerySize + " recent queries done");
        }

        final DirectoryResult result = new DirectoryResult();

        final Cursor merged;
        if (cursors.size() > 0) {
            merged = new MergeCursor(cursors.toArray(new Cursor[cursors.size()]));
        } else {
            // Return something when nobody is ready
            merged = new MatrixCursor(new String[0]);
        }

        final Cursor notMovableMasked = new NotMovableMaskCursor(merged);
        final Cursor sorted = mState.sortModel.sortCursor(notMovableMasked, mFileTypeMap);

        // Tell the UI if this is an in-progress result. When loading is complete, another update is
        // sent with EXTRA_LOADING set to false.
        Bundle extras = new Bundle();
        extras.putBoolean(DocumentsContract.EXTRA_LOADING, !allDone);
        sorted.setExtras(extras);

        result.cursor = sorted;

        return result;
    }

    /**
     * Returns a map of Authority -> rootIds
     */
    private Map<String, List<String>> indexRecentsRoots() {
        final Collection<RootInfo> roots = mProviders.getMatchingRootsBlocking(mState);
        HashMap<String, List<String>> rootsIndex = new HashMap<>();
        for (RootInfo root : roots) {
            if (!root.supportsRecents()) {
                continue;
            }

            if (!rootsIndex.containsKey(root.authority)) {
                rootsIndex.put(root.authority, new ArrayList<>());
            }
            rootsIndex.get(root.authority).add(root.rootId);
        }

        return rootsIndex;
    }

    @Override
    public void cancelLoadInBackground() {
        super.cancelLoadInBackground();
    }

    @Override
    public void deliverResult(DirectoryResult result) {
        if (isReset()) {
            IoUtils.closeQuietly(result);
            return;
        }
        DirectoryResult oldResult = mResult;
        mResult = result;

        if (isStarted()) {
            super.deliverResult(result);
        }

        if (oldResult != null && oldResult != result) {
            IoUtils.closeQuietly(oldResult);
        }
    }

    @Override
    protected void onStartLoading() {
        if (mResult != null) {
            deliverResult(mResult);
        }
        if (takeContentChanged() || mResult == null) {
            forceLoad();
        }
    }

    @Override
    protected void onStopLoading() {
        cancelLoad();
    }

    @Override
    public void onCanceled(DirectoryResult result) {
        IoUtils.closeQuietly(result);
    }

    @Override
    protected void onReset() {
        super.onReset();

        // Ensure the loader is stopped
        onStopLoading();

        synchronized (mTasks) {
            for (RecentsTask task : mTasks.values()) {
                IoUtils.closeQuietly(task);
            }
        }

        IoUtils.closeQuietly(mResult);
        mResult = null;
    }

    // TODO: create better transfer of ownership around cursor to ensure its
    // closed in all edge cases.

    private class RecentsTask extends AbstractFuture<Cursor[]> implements Runnable, Closeable {
        public final String authority;
        public final List<String> rootIds;

        private Cursor[] mCursors;
        private boolean mIsClosed = false;

        public RecentsTask(String authority, List<String> rootIds) {
            this.authority = authority;
            this.rootIds = rootIds;
        }

        @Override
        public void run() {
            if (isCancelled()) return;

            try {
                mQueryPermits.acquire();
            } catch (InterruptedException e) {
                return;
            }

            try {
                runInternal();
            } finally {
                mQueryPermits.release();
            }
        }

        private synchronized void runInternal() {
            if (mIsClosed) {
                return;
            }

            ContentProviderClient client = null;
            try {
                client = DocumentsApplication.acquireUnstableProviderOrThrow(
                        getContext().getContentResolver(), authority);

                final Cursor[] res = new Cursor[rootIds.size()];
                mCursors = new Cursor[rootIds.size()];
                for (int i = 0; i < rootIds.size(); i++) {
                    final Uri uri =
                            DocumentsContract.buildRecentDocumentsUri(authority, rootIds.get(i));
                    try {
                        if (mFeatures.isContentPagingEnabled()) {
                            final Bundle queryArgs = new Bundle();
                            mState.sortModel.addQuerySortArgs(queryArgs);
                            res[i] = client.query(uri, null, queryArgs, null);
                        } else {
                            res[i] = client.query(
                                    uri, null, null, null, mState.sortModel.getDocumentSortQuery());
                        }
                        mCursors[i] = new RootCursorWrapper(authority, rootIds.get(i), res[i],
                                MAX_DOCS_FROM_ROOT);
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to load " + authority + ", " + rootIds.get(i), e);
                    }
                }

            } catch (Exception e) {
                Log.w(TAG, "Failed to acquire content resolver for authority: " + authority);
            } finally {
                ContentProviderClient.releaseQuietly(client);
            }

            set(mCursors);

            mFirstPassLatch.countDown();
            if (mFirstPassDone) {
                onContentChanged();
            }
        }

        @Override
        public synchronized void close() throws IOException {
            if (mCursors == null) {
                return;
            }

            for (Cursor cursor : mCursors) {
                IoUtils.closeQuietly(cursor);
            }

            mIsClosed = true;
        }
    }

    private static class NotMovableMaskCursor extends CursorWrapper {
        private static final int NOT_MOVABLE_MASK =
                ~(Document.FLAG_SUPPORTS_DELETE
                        | Document.FLAG_SUPPORTS_REMOVE
                        | Document.FLAG_SUPPORTS_MOVE);

        private NotMovableMaskCursor(Cursor cursor) {
            super(cursor);
        }

        @Override
        public int getInt(int index) {
            final int flagIndex = getWrappedCursor().getColumnIndex(Document.COLUMN_FLAGS);
            final int value = super.getInt(index);
            return (index == flagIndex) ? (value & NOT_MOVABLE_MASK) : value;
        }
    }
}
