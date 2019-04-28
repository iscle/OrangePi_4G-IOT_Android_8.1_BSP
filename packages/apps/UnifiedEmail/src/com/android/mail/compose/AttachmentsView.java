/**
 * Copyright (c) 2011, Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.mail.compose;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.webkit.MimeTypeMap;
import android.widget.LinearLayout;

import com.android.mail.R;
import com.android.mail.providers.Account;
import com.android.mail.providers.Attachment;
import com.android.mail.ui.AttachmentTile;
import com.android.mail.ui.AttachmentTile.AttachmentPreview;
import com.android.mail.ui.AttachmentTileGrid;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;

/*
 * View for displaying attachments in the compose screen.
 */
class AttachmentsView extends LinearLayout {
    private static final String LOG_TAG = LogTag.getLogTag();

    private final ArrayList<Attachment> mAttachments;
    private AttachmentAddedOrDeletedListener mChangeListener;
    private AttachmentTileGrid mTileGrid;
    private LinearLayout mAttachmentLayout;

    public AttachmentsView(Context context) {
        this(context, null);
    }

    public AttachmentsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mAttachments = Lists.newArrayList();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mTileGrid = (AttachmentTileGrid) findViewById(R.id.attachment_tile_grid);
        mAttachmentLayout = (LinearLayout) findViewById(R.id.attachment_bar_list);
    }

    public void expandView() {
        mTileGrid.setVisibility(VISIBLE);
        mAttachmentLayout.setVisibility(VISIBLE);

        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(getWindowToken(), 0);
        }
    }

    /**
     * Set a listener for changes to the attachments.
     */
    public void setAttachmentChangesListener(AttachmentAddedOrDeletedListener listener) {
        mChangeListener = listener;
    }

    /**
     * Adds an attachment and updates the ui accordingly.
     */
    private void addAttachment(final Attachment attachment) {
        mAttachments.add(attachment);

        // If the attachment is inline do not display this attachment.
        if (attachment.isInlineAttachment()) {
            return;
        }

        if (!isShown()) {
            setVisibility(View.VISIBLE);
        }

        expandView();

        // If we have an attachment that should be shown in a tiled look,
        // set up the tile and add it to the tile grid.
        if (AttachmentTile.isTiledAttachment(attachment)) {
            final ComposeAttachmentTile attachmentTile =
                    mTileGrid.addComposeTileFromAttachment(attachment);
            attachmentTile.addDeleteListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    deleteAttachment(attachmentTile, attachment);
                }
            });
        // Otherwise, use the old bar look and add it to the new
        // inner LinearLayout.
        } else {
            final AttachmentComposeView attachmentView =
                new AttachmentComposeView(getContext(), attachment);

            attachmentView.addDeleteListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    deleteAttachment(attachmentView, attachment);
                }
            });


            mAttachmentLayout.addView(attachmentView, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT));
        }
        if (mChangeListener != null) {
            mChangeListener.onAttachmentAdded();
        }
    }

    @VisibleForTesting
    protected void deleteAttachment(final View attachmentView,
            final Attachment attachment) {
        mAttachments.remove(attachment);
        ((ViewGroup) attachmentView.getParent()).removeView(attachmentView);
        if (mChangeListener != null) {
            mChangeListener.onAttachmentDeleted();
        }
    }

    /**
     * Get all attachments being managed by this view.
     * @return attachments.
     */
    public ArrayList<Attachment> getAttachments() {
        return mAttachments;
    }

    /**
     * Get all attachments previews that have been loaded
     * @return attachments previews.
     */
    public ArrayList<AttachmentPreview> getAttachmentPreviews() {
        return mTileGrid.getAttachmentPreviews();
    }

    /**
     * Call this on restore instance state so previews persist across configuration changes
     */
    public void setAttachmentPreviews(ArrayList<AttachmentPreview> previews) {
        mTileGrid.setAttachmentPreviews(previews);
    }

    /**
     * Delete all attachments being managed by this view.
     */
    public void deleteAllAttachments() {
        mAttachments.clear();
        mTileGrid.removeAllViews();
        mAttachmentLayout.removeAllViews();
        setVisibility(GONE);
    }

    /**
     * Get the total size of all attachments currently in this view.
     */
    private long getTotalAttachmentsSize() {
        long totalSize = 0;
        for (Attachment attachment : mAttachments) {
            totalSize += attachment.size;
        }
        return totalSize;
    }

    /**
     * Interface to implement to be notified about changes to the attachments
     * explicitly made by the user.
     */
    public interface AttachmentAddedOrDeletedListener {
        public void onAttachmentDeleted();

        public void onAttachmentAdded();
    }

    /**
     * Checks if the passed Uri is a virtual document.
     *
     * @param contentUri
     * @return true if virtual, false if regular.
     */
    @TargetApi(24)
    private boolean isVirtualDocument(Uri contentUri) {
        // For SAF files, check if it's a virtual document.
        if (!DocumentsContract.isDocumentUri(getContext(), contentUri)) {
          return false;
        }

        final ContentResolver contentResolver = getContext().getContentResolver();
        final Cursor metadataCursor = contentResolver.query(contentUri,
                new String[] { DocumentsContract.Document.COLUMN_FLAGS }, null, null, null);
        if (metadataCursor != null) {
            try {
                int flags = 0;
                if (metadataCursor.moveToNext()) {
                    flags = metadataCursor.getInt(0);
                }
                if ((flags & DocumentsContract.Document.FLAG_VIRTUAL_DOCUMENT) != 0) {
                    return true;
                }
            } finally {
                metadataCursor.close();
            }
        }

        return false;
    }

    /**
     * Generate an {@link Attachment} object for a given local content URI. Attempts to populate
     * the {@link Attachment#name}, {@link Attachment#size}, and {@link Attachment#contentType}
     * fields using a {@link ContentResolver}.
     *
     * @param contentUri
     * @return an Attachment object
     * @throws AttachmentFailureException
     */
    public Attachment generateLocalAttachment(Uri contentUri) throws AttachmentFailureException {
        if (contentUri == null || TextUtils.isEmpty(contentUri.getPath())) {
            throw new AttachmentFailureException("Failed to create local attachment");
        }

        // FIXME: do not query resolver for type on the UI thread
        final ContentResolver contentResolver = getContext().getContentResolver();
        String contentType = contentResolver.getType(contentUri);

        if (contentType == null) contentType = "";

        final Attachment attachment = new Attachment();
        attachment.uri = null;  // URI will be assigned by the provider upon send/save
        attachment.contentUri = contentUri;
        attachment.thumbnailUri = contentUri;

        Cursor metadataCursor = null;
        String name = null;
        int size = -1;  // Unknown, will be determined either now or in the service
        final boolean isVirtual = Utils.isRunningNOrLater()
                ? isVirtualDocument(contentUri) : false;

        if (isVirtual) {
            final String[] mimeTypes = contentResolver.getStreamTypes(contentUri, "*/*");
            if (mimeTypes != null && mimeTypes.length > 0) {
                attachment.virtualMimeType = mimeTypes[0];
            } else{
                throw new AttachmentFailureException(
                        "Cannot attach a virtual document without any streamable format.");
            }
        }

        try {
            metadataCursor = contentResolver.query(
                    contentUri, new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE},
                    null, null, null);
            if (metadataCursor != null) {
                try {
                    if (metadataCursor.moveToNext()) {
                        name =  metadataCursor.getString(0);
                        // For virtual document this size is not the one which will be attached,
                        // so ignore it.
                        if (!isVirtual) {
                            size = metadataCursor.getInt(1);
                        }
                    }
                } finally {
                    metadataCursor.close();
                }
            }
        } catch (SQLiteException ex) {
            // One of the two columns is probably missing, let's make one more attempt to get at
            // least one.
            // Note that the documentations in Intent#ACTION_OPENABLE and
            // OpenableColumns seem to contradict each other about whether these columns are
            // required, but it doesn't hurt to fail properly.

            // Let's try to get DISPLAY_NAME
            try {
                metadataCursor = getOptionalColumn(contentResolver, contentUri,
                        OpenableColumns.DISPLAY_NAME);
                if (metadataCursor != null && metadataCursor.moveToNext()) {
                    name = metadataCursor.getString(0);
                }
            } finally {
                if (metadataCursor != null) metadataCursor.close();
            }

            // Let's try to get SIZE
            if (!isVirtual) {
                try {
                    metadataCursor =
                            getOptionalColumn(contentResolver, contentUri, OpenableColumns.SIZE);
                    if (metadataCursor != null && metadataCursor.moveToNext()) {
                        size = metadataCursor.getInt(0);
                    } else {
                        // Unable to get the size from the metadata cursor. Open the file and seek.
                        size = getSizeFromFile(contentUri, contentResolver);
                    }
                } finally {
                    if (metadataCursor != null) metadataCursor.close();
                }
            }
        } catch (SecurityException e) {
            throw new AttachmentFailureException("Security Exception from attachment uri", e);
        }

        if (name == null) {
            name = contentUri.getLastPathSegment();
        }

        // For virtual files append the inferred extension name.
        if (isVirtual) {
            String extension = MimeTypeMap.getSingleton()
                    .getExtensionFromMimeType(attachment.virtualMimeType);
            if (extension != null) {
                name += "." + extension;
            }
        }

        // TODO: This can't work with pipes. Fix it.
        if (size == -1 && !isVirtual) {
            // if the attachment is not a content:// for example, a file:// URI
            size = getSizeFromFile(contentUri, contentResolver);
        }

        // Save the computed values into the attachment.
        attachment.size = size;
        attachment.setName(name);
        attachment.setContentType(contentType);

        return attachment;
    }

    /**
     * Adds an attachment of either local or remote origin, checking to see if the attachment
     * exceeds file size limits.
     * @param account
     * @param attachment the attachment to be added.
     *
     * @throws AttachmentFailureException if an error occurs adding the attachment.
     */
    public void addAttachment(Account account, Attachment attachment)
            throws AttachmentFailureException {
        final int maxSize = account.settings.getMaxAttachmentSize();

        // The attachment size is known and it's too big.
        if (attachment.size > maxSize) {
            throw new AttachmentFailureException(
                    "Attachment too large to attach", R.string.too_large_to_attach_single);
        } else if (attachment.size != -1 && (getTotalAttachmentsSize()
                + attachment.size) > maxSize) {
            throw new AttachmentFailureException(
                    "Attachment too large to attach", R.string.too_large_to_attach_additional);
        } else {
            addAttachment(attachment);
        }
    }

    /**
     * @return size of the file or -1 if unknown.
     */
    private static int getSizeFromFile(Uri uri, ContentResolver contentResolver) {
        int size = -1;
        ParcelFileDescriptor file = null;
        try {
            file = contentResolver.openFileDescriptor(uri, "r");
            size = (int) file.getStatSize();
        } catch (FileNotFoundException e) {
            LogUtils.w(LOG_TAG, e, "Error opening file to obtain size.");
        } finally {
            try {
                if (file != null) {
                    file.close();
                }
            } catch (IOException e) {
                LogUtils.w(LOG_TAG, "Error closing file opened to obtain size.");
            }
        }
        return size;
    }

    /**
     * @return a cursor to the requested column or null if an exception occurs while trying
     * to query it.
     */
    private static Cursor getOptionalColumn(ContentResolver contentResolver, Uri uri,
            String columnName) {
        Cursor result = null;
        try {
            result = contentResolver.query(uri, new String[]{columnName}, null, null, null);
        } catch (SQLiteException ex) {
            // ignore, leave result null
        }
        return result;
    }

    public void focusLastAttachment() {
        Attachment lastAttachment = mAttachments.get(mAttachments.size() - 1);
        View lastView = null;
        int last = 0;
        if (AttachmentTile.isTiledAttachment(lastAttachment)) {
            last = mTileGrid.getChildCount() - 1;
            if (last > 0) {
                lastView = mTileGrid.getChildAt(last);
            }
        } else {
            last = mAttachmentLayout.getChildCount() - 1;
            if (last > 0) {
                lastView = mAttachmentLayout.getChildAt(last);
            }
        }
        if (lastView != null) {
            lastView.requestFocus();
        }
    }

    /**
     * Class containing information about failures when adding attachments.
     */
    static class AttachmentFailureException extends Exception {
        private static final long serialVersionUID = 1L;
        private final int errorRes;

        public AttachmentFailureException(String detailMessage) {
            super(detailMessage);
            this.errorRes = R.string.generic_attachment_problem;
        }

        public AttachmentFailureException(String error, int errorRes) {
            super(error);
            this.errorRes = errorRes;
        }

        public AttachmentFailureException(String detailMessage, Throwable throwable) {
            super(detailMessage, throwable);
            this.errorRes = R.string.generic_attachment_problem;
        }

        /**
         * Get the error string resource that corresponds to this attachment failure. Always a valid
         * string resource.
         */
        public int getErrorRes() {
            return errorRes;
        }
    }
}
