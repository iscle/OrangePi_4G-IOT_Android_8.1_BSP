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

package com.android.documentsui;

import static com.android.documentsui.base.DocumentInfo.getCursorInt;
import static com.android.documentsui.base.DocumentInfo.getCursorString;
import static com.android.documentsui.base.Shared.DEBUG;

import android.app.Activity;
import android.app.LoaderManager.LoaderCallbacks;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.Loader;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.DocumentsContract;
import android.support.annotation.VisibleForTesting;
import android.util.Log;
import android.util.Pair;
import android.view.DragEvent;

import com.android.documentsui.AbstractActionHandler.CommonAddons;
import com.android.documentsui.LoadDocStackTask.LoadDocStackCallback;
import com.android.documentsui.base.BooleanConsumer;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.Lookup;
import com.android.documentsui.base.Providers;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.Shared;
import com.android.documentsui.base.State;
import com.android.documentsui.dirlist.AnimationView;
import com.android.documentsui.dirlist.AnimationView.AnimationType;
import com.android.documentsui.dirlist.DocumentDetails;
import com.android.documentsui.dirlist.FocusHandler;
import com.android.documentsui.files.LauncherActivity;
import com.android.documentsui.queries.SearchViewManager;
import com.android.documentsui.roots.GetRootDocumentTask;
import com.android.documentsui.roots.LoadRootTask;
import com.android.documentsui.roots.ProvidersAccess;
import com.android.documentsui.selection.Selection;
import com.android.documentsui.selection.SelectionManager;
import com.android.documentsui.sidebar.EjectRootTask;
import com.android.documentsui.ui.Snackbars;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import javax.annotation.Nullable;

/**
 * Provides support for specializing the actions (openDocument etc.) to the host activity.
 */
public abstract class AbstractActionHandler<T extends Activity & CommonAddons>
        implements ActionHandler {

    @VisibleForTesting
    public static final int CODE_FORWARD = 42;
    public static final int CODE_AUTHENTICATION = 43;

    @VisibleForTesting
    static final int LOADER_ID = 42;

    private static final String TAG = "AbstractActionHandler";
    private static final int REFRESH_SPINNER_TIMEOUT = 500;

    protected final T mActivity;
    protected final State mState;
    protected final ProvidersAccess mProviders;
    protected final DocumentsAccess mDocs;
    protected final FocusHandler mFocusHandler;
    protected final SelectionManager mSelectionMgr;
    protected final SearchViewManager mSearchMgr;
    protected final Lookup<String, Executor> mExecutors;
    protected final Injector<?> mInjector;

    private final LoaderBindings mBindings;

    private Runnable mDisplayStateChangedListener;

    private DirectoryReloadLock mDirectoryReloadLock;

    @Override
    public void registerDisplayStateChangedListener(Runnable l) {
        mDisplayStateChangedListener = l;
    }
    @Override
    public void unregisterDisplayStateChangedListener(Runnable l) {
        if (mDisplayStateChangedListener == l) {
            mDisplayStateChangedListener = null;
        }
    }

    public AbstractActionHandler(
            T activity,
            State state,
            ProvidersAccess providers,
            DocumentsAccess docs,
            SearchViewManager searchMgr,
            Lookup<String, Executor> executors,
            Injector<?> injector) {

        assert(activity != null);
        assert(state != null);
        assert(providers != null);
        assert(searchMgr != null);
        assert(docs != null);
        assert(injector != null);

        mActivity = activity;
        mState = state;
        mProviders = providers;
        mDocs = docs;
        mFocusHandler = injector.focusManager;
        mSelectionMgr = injector.selectionMgr;
        mSearchMgr = searchMgr;
        mExecutors = executors;
        mInjector = injector;

        mBindings = new LoaderBindings();
    }

    @Override
    public void ejectRoot(RootInfo root, BooleanConsumer listener) {
        new EjectRootTask(
                mActivity.getContentResolver(),
                root.authority,
                root.rootId,
                listener).executeOnExecutor(ProviderExecutor.forAuthority(root.authority));
    }

    @Override
    public void startAuthentication(PendingIntent intent) {
        try {
            mActivity.startIntentSenderForResult(intent.getIntentSender(), CODE_AUTHENTICATION,
                    null, 0, 0, 0);
        } catch (IntentSender.SendIntentException cancelled) {
            Log.d(TAG, "Authentication Pending Intent either canceled or ignored.");
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case CODE_AUTHENTICATION:
                onAuthenticationResult(resultCode);
                break;
        }
    }

    private void onAuthenticationResult(int resultCode) {
        if (resultCode == Activity.RESULT_OK) {
            Log.v(TAG, "Authentication was successful. Refreshing directory now.");
            mActivity.refreshCurrentRootAndDirectory(AnimationView.ANIM_NONE);
        }
    }

    @Override
    public void getRootDocument(RootInfo root, int timeout, Consumer<DocumentInfo> callback) {
        GetRootDocumentTask task = new GetRootDocumentTask(
                root,
                mActivity,
                timeout,
                mDocs,
                callback);

        task.executeOnExecutor(mExecutors.lookup(root.authority));
    }

    @Override
    public void refreshDocument(DocumentInfo doc, BooleanConsumer callback) {
        RefreshTask task = new RefreshTask(
                mInjector.features,
                mState,
                doc,
                REFRESH_SPINNER_TIMEOUT,
                mActivity.getApplicationContext(),
                mActivity::isDestroyed,
                callback);
        task.executeOnExecutor(mExecutors.lookup(doc == null ? null : doc.authority));
    }

    @Override
    public void openSelectedInNewWindow() {
        throw new UnsupportedOperationException("Can't open in new window.");
    }

    @Override
    public void openInNewWindow(DocumentStack path) {
        Metrics.logUserAction(mActivity, Metrics.USER_ACTION_NEW_WINDOW);

        Intent intent = LauncherActivity.createLaunchIntent(mActivity);
        intent.putExtra(Shared.EXTRA_STACK, (Parcelable) path);

        // Multi-window necessitates we pick how we are launched.
        // By default we'd be launched in-place above the existing app.
        // By setting launch-to-side ActivityManager will open us to side.
        if (mActivity.isInMultiWindowMode()) {
            intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT);
        }

        mActivity.startActivity(intent);
    }

    @Override
    public boolean openDocument(DocumentDetails doc, @ViewType int type, @ViewType int fallback) {
        throw new UnsupportedOperationException("Can't open document.");
    }

    public void showInspector(DocumentInfo doc) {
        throw new UnsupportedOperationException("Can't open properties.");
    }

    @Override
    public void springOpenDirectory(DocumentInfo doc) {
        throw new UnsupportedOperationException("Can't spring open directories.");
    }

    @Override
    public void openSettings(RootInfo root) {
        throw new UnsupportedOperationException("Can't open settings.");
    }

    @Override
    public void openRoot(ResolveInfo app) {
        throw new UnsupportedOperationException("Can't open an app.");
    }

    @Override
    public void showAppDetails(ResolveInfo info) {
        throw new UnsupportedOperationException("Can't show app details.");
    }

    @Override
    public boolean dropOn(DragEvent event, RootInfo root) {
        throw new UnsupportedOperationException("Can't open an app.");
    }

    @Override
    public void pasteIntoFolder(RootInfo root) {
        throw new UnsupportedOperationException("Can't paste into folder.");
    }

    @Override
    public void viewInOwner() {
        throw new UnsupportedOperationException("Can't view in application.");
    }

    @Override
    public void selectAllFiles() {
        Metrics.logUserAction(mActivity, Metrics.USER_ACTION_SELECT_ALL);
        Model model = mInjector.getModel();

        // Exclude disabled files
        List<String> enabled = new ArrayList<>();
        for (String id : model.getModelIds()) {
            Cursor cursor = model.getItem(id);
            if (cursor == null) {
                Log.w(TAG, "Skipping selection. Can't obtain cursor for modeId: " + id);
                continue;
            }
            String docMimeType = getCursorString(
                    cursor, DocumentsContract.Document.COLUMN_MIME_TYPE);
            int docFlags = getCursorInt(cursor, DocumentsContract.Document.COLUMN_FLAGS);
            if (mInjector.config.isDocumentEnabled(docMimeType, docFlags, mState)) {
                enabled.add(id);
            }
        }

        // Only select things currently visible in the adapter.
        boolean changed = mSelectionMgr.setItemsSelected(enabled, true);
        if (changed) {
            mDisplayStateChangedListener.run();
        }
    }

    @Override
    public void showCreateDirectoryDialog() {
        Metrics.logUserAction(mActivity, Metrics.USER_ACTION_CREATE_DIR);

        CreateDirectoryFragment.show(mActivity.getFragmentManager());
    }

    @Override
    @Nullable
    public DocumentInfo renameDocument(String name, DocumentInfo document) {
        throw new UnsupportedOperationException("Can't rename documents.");
    }

    @Override
    public void showChooserForDoc(DocumentInfo doc) {
        throw new UnsupportedOperationException("Show chooser for doc not supported!");
    }

    @Override
    public void openRootDocument(@Nullable DocumentInfo rootDoc) {
        if (rootDoc == null) {
            // There are 2 cases where rootDoc is null -- 1) loading recents; 2) failed to load root
            // document. Either case we should call refreshCurrentRootAndDirectory() to let
            // DirectoryFragment update UI.
            mActivity.refreshCurrentRootAndDirectory(AnimationView.ANIM_NONE);
        } else {
            openContainerDocument(rootDoc);
        }
    }

    @Override
    public void openContainerDocument(DocumentInfo doc) {
        assert(doc.isContainer());

        if (mSearchMgr.isSearching()) {
            loadDocument(
                    doc.derivedUri,
                    (@Nullable DocumentStack stack) -> openFolderInSearchResult(stack, doc));
        } else {
            openChildContainer(doc);
        }
    }

    private void openFolderInSearchResult(@Nullable DocumentStack stack, DocumentInfo doc) {
        if (stack == null) {
            mState.stack.popToRootDocument();

            // Update navigator to give horizontal breadcrumb a chance to update documents. It
            // doesn't update its content if the size of document stack doesn't change.
            // TODO: update breadcrumb to take range update.
            mActivity.updateNavigator();

            mState.stack.push(doc);
        } else {
            if (!Objects.equals(mState.stack.getRoot(), stack.getRoot())) {
                Log.w(TAG, "Provider returns " + stack.getRoot() + " rather than expected "
                        + mState.stack.getRoot());
            }

            mState.stack.reset();
            // Update navigator to give horizontal breadcrumb a chance to update documents. It
            // doesn't update its content if the size of document stack doesn't change.
            // TODO: update breadcrumb to take range update.
            mActivity.updateNavigator();

            mState.stack.reset(stack);
        }

        // Show an opening animation only if pressing "back" would get us back to the
        // previous directory. Especially after opening a root document, pressing
        // back, wouldn't go to the previous root, but close the activity.
        final int anim = (mState.stack.hasLocationChanged() && mState.stack.size() > 1)
                ? AnimationView.ANIM_ENTER : AnimationView.ANIM_NONE;
        mActivity.refreshCurrentRootAndDirectory(anim);
    }

    private void openChildContainer(DocumentInfo doc) {
        DocumentInfo currentDoc = null;

        if (doc.isDirectory()) {
            // Regular directory.
            currentDoc = doc;
        } else if (doc.isArchive()) {
            // Archive.
            currentDoc = mDocs.getArchiveDocument(doc.derivedUri);
        }

        assert(currentDoc != null);
        mActivity.notifyDirectoryNavigated(currentDoc.derivedUri);

        mState.stack.push(currentDoc);
        // Show an opening animation only if pressing "back" would get us back to the
        // previous directory. Especially after opening a root document, pressing
        // back, wouldn't go to the previous root, but close the activity.
        final int anim = (mState.stack.hasLocationChanged() && mState.stack.size() > 1)
                ? AnimationView.ANIM_ENTER : AnimationView.ANIM_NONE;
        mActivity.refreshCurrentRootAndDirectory(anim);
    }

    @Override
    public void setDebugMode(boolean enabled) {
        if (!mInjector.features.isDebugSupportEnabled()) {
            return;
        }

        mState.debugMode = enabled;
        mInjector.features.forceFeature(R.bool.feature_command_interceptor, enabled);
        mInjector.features.forceFeature(R.bool.feature_inspector, enabled);
        mActivity.invalidateOptionsMenu();

        if (enabled) {
            showDebugMessage();
        } else {
            mActivity.getActionBar().setBackgroundDrawable(new ColorDrawable(
                    mActivity.getResources().getColor(R.color.primary)));
            mActivity.getWindow().setStatusBarColor(
                    mActivity.getResources().getColor(R.color.primary_dark));
        }
    }

    @Override
    public void showDebugMessage() {
        assert (mInjector.features.isDebugSupportEnabled());

        int[] colors = mInjector.debugHelper.getNextColors();
        Pair<String, Integer> messagePair = mInjector.debugHelper.getNextMessage();

        Snackbars.showCustomTextWithImage(mActivity, messagePair.first, messagePair.second);

        mActivity.getActionBar().setBackgroundDrawable(new ColorDrawable(colors[0]));
        mActivity.getWindow().setStatusBarColor(colors[1]);
    }

    @Override
    public void cutToClipboard() {
        throw new UnsupportedOperationException("Cut not supported!");
    }

    @Override
    public void copyToClipboard() {
        throw new UnsupportedOperationException("Copy not supported!");
    }

    @Override
    public void deleteSelectedDocuments() {
        throw new UnsupportedOperationException("Delete not supported!");
    }

    @Override
    public void shareSelectedDocuments() {
        throw new UnsupportedOperationException("Share not supported!");
    }

    protected final void loadDocument(Uri uri, LoadDocStackCallback callback) {
        new LoadDocStackTask(
                mActivity,
                mProviders,
                mDocs,
                callback
                ).executeOnExecutor(mExecutors.lookup(uri.getAuthority()), uri);
    }

    @Override
    public final void loadRoot(Uri uri) {
        new LoadRootTask<>(mActivity, mProviders, mState, uri)
                .executeOnExecutor(mExecutors.lookup(uri.getAuthority()));
    }

    @Override
    public void loadDocumentsForCurrentStack() {
        DocumentStack stack = mState.stack;
        if (!stack.isRecents() && stack.isEmpty()) {
            DirectoryResult result = new DirectoryResult();

            // TODO (b/35996595): Consider plumbing through the actual exception, though it might
            // not be very useful (always pointing to DatabaseUtils#readExceptionFromParcel()).
            result.exception = new IllegalStateException("Failed to load root document.");
            mInjector.getModel().update(result);
            return;
        }

        mActivity.getLoaderManager().restartLoader(LOADER_ID, null, mBindings);
    }

    protected final boolean launchToDocument(Uri uri) {
        // We don't support launching to a document in an archive.
        if (!Providers.isArchiveUri(uri)) {
            loadDocument(uri, this::onStackLoaded);
            return true;
        }

        return false;
    }

    private void onStackLoaded(@Nullable DocumentStack stack) {
        if (stack != null) {
            if (!stack.peek().isDirectory()) {
                // Requested document is not a directory. Pop it so that we can launch into its
                // parent.
                stack.pop();
            }
            mState.stack.reset(stack);
            mActivity.refreshCurrentRootAndDirectory(AnimationView.ANIM_NONE);

            Metrics.logLaunchAtLocation(mActivity, mState, stack.getRoot().getUri());
        } else {
            Log.w(TAG, "Failed to launch into the given uri. Launch to default location.");
            launchToDefaultLocation();

            Metrics.logLaunchAtLocation(mActivity, mState, null);
        }
    }

    protected abstract void launchToDefaultLocation();

    protected void restoreRootAndDirectory() {
        if (!mState.stack.getRoot().isRecents() && mState.stack.isEmpty()) {
            mActivity.onRootPicked(mState.stack.getRoot());
        } else {
            mActivity.restoreRootAndDirectory();
        }
    }

    protected final void loadHomeDir() {
        loadRoot(Shared.getDefaultRootUri(mActivity));
    }

    protected Selection getStableSelection() {
        return mSelectionMgr.getSelection(new Selection());
    }

    @Override
    public ActionHandler reset(DirectoryReloadLock reloadLock) {
        mDirectoryReloadLock = reloadLock;
        mActivity.getLoaderManager().destroyLoader(LOADER_ID);
        return this;
    }

    private final class LoaderBindings implements LoaderCallbacks<DirectoryResult> {

        @Override
        public Loader<DirectoryResult> onCreateLoader(int id, Bundle args) {
            Context context = mActivity;

            if (mState.stack.isRecents()) {

                if (DEBUG) Log.d(TAG, "Creating new loader recents.");
                return new RecentsLoader(
                        context,
                        mProviders,
                        mState,
                        mInjector.features,
                        mExecutors,
                        mInjector.fileTypeLookup);
            } else {

                Uri contentsUri = mSearchMgr.isSearching()
                        ? DocumentsContract.buildSearchDocumentsUri(
                            mState.stack.getRoot().authority,
                            mState.stack.getRoot().rootId,
                            mSearchMgr.getCurrentSearch())
                        : DocumentsContract.buildChildDocumentsUri(
                                mState.stack.peek().authority,
                                mState.stack.peek().documentId);

                if (mInjector.config.managedModeEnabled(mState.stack)) {
                    contentsUri = DocumentsContract.setManageMode(contentsUri);
                }

                if (DEBUG) Log.d(TAG,
                        "Creating new directory loader for: "
                                + DocumentInfo.debugString(mState.stack.peek()));

                return new DirectoryLoader(
                        mInjector.features,
                        context,
                        mState.stack.getRoot(),
                        mState.stack.peek(),
                        contentsUri,
                        mState.sortModel,
                        mInjector.fileTypeLookup,
                        mDirectoryReloadLock,
                        mSearchMgr.isSearching());
            }
        }

        @Override
        public void onLoadFinished(Loader<DirectoryResult> loader, DirectoryResult result) {
            if (DEBUG) Log.d(TAG, "Loader has finished for: "
                    + DocumentInfo.debugString(mState.stack.peek()));
            assert(result != null);

            mInjector.getModel().update(result);
        }

        @Override
        public void onLoaderReset(Loader<DirectoryResult> loader) {}
    }
    /**
     * A class primarily for the support of isolating our tests
     * from our concrete activity implementations.
     */
    public interface CommonAddons {
        void restoreRootAndDirectory();
        void refreshCurrentRootAndDirectory(@AnimationType int anim);
        void onRootPicked(RootInfo root);
        // TODO: Move this to PickAddons as multi-document picking is exclusive to that activity.
        void onDocumentsPicked(List<DocumentInfo> docs);
        void onDocumentPicked(DocumentInfo doc);
        RootInfo getCurrentRoot();
        DocumentInfo getCurrentDirectory();
        void setRootsDrawerOpen(boolean open);

        // TODO: Let navigator listens to State
        void updateNavigator();

        @VisibleForTesting
        void notifyDirectoryNavigated(Uri docUri);
    }
}
