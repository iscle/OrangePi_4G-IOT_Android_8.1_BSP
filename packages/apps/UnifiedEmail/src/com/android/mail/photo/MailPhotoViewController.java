/*
 * Copyright (C) 2014 Google Inc.
 * Licensed to The Android Open Source Project.
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

package com.android.mail.photo;

import android.app.FragmentManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.print.PrintHelper;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.ex.photo.ActionBarInterface;
import com.android.ex.photo.PhotoViewController;
import com.android.ex.photo.fragments.PhotoViewFragment;
import com.android.ex.photo.views.ProgressBarWrapper;
import com.android.mail.R;
import com.android.mail.analytics.Analytics;
import com.android.mail.browse.AttachmentActionHandler;
import com.android.mail.print.PrintUtils;
import com.android.mail.providers.Attachment;
import com.android.mail.providers.Message;
import com.android.mail.providers.UIProvider;
import com.android.mail.utils.AttachmentUtils;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;
import com.google.common.collect.Lists;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

/**
 * Derives from {@link PhotoViewController} to customize behavior
 * for UnifiedEmail's implementation of the photoviewer.
 * All of the work is actually performed here.
 */
public class MailPhotoViewController extends PhotoViewController {

    public interface ActivityInterface extends PhotoViewController.ActivityInterface {
        FragmentManager getFragmentManager();
        MenuInflater getMenuInflater();
    }

    private final ActivityInterface mMailActivity;

    private static final String LOG_TAG = LogTag.getLogTag();

    private String mAccountType;
    private MenuItem mSaveItem;
    private MenuItem mSaveAllItem;
    private MenuItem mShareItem;
    private MenuItem mShareAllItem;
    private MenuItem mPrintItem;
    /**
     * Only for attachments that are currently downloading. Attachments that failed show the
     * retry button.
     */
    private MenuItem mDownloadAgainItem;
    private MenuItem mExtraOption1Item;
    protected AttachmentActionHandler mActionHandler;
    private Menu mMenu;

    private boolean mHideExtraOptionOne;

    public MailPhotoViewController(ActivityInterface activity) {
        super(activity);
        mMailActivity = activity;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mActionHandler = new AttachmentActionHandler(mMailActivity.getContext(), null);
        mActionHandler.initialize(mMailActivity.getFragmentManager());

        final Intent intent = mMailActivity.getIntent();
        mAccountType = intent.getStringExtra(MailPhotoViewActivity.EXTRA_ACCOUNT_TYPE);
        final String account = intent.getStringExtra(MailPhotoViewActivity.EXTRA_ACCOUNT);
        final Message msg = intent.getParcelableExtra(MailPhotoViewActivity.EXTRA_MESSAGE);
        mHideExtraOptionOne = intent.getBooleanExtra(
                MailPhotoViewActivity.EXTRA_HIDE_EXTRA_OPTION_ONE, false);
        mActionHandler.setAccount(account);
        mActionHandler.setMessage(msg);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = mMailActivity.getMenuInflater();

        inflater.inflate(R.menu.photo_view_menu, menu);
        mMenu = menu;

        mSaveItem = mMenu.findItem(R.id.menu_save);
        mSaveAllItem = mMenu.findItem(R.id.menu_save_all);
        mShareItem = mMenu.findItem(R.id.menu_share);
        mShareAllItem = mMenu.findItem(R.id.menu_share_all);
        mPrintItem = mMenu.findItem(R.id.menu_print);
        mDownloadAgainItem = mMenu.findItem(R.id.menu_download_again);
        mExtraOption1Item = mMenu.findItem(R.id.attachment_extra_option1);

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        updateActionItems();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();

        Analytics.getInstance().sendMenuItemEvent(Analytics.EVENT_CATEGORY_MENU_ITEM, itemId,
                "photo_viewer", 0);

        if (itemId == android.R.id.home) {
            // app icon in action bar clicked; go back to conversation
            mMailActivity.finish();
        } else if (itemId == R.id.menu_save) { // save the current photo
            saveAttachment();
        } else if (itemId == R.id.menu_save_all) { // save all of the photos
            saveAllAttachments();
        } else if (itemId == R.id.menu_share) { // share the current photo
            shareAttachment();
        } else if (itemId == R.id.menu_share_all) { // share all of the photos
            shareAllAttachments();
        } else if (itemId == R.id.menu_print) { // print the current photo
            printAttachment();
        } else if (itemId == R.id.menu_download_again) { // redownload the current photo
            redownloadAttachment();
        } else if (itemId == R.id.attachment_extra_option1) {
            mActionHandler.setAttachment(getCurrentAttachment());
            mActionHandler.handleOption1();
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    /**
     * Updates the action items to tweak their visibility in case
     * there is functionality that is not relevant (eg, the Save
     * button should not appear if the photo has already been saved).
     */
    @Override
    public void updateActionItems() {
        final Attachment attachment = getCurrentAttachment();

        if (attachment != null && mSaveItem != null && mShareItem != null) {
            mSaveItem.setEnabled(!attachment.isDownloading()
                    && attachment.canSave() && !attachment.isSavedToExternal());
            final boolean canShare = attachment.canShare();
            mShareItem.setEnabled(canShare);
            mPrintItem.setEnabled(canShare);
            mDownloadAgainItem.setEnabled(attachment.canSave() && attachment.isDownloading());
            mExtraOption1Item.setVisible(!mHideExtraOptionOne &&
                    mActionHandler.shouldShowExtraOption1(mAccountType,
                            attachment.getContentType()));
        } else {
            if (mMenu != null) {
                mMenu.setGroupEnabled(R.id.photo_view_menu_group, false);
            }
            return;
        }

        List<Attachment> attachments = getAllAttachments();
        if (attachments != null) {
            boolean enabled = false;
            for (final Attachment a : attachments) {
                // If one attachment can be saved, enable save all
                if (!a.isDownloading() && a.canSave() && !a.isSavedToExternal()) {
                    enabled = true;
                    break;
                }
            }
            mSaveAllItem.setEnabled(enabled);

            // all attachments must be present to be able to share all
            enabled = true;
            for (final Attachment a : attachments) {
                if (!a.canShare()) {
                    enabled = false;
                    break;
                }
            }
            mShareAllItem.setEnabled(enabled);
        }

        // Turn off functionality that only works on JellyBean.
        if (!Utils.isRunningJellybeanOrLater()) {
            mShareItem.setVisible(false);
            mShareAllItem.setVisible(false);
        }

        // Turn off functionality that only works on KitKat.
        if (!Utils.isRunningKitkatOrLater()) {
            mPrintItem.setVisible(false);
        }
    }


    /**
     * Adjusts the activity title and subtitle to reflect the image name and size.
     */
    @Override
    public void updateActionBar() {
        super.updateActionBar();

        final Attachment attachment = getCurrentAttachment();
        final ActionBarInterface actionBar = mMailActivity.getActionBarInterface();
        final String size = AttachmentUtils.convertToHumanReadableSize(
                mMailActivity.getContext(), attachment.size);

        // update the status
        // There are 3 states
        //      1. Saved, Attachment Size
        //      2. Saving...
        //      3. Default, Attachment Size
        if (attachment.isSavedToExternal()) {
            actionBar.setSubtitle(mMailActivity.getResources().getString(R.string.saved, size));
        } else if (attachment.isDownloading() &&
                attachment.destination == UIProvider.AttachmentDestination.EXTERNAL) {
            actionBar.setSubtitle(mMailActivity.getResources().getString(R.string.saving));
        } else {
            actionBar.setSubtitle(size);
        }
        updateActionItems();
    }

    @Override
    public void onFragmentVisible(PhotoViewFragment fragment) {
        super.onFragmentVisible(fragment);
        final Attachment attachment = getCurrentAttachment();
        if (attachment.state == UIProvider.AttachmentState.PAUSED) {
            mActionHandler.setAttachment(attachment);
            mActionHandler.startDownloadingAttachment(attachment.destination);
        }
    }

    @Override
    public void onCursorChanged(PhotoViewFragment fragment, Cursor cursor) {
        super.onCursorChanged(fragment, cursor);
        updateProgressAndEmptyViews(fragment, new Attachment(cursor));
    }

    /**
     * Updates the empty views of the fragment based upon the current
     * state of the attachment.
     * @param fragment the current fragment
     */
    private void updateProgressAndEmptyViews(
            final PhotoViewFragment fragment, final Attachment attachment) {
        final ProgressBarWrapper progressBar = fragment.getPhotoProgressBar();
        final TextView emptyText = fragment.getEmptyText();
        final ImageView retryButton = fragment.getRetryButton();

        // update the progress
        if (attachment.shouldShowProgress()) {
            progressBar.setMax(attachment.size);
            progressBar.setProgress(attachment.downloadedSize);
            progressBar.setIndeterminate(false);
        } else if (fragment.isProgressBarNeeded()) {
            progressBar.setIndeterminate(true);
        }

        // If the download failed, show the empty text and retry button
        if (attachment.isDownloadFailed()) {
            emptyText.setText(R.string.photo_load_failed);
            emptyText.setVisibility(View.VISIBLE);
            retryButton.setVisibility(View.VISIBLE);
            retryButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    redownloadAttachment();
                    emptyText.setVisibility(View.GONE);
                    retryButton.setVisibility(View.GONE);
                }
            });
            progressBar.setVisibility(View.GONE);
        }
    }

    /**
     * Save the current attachment.
     */
    private void saveAttachment() {
        saveAttachment(getCurrentAttachment());
    }

    /**
     * Redownloads the attachment.
     */
    private void redownloadAttachment() {
        final Attachment attachment = getCurrentAttachment();
        if (attachment != null && attachment.canSave()) {
            // REDOWNLOADING command is only for attachments that are finished or failed.
            // For an attachment that is downloading (or paused in the DownloadManager), we need to
            // cancel it first.
            mActionHandler.setAttachment(attachment);
            mActionHandler.cancelAttachment();
            mActionHandler.startDownloadingAttachment(attachment.destination);
        }
    }

    /**
     * Saves the attachment.
     * @param attachment the attachment to save.
     */
    private void saveAttachment(final Attachment attachment) {
        if (attachment != null && attachment.canSave()) {
            mActionHandler.setAttachment(attachment);
            mActionHandler.startDownloadingAttachment(UIProvider.AttachmentDestination.EXTERNAL);
        }
    }

    /**
     * Save all of the attachments in the cursor.
     */
    private void saveAllAttachments() {
        Cursor cursor = getCursorAtProperPosition();

        if (cursor == null) {
            return;
        }

        int i = -1;
        while (cursor.moveToPosition(++i)) {
            saveAttachment(new Attachment(cursor));
        }
    }

    /**
     * Share the current attachment.
     */
    private void shareAttachment() {
        shareAttachment(getCurrentAttachment());
    }

    /**
     * Shares the attachment
     * @param attachment the attachment to share
     */
    private void shareAttachment(final Attachment attachment) {
        if (attachment != null) {
            mActionHandler.setAttachment(attachment);
            mActionHandler.shareAttachment();
        }
    }

    /**
     * Share all of the attachments in the cursor.
     */
    private void shareAllAttachments() {
        Cursor cursor = getCursorAtProperPosition();

        if (cursor == null) {
            return;
        }

        ArrayList<Parcelable> uris = new ArrayList<Parcelable>();
        int i = -1;
        while (cursor.moveToPosition(++i)) {
            uris.add(Utils.normalizeUri(new Attachment(cursor).contentUri));
        }

        mActionHandler.shareAttachments(uris);
    }

    private void printAttachment() {
        final Attachment attachment = getCurrentAttachment();
        final Context context = mMailActivity.getContext();
        final PrintHelper printHelper = new PrintHelper(context);
        try {
            printHelper.setScaleMode(PrintHelper.SCALE_MODE_FIT);
            printHelper.printBitmap(PrintUtils.buildPrintJobName(context, attachment.getName()),
                    attachment.contentUri);
        } catch (FileNotFoundException e) {
            // couldn't print a photo at the particular Uri. Should we notify the user?
            LogUtils.e(LOG_TAG, e, "Can't print photo");
        }
    }

    /**
     * Helper method to get the currently visible attachment.
     */
    protected Attachment getCurrentAttachment() {
        final Cursor cursor = getCursorAtProperPosition();

        if (cursor == null) {
            return null;
        }

        return new Attachment(cursor);
    }

    private List<Attachment> getAllAttachments() {
        final Cursor cursor = getCursor();

        if (cursor == null || cursor.isClosed() || !cursor.moveToFirst()) {
            return null;
        }

        List<Attachment> list = Lists.newArrayList();
        do {
            list.add(new Attachment(cursor));
        } while (cursor.moveToNext());

        return list;
    }

    public ActivityInterface getMailActivity() {
        return mMailActivity;
    }
}
