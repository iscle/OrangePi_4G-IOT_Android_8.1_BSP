/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.storagemanager.deletionhelper;

import android.view.View;

import com.android.storagemanager.testing.StorageManagerRobolectricTestRunner;
import com.android.storagemanager.testing.TestingConstants;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.annotation.Config;

import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(StorageManagerRobolectricTestRunner.class)
@Config(manifest = TestingConstants.MANIFEST, sdk = TestingConstants.SDK_VERSION)
public class LoadingSpinnerControllerTest {
    @Mock DeletionHelperActivity mActivity;
    @Mock View mListView;
    LoadingSpinnerController mController;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mController = new LoadingSpinnerController(mActivity);
        doAnswer(
                        new Answer() {
                            @Override
                            public Object answer(InvocationOnMock invocationOnMock)
                                    throws Throwable {
                                final boolean isLoading =
                                        (boolean) (invocationOnMock.getArguments())[1];
                                when(mActivity.isLoadingVisible()).thenReturn(isLoading);
                                return null;
                            }
                        })
                .when(mActivity)
                .setLoading(any(View.class), anyBoolean(), anyBoolean());
    }

    @Test
    public void neverLoadIfCategoryLoadsBeforeInitialized() {
        mController.onCategoryLoad();
        mController.initializeLoading(mListView);

        // Loading should never have been shown.
        verify(mActivity, never()).setLoading(any(View.class), eq(true), anyBoolean());
    }

    @Test
    public void loadUntilCategoriesLoaded() {
        mController.initializeLoading(mListView);
        verify(mActivity, never()).setLoading(any(View.class), eq(false), anyBoolean());

        mController.onCategoryLoad();
        verify(mActivity).setLoading(any(View.class), eq(false), anyBoolean());
    }

    @Test
    public void loadingMultipleCategoriesDoesntCauseFlicker() {
        mController.initializeLoading(mListView);

        mController.onCategoryLoad();
        mController.onCategoryLoad();
        mController.onCategoryLoad();

        verify(mActivity, times(1)).setLoading(any(View.class), eq(false), anyBoolean());
    }
}
