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

import android.annotation.IntDef;
import android.app.PendingIntent;
import android.content.ContentProvider;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.view.DragEvent;

import com.android.documentsui.base.BooleanConsumer;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.dirlist.DocumentDetails;
import com.android.documentsui.selection.Selection;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.function.Consumer;

import javax.annotation.Nullable;

public interface ActionHandler {

    @IntDef({
        VIEW_TYPE_NONE,
        VIEW_TYPE_REGULAR,
        VIEW_TYPE_PREVIEW
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ViewType {}
    public static final int VIEW_TYPE_NONE = 0;
    public static final int VIEW_TYPE_REGULAR = 1;
    public static final int VIEW_TYPE_PREVIEW = 2;

    void onActivityResult(int requestCode, int resultCode, Intent data);

    void openSettings(RootInfo root);

    /**
     * Drops documents on a root.
     */
    boolean dropOn(DragEvent event, RootInfo root);

    /**
     * Attempts to eject the identified root. Returns a boolean answer to listener.
     */
    void ejectRoot(RootInfo root, BooleanConsumer listener);

    /**
     * Attempts to fetch the DocumentInfo for the supplied root. Returns the DocumentInfo to the
     * callback. If the task times out, callback will be called with null DocumentInfo. Supply
     * {@link TimeoutTask#DEFAULT_TIMEOUT} if you don't want to the task to ever time out.
     */
    void getRootDocument(RootInfo root, int timeout, Consumer<DocumentInfo> callback);

    /**
     * Attempts to refresh the given DocumentInfo, which should be at the top of the state stack.
     * Returns a boolean answer to the callback, given by {@link ContentProvider#refresh}.
     */
    void refreshDocument(DocumentInfo doc, BooleanConsumer callback);


    /**
     * Attempts to start the authentication process caused by
     * {@link android.app.AuthenticationRequiredException}.
     */
    void startAuthentication(PendingIntent intent);

    void showAppDetails(ResolveInfo info);

    void openRoot(RootInfo root);

    void openRoot(ResolveInfo app);

    void loadRoot(Uri uri);

    void openSelectedInNewWindow();

    void openInNewWindow(DocumentStack path);

    void pasteIntoFolder(RootInfo root);

    void selectAllFiles();

    void showCreateDirectoryDialog();

    void showInspector(DocumentInfo doc);

    @Nullable DocumentInfo renameDocument(String name, DocumentInfo document);

    /**
     * If container, then opens the container, otherwise views using the specified type of view.
     * If the primary view type is unavailable, then fallback to the alternative type of view.
     */
    boolean openDocument(DocumentDetails doc, @ViewType int type, @ViewType int fallback);

    /**
     * This is called when user hovers over a doc for enough time during a drag n' drop, to open a
     * folder that accepts drop. We should only open a container that's not an archive, since archives
     * do not accept dropping.
     */
    void springOpenDirectory(DocumentInfo doc);

    void showChooserForDoc(DocumentInfo doc);

    void openRootDocument(@Nullable DocumentInfo rootDoc);

    void openContainerDocument(DocumentInfo doc);

    void cutToClipboard();

    void copyToClipboard();

    /**
     * In general, selected = selection or single focused item
     */
    void deleteSelectedDocuments();

    void shareSelectedDocuments();

    /**
     * Called when initial activity setup is complete. Implementations
     * should override this method to set the initial location of the
     * app.
     */
    void initLocation(Intent intent);

    void registerDisplayStateChangedListener(Runnable l);
    void unregisterDisplayStateChangedListener(Runnable l);

    void loadDocumentsForCurrentStack();

    void viewInOwner();

    void setDebugMode(boolean enabled);
    void showDebugMessage();

    /**
     * Allow action handler to be initialized in a new scope.
     * @return this
     */
    <T extends ActionHandler> T reset(DirectoryReloadLock reloadLock);
}
