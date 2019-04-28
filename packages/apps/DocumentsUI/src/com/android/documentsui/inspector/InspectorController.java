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
package com.android.documentsui.inspector;

import static android.provider.DocumentsContract.Document.FLAG_SUPPORTS_SETTINGS;
import static com.android.internal.util.Preconditions.checkArgument;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.view.View;
import android.view.View.OnClickListener;
import com.android.documentsui.DocumentsApplication;
import com.android.documentsui.ProviderExecutor;
import com.android.documentsui.R;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.Lookup;
import com.android.documentsui.inspector.actions.Action;
import com.android.documentsui.inspector.actions.ClearDefaultAppAction;
import com.android.documentsui.inspector.actions.ShowInProviderAction;
import com.android.documentsui.roots.ProvidersAccess;
import com.android.documentsui.ui.Snackbars;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
/**
 * A controller that coordinates retrieving document information and sending it to the view.
 */
public final class InspectorController {

    private final Loader mLoader;
    private final Consumer<DocumentInfo> mHeader;
    private final DetailsDisplay mDetails;
    private final ActionDisplay mShowProvider;
    private final ActionDisplay mAppDefaults;
    private final Consumer<DocumentInfo> mDebugView;
    private final boolean mShowDebug;
    private final Context mContext;
    private final PackageManager mPackageManager;
    private final ProvidersAccess mProviders;
    private final Runnable mShowSnackbar;
    private final Lookup<String, Executor> mExecutors;

    /**
     * InspectorControllerTest relies on this controller.
     */
    @VisibleForTesting
    public InspectorController(Context context, Loader loader, PackageManager pm,
            ProvidersAccess providers, boolean showDebug, Consumer<DocumentInfo> header,
            DetailsDisplay details, ActionDisplay showProvider, ActionDisplay appDefaults,
            Consumer<DocumentInfo> debugView, Lookup<String, Executor> executors,
            Runnable showSnackbar) {

        checkArgument(context != null);
        checkArgument(loader != null);
        checkArgument(pm != null);
        checkArgument(providers != null);
        checkArgument(header != null);
        checkArgument(details != null);
        checkArgument(showProvider != null);
        checkArgument(appDefaults != null);
        checkArgument(debugView != null);
        checkArgument(showSnackbar != null);
        checkArgument(executors != null);

        mContext = context;
        mLoader = loader;
        mPackageManager = pm;
        mShowDebug = showDebug;
        mProviders = providers;
        mHeader = header;
        mDetails = details;
        mShowProvider = showProvider;
        mAppDefaults = appDefaults;
        mDebugView = debugView;
        mExecutors = executors;
        mShowSnackbar = showSnackbar;
    }

    public InspectorController(Activity activity, Loader loader, View layout, boolean showDebug) {

        this(activity,
                loader,
                activity.getPackageManager(),
                DocumentsApplication.getProvidersCache (activity),
                showDebug,
                (HeaderView) layout.findViewById(R.id.inspector_header_view),
                (DetailsView) layout.findViewById(R.id.inspector_details_view),
                (ActionDisplay) layout.findViewById(R.id.inspector_show_in_provider_view),
                (ActionDisplay) layout.findViewById(R.id.inspector_app_defaults_view),
                (DebugView) layout.findViewById(R.id.inspector_debug_view),
                ProviderExecutor::forAuthority,
                () -> {
                    // using a runnable to support unit testing this feature.
                    Snackbars.showInspectorError(activity);
                }
        );
        if (showDebug) {
            layout.findViewById(R.id.inspector_debug_view).setVisibility(View.VISIBLE);
        }
    }

    public void reset() {
        mLoader.reset();
    }

    public void loadInfo(Uri uri) {
        mLoader.loadDocInfo(uri, this::updateView);
    }

    /**
     * Updates the view with documentInfo.
     */
    @Nullable
    public void updateView(@Nullable DocumentInfo docInfo) {

        if (docInfo == null) {
            mShowSnackbar.run();
        } else {
            mHeader.accept(docInfo);
            mDetails.accept(docInfo);

            if (docInfo.isDirectory()) {
                mLoader.loadDirCount(docInfo, this::displayChildCount);
            } else {

                mShowProvider.setVisible(docInfo.isSettingsSupported());
                if (docInfo.isSettingsSupported()) {
                    Action showProviderAction =
                        new ShowInProviderAction(mContext, mPackageManager, docInfo, mProviders);
                    mShowProvider.init(
                            showProviderAction,
                            (view) -> {
                                showInProvider(docInfo.derivedUri);
                            });
                }

                Action defaultAction =
                        new ClearDefaultAppAction(mContext, mPackageManager, docInfo);

                mAppDefaults.setVisible(defaultAction.canPerformAction());
                if (defaultAction.canPerformAction()) {
                    mAppDefaults.init(
                            defaultAction,
                            (View) -> {
                                clearDefaultApp(defaultAction.getPackageName());
                            });
                }
            }

            if (mShowDebug) {
                mDebugView.accept(docInfo);
            }
        }
    }

    /**
     * Displays a directory's information to the view.
     *
     * @param count - number of items in the directory.
     */
    private void displayChildCount(Integer count) {
        mDetails.setChildrenCount(count);
    }

    /**
     * Shows the selected document in it's content provider.
     *
     * @param DocumentInfo whose flag FLAG_SUPPORTS_SETTINGS is set.
     */
    public void showInProvider(Uri uri) {

        Intent intent = new Intent(DocumentsContract.ACTION_DOCUMENT_SETTINGS);
        intent.setPackage(mProviders.getPackageName(uri.getAuthority()));
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.setData(uri);
        mContext.startActivity(intent);
    }

    /**
     * Clears the default app that's opens that file type.
     *
     * @param packageName of the preferred app.
     */
    public void clearDefaultApp(String packageName) {
        assert packageName != null;
        mPackageManager.clearPackagePreferredActivities(packageName);

        mAppDefaults.setAppIcon(null);
        mAppDefaults.setAppName(mContext.getString(R.string.handler_app_not_selected));
        mAppDefaults.showAction(false);
    }

    /**
     * Interface for loading document metadata.
     */
    public interface Loader {

        /**
         * Starts the Asynchronous process of loading file data.
         *
         * @param uri - A content uri to query metadata from.
         * @param callback - Function to be called when the loader has finished loading metadata. A
         * DocumentInfo will be sent to this method. DocumentInfo may be null.
         */
        void loadDocInfo(Uri uri, Consumer<DocumentInfo> callback);

        /**
         * Loads a folders item count.
         * @param directory - a documentInfo thats a directory.
         * @param callback - Function to be called when the loader has finished loading the number
         * of children.
         */
        void loadDirCount(DocumentInfo directory, Consumer<Integer> callback);

        /**
         * Deletes all loader id's when android lifecycle ends.
         */
        void reset();
    }

    /**
     * This interface is for unit testing.
     */
    public interface ActionDisplay {

        /**
         * Initializes the view based on the action.
         * @param action - ClearDefaultAppAction or ShowInProviderAction
         * @param listener - listener for when the action is pressed.
         */
        void init(Action action, OnClickListener listener);

        /**
         * Makes the action visible.
         */
        void setVisible(boolean visible);

        void setActionHeader(String header);

        void setAppIcon(Drawable icon);

        void setAppName(String name);

        void showAction(boolean visible);
    }

    /**
     * Provides details about a file.
     */
    public interface DetailsDisplay {

        void accept(DocumentInfo info);

        void setChildrenCount(int count);
    }
}