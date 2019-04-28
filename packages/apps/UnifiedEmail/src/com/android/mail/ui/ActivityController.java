/*******************************************************************************
 *      Copyright (C) 2012 Google Inc.
 *      Licensed to The Android Open Source Project.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 *******************************************************************************/

package com.android.mail.ui;

import android.app.Dialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.annotation.LayoutRes;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;

import com.android.mail.browse.ConversationCursor.ConversationListener;
import com.android.mail.browse.ConversationListFooterView;
import com.android.mail.providers.Folder;
import com.android.mail.ui.ViewMode.ModeChangeListener;

/**
 * An Activity controller knows how to combine views and listeners into a functioning activity.
 * ActivityControllers are delegates that implement methods by calling underlying views to modify,
 * or respond to user action.
 *
 * There are two ways of adding methods to this interface:
 * <ul>
 *     <li>When the methods pertain to a single logical grouping: consider adding a new
 *     interface and putting all the methods in that interface. As an example,
 *     look at {@link AccountController}. The controller implements this,
 *     and returns itself in
 *     {@link com.android.mail.ui.ControllableActivity#getAccountController()}. This allows
 *     for account-specific methods to be added without creating new methods in this interface
 *     .</li>
 *     <li>Methods that relate to an activity can be added directly. As an example,
 *     look at {@link #onActivityResult(int, int, android.content.Intent)} which is identical to
 *     its declaration in {@link android.app.Activity}.</li>
 *     <li>Everything else. As an example, look at {@link #isDrawerEnabled()}. Try to avoid
 *     this path because an implementation has to provided in many classes:
 *     {@link MailActivity}, {@link FolderSelectionActivity}, and the controllers.</li>
 * </ul>
 */
public interface ActivityController extends LayoutListener,
        ModeChangeListener, ConversationListCallbacks,
        ConversationSetObserver, ConversationListener, FolderSelector,
        UndoListener, ConversationUpdater, ErrorListener, FolderController, AccountController,
        ConversationPositionTracker.Callbacks, ConversationListFooterView.FooterViewClickListener,
        RecentFolderController, FragmentLauncher, KeyboardNavigationController {

    // As far as possible, the methods here that correspond to Activity lifecycle have the same name
    // as their counterpart in the Activity lifecycle.


    /**
     * @see android.app.Activity#onActivityResult
     * @param requestCode
     * @param resultCode
     * @param data
     */
    void onActivityResult(int requestCode, int resultCode, Intent data);

    /**
     * Called by the Mail activity when the back button is pressed. Returning true consumes the
     * event and disallows the calling method from trying to handle the back button any other way.
     *
     * @see android.app.Activity#onBackPressed()
     * @return true if the back press was handled and the event was consumed. Return false if the
     * event was not consumed.
     */
    boolean onBackPressed();

    /**
     * Called when the root activity calls onCreate. Any initialization needs to
     * be done here. Subclasses need to call their parents' onCreate method, since it performs
     * valuable initialization common to all subclasses.
     *
     * This was called initialize in Gmail.
     *
     * @see android.app.Activity#onCreate
     * @param savedState
     */
    void onCreate(Bundle savedState);

    /**
     * @see android.app.Activity#onPostCreate
     */
    void onPostCreate(Bundle savedState);

    /**
     * @see android.app.Activity#onConfigurationChanged
     */
    void onConfigurationChanged(Configuration newConfig);

    /**
     * @see android.app.Activity#onStart
     */
    void onStart();

    /**
     * Called when the the root activity calls onRestart
     * @see android.app.Activity#onRestart
     */
    void onRestart();

    /**
     * @see android.app.Activity#onCreateDialog(int, Bundle)
     * @param id
     * @param bundle
     * @return
     */
    Dialog onCreateDialog(int id, Bundle bundle);

    /**
     * @see android.app.Activity#onCreateOptionsMenu(Menu)
     * @param menu
     * @return
     */
    boolean onCreateOptionsMenu(Menu menu);

    /**
     * @see android.app.Activity#onKeyDown(int, KeyEvent)
     * @param keyCode
     * @param event
     * @return
     */
    boolean onKeyDown(int keyCode, KeyEvent event);

    /**
     * Called by Mail activity when menu items are selected
     * @see android.app.Activity#onOptionsItemSelected(MenuItem)
     * @param item
     * @return
     */
    boolean onOptionsItemSelected(MenuItem item);

    /**
     * Called by the Mail activity on Activity pause.
     * @see android.app.Activity#onPause
     */
    void onPause();

    /**
     * @see android.app.Activity#onDestroy
     */
    void onDestroy();

    /**
     * @see android.app.Activity#onPrepareDialog
     * @param id
     * @param dialog
     * @param bundle
     */
    void onPrepareDialog(int id, Dialog dialog, Bundle bundle);

    /**
     * Called by the Mail activity when menu items need to be prepared.
     * @see android.app.Activity#onPrepareOptionsMenu(Menu)
     * @param menu
     * @return
     */
    void onPrepareOptionsMenu(Menu menu);

    /**
     * Called by the Mail activity on Activity resume.
     * @see android.app.Activity#onResume
     */
    void onResume();

    /**
     * @see android.app.Activity#onRestoreInstanceState
     */
    void onRestoreInstanceState(Bundle savedInstanceState);

    /**
     * @see android.app.Activity#onSaveInstanceState
     * @param outState
     */
    void onSaveInstanceState(Bundle outState);

    /**
     * Begin a search with the given query string.
     */
    void executeSearch(String query);

    /**
     * Called by the Mail activity on Activity stop.
     * @see android.app.Activity#onStop
     */
    void onStop();

    /**
     * Called by the Mail activity when window focus changes.
     * @see android.app.Activity#onWindowFocusChanged(boolean)
     * @param hasFocus
     */
    void onWindowFocusChanged(boolean hasFocus);

    /**
     * Handle a touch event.
     */
    void onTouchEvent(MotionEvent event);

    /**
     * Returns whether the first conversation in the conversation list should be
     * automatically selected and shown.
     */
    boolean shouldShowFirstConversation();

    /**
     * Get the selected set of conversations. Guaranteed to return non-null, this should return
     * an empty set if no conversation is currently selected.
     * @return
     */
    public ConversationCheckedSet getCheckedSet();

    /**
     * Start search mode if the account being view supports the search capability.
     */
    void startSearch();

    /**
     * Return the folder currently being viewed by the activity.
     */
    public Folder getHierarchyFolder();

    /**
     * Handles the animation end of the animated adapter.
     */
    void onAnimationEnd(AnimatedAdapter animatedAdapter);

    /**
     * Called when Accessibility is enabled or disabled.
     */
    void onAccessibilityStateChanged();

    /**
     * Called to determine if the drawer is enabled for this controller/activity instance.
     * Note: the value returned should not change for this controller instance.
     */
    boolean isDrawerEnabled();

    /**
     * Called to determine if menu items in the action bar should be hidden.
     * Currently this is used for when the drawer is open to hide certain
     * items that are not applicable while the drawer is open.
     */
    boolean shouldHideMenuItems();

    DrawerController getDrawerController();

    /**
     * Called to determine the layout resource to use for the activity's content view.
     * @return Resource ID
     */
    @LayoutRes int getContentViewResource();

    View.OnClickListener getNavigationViewClickListener();

    /**
     * If the search bar should always be visible on top of the screen (e.g. search result list).
     * @param viewMode the new view mode. This is passed as a parameter because we don't know
     *   which onViewModeChanged callback gets called first, so the view modes might differ.
     */
    boolean shouldShowSearchBarByDefault(int viewMode);

    /**
     * If we should show the search menu item.
     */
    boolean shouldShowSearchMenuItem();

    /**
     * Attach layout listener so our custom toolbar can listen to thread list layout events.
     */
    void addConversationListLayoutListener(TwoPaneLayout.ConversationListLayoutListener listener);
}
