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
 * limitations under the License
 */

package android.widget.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.app.Activity;
import android.app.Instrumentation;
import android.support.test.InstrumentationRegistry;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.PopupMenu;

import com.android.compatibility.common.util.CtsTouchUtils;
import com.android.compatibility.common.util.WidgetTestUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class PopupMenuTest {
    private Instrumentation mInstrumentation;
    private Activity mActivity;

    private Builder mBuilder;
    private PopupMenu mPopupMenu;

    @Rule
    public ActivityTestRule<PopupMenuCtsActivity> mActivityRule =
            new ActivityTestRule<>(PopupMenuCtsActivity.class);


    @UiThreadTest
    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();

        // Disable and remove focusability on the first child of our activity so that
        // it doesn't bring in the soft keyboard that can mess up with some of the tests
        // (such as menu dismissal when we emulate a tap outside the menu bounds).
        final EditText editText = (EditText) mActivity.findViewById(R.id.anchor_upper_left);
        editText.setEnabled(false);
        editText.setFocusable(false);
    }

    @After
    public void teardown() throws Throwable {
        if (mPopupMenu != null) {
            mActivityRule.runOnUiThread(mPopupMenu::dismiss);
        }
    }

    private void verifyMenuContent() {
        final Menu menu = mPopupMenu.getMenu();
        assertEquals(6, menu.size());
        assertEquals(R.id.action_highlight, menu.getItem(0).getItemId());
        assertEquals(R.id.action_edit, menu.getItem(1).getItemId());
        assertEquals(R.id.action_delete, menu.getItem(2).getItemId());
        assertEquals(R.id.action_ignore, menu.getItem(3).getItemId());
        assertEquals(R.id.action_share, menu.getItem(4).getItemId());
        assertEquals(R.id.action_print, menu.getItem(5).getItemId());

        final SubMenu shareSubMenu = menu.getItem(4).getSubMenu();
        assertNotNull(shareSubMenu);
        assertEquals(2, shareSubMenu.size());
        assertEquals(R.id.action_share_email, shareSubMenu.getItem(0).getItemId());
        assertEquals(R.id.action_share_circles, shareSubMenu.getItem(1).getItemId());
    }

    @Test
    public void testPopulateViaInflater() throws Throwable {
        mBuilder = new Builder().inflateWithInflater(true);
        mActivityRule.runOnUiThread(mBuilder::show);
        mInstrumentation.waitForIdleSync();

        verifyMenuContent();
    }

    @Test
    public void testDirectPopulate() throws Throwable {
        mBuilder = new Builder().inflateWithInflater(false);
        mActivityRule.runOnUiThread(mBuilder::show);
        mInstrumentation.waitForIdleSync();

        verifyMenuContent();
    }

    @Test
    public void testAccessGravity() throws Throwable {
        mBuilder = new Builder();
        mActivityRule.runOnUiThread(mBuilder::show);

        assertEquals(Gravity.NO_GRAVITY, mPopupMenu.getGravity());
        mPopupMenu.setGravity(Gravity.TOP);
        assertEquals(Gravity.TOP, mPopupMenu.getGravity());
    }

    @Test
    public void testConstructorWithGravity() throws Throwable {
        mBuilder = new Builder().withGravity(Gravity.TOP);
        mActivityRule.runOnUiThread(mBuilder::show);

        assertEquals(Gravity.TOP, mPopupMenu.getGravity());
    }

    @Test
    public void testDismissalViaAPI() throws Throwable {
        mBuilder = new Builder().withDismissListener();
        mActivityRule.runOnUiThread(mBuilder::show);

        mInstrumentation.waitForIdleSync();
        verify(mBuilder.mOnDismissListener, never()).onDismiss(mPopupMenu);

        mActivityRule.runOnUiThread(mPopupMenu::dismiss);
        mInstrumentation.waitForIdleSync();
        verify(mBuilder.mOnDismissListener, times(1)).onDismiss(mPopupMenu);

        mActivityRule.runOnUiThread(mPopupMenu::dismiss);
        mInstrumentation.waitForIdleSync();
        // Shouldn't have any more interactions with our dismiss listener since the menu was
        // already dismissed when we called dismiss()
        verifyNoMoreInteractions(mBuilder.mOnDismissListener);
    }

    @Test
    public void testNestedDismissalViaAPI() throws Throwable {
        // Use empty popup style to remove all transitions from the popup. That way we don't
        // need to synchronize with the popup window enter transition before proceeding to
        // "click" a submenu item.
        mBuilder = new Builder().withDismissListener()
                .withPopupStyleResource(R.style.PopupWindow_NullTransitions);
        mActivityRule.runOnUiThread(mBuilder::show);
        mInstrumentation.waitForIdleSync();
        verify(mBuilder.mOnDismissListener, never()).onDismiss(mPopupMenu);

        mActivityRule.runOnUiThread(
                () -> mPopupMenu.getMenu().performIdentifierAction(R.id.action_share, 0));
        mInstrumentation.waitForIdleSync();

        mActivityRule.runOnUiThread(
                () -> mPopupMenu.getMenu().findItem(R.id.action_share).getSubMenu().
                        performIdentifierAction(R.id.action_share_email, 0));
        mInstrumentation.waitForIdleSync();

        mActivityRule.runOnUiThread(mPopupMenu::dismiss);
        mInstrumentation.waitForIdleSync();
        verify(mBuilder.mOnDismissListener, times(1)).onDismiss(mPopupMenu);

        mActivityRule.runOnUiThread(mPopupMenu::dismiss);
        mInstrumentation.waitForIdleSync();
        // Shouldn't have any more interactions with our dismiss listener since the menu was
        // already dismissed when we called dismiss()
        verifyNoMoreInteractions(mBuilder.mOnDismissListener);
    }

    @Test
    public void testDismissalViaTouch() throws Throwable {
        // Use empty popup style to remove all transitions from the popup. That way we don't
        // need to synchronize with the popup window enter transition before proceeding to
        // emulate a click outside the popup window bounds.
        mBuilder = new Builder().withDismissListener()
                .withPopupMenuContent(R.menu.popup_menu_single)
                .withPopupStyleResource(R.style.PopupWindow_NullTransitions);
        mActivityRule.runOnUiThread(mBuilder::show);
        mInstrumentation.waitForIdleSync();

        // The call below uses Instrumentation to emulate a tap outside the bounds of the
        // displayed popup menu. This tap is then treated by the framework to be "split" as
        // the ACTION_OUTSIDE for the popup itself, as well as DOWN / MOVE / UP for the underlying
        // view root if the popup is not modal.
        // It is not correct to emulate these two sequences separately in the test, as it
        // wouldn't emulate the user-facing interaction for this test. Also, we don't want to use
        // View.dispatchTouchEvent directly as that would require emulation of two separate
        // sequences as well.
        CtsTouchUtils.emulateTapOnView(mInstrumentation, mBuilder.mAnchor, 10, -20);

        // At this point our popup should have notified its dismiss listener
        verify(mBuilder.mOnDismissListener, times(1)).onDismiss(mPopupMenu);
    }

    @Test
    public void testSimpleMenuItemClickViaAPI() throws Throwable {
        mBuilder = new Builder().withMenuItemClickListener().withDismissListener();
        mActivityRule.runOnUiThread(mBuilder::show);

        // Verify that our menu item click listener hasn't been called yet
        verify(mBuilder.mOnMenuItemClickListener, never()).onMenuItemClick(any(MenuItem.class));

        mActivityRule.runOnUiThread(
                () -> mPopupMenu.getMenu().performIdentifierAction(R.id.action_highlight, 0));

        // Verify that our menu item click listener has been called with the expected menu item
        verify(mBuilder.mOnMenuItemClickListener, times(1)).onMenuItemClick(
                mPopupMenu.getMenu().findItem(R.id.action_highlight));

        // Popup menu should be automatically dismissed on selecting an item
        verify(mBuilder.mOnDismissListener, times(1)).onDismiss(mPopupMenu);
        verifyNoMoreInteractions(mBuilder.mOnDismissListener);
    }

    @Test
    public void testSubMenuClickViaAPI() throws Throwable {
        // Use empty popup style to remove all transitions from the popup. That way we don't
        // need to synchronize with the popup window enter transition before proceeding to
        // "click" a submenu item.
        mBuilder = new Builder().withDismissListener().withMenuItemClickListener()
                .withPopupStyleResource(R.style.PopupWindow_NullTransitions);
        mActivityRule.runOnUiThread(mBuilder::show);
        mInstrumentation.waitForIdleSync();

        // Verify that our menu item click listener hasn't been called yet
        verify(mBuilder.mOnMenuItemClickListener, never()).onMenuItemClick(any(MenuItem.class));

        mActivityRule.runOnUiThread(
                () -> mPopupMenu.getMenu().performIdentifierAction(R.id.action_share, 0));
        // Verify that our menu item click listener has been called on "share" action
        // and that the dismiss listener hasn't been called just as a result of opening the submenu.
        verify(mBuilder.mOnMenuItemClickListener, times(1)).onMenuItemClick(
                mPopupMenu.getMenu().findItem(R.id.action_share));
        verify(mBuilder.mOnDismissListener, never()).onDismiss(mPopupMenu);

        mActivityRule.runOnUiThread(
                () -> mPopupMenu.getMenu().findItem(R.id.action_share).getSubMenu().
                        performIdentifierAction(R.id.action_share_email, 0));

        // Verify that out menu item click listener has been called with the expected menu item
        verify(mBuilder.mOnMenuItemClickListener, times(1)).onMenuItemClick(
                mPopupMenu.getMenu().findItem(R.id.action_share).getSubMenu()
                        .findItem(R.id.action_share_email));
        verifyNoMoreInteractions(mBuilder.mOnMenuItemClickListener);

        // Popup menu should be automatically dismissed on selecting an item
        verify(mBuilder.mOnDismissListener, times(1)).onDismiss(mPopupMenu);
        verifyNoMoreInteractions(mBuilder.mOnDismissListener);
    }

    @Test
    public void testItemViewAttributes() throws Throwable {
        mBuilder = new Builder().withDismissListener();
        WidgetTestUtils.runOnMainAndLayoutSync(mActivityRule, mBuilder::show, true);

        Menu menu = mPopupMenu.getMenu();
        ListView menuItemList = mPopupMenu.getMenuListView();

        for (int i = 0; i != menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            View itemView = null;
            // On smaller screens, not all menu items will be visible.
            if (i < menuItemList.getChildCount()) {
                itemView = menuItemList.getChildAt(i);
                assertNotNull(itemView);
            }

            if (i < 2) {
                assertNotNull(item.getContentDescription());
                assertNotNull(item.getTooltipText());
            } else {
                assertNull(item.getContentDescription());
                assertNull(item.getTooltipText());
            }
            if (itemView != null) {
                // Tooltips are not set on list-based menus.
                assertNull(itemView.getTooltipText());
                assertEquals(item.getContentDescription(), itemView.getContentDescription());
            }
        }
    }

    /**
     * Inner helper class to configure an instance of {@link PopupMenu} for the specific test.
     * The main reason for its existence is that once a popup menu is shown with the show() method,
     * most of its configuration APIs are no-ops. This means that we can't add logic that is
     * specific to a certain test once it's shown and we have a reference to a displayed
     * {@link PopupMenu}.
     */
    public class Builder {
        private boolean mHasDismissListener;
        private boolean mHasMenuItemClickListener;
        private boolean mInflateWithInflater;

        private int mPopupMenuContent = R.menu.popup_menu;

        private boolean mUseCustomPopupResource;
        private int mPopupStyleResource = 0;

        private boolean mUseCustomGravity;
        private int mGravity = Gravity.NO_GRAVITY;

        private PopupMenu.OnMenuItemClickListener mOnMenuItemClickListener;
        private PopupMenu.OnDismissListener mOnDismissListener;

        private View mAnchor;

        public Builder withMenuItemClickListener() {
            mHasMenuItemClickListener = true;
            return this;
        }

        public Builder withDismissListener() {
            mHasDismissListener = true;
            return this;
        }

        public Builder inflateWithInflater(boolean inflateWithInflater) {
            mInflateWithInflater = inflateWithInflater;
            return this;
        }

        public Builder withPopupStyleResource(int popupStyleResource) {
            mUseCustomPopupResource = true;
            mPopupStyleResource = popupStyleResource;
            return this;
        }

        public Builder withPopupMenuContent(int popupMenuContent) {
            mPopupMenuContent = popupMenuContent;
            return this;
        }

        public Builder withGravity(int gravity) {
            mUseCustomGravity = true;
            mGravity = gravity;
            return this;
        }

        private void configure() {
            mAnchor = mActivity.findViewById(R.id.anchor_middle_left);
            if (!mUseCustomGravity && !mUseCustomPopupResource) {
                mPopupMenu = new PopupMenu(mActivity, mAnchor);
            } else if (!mUseCustomPopupResource) {
                mPopupMenu = new PopupMenu(mActivity, mAnchor, mGravity);
            } else {
                mPopupMenu = new PopupMenu(mActivity, mAnchor, Gravity.NO_GRAVITY,
                        0, mPopupStyleResource);
            }

            if (mInflateWithInflater) {
                final MenuInflater menuInflater = mPopupMenu.getMenuInflater();
                menuInflater.inflate(mPopupMenuContent, mPopupMenu.getMenu());
            } else {
                mPopupMenu.inflate(mPopupMenuContent);
            }

            if (mHasMenuItemClickListener) {
                // Register a mock listener to be notified when a menu item in our popup menu has
                // been clicked.
                mOnMenuItemClickListener = mock(PopupMenu.OnMenuItemClickListener.class);
                mPopupMenu.setOnMenuItemClickListener(mOnMenuItemClickListener);
            }

            if (mHasDismissListener) {
                // Register a mock listener to be notified when our popup menu is dismissed.
                mOnDismissListener = mock(PopupMenu.OnDismissListener.class);
                mPopupMenu.setOnDismissListener(mOnDismissListener);
            }
        }

        public void show() {
            configure();
            // Show the popup menu
            mPopupMenu.show();
        }
    }
}
