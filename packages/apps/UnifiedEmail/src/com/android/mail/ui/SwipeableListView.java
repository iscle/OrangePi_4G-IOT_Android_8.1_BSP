/*
 * Copyright (C) 2012 Google Inc.
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

package com.android.mail.ui;

import android.content.ContentValues;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.ListView;

import com.android.mail.R;
import com.android.mail.analytics.Analytics;
import com.android.mail.browse.ConversationCursor;
import com.android.mail.browse.ConversationItemView;
import com.android.mail.browse.SwipeableConversationItemView;
import com.android.mail.providers.Account;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.providers.FolderList;
import com.android.mail.ui.SwipeHelper.Callback;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

public class SwipeableListView extends ListView implements Callback, OnScrollListener {
    private static final long INVALID_CONVERSATION_ID = -1;

    private final SwipeHelper mSwipeHelper;
    /**
     * Are swipes enabled on all items? (Each individual item can still prevent swiping.)<br>
     * When swiping is disabled, the UI still reacts to the gesture to acknowledge it.
     */
    private boolean mEnableSwipe = false;
    /**
     * When set, we prevent the SwipeHelper from kicking in at all. This
     * short-circuits {@link #mEnableSwipe}.
     */
    private boolean mPreventSwipesEntirely = false;

    public static final String LOG_TAG = LogTag.getLogTag();

    private ConversationCheckedSet mConvCheckedSet;
    private int mSwipeAction;
    private Account mAccount;
    private Folder mFolder;
    private ListItemSwipedListener mSwipedListener;
    private boolean mScrolling;

    private SwipeListener mSwipeListener;

    private long mSelectedConversationId = INVALID_CONVERSATION_ID;

    // Instantiated through view inflation
    @SuppressWarnings("unused")
    public SwipeableListView(Context context) {
        this(context, null);
    }

    public SwipeableListView(Context context, AttributeSet attrs) {
        this(context, attrs, -1);
    }

    public SwipeableListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        float densityScale = getResources().getDisplayMetrics().density;
        float pagingTouchSlop = ViewConfiguration.get(context).getScaledPagingTouchSlop();
        mSwipeHelper = new SwipeHelper(context, SwipeHelper.X, this, densityScale,
                pagingTouchSlop);
        mScrolling = false;
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        float densityScale = getResources().getDisplayMetrics().density;
        mSwipeHelper.setDensityScale(densityScale);
        float pagingTouchSlop = ViewConfiguration.get(getContext()).getScaledPagingTouchSlop();
        mSwipeHelper.setPagingTouchSlop(pagingTouchSlop);
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        LogUtils.d(Utils.VIEW_DEBUGGING_TAG,
                "START CLF-ListView.onFocusChanged layoutRequested=%s root.layoutRequested=%s",
                isLayoutRequested(), getRootView().isLayoutRequested());
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        LogUtils.d(Utils.VIEW_DEBUGGING_TAG, new Error(),
                "FINISH CLF-ListView.onFocusChanged layoutRequested=%s root.layoutRequested=%s",
                isLayoutRequested(), getRootView().isLayoutRequested());
    }

    /**
     * Enable swipe gestures.
     */
    public void enableSwipe(boolean enable) {
        mEnableSwipe = enable;
    }

    /**
     * Completely ignore any horizontal swiping gestures.
     */
    public void preventSwipesEntirely() {
        mPreventSwipesEntirely = true;
    }

    /**
     * Reverses a prior call to {@link #preventSwipesEntirely()}.
     */
    public void stopPreventingSwipes() {
        mPreventSwipesEntirely = false;
    }

    public void setSwipeAction(int action) {
        mSwipeAction = action;
    }

    public void setListItemSwipedListener(ListItemSwipedListener listener) {
        mSwipedListener = listener;
    }

    public int getSwipeAction() {
        return mSwipeAction;
    }

    public void setCheckedSet(ConversationCheckedSet set) {
        mConvCheckedSet = set;
    }

    public void setCurrentAccount(Account account) {
        mAccount = account;
    }

    public void setCurrentFolder(Folder folder) {
        mFolder = folder;
    }

    @Override
    public ConversationCheckedSet getCheckedSet() {
        return mConvCheckedSet;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (mScrolling) {
            return super.onInterceptTouchEvent(ev);
        } else {
            return (!mPreventSwipesEntirely && mSwipeHelper.onInterceptTouchEvent(ev))
                    || super.onInterceptTouchEvent(ev);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return (!mPreventSwipesEntirely && mSwipeHelper.onTouchEvent(ev)) || super.onTouchEvent(ev);
    }

    @Override
    public View getChildAtPosition(MotionEvent ev) {
        // find the view under the pointer, accounting for GONE views
        final int count = getChildCount();
        final int touchY = (int) ev.getY();
        int childIdx = 0;
        View slidingChild;
        for (; childIdx < count; childIdx++) {
            slidingChild = getChildAt(childIdx);
            if (slidingChild.getVisibility() == GONE) {
                continue;
            }
            if (touchY >= slidingChild.getTop() && touchY <= slidingChild.getBottom()) {
                if (slidingChild instanceof SwipeableConversationItemView) {
                    return ((SwipeableConversationItemView) slidingChild).getSwipeableItemView();
                }
                return slidingChild;
            }
        }
        return null;
    }

    @Override
    public boolean canChildBeDismissed(SwipeableItemView v) {
        return mEnableSwipe && v.canChildBeDismissed();
    }

    @Override
    public void onChildDismissed(SwipeableItemView v) {
        if (v != null) {
            v.dismiss();
        }
    }

    // Call this whenever a new action is taken; this forces a commit of any
    // existing destructive actions.
    public void commitDestructiveActions(boolean animate) {
        final AnimatedAdapter adapter = getAnimatedAdapter();
        if (adapter != null) {
            adapter.commitLeaveBehindItems(animate);
        }
    }

    public void dismissChild(final ConversationItemView target) {
        // Notifies the SwipeListener that a swipe has ended.
        if (mSwipeListener != null) {
            mSwipeListener.onEndSwipe();
        }

        final ToastBarOperation undoOp;

        undoOp = new ToastBarOperation(1, mSwipeAction, ToastBarOperation.UNDO, false /* batch */,
                mFolder);
        Conversation conv = target.getConversation();
        target.getConversation().position = findConversation(target, conv);
        final AnimatedAdapter adapter = getAnimatedAdapter();
        if (adapter == null) {
            return;
        }
        adapter.setupLeaveBehind(conv, undoOp, conv.position, target.getHeight());
        ConversationCursor cc = (ConversationCursor) adapter.getCursor();
        Collection<Conversation> convList = Conversation.listOf(conv);
        ArrayList<Uri> folderUris;
        ArrayList<Boolean> adds;

        Analytics.getInstance().sendMenuItemEvent("list_swipe", mSwipeAction, null, 0);

        if (mSwipeAction == R.id.remove_folder) {
            FolderOperation folderOp = new FolderOperation(mFolder, false);
            HashMap<Uri, Folder> targetFolders = Folder
                    .hashMapForFolders(conv.getRawFolders());
            targetFolders.remove(folderOp.mFolder.folderUri.fullUri);
            final FolderList folders = FolderList.copyOf(targetFolders.values());
            conv.setRawFolders(folders);
            final ContentValues values = new ContentValues();
            folderUris = new ArrayList<Uri>();
            folderUris.add(mFolder.folderUri.fullUri);
            adds = new ArrayList<Boolean>();
            adds.add(Boolean.FALSE);
            ConversationCursor.addFolderUpdates(folderUris, adds, values);
            ConversationCursor.addTargetFolders(targetFolders.values(), values);
            cc.mostlyDestructiveUpdate(Conversation.listOf(conv), values);
        } else if (mSwipeAction == R.id.archive) {
            cc.mostlyArchive(convList);
        } else if (mSwipeAction == R.id.delete) {
            cc.mostlyDelete(convList);
        } else if (mSwipeAction == R.id.discard_outbox) {
            cc.moveFailedIntoDrafts(convList);
        }
        if (mSwipedListener != null) {
            mSwipedListener.onListItemSwiped(convList);
        }
        adapter.notifyDataSetChanged();
        if (mConvCheckedSet != null && !mConvCheckedSet.isEmpty()
                && mConvCheckedSet.contains(conv)) {
            mConvCheckedSet.toggle(conv);
            // Don't commit destructive actions if the item we just removed from
            // the selection set is the item we just destroyed!
            if (!conv.isMostlyDead() && mConvCheckedSet.isEmpty()) {
                commitDestructiveActions(true);
            }
        }
    }

    @Override
    public void onBeginDrag(View v) {
        // We do this so the underlying ScrollView knows that it won't get
        // the chance to intercept events anymore
        requestDisallowInterceptTouchEvent(true);
        cancelDismissCounter();

        // Notifies the SwipeListener that a swipe has begun.
        if (mSwipeListener != null) {
            mSwipeListener.onBeginSwipe();
        }
    }

    @Override
    public void onDragCancelled(SwipeableItemView v) {
        final AnimatedAdapter adapter = getAnimatedAdapter();
        if (adapter != null) {
            adapter.startDismissCounter();
            adapter.cancelFadeOutLastLeaveBehindItemText();
        }

        // Notifies the SwipeListener that a swipe has ended.
        if (mSwipeListener != null) {
            mSwipeListener.onEndSwipe();
        }
    }

    /**
     * Archive items using the swipe away animation before shrinking them away.
     */
    public boolean destroyItems(Collection<Conversation> convs,
            final ListItemsRemovedListener listener) {
        if (convs == null) {
            LogUtils.e(LOG_TAG, "SwipeableListView.destroyItems: null conversations.");
            return false;
        }
        final AnimatedAdapter adapter = getAnimatedAdapter();
        if (adapter == null) {
            LogUtils.e(LOG_TAG, "SwipeableListView.destroyItems: Cannot destroy: adapter is null.");
            return false;
        }
        adapter.swipeDelete(convs, listener);
        return true;
    }

    public int findConversation(ConversationItemView view, Conversation conv) {
        int position = INVALID_POSITION;
        long convId = conv.id;
        try {
            position = getPositionForView(view);
        } catch (Exception e) {
            position = INVALID_POSITION;
            LogUtils.w(LOG_TAG, e, "Exception finding position; using alternate strategy");
        }
        if (position == INVALID_POSITION) {
            // Try the other way!
            Conversation foundConv;
            long foundId;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child instanceof SwipeableConversationItemView) {
                    foundConv = ((SwipeableConversationItemView) child).getSwipeableItemView()
                            .getConversation();
                    foundId = foundConv.id;
                    if (foundId == convId) {
                        position = i + getFirstVisiblePosition();
                        break;
                    }
                }
            }
        }
        return position;
    }

    private AnimatedAdapter getAnimatedAdapter() {
        return (AnimatedAdapter) getAdapter();
    }

    @Override
    public boolean performItemClick(View view, int pos, long id) {
        // Superclass method modifies the selection set
        final boolean handled = super.performItemClick(view, pos, id);

        // Commit any existing destructive actions when the user selects a
        // conversation to view.
        commitDestructiveActions(true);
        return handled;
    }

    @Override
    public void onScroll() {
        commitDestructiveActions(true);
    }

    public interface ListItemsRemovedListener {
        public void onListItemsRemoved();
    }

    public interface ListItemSwipedListener {
        public void onListItemSwiped(Collection<Conversation> conversations);
    }

    @Override
    public final void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
            int totalItemCount) {
    }

    @Override
    public void onScrollStateChanged(final AbsListView view, final int scrollState) {
        mScrolling = scrollState != OnScrollListener.SCROLL_STATE_IDLE;

        if (!mScrolling) {
            final Context c = getContext();
            if (c instanceof ControllableActivity) {
                final ControllableActivity activity = (ControllableActivity) c;
                activity.onAnimationEnd(null /* adapter */);
            } else {
                LogUtils.wtf(LOG_TAG, "unexpected context=%s", c);
            }
        }
    }

    public boolean isScrolling() {
        return mScrolling;
    }

    /**
     * Set the currently selected (focused by the list view) position.
     */
    public void setSelectedConversation(Conversation conv) {
        if (conv == null) {
            return;
        }

        mSelectedConversationId = conv.id;
    }

    public boolean isConversationSelected(Conversation conv) {
        return mSelectedConversationId != INVALID_CONVERSATION_ID && conv != null
                && mSelectedConversationId == conv.id;
    }

    /**
     * This is only used for debugging/logging purposes. DO NOT call this function to try to get
     * the currently selected position. Use {@link #mSelectedConversationId} instead.
     */
    public int getSelectedConversationPosDebug() {
        for (int i = getFirstVisiblePosition(); i < getLastVisiblePosition(); i++) {
            final Object item = getItemAtPosition(i);
            if (item instanceof ConversationCursor) {
                final Conversation c = ((ConversationCursor) item).getConversation();
                if (c.id == mSelectedConversationId) {
                    return i;
                }
            }
        }
        return ListView.INVALID_POSITION;
    }

    @Override
    public void onTouchModeChanged(boolean isInTouchMode) {
        super.onTouchModeChanged(isInTouchMode);
        if (!isInTouchMode) {
            // We need to invalidate going from touch mode -> keyboard mode because the currently
            // selected item might have changed in touch mode. However, since from the framework's
            // perspective the selected position doesn't matter in touch mode, when we enter
            // keyboard mode via up/down arrow, the list view will ONLY invalidate the newly
            // selected item and not the currently selected item. As a result, we might get an
            // inconsistent UI where it looks like both the old and new selected items are focused.
            final int index = getSelectedItemPosition();
            if (index != ListView.INVALID_POSITION) {
                final View child = getChildAt(index - getFirstVisiblePosition());
                if (child != null) {
                    child.invalidate();
                }
            }
        }
    }

    @Override
    public void cancelDismissCounter() {
        AnimatedAdapter adapter = getAnimatedAdapter();
        if (adapter != null) {
            adapter.cancelDismissCounter();
        }
    }

    @Override
    public LeaveBehindItem getLastSwipedItem() {
        AnimatedAdapter adapter = getAnimatedAdapter();
        if (adapter != null) {
            return adapter.getLastLeaveBehindItem();
        }
        return null;
    }

    public void setSwipeListener(SwipeListener swipeListener) {
        mSwipeListener = swipeListener;
    }

    public interface SwipeListener {
        public void onBeginSwipe();
        public void onEndSwipe();
    }
}
