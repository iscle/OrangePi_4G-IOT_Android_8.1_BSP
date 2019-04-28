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

package com.android.documentsui.files;

import static com.android.documentsui.base.DocumentInfo.getCursorString;
import static com.android.documentsui.base.Shared.DEBUG;
import static com.android.documentsui.base.Shared.MAX_DOCS_IN_INTENT;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Intent;
import android.content.QuickViewConstants;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.util.Range;

import com.android.documentsui.R;
import com.android.documentsui.base.DebugFlags;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.Model;
import com.android.documentsui.roots.RootCursorWrapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides support for gather a list of quick-viewable files into a quick view intent.
 */
public final class QuickViewIntentBuilder {

    // trusted quick view package can be set via system property on debug builds.
    // Unfortunately when the value is set, it interferes with testing (supercedes
    // any value set in the resource system).
    // For that reason when trusted quick view package is set to this magic value
    // we won't honor the system property.
    public static final String IGNORE_DEBUG_PROP = "*disabled*";
    private static final String TAG = "QuickViewIntentBuilder";

    private static final String[] IN_ARCHIVE_FEATURES = {};
    private static final String[] FULL_FEATURES = {
            QuickViewConstants.FEATURE_VIEW,
            QuickViewConstants.FEATURE_EDIT,
            QuickViewConstants.FEATURE_SEND,
            QuickViewConstants.FEATURE_DOWNLOAD,
            QuickViewConstants.FEATURE_PRINT
    };

    private final DocumentInfo mDocument;
    private final Model mModel;

    private final PackageManager mPackageMgr;
    private final Resources mResources;

    public QuickViewIntentBuilder(
            PackageManager packageMgr,
            Resources resources,
            DocumentInfo doc,
            Model model) {

        assert(packageMgr != null);
        assert(resources != null);
        assert(doc != null);
        assert(model != null);

        mPackageMgr = packageMgr;
        mResources = resources;
        mDocument = doc;
        mModel = model;
    }

    /**
     * Builds the intent for quick viewing. Short circuits building if a handler cannot
     * be resolved; in this case {@code null} is returned.
     */
    @Nullable Intent build() {
        if (DEBUG) Log.d(TAG, "Preparing intent for doc:" + mDocument.documentId);

        String trustedPkg = getQuickViewPackage();

        if (!TextUtils.isEmpty(trustedPkg)) {
            Intent intent = new Intent(Intent.ACTION_QUICK_VIEW);
            intent.setDataAndType(mDocument.derivedUri, mDocument.mimeType);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            intent.setPackage(trustedPkg);
            if (hasRegisteredHandler(intent)) {
                includeQuickViewFeaturesFlag(intent, mDocument);

                final ArrayList<Uri> uris = new ArrayList<>();
                final int documentLocation = collectViewableUris(uris);
                final Range<Integer> range = computeSiblingsRange(uris, documentLocation);

                ClipData clipData = null;
                ClipData.Item item;
                Uri uri;
                for (int i = range.getLower(); i <= range.getUpper(); i++) {
                    uri = uris.get(i);
                    item = new ClipData.Item(uri);
                    if (DEBUG) Log.d(TAG, "Including file: " + uri);
                    if (clipData == null) {
                        clipData = new ClipData(
                                "URIs", new String[] { ClipDescription.MIMETYPE_TEXT_URILIST },
                                item);
                    } else {
                        clipData.addItem(item);
                    }
                }

                // The documentLocation variable contains an index in "uris". However,
                // ClipData contains a slice of "uris", so we need to shift the location
                // so it points to the same Uri.
                intent.putExtra(Intent.EXTRA_INDEX, documentLocation - range.getLower());
                intent.setClipData(clipData);

                return intent;
            } else {
                Log.e(TAG, "Can't resolve trusted quick view package: " + trustedPkg);
            }
        }

        return null;
    }

    private String getQuickViewPackage() {
        String resValue = mResources.getString(R.string.trusted_quick_viewer_package);

        // Allow automated tests to hard-disable quick viewing.
        if (IGNORE_DEBUG_PROP.equals(resValue)) {
            return "";
        }

        // Allow users of debug devices to override default quick viewer
        // for the purposes of testing.
        if (Build.IS_DEBUGGABLE) {
            String quickViewer = DebugFlags.getQuickViewer();
            if (quickViewer != null) {
                return quickViewer;
            }
            return android.os.SystemProperties.get("debug.quick_viewer", resValue);
        }
        return resValue;
    }

    private int collectViewableUris(ArrayList<Uri> uris) {
        final String[] siblingIds = mModel.getModelIds();
        uris.ensureCapacity(siblingIds.length);

        int documentLocation = 0;
        Cursor cursor;
        String mimeType;
        String id;
        String authority;
        Uri uri;

        // Cursor's are not guaranteed to be immutable. Hence, traverse it only once.
        for (int i = 0; i < siblingIds.length; i++) {
            cursor = mModel.getItem(siblingIds[i]);

            if (cursor == null) {
                if (DEBUG) Log.d(TAG,
                        "Unable to obtain cursor for sibling document, modelId: "
                        + siblingIds[i]);
                continue;
            }

            mimeType = getCursorString(cursor, Document.COLUMN_MIME_TYPE);
            if (Document.MIME_TYPE_DIR.equals(mimeType)) {
                if (DEBUG) Log.d(TAG,
                        "Skipping directory, not supported by quick view. modelId: "
                        + siblingIds[i]);
                continue;
            }

            id = getCursorString(cursor, Document.COLUMN_DOCUMENT_ID);
            authority = getCursorString(cursor, RootCursorWrapper.COLUMN_AUTHORITY);
            uri = DocumentsContract.buildDocumentUri(authority, id);

            uris.add(uri);

            if (id.equals(mDocument.documentId)) {
                documentLocation = uris.size() - 1;  // Position in "uris", not in the model.
                if (DEBUG) Log.d(TAG, "Found starting point for QV. " + documentLocation);
            }
        }

        return documentLocation;
    }

    private boolean hasRegisteredHandler(Intent intent) {
        // Try to resolve the intent. If a matching app isn't installed, it won't resolve.
        return intent.resolveActivity(mPackageMgr) != null;
    }

    private static void includeQuickViewFeaturesFlag(Intent intent, DocumentInfo doc) {
        intent.putExtra(
                Intent.EXTRA_QUICK_VIEW_FEATURES,
                doc.isInArchive() ? IN_ARCHIVE_FEATURES : FULL_FEATURES);
    }

    private static Range<Integer> computeSiblingsRange(List<Uri> uris, int documentLocation) {
        // Restrict number of siblings to avoid hitting the IPC limit.
        // TODO: Remove this restriction once ClipData can hold an arbitrary number of
        // items.
        int firstSibling;
        int lastSibling;
        if (documentLocation < uris.size() / 2) {
            firstSibling = Math.max(0, documentLocation - MAX_DOCS_IN_INTENT / 2);
            lastSibling = Math.min(uris.size() - 1, firstSibling + MAX_DOCS_IN_INTENT - 1);
        } else {
            lastSibling = Math.min(uris.size() - 1, documentLocation + MAX_DOCS_IN_INTENT / 2);
            firstSibling = Math.max(0, lastSibling - MAX_DOCS_IN_INTENT + 1);
        }

        if (DEBUG) Log.d(TAG, "Copmuted siblings from index: " + firstSibling
                + " to: " + lastSibling);

        return new Range(firstSibling, lastSibling);
    }
}
