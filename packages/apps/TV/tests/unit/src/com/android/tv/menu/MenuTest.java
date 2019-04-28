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
package com.android.tv.menu;

import static android.support.test.InstrumentationRegistry.getTargetContext;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.support.test.filters.SmallTest;

import com.android.tv.menu.Menu.OnMenuVisibilityChangeListener;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * Tests for {@link Menu}.
 */
@SmallTest
public class MenuTest {
    private Menu mMenu;
    private IMenuView mMenuView;
    private OnMenuVisibilityChangeListener mVisibilityChangeListener;

    @Before
    public void setUp() {
        mMenuView = Mockito.mock(IMenuView.class);
        MenuRowFactory factory = Mockito.mock(MenuRowFactory.class);
        Mockito.when(factory.createMenuRow(Mockito.any(Menu.class), Mockito.any(Class.class)))
                .thenReturn(null);
        mVisibilityChangeListener = Mockito.mock(OnMenuVisibilityChangeListener.class);
        mMenu = new Menu(getTargetContext(), mMenuView, factory, mVisibilityChangeListener);
        mMenu.disableAnimationForTest();
    }

    @Test
    public void testScheduleHide() {
        mMenu.show(Menu.REASON_NONE);
        setMenuVisible(true);
        assertTrue("Hide is not scheduled", mMenu.isHideScheduled());
        mMenu.hide(false);
        setMenuVisible(false);
        assertFalse("Hide is scheduled", mMenu.isHideScheduled());

        mMenu.setKeepVisible(true);
        mMenu.show(Menu.REASON_NONE);
        setMenuVisible(true);
        assertFalse("Hide is scheduled", mMenu.isHideScheduled());
        mMenu.setKeepVisible(false);
        assertTrue("Hide is not scheduled", mMenu.isHideScheduled());
        mMenu.setKeepVisible(true);
        assertFalse("Hide is scheduled", mMenu.isHideScheduled());
        mMenu.hide(false);
        setMenuVisible(false);
        assertFalse("Hide is scheduled", mMenu.isHideScheduled());
    }

    @Test
    public void testShowHide_ReasonNone() {
        // Show with REASON_NONE
        mMenu.show(Menu.REASON_NONE);
        setMenuVisible(true);
        // Listener should be called with "true" argument.
        Mockito.verify(mVisibilityChangeListener, Mockito.atLeastOnce())
                .onMenuVisibilityChange(Matchers.eq(true));
        Mockito.verify(mVisibilityChangeListener, Mockito.never())
                .onMenuVisibilityChange(Matchers.eq(false));
        // IMenuView.show should be called with the same parameter.
        Mockito.verify(mMenuView).onShow(Matchers.eq(Menu.REASON_NONE),
                Matchers.isNull(String.class), Matchers.isNull(Runnable.class));
        mMenu.hide(true);
        setMenuVisible(false);
        // Listener should be called with "false" argument.
        Mockito.verify(mVisibilityChangeListener, Mockito.atLeastOnce())
                .onMenuVisibilityChange(Matchers.eq(false));
        Mockito.verify(mMenuView).onHide();
    }

    @Test
    public void testShowHide_ReasonGuide() {
        // Show with REASON_GUIDE
        mMenu.show(Menu.REASON_GUIDE);
        setMenuVisible(true);
        // Listener should be called with "true" argument.
        Mockito.verify(mVisibilityChangeListener, Mockito.atLeastOnce())
                .onMenuVisibilityChange(Matchers.eq(true));
        Mockito.verify(mVisibilityChangeListener, Mockito.never())
                .onMenuVisibilityChange(Matchers.eq(false));
        // IMenuView.show should be called with the same parameter.
        Mockito.verify(mMenuView).onShow(Matchers.eq(Menu.REASON_GUIDE),
                Matchers.eq(ChannelsRow.ID), Matchers.isNull(Runnable.class));
        mMenu.hide(false);
        setMenuVisible(false);
        // Listener should be called with "false" argument.
        Mockito.verify(mVisibilityChangeListener, Mockito.atLeastOnce())
                .onMenuVisibilityChange(Matchers.eq(false));
        Mockito.verify(mMenuView).onHide();
    }

    @Test
    public void testShowHide_ReasonPlayControlsFastForward() {
        // Show with REASON_PLAY_CONTROLS_FAST_FORWARD
        mMenu.show(Menu.REASON_PLAY_CONTROLS_FAST_FORWARD);
        setMenuVisible(true);
        // Listener should be called with "true" argument.
        Mockito.verify(mVisibilityChangeListener, Mockito.atLeastOnce())
                .onMenuVisibilityChange(Matchers.eq(true));
        Mockito.verify(mVisibilityChangeListener, Mockito.never())
                .onMenuVisibilityChange(Matchers.eq(false));
        // IMenuView.show should be called with the same parameter.
        Mockito.verify(mMenuView).onShow(Matchers.eq(Menu.REASON_PLAY_CONTROLS_FAST_FORWARD),
                Matchers.eq(PlayControlsRow.ID), Matchers.isNull(Runnable.class));
        mMenu.hide(false);
        setMenuVisible(false);
        // Listener should be called with "false" argument.
        Mockito.verify(mVisibilityChangeListener, Mockito.atLeastOnce())
                .onMenuVisibilityChange(Matchers.eq(false));
        Mockito.verify(mMenuView).onHide();
    }

    private void setMenuVisible(final boolean visible) {
        Mockito.when(mMenuView.isVisible()).thenAnswer(new Answer<Boolean>() {
            @Override
            public Boolean answer(InvocationOnMock invocation) throws Throwable {
                return visible;
            }
        });
    }
}
