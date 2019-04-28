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

package com.android.mail.drawer;

import android.support.annotation.IntDef;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.bitmap.BitmapCache;
import com.android.mail.bitmap.ContactResolver;
import com.android.mail.providers.Account;
import com.android.mail.providers.Folder;
import com.android.mail.ui.ControllableActivity;
import com.android.mail.ui.FolderListFragment;
import com.android.mail.utils.FolderUri;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;


/**
 * An element that is shown in the {@link com.android.mail.ui.FolderListFragment}. This class is
 * only used for elements that are shown in the {@link com.android.mail.ui.DrawerFragment}.
 * This class is an enumeration of a few element types: Account, a folder, a recent folder,
 * or a header (a resource string). A {@link DrawerItem} can only be one type and can never
 * switch types. Items are created using methods like
 * {@link DrawerItem#ofAccount(ControllableActivity, Account, int, boolean, BitmapCache,
 * ContactResolver)},
 * {@link DrawerItem#ofWaitView(ControllableActivity)}, etc.
 *
 * Once created, the item can create a view using
 * {@link #getView(android.view.View, android.view.ViewGroup)}.
 */
public abstract class DrawerItem {
    public final Folder mFolder;
    public final Account mAccount;

    /** These are view types for view recycling purposes */
    @Retention(RetentionPolicy.CLASS)
    @IntDef({VIEW_FOLDER, VIEW_HEADER, VIEW_BLANK_HEADER, VIEW_BOTTOM_SPACE, VIEW_ACCOUNT,
            VIEW_WAITING_FOR_SYNC, VIEW_FOOTER_HELP, VIEW_FOOTER_SETTINGS})
    public @interface DrawerItemType {}
    /** A normal folder, also a child, if a parent is specified. */
    public static final int VIEW_FOLDER = 0;
    /** A text-label which serves as a header in sectioned lists. */
    public static final int VIEW_HEADER = 1;
    /** A blank divider which serves as a header in sectioned lists. */
    public static final int VIEW_BLANK_HEADER = 2;
    /** A spacer which serves as a footer below the last item. */
    public static final int VIEW_BOTTOM_SPACE = 3;
    /** An account object, which allows switching accounts rather than folders. */
    public static final int VIEW_ACCOUNT = 4;
    /** An expandable object for expanding/collapsing more of the list */
    public static final int VIEW_WAITING_FOR_SYNC = 5;
    /** A footer item for Help */
    public static final int VIEW_FOOTER_HELP = 6;
    /** A footer item for Settings */
    public static final int VIEW_FOOTER_SETTINGS = 7;
    /** The value (1-indexed) of the last View type.  Useful when returning the number of types. */
    private static final int LAST_FIELD = VIEW_FOOTER_SETTINGS + 1;

    /** The parent activity */
    protected final ControllableActivity mActivity;
    protected final LayoutInflater mInflater;

    /**
     * These values determine the behavior of the drawer items.
     *
     * Either {@link #FOLDER_INBOX}, {@link #FOLDER_RECENT} or {@link #FOLDER_OTHER} when
     * {@link #getType()} is {@link #VIEW_FOLDER}, or {@link #NONFOLDER_ITEM} otherwise.
     */
    @Retention(RetentionPolicy.CLASS)
    @IntDef({UNSET, NONFOLDER_ITEM, FOLDER_INBOX, FOLDER_RECENT, FOLDER_OTHER})
    public @interface DrawerItemCategory {}
    public final @DrawerItemCategory int mItemCategory;
    /** Non existent item or folder type not yet set */
    public static final int UNSET = 0;
    /** An unclickable text-header visually separating the different types. */
    public static final int NONFOLDER_ITEM = 0;
    /** An inbox folder: Inbox, ...*/
    public static final int FOLDER_INBOX = 1;
    /** A folder from whom a conversation was recently viewed */
    public static final int FOLDER_RECENT = 2;
    /** A non-inbox folder that is shown in the "everything else" group. */
    public static final int FOLDER_OTHER = 3;

    /**
     * Creates a drawer item with every instance variable specified.
     *
     * @param activity the underlying activity
     * @param folder a non-null folder, if this is a folder type
     * @param itemCategory the type of the folder. For folders this is:
     *            {@link #FOLDER_INBOX}, {@link #FOLDER_RECENT}, {@link #FOLDER_OTHER},
     *            or for non-folders this is {@link #NONFOLDER_ITEM}
     * @param account the account object, for an account drawer element
     */
    protected DrawerItem(ControllableActivity activity, Folder folder,
            @DrawerItemCategory int itemCategory, Account account) {
        mActivity = activity;
        mFolder = folder;
        mItemCategory = itemCategory;
        mAccount = account;
        mInflater = LayoutInflater.from(activity.getActivityContext());
    }

    /**
     * Create a folder item with the given type.
     *
     * @param activity the underlying activity
     * @param folder a folder that this item represents
     * @param itemCategory one of {@link #FOLDER_INBOX}, {@link #FOLDER_RECENT} or
     * {@link #FOLDER_OTHER}
     * @return a drawer item for the folder.
     */
    public static DrawerItem ofFolder(ControllableActivity activity, Folder folder,
            @DrawerItemCategory int itemCategory) {
        return new FolderDrawerItem(activity, folder, itemCategory);
    }

    /**
     * Creates an item from an account.
     * @param activity the underlying activity
     * @param account the account to create a drawer item for
     * @param unreadCount the unread count of the account, pass zero if
     * @param isCurrentAccount true if the account is the current account, false otherwise
     * @return a drawer item for the account.
     */
    public static DrawerItem ofAccount(ControllableActivity activity, Account account,
            int unreadCount, boolean isCurrentAccount, BitmapCache cache,
            ContactResolver contactResolver) {
        return new AccountDrawerItem(activity, account, unreadCount, isCurrentAccount, cache,
                contactResolver);
    }

    /**
     * Create a header item with a string resource.
     *
     * @param activity the underlying activity
     * @param resource the string resource: R.string.all_folders_heading
     * @return a drawer item for the header.
     */
    public static DrawerItem ofHeader(ControllableActivity activity, int resource) {
        return new HeaderDrawerItem(activity, resource);
    }

    public static DrawerItem ofBlankHeader(ControllableActivity activity) {
        return new BlankHeaderDrawerItem(activity);
    }

    public static DrawerItem ofBottomSpace(ControllableActivity activity) {
        return new BottomSpaceDrawerItem(activity);
    }

    /**
     * Create a "waiting for initialization" item.
     *
     * @param activity the underlying activity
     * @return a drawer item with an indeterminate progress indicator.
     */
    public static DrawerItem ofWaitView(ControllableActivity activity) {
        return new WaitViewDrawerItem(activity);
    }

    public static DrawerItem ofHelpItem(ControllableActivity activity, Account account,
            FolderListFragment.DrawerStateListener drawerListener) {
        return new HelpItem(activity, account, drawerListener);
    }

    public static DrawerItem ofSettingsItem(ControllableActivity activity, Account account,
            FolderListFragment.DrawerStateListener drawerListener) {
        return new SettingsItem(activity, account, drawerListener);
    }

    /**
     * Returns a view for the given item. The method signature is identical to that required by a
     * {@link android.widget.ListAdapter#getView(int, android.view.View, android.view.ViewGroup)}.
     */
    public abstract View getView(View convertView, ViewGroup parent);

    /**
     * Book-keeping for how many different view types there are.
     * @return number of different types of view items
     */
    public static int getViewTypeCount() {
        return LAST_FIELD;
    }

    /**
     * Returns whether this view is enabled or not. An enabled view is one that accepts user taps
     * and acts upon them.
     * @return true if this view is enabled, false otherwise.
     */
    public abstract boolean isItemEnabled();

    /**
     * Returns whether this view is highlighted or not.
     *
     *
     * @param currentFolder The current folder, according to the
     *                      {@link com.android.mail.ui.FolderListFragment}
     * @param currentType The type of the current folder. We want to only highlight a folder once.
     *                    A folder might be in two places at once: in "All Folders", and in
     *                    "Recent Folder". Valid types of selected folders are :
     *                    {@link DrawerItem#FOLDER_INBOX}, {@link DrawerItem#FOLDER_RECENT} or
     *                    {@link DrawerItem#FOLDER_OTHER}, or {@link DrawerItem#UNSET}.

     * @return true if this DrawerItem results in a view that is highlighted (this DrawerItem is
     *              the current folder.
     */
    public abstract boolean isHighlighted(FolderUri currentFolder, int currentType);

    public abstract @DrawerItemType int getType();

    public void onClick(View v) {}
}

