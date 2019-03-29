/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.appwidget.cts;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.hamcrest.MockitoHamcrest.argThat;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.appwidget.cts.provider.AppWidgetProviderCallbacks;
import android.appwidget.cts.provider.FirstAppWidgetProvider;
import android.appwidget.cts.provider.SecondAppWidgetProvider;
import android.appwidget.cts.service.MyAppWidgetService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService.RemoteViewsFactory;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.mockito.InOrder;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class AppWidgetTest extends AppWidgetTestCase {

    private static final long OPERATION_TIMEOUT = 20 * 1000; // 20 sec

    private final Object mLock = new Object();

    @Override
    public void setUp() throws Exception {
        // Workaround for dexmaker bug: https://code.google.com/p/dexmaker/issues/detail?id=2
        // Dexmaker is used by mockito.
        System.setProperty("dexmaker.dexcache", getInstrumentation()
                .getTargetContext().getCacheDir().getPath());
    }

    private static final String GRANT_BIND_APP_WIDGET_PERMISSION_COMMAND =
            "appwidget grantbind --package android.appwidget.cts --user 0";

    private static final String REVOKE_BIND_APP_WIDGET_PERMISSION_COMMAND =
            "appwidget revokebind --package android.appwidget.cts --user 0";


    public void testGetAppInstalledProvidersForCurrentUserLegacy() throws Exception {
        if (!hasAppWidgets()) {
            return;
        }

        // By default we should get only providers for the current user.
        List<AppWidgetProviderInfo> providers = getAppWidgetManager().getInstalledProviders();

        // Make sure we have our two providers in the list.
        assertExpectedInstalledProviders(providers);
    }

    public void testGetAppInstalledProvidersForCurrentUserNewCurrentProfile() throws Exception {
        if (!hasAppWidgets()) {
            return;
        }

        // We ask only for providers for the current user.
        List<AppWidgetProviderInfo> providers = getAppWidgetManager()
                .getInstalledProvidersForProfile(Process.myUserHandle());

        // Make sure we have our two providers in the list.
        assertExpectedInstalledProviders(providers);
    }

    public void testGetAppInstalledProvidersForCurrentUserNewAllProfiles() throws Exception {
        if (!hasAppWidgets()) {
            return;
        }

        // We ask only for providers for all current user's profiles
        UserManager userManager = (UserManager) getInstrumentation()
                .getTargetContext().getSystemService(Context.USER_SERVICE);

        List<AppWidgetProviderInfo> allProviders = new ArrayList<>();

        List<UserHandle> profiles = userManager.getUserProfiles();
        final int profileCount = profiles.size();
        for (int i = 0; i < profileCount; i++) {
            UserHandle profile = profiles.get(i);
            List<AppWidgetProviderInfo> providers = getAppWidgetManager()
                    .getInstalledProvidersForProfile(profile);
            allProviders.addAll(providers);
        }

        // Make sure we have our two providers in the list.
        assertExpectedInstalledProviders(allProviders);
    }

    public void testBindAppWidget() throws Exception {
        if (!hasAppWidgets()) {
            return;
        }

        // Create a host and start listening.
        AppWidgetHost host = new AppWidgetHost(getInstrumentation().getTargetContext(), 0);
        host.deleteHost();
        host.startListening();

        // Allocate an app widget id to bind.
        final int appWidgetId = host.allocateAppWidgetId();

        // Grab a provider we defined to be bound.
        AppWidgetProviderInfo provider = getFirstAppWidgetProviderInfo();

        // Bind the widget.
        boolean widgetBound = getAppWidgetManager().bindAppWidgetIdIfAllowed(appWidgetId,
                provider.getProfile(), provider.provider, null);
        assertFalse(widgetBound);

        // Well, app do not have this permission unless explicitly granted
        // by the user. Now we will pretend for the user and grant it.
        grantBindAppWidgetPermission();

        try {
            // Bind the widget as we have a permission for that.
            widgetBound = getAppWidgetManager().bindAppWidgetIdIfAllowed(
                    appWidgetId, provider.getProfile(), provider.provider, null);
            assertTrue(widgetBound);

            // Deallocate the app widget id.
            host.deleteAppWidgetId(appWidgetId);
        } finally {
            // Clean up.
            host.deleteAppWidgetId(appWidgetId);
            host.deleteHost();
            revokeBindAppWidgetPermission();
        }
    }

    public void testGetAppWidgetIdsForHost() throws Exception {
        if (!hasAppWidgets()) {
            return;
        }
        AppWidgetHost host1 = new AppWidgetHost(getInstrumentation().getTargetContext(), 1);
        AppWidgetHost host2 = new AppWidgetHost(getInstrumentation().getTargetContext(), 2);

        host1.deleteHost();
        host2.deleteHost();

        assertTrue(Arrays.equals(host1.getAppWidgetIds(), new int[]{}));
        assertTrue(Arrays.equals(host2.getAppWidgetIds(), new int[]{}));

        int id1 = host1.allocateAppWidgetId();
        assertTrue(Arrays.equals(host1.getAppWidgetIds(), new int[]{id1}));
        assertTrue(Arrays.equals(host2.getAppWidgetIds(), new int[]{}));

        int id2 = host1.allocateAppWidgetId();
        assertTrue(Arrays.equals(host1.getAppWidgetIds(), new int[]{id1, id2}));
        assertTrue(Arrays.equals(host2.getAppWidgetIds(), new int[]{}));

        int id3 = host2.allocateAppWidgetId();
        assertTrue(Arrays.equals(host1.getAppWidgetIds(), new int[]{id1, id2}));
        assertTrue(Arrays.equals(host2.getAppWidgetIds(), new int[]{id3}));

        host1.deleteHost();
        assertTrue(Arrays.equals(host1.getAppWidgetIds(), new int[]{}));
        assertTrue(Arrays.equals(host2.getAppWidgetIds(), new int[]{id3}));

        host2.deleteHost();
    }

    public void testAppWidgetProviderCallbacks() throws Exception {
        if (!hasAppWidgets()) {
            return;
        }

        AtomicInteger invocationCounter = new AtomicInteger();

        // Set a mock to intercept provider callbacks.
        AppWidgetProviderCallbacks callbacks = createAppWidgetProviderCallbacks(invocationCounter);
        FirstAppWidgetProvider.setCallbacks(callbacks);

        int firstAppWidgetId = 0;
        int secondAppWidgetId = 0;

        final Bundle firstOptions;
        final Bundle secondOptions;

        // Create a host and start listening.
        AppWidgetHost host = spy(new AppWidgetHost(getInstrumentation().getTargetContext(), 0));
        host.deleteHost();
        host.startListening();

        // We want to bind a widget.
        grantBindAppWidgetPermission();
        try {
            // Allocate the first widget id to bind.
            firstAppWidgetId = host.allocateAppWidgetId();

            // Grab a provider we defined to be bound.
            AppWidgetProviderInfo provider = getFirstAppWidgetProviderInfo();

            // Bind the first widget.
            getAppWidgetManager().bindAppWidgetIdIfAllowed(firstAppWidgetId,
                    provider.getProfile(), provider.provider, null);

            // Wait for onEnabled and onUpdate
            waitForCallCount(invocationCounter, 2);

            // Update the first widget options.
            firstOptions = new Bundle();
            firstOptions.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 1);
            firstOptions.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 2);
            firstOptions.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 3);
            firstOptions.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 4);
            getAppWidgetManager().updateAppWidgetOptions(firstAppWidgetId, firstOptions);

            // Wait for onAppWidgetOptionsChanged
            waitForCallCount(invocationCounter, 3);

            // Allocate the second app widget id to bind.
            secondAppWidgetId = host.allocateAppWidgetId();

            // Bind the second widget.
            getAppWidgetManager().bindAppWidgetIdIfAllowed(secondAppWidgetId,
                    provider.getProfile(), provider.provider, null);

            // Wait for onUpdate
            waitForCallCount(invocationCounter, 4);

            // Update the second widget options.
            secondOptions = new Bundle();
            secondOptions.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 5);
            secondOptions.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 6);
            secondOptions.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 7);
            secondOptions.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 8);
            getAppWidgetManager().updateAppWidgetOptions(secondAppWidgetId, secondOptions);

            // Wait for onAppWidgetOptionsChanged
            waitForCallCount(invocationCounter, 5);

            // Delete the first widget.
            host.deleteAppWidgetId(firstAppWidgetId);

            // Wait for onDeleted
            waitForCallCount(invocationCounter, 6);

            // Delete the second widget.
            host.deleteAppWidgetId(secondAppWidgetId);

            // Wait for onDeleted and onDisabled
            waitForCallCount(invocationCounter, 8);

            // Make sure the provider callbacks are correct.
            InOrder inOrder = inOrder(callbacks);

            inOrder.verify(callbacks).onEnabled(any(Context.class));
            inOrder.verify(callbacks).onUpdate(any(Context.class),
                    any(AppWidgetManager.class), eq(new int[] {firstAppWidgetId}));
            inOrder.verify(callbacks).onAppWidgetOptionsChanged(any(Context.class),
                    any(AppWidgetManager.class), same(firstAppWidgetId), argThat(
                            new OptionsMatcher(firstOptions)));
            inOrder.verify(callbacks).onUpdate(any(Context.class),
                    any(AppWidgetManager.class), eq(new int[] {secondAppWidgetId}));
            inOrder.verify(callbacks).onAppWidgetOptionsChanged(any(Context.class),
                    any(AppWidgetManager.class), same(secondAppWidgetId), argThat(
                            new OptionsMatcher(secondOptions)));
            inOrder.verify(callbacks).onDeleted(any(Context.class),
                    argThat(new WidgetIdsMatcher(new int[]{firstAppWidgetId})));
            inOrder.verify(callbacks).onDeleted(any(Context.class),
                    argThat(new WidgetIdsMatcher(new int[]{secondAppWidgetId})));
            inOrder.verify(callbacks).onDisabled(any(Context.class));
        } finally {
            // Clean up.
            host.deleteAppWidgetId(firstAppWidgetId);
            host.deleteAppWidgetId(secondAppWidgetId);
            host.deleteHost();
            FirstAppWidgetProvider.setCallbacks(null);
            revokeBindAppWidgetPermission();
        }
    }

    public void testTwoAppWidgetProviderCallbacks() throws Exception {
        if (!hasAppWidgets()) {
            return;
        }

        AtomicInteger invocationCounter = new AtomicInteger();

        // Set a mock to intercept first provider callbacks.
        AppWidgetProviderCallbacks firstCallbacks = createAppWidgetProviderCallbacks(
                invocationCounter);
        FirstAppWidgetProvider.setCallbacks(firstCallbacks);

        // Set a mock to intercept second provider callbacks.
        AppWidgetProviderCallbacks secondCallbacks = createAppWidgetProviderCallbacks(
                invocationCounter);
        SecondAppWidgetProvider.setCallbacks(secondCallbacks);

        int firstAppWidgetId = 0;
        int secondAppWidgetId = 0;

        // Create a host and start listening.
        AppWidgetHost host = spy(new AppWidgetHost(
                getInstrumentation().getTargetContext(), 0));
        host.deleteHost();
        host.startListening();

        // We want to bind widgets.
        grantBindAppWidgetPermission();
        try {
            // Allocate the first widget id to bind.
            firstAppWidgetId = host.allocateAppWidgetId();

            // Allocate the second widget id to bind.
            secondAppWidgetId = host.allocateAppWidgetId();

            // Grab the first provider we defined to be bound.
            AppWidgetProviderInfo firstProvider = getFirstAppWidgetProviderInfo();

            // Bind the first widget.
            getAppWidgetManager().bindAppWidgetIdIfAllowed(firstAppWidgetId,
                    firstProvider.getProfile(), firstProvider.provider, null);

            // Wait for onEnabled and onUpdate
            waitForCallCount(invocationCounter, 2);

            // Grab the second provider we defined to be bound.
            AppWidgetProviderInfo secondProvider = getSecondAppWidgetProviderInfo();

            // Bind the second widget.
            getAppWidgetManager().bindAppWidgetIdIfAllowed(secondAppWidgetId,
                    secondProvider.getProfile(), secondProvider.provider, null);

            // Wait for onEnabled and onUpdate
            waitForCallCount(invocationCounter, 4);

            // Delete the first widget.
            host.deleteAppWidgetId(firstAppWidgetId);

            // Wait for onDeleted and onDisabled
            waitForCallCount(invocationCounter, 6);

            // Delete the second widget.
            host.deleteAppWidgetId(secondAppWidgetId);

            // Wait for onDeleted and onDisabled
            waitForCallCount(invocationCounter, 8);

            // Make sure the first provider callbacks are correct.
            InOrder firstInOrder = inOrder(firstCallbacks);
            firstInOrder.verify(firstCallbacks).onEnabled(any(Context.class));
            firstInOrder.verify(firstCallbacks).onUpdate(any(Context.class),
                    any(AppWidgetManager.class), eq(new int[]{firstAppWidgetId}));
            firstInOrder.verify(firstCallbacks).onDeleted(any(Context.class),
                    argThat(new WidgetIdsMatcher(new int[]{firstAppWidgetId})));
            firstInOrder.verify(firstCallbacks).onDisabled(any(Context.class));

            // Make sure the second provider callbacks are correct.
            InOrder secondInOrder = inOrder(secondCallbacks);
            secondInOrder.verify(secondCallbacks).onEnabled(any(Context.class));
            secondInOrder.verify(secondCallbacks).onUpdate(any(Context.class),
                    any(AppWidgetManager.class), eq(new int[]{secondAppWidgetId}));
            secondInOrder.verify(secondCallbacks).onDeleted(any(Context.class),
                    argThat(new WidgetIdsMatcher(new int[] {secondAppWidgetId})));
            secondInOrder.verify(secondCallbacks).onDisabled(any(Context.class));
        } finally {
            // Clean up.
            host.deleteAppWidgetId(firstAppWidgetId);
            host.deleteAppWidgetId(secondAppWidgetId);
            host.deleteHost();
            FirstAppWidgetProvider.setCallbacks(null);
            SecondAppWidgetProvider.setCallbacks(null);
            revokeBindAppWidgetPermission();
        }
    }

    public void testGetAppWidgetIdsForProvider() throws Exception {
        if (!hasAppWidgets()) {
            return;
        }

        // We want to bind widgets.
        grantBindAppWidgetPermission();

        // Create a host and start listening.
        AppWidgetHost host = new AppWidgetHost(
                getInstrumentation().getTargetContext(), 0);
        host.deleteHost();
        host.startListening();

        int firstAppWidgetId = 0;
        int secondAppWidgetId = 0;

        try {
            // Grab the provider we defined to be bound.
            AppWidgetProviderInfo provider = getFirstAppWidgetProviderInfo();

            // Initially we have no widgets.
            int[] widgetsIds = getAppWidgetManager().getAppWidgetIds(provider.provider);
            assertTrue(widgetsIds.length == 0);

            // Allocate the first widget id to bind.
            firstAppWidgetId = host.allocateAppWidgetId();

            // Bind the first widget.
            getAppWidgetManager().bindAppWidgetIdIfAllowed(firstAppWidgetId,
                    provider.getProfile(), provider.provider, null);

            // Allocate the second widget id to bind.
            secondAppWidgetId = host.allocateAppWidgetId();

            // Bind the second widget.
            getAppWidgetManager().bindAppWidgetIdIfAllowed(secondAppWidgetId,
                    provider.getProfile(), provider.provider, null);

            // Now we have two widgets,
            widgetsIds = getAppWidgetManager().getAppWidgetIds(provider.provider);
            assertTrue(Arrays.equals(widgetsIds, new int[]{firstAppWidgetId, secondAppWidgetId}));
        } finally {
            // Clean up.
            host.deleteAppWidgetId(firstAppWidgetId);
            host.deleteAppWidgetId(secondAppWidgetId);
            host.deleteHost();
            revokeBindAppWidgetPermission();
        }
    }

    public void testGetAppWidgetInfo() throws Exception {
        if (!hasAppWidgets()) {
            return;
        }

        // We want to bind widgets.
        grantBindAppWidgetPermission();

        // Create a host and start listening.
        AppWidgetHost host = new AppWidgetHost(
                getInstrumentation().getTargetContext(), 0);
        host.deleteHost();
        host.startListening();

        int appWidgetId = 0;

        try {
            // Allocate an widget id to bind.
            appWidgetId = host.allocateAppWidgetId();

            // The widget is not bound, so no info.
            AppWidgetProviderInfo foundProvider = getAppWidgetManager()
                    .getAppWidgetInfo(appWidgetId);
            assertNull(foundProvider);

            // Grab the provider we defined to be bound.
            AppWidgetProviderInfo provider = getFirstAppWidgetProviderInfo();

            // Bind the app widget.
            getAppWidgetManager().bindAppWidgetIdIfAllowed(appWidgetId,
                    provider.getProfile(), provider.provider, null);

            // The widget is bound, so the provider info should be there.
            foundProvider = getAppWidgetManager().getAppWidgetInfo(appWidgetId);
            assertEquals(provider.provider, foundProvider.provider);
            assertEquals(provider.getProfile(), foundProvider.getProfile());

            Context context = getInstrumentation().getTargetContext();

            // Let us make sure the provider info is sane.
            String label = foundProvider.loadLabel(context.getPackageManager());
            assertTrue(!TextUtils.isEmpty(label));

            Drawable icon = foundProvider.loadIcon(context, DisplayMetrics.DENSITY_DEFAULT);
            assertNotNull(icon);

            Drawable previewImage = foundProvider.loadPreviewImage(context, 0);
            assertNotNull(previewImage);
        } finally {
            // Clean up.
            host.deleteAppWidgetId(appWidgetId);
            host.deleteHost();
            revokeBindAppWidgetPermission();
        }
    }

    public void testGetAppWidgetOptions() throws Exception {
        if (!hasAppWidgets()) {
            return;
        }

        // We want to bind widgets.
        grantBindAppWidgetPermission();

        // Create a host and start listening.
        AppWidgetHost host = new AppWidgetHost(
                getInstrumentation().getTargetContext(), 0);
        host.deleteHost();
        host.startListening();

        int appWidgetId = 0;

        try {
            // Grab the provider we defined to be bound.
            AppWidgetProviderInfo provider = getFirstAppWidgetProviderInfo();

            // Allocate an widget id to bind.
            appWidgetId = host.allocateAppWidgetId();

            // Initially we have no options.
            Bundle foundOptions = getAppWidgetManager().getAppWidgetOptions(appWidgetId);
            assertTrue(foundOptions.isEmpty());

            // We want to set the options when binding.
            Bundle setOptions = new Bundle();
            setOptions.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 1);
            setOptions.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 2);
            setOptions.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 3);
            setOptions.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 4);

            // Bind the app widget.
            getAppWidgetManager().bindAppWidgetIdIfAllowed(appWidgetId,
                    provider.getProfile(), provider.provider, setOptions);

            // Make sure we get the options used when binding.
            foundOptions = getAppWidgetManager().getAppWidgetOptions(appWidgetId);
            assertTrue(equalOptions(setOptions, foundOptions));
        } finally {
            // Clean up.
            host.deleteAppWidgetId(appWidgetId);
            host.deleteHost();
            revokeBindAppWidgetPermission();
        }
    }

    public void testDeleteHost() throws Exception {
        if (!hasAppWidgets()) {
            return;
        }

        // We want to bind widgets.
        grantBindAppWidgetPermission();

        // Create a host and start listening.
        AppWidgetHost host = new AppWidgetHost(
                getInstrumentation().getTargetContext(), 0);
        host.deleteHost();
        host.startListening();

        int appWidgetId = 0;

        try {
            // Allocate an widget id to bind.
            appWidgetId = host.allocateAppWidgetId();

            // Grab the provider we defined to be bound.
            AppWidgetProviderInfo provider = getFirstAppWidgetProviderInfo();

            // Bind the app widget.
            getAppWidgetManager().bindAppWidgetIdIfAllowed(appWidgetId,
                    provider.getProfile(), provider.provider, null);

            // The widget should be there.
            int[] widgetIds = getAppWidgetManager().getAppWidgetIds(provider.provider);
            assertTrue(Arrays.equals(widgetIds, new int[]{appWidgetId}));

            // Delete the host.
            host.deleteHost();

            // The host is gone and with it the widgets.
            widgetIds = getAppWidgetManager().getAppWidgetIds(provider.provider);
            assertTrue(widgetIds.length == 0);
        } finally {
            // Clean up.
            host.deleteAppWidgetId(appWidgetId);
            host.deleteHost();
            revokeBindAppWidgetPermission();
        }
    }

    public void testDeleteHosts() throws Exception {
        if (!hasAppWidgets()) {
            return;
        }

        // We want to bind widgets.
        grantBindAppWidgetPermission();

        // Create the first host and start listening.
        AppWidgetHost firstHost = new AppWidgetHost(
                getInstrumentation().getTargetContext(), 0);
        firstHost.deleteHost();
        firstHost.startListening();

        // Create the second host and start listening.
        AppWidgetHost secondHost = new AppWidgetHost(
                getInstrumentation().getTargetContext(), 1);
        secondHost.deleteHost();
        secondHost.startListening();

        int firstAppWidgetId = 0;
        int secondAppWidgetId = 0;

        try {
            // Grab the provider we defined to be bound.
            AppWidgetProviderInfo provider = getFirstAppWidgetProviderInfo();

            // Allocate the first widget id to bind.
            firstAppWidgetId = firstHost.allocateAppWidgetId();

            // Bind the first app widget.
            getAppWidgetManager().bindAppWidgetIdIfAllowed(firstAppWidgetId,
                    provider.getProfile(), provider.provider, null);

            // Allocate the second widget id to bind.
            secondAppWidgetId = secondHost.allocateAppWidgetId();

            // Bind the second app widget.
            getAppWidgetManager().bindAppWidgetIdIfAllowed(secondAppWidgetId,
                    provider.getProfile(), provider.provider, null);

            // The widgets should be there.
            int[] widgetIds = getAppWidgetManager().getAppWidgetIds(provider.provider);
            assertTrue(Arrays.equals(widgetIds, new int[]{firstAppWidgetId, secondAppWidgetId}));

            // Delete all hosts.
            AppWidgetHost.deleteAllHosts();

            // The hosts are gone and with it the widgets.
            widgetIds = getAppWidgetManager().getAppWidgetIds(provider.provider);
            assertTrue(widgetIds.length == 0);
        } finally {
            // Clean up.
            firstHost.deleteAppWidgetId(firstAppWidgetId);
            secondHost.deleteAppWidgetId(secondAppWidgetId);
            AppWidgetHost.deleteAllHosts();
            revokeBindAppWidgetPermission();
        }
    }

    public void testOnProvidersChanged() throws Exception {
        if (!hasAppWidgets()) {
            return;
        }

        // We want to bind widgets.
        grantBindAppWidgetPermission();

        final AtomicInteger onProvidersChangedCallCounter = new AtomicInteger();

        // Create a host and start listening.
        AppWidgetHost host = new AppWidgetHost(
                getInstrumentation().getTargetContext(), 0) {
            @Override
            public void onProvidersChanged() {
                synchronized (mLock) {
                    onProvidersChangedCallCounter.incrementAndGet();
                    mLock.notifyAll();
                }
            }
        };
        host.deleteHost();
        host.startListening();

        int appWidgetId = 0;

        try {
            // Grab the provider we defined to be bound.
            AppWidgetProviderInfo firstLookupProvider = getFirstAppWidgetProviderInfo();

            // Allocate a widget id to bind.
            appWidgetId = host.allocateAppWidgetId();

            // Bind the first app widget.
            getAppWidgetManager().bindAppWidgetIdIfAllowed(appWidgetId,
                    firstLookupProvider.getProfile(), firstLookupProvider.provider, null);

            // Disable the provider we just bound to.
            PackageManager packageManager = getInstrumentation().getTargetContext()
                    .getApplicationContext().getPackageManager();
            packageManager.setComponentEnabledSetting(firstLookupProvider.provider,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);

            // Wait for the package change to propagate.
            waitForCallCount(onProvidersChangedCallCounter, 1);

            // The provider should not be present anymore.
            AppWidgetProviderInfo secondLookupProvider = getFirstAppWidgetProviderInfo();
            assertNull(secondLookupProvider);

            // Enable the provider we disabled.
            packageManager.setComponentEnabledSetting(firstLookupProvider.provider,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);

            // Wait for the package change to propagate.
            waitForCallCount(onProvidersChangedCallCounter, 2);
        } finally {
            // Clean up.
            host.deleteAppWidgetId(appWidgetId);
            host.deleteHost();
            revokeBindAppWidgetPermission();
        }
    }

    public void testUpdateAppWidgetViaComponentName() throws Exception {
        if (!hasAppWidgets()) {
            return;
        }

        // We want to bind widgets.
        grantBindAppWidgetPermission();

        final AtomicInteger updateAppWidgetCallCount = new AtomicInteger();

        // Create a host and start listening.
        AppWidgetHost host = new AppWidgetHost(
                getInstrumentation().getTargetContext(), 0) {
            @Override
            protected AppWidgetHostView onCreateView(Context context, int appWidgetId,
                    AppWidgetProviderInfo appWidget) {
                return new MyAppWidgetHostView(context);
            }
        };
        host.deleteHost();
        host.startListening();

        int firstAppWidgetId = 0;
        int secondAppWidgetId = 0;

        try {
            // Grab the provider to be bound.
            AppWidgetProviderInfo provider = getFirstAppWidgetProviderInfo();

            // Allocate the first widget id to bind.
            firstAppWidgetId = host.allocateAppWidgetId();

            // Bind the first app widget.
            getAppWidgetManager().bindAppWidgetIdIfAllowed(firstAppWidgetId,
                    provider.getProfile(), provider.provider, null);

            // Create the first host view.
            MyAppWidgetHostView firstHostView = (MyAppWidgetHostView) host.createView(
                    getInstrumentation().getContext(), firstAppWidgetId, provider);
            MyAppWidgetHostView.OnUpdateAppWidgetListener firstAppHostViewListener =
                    mock(MyAppWidgetHostView.OnUpdateAppWidgetListener.class);
            firstHostView.setOnUpdateAppWidgetListener(firstAppHostViewListener);

            // Allocate the second widget id to bind.
            secondAppWidgetId = host.allocateAppWidgetId();

            // Bind the second app widget.
            getAppWidgetManager().bindAppWidgetIdIfAllowed(secondAppWidgetId,
                    provider.getProfile(), provider.provider, null);

            // Create the second host view.
            MyAppWidgetHostView secondHostView = (MyAppWidgetHostView) host.createView(
                    getInstrumentation().getContext(), secondAppWidgetId, provider);
            MyAppWidgetHostView.OnUpdateAppWidgetListener secondAppHostViewListener =
                    mock(MyAppWidgetHostView.OnUpdateAppWidgetListener.class);
            doAnswer(new Answer<Void>() {
                @Override
                public Void answer(InvocationOnMock invocation) throws Throwable {
                    synchronized (mLock) {
                        updateAppWidgetCallCount.incrementAndGet();
                        mLock.notifyAll();
                    }
                    return null;
                }
            }).when(secondAppHostViewListener).onUpdateAppWidget(any(RemoteViews.class));
            secondHostView.setOnUpdateAppWidgetListener(secondAppHostViewListener);

            // Update all app widgets.
            final RemoteViews content = new RemoteViews(
                    getInstrumentation().getContext().getPackageName(),
                    R.layout.second_initial_layout);
            getAppWidgetManager().updateAppWidget(provider.provider, content);

            waitForCallCount(updateAppWidgetCallCount, 1);

            // Verify the expected callbacks.
            InOrder firstInOrder = inOrder(firstAppHostViewListener);
            firstInOrder.verify(firstAppHostViewListener).onUpdateAppWidget(argThat(
                    new RemoteViewsMatcher(content.getLayoutId(),
                            provider.provider.getPackageName())));

            InOrder secondInOrder = inOrder(secondAppHostViewListener);
            secondInOrder.verify(secondAppHostViewListener).onUpdateAppWidget(argThat(
                    new RemoteViewsMatcher(content.getLayoutId(),
                            provider.provider.getPackageName())));
        } finally {
            // Clean up.
            host.deleteAppWidgetId(firstAppWidgetId);
            host.deleteAppWidgetId(secondAppWidgetId);
            host.deleteHost();
            revokeBindAppWidgetPermission();
        }
    }

    public void testUpdateAppWidgetViaWidgetId() throws Exception {
        if (!hasAppWidgets()) {
            return;
        }

        // We want to bind widgets.
        grantBindAppWidgetPermission();

        final AtomicInteger updateAppWidgetCallCount = new AtomicInteger();

        // Create a host and start listening.
        AppWidgetHost host = new AppWidgetHost(
                getInstrumentation().getTargetContext(), 0) {
            @Override
            protected AppWidgetHostView onCreateView(Context context, int appWidgetId,
                    AppWidgetProviderInfo appWidget) {
                return new MyAppWidgetHostView(context);
            }
        };
        host.deleteHost();
        host.startListening();

        int firstAppWidgetId = 0;

        try {
            // Grab the provider to be bound.
            AppWidgetProviderInfo provider = getFirstAppWidgetProviderInfo();

            // Allocate the first widget id to bind.
            firstAppWidgetId = host.allocateAppWidgetId();

            // Bind the first app widget.
            getAppWidgetManager().bindAppWidgetIdIfAllowed(firstAppWidgetId,
                    provider.getProfile(), provider.provider, null);

            // Create the first host view.
            MyAppWidgetHostView hostView = (MyAppWidgetHostView) host.createView(
                    getInstrumentation().getContext(), firstAppWidgetId, provider);
            MyAppWidgetHostView.OnUpdateAppWidgetListener appHostViewListener =
                    mock(MyAppWidgetHostView.OnUpdateAppWidgetListener.class);
            doAnswer(new Answer<Void>() {
                @Override
                public Void answer(InvocationOnMock invocation) throws Throwable {
                    synchronized (mLock) {
                        updateAppWidgetCallCount.incrementAndGet();
                        mLock.notifyAll();
                    }
                    return null;
                }
            }).when(appHostViewListener).onUpdateAppWidget(any(RemoteViews.class));
            hostView.setOnUpdateAppWidgetListener(appHostViewListener);

            // Update all app widgets.
            RemoteViews content = new RemoteViews(
                    getInstrumentation().getContext().getPackageName(),
                    R.layout.second_initial_layout);
            getAppWidgetManager().updateAppWidget(firstAppWidgetId, content);

            waitForCallCount(updateAppWidgetCallCount, 1);

            // Verify the expected callbacks.
            InOrder inOrder = inOrder(appHostViewListener);
            inOrder.verify(appHostViewListener).onUpdateAppWidget(argThat(
                    new RemoteViewsMatcher(content.getLayoutId(),
                            provider.provider.getPackageName())
            ));
        } finally {
            // Clean up.
            host.deleteAppWidgetId(firstAppWidgetId);
            host.deleteHost();
            revokeBindAppWidgetPermission();
        }
    }

    public void testUpdateAppWidgetViaWidgetIds() throws Exception {
        if (!hasAppWidgets()) {
            return;
        }

        // We want to bind widgets.
        grantBindAppWidgetPermission();

        final AtomicInteger onUpdateAppWidgetCallCount = new AtomicInteger();

        // Create a host and start listening.
        AppWidgetHost host = new AppWidgetHost(
                getInstrumentation().getTargetContext(), 0) {
            @Override
            protected AppWidgetHostView onCreateView(Context context, int appWidgetId,
                    AppWidgetProviderInfo appWidget) {
                return new MyAppWidgetHostView(context);
            }
        };
        host.deleteHost();
        host.startListening();

        int firstAppWidgetId = 0;
        int secondAppWidgetId = 0;

        try {
            // Grab the provider to be bound.
            AppWidgetProviderInfo provider = getFirstAppWidgetProviderInfo();

            // Allocate the first widget id to bind.
            firstAppWidgetId = host.allocateAppWidgetId();

            // Bind the first app widget.
            getAppWidgetManager().bindAppWidgetIdIfAllowed(firstAppWidgetId,
                    provider.getProfile(), provider.provider, null);

            // Create the first host view.
            MyAppWidgetHostView firstHostView = (MyAppWidgetHostView) host.createView(
                    getInstrumentation().getContext(), firstAppWidgetId, provider);
            MyAppWidgetHostView.OnUpdateAppWidgetListener firstAppHostViewListener =
                    mock(MyAppWidgetHostView.OnUpdateAppWidgetListener.class);
            firstHostView.setOnUpdateAppWidgetListener(firstAppHostViewListener);

            // Allocate the second widget id to bind.
            secondAppWidgetId = host.allocateAppWidgetId();

            // Bind the second app widget.
            getAppWidgetManager().bindAppWidgetIdIfAllowed(secondAppWidgetId,
                    provider.getProfile(), provider.provider, null);

            // Create the second host view.
            MyAppWidgetHostView secondHostView = (MyAppWidgetHostView) host.createView(
                    getInstrumentation().getContext(), secondAppWidgetId, provider);
            MyAppWidgetHostView.OnUpdateAppWidgetListener secondAppHostViewListener =
                    mock(MyAppWidgetHostView.OnUpdateAppWidgetListener.class);
            doAnswer(new Answer<Void>() {
                @Override
                public Void answer(InvocationOnMock invocation) throws Throwable {
                    synchronized (mLock) {
                        onUpdateAppWidgetCallCount.incrementAndGet();
                        mLock.notifyAll();
                    }
                    return null;
                }
            }).when(secondAppHostViewListener).onUpdateAppWidget(any(RemoteViews.class));
            secondHostView.setOnUpdateAppWidgetListener(secondAppHostViewListener);

            // Update all app widgets.
            RemoteViews content = new RemoteViews(
                    getInstrumentation().getContext().getPackageName(),
                    R.layout.second_initial_layout);
            getAppWidgetManager().updateAppWidget(new int[] {firstAppWidgetId,
                    secondAppWidgetId}, content);

            waitForCallCount(onUpdateAppWidgetCallCount, 1);

            // Verify the expected callbacks.
            InOrder firstInOrder = inOrder(firstAppHostViewListener);
            firstInOrder.verify(firstAppHostViewListener).onUpdateAppWidget(
                    argThat(new RemoteViewsMatcher(content.getLayoutId(),
                            provider.provider.getPackageName())));

            InOrder secondInOrder = inOrder(secondAppHostViewListener);
            secondInOrder.verify(secondAppHostViewListener).onUpdateAppWidget(
                    argThat(new RemoteViewsMatcher(content.getLayoutId(),
                            provider.provider.getPackageName()))
            );
        } finally {
            // Clean up.
            host.deleteAppWidgetId(firstAppWidgetId);
            host.deleteAppWidgetId(secondAppWidgetId);
            host.deleteHost();
            revokeBindAppWidgetPermission();
        }
    }

    public void testPartiallyUpdateAppWidgetViaWidgetId() throws Exception {
        if (!hasAppWidgets()) {
            return;
        }

        // We want to bind widgets.
        grantBindAppWidgetPermission();

        final AtomicInteger updateAppWidgetCallCount = new AtomicInteger();

        // Create a host and start listening.
        AppWidgetHost host = new AppWidgetHost(
                getInstrumentation().getTargetContext(), 0) {
            @Override
            protected AppWidgetHostView onCreateView(Context context, int appWidgetId,
                    AppWidgetProviderInfo appWidget) {
                return new MyAppWidgetHostView(context);
            }
        };
        host.deleteHost();
        host.startListening();

        int firstAppWidgetId = 0;

        try {
            // Grab the provider to be bound.
            AppWidgetProviderInfo provider = getFirstAppWidgetProviderInfo();

            // Allocate the first widget id to bind.
            firstAppWidgetId = host.allocateAppWidgetId();

            // Bind the first app widget.
            getAppWidgetManager().bindAppWidgetIdIfAllowed(firstAppWidgetId,
                    provider.getProfile(), provider.provider, null);

            // Create the first host view.
            MyAppWidgetHostView hostView = (MyAppWidgetHostView) host.createView(
                    getInstrumentation().getContext(), firstAppWidgetId, provider);
            MyAppWidgetHostView.OnUpdateAppWidgetListener appHostViewListener =
                    mock(MyAppWidgetHostView.OnUpdateAppWidgetListener.class);
            doAnswer(new Answer<Void>() {
                @Override
                public Void answer(InvocationOnMock invocation) throws Throwable {
                    synchronized (mLock) {
                        updateAppWidgetCallCount.incrementAndGet();
                        mLock.notifyAll();
                    }
                    return null;
                }
            }).when(appHostViewListener).onUpdateAppWidget(any(RemoteViews.class));
            hostView.setOnUpdateAppWidgetListener(appHostViewListener);

            // Set the content for all app widgets.
            RemoteViews content = new RemoteViews(
                    getInstrumentation().getContext().getPackageName(),
                    R.layout.first_initial_layout);
            getAppWidgetManager().updateAppWidget(firstAppWidgetId, content);

            waitForCallCount(updateAppWidgetCallCount, 1);

            // Partially update the content for all app widgets (pretend we changed something).
            getAppWidgetManager().partiallyUpdateAppWidget(firstAppWidgetId, content);

            waitForCallCount(updateAppWidgetCallCount, 2);

            // Verify the expected callbacks.
            InOrder inOrder = inOrder(appHostViewListener);
            inOrder.verify(appHostViewListener, times(2)).onUpdateAppWidget(
                    argThat(new RemoteViewsMatcher(content.getLayoutId(),
                            provider.provider.getPackageName())));
        } finally {
            // Clean up.
            host.deleteAppWidgetId(firstAppWidgetId);
            host.deleteHost();
            revokeBindAppWidgetPermission();
        }
    }

    public void testPartiallyUpdateAppWidgetViaWidgetIds() throws Exception {
        if (!hasAppWidgets()) {
            return;
        }

        // We want to bind widgets.
        grantBindAppWidgetPermission();

        final AtomicInteger firstAppWidgetCallCounter = new AtomicInteger();
        final AtomicInteger secondAppWidgetCallCounter = new AtomicInteger();

        // Create a host and start listening.
        AppWidgetHost host = new AppWidgetHost(
                getInstrumentation().getTargetContext(), 0) {
            @Override
            protected AppWidgetHostView onCreateView(Context context, int appWidgetId,
                    AppWidgetProviderInfo appWidget) {
                return new MyAppWidgetHostView(context);
            }
        };
        host.deleteHost();
        host.startListening();

        int firstAppWidgetId = 0;
        int secondAppWidgetId = 0;

        try {
            // Grab the provider to be bound.
            AppWidgetProviderInfo provider = getFirstAppWidgetProviderInfo();

            // Allocate the first widget id to bind.
            firstAppWidgetId = host.allocateAppWidgetId();

            // Bind the first app widget.
            getAppWidgetManager().bindAppWidgetIdIfAllowed(firstAppWidgetId,
                    provider.getProfile(), provider.provider, null);

            // Create the first host view.
            MyAppWidgetHostView firstHostView = (MyAppWidgetHostView) host.createView(
                    getInstrumentation().getContext(), firstAppWidgetId, provider);
            MyAppWidgetHostView.OnUpdateAppWidgetListener firstAppHostViewListener =
                    mock(MyAppWidgetHostView.OnUpdateAppWidgetListener.class);
            doAnswer(new Answer<Void>() {
                @Override
                public Void answer(InvocationOnMock invocation) throws Throwable {
                    synchronized (mLock) {
                        firstAppWidgetCallCounter.incrementAndGet();
                        mLock.notifyAll();
                    }
                    return null;
                }
            }).when(firstAppHostViewListener).onUpdateAppWidget(any(RemoteViews.class));
            firstHostView.setOnUpdateAppWidgetListener(firstAppHostViewListener);

            // Allocate the second widget id to bind.
            secondAppWidgetId = host.allocateAppWidgetId();

            // Bind the second app widget.
            getAppWidgetManager().bindAppWidgetIdIfAllowed(secondAppWidgetId,
                    provider.getProfile(), provider.provider, null);

            // Create the second host view.
            MyAppWidgetHostView secondHostView = (MyAppWidgetHostView) host.createView(
                    getInstrumentation().getContext(), secondAppWidgetId, provider);
            MyAppWidgetHostView.OnUpdateAppWidgetListener secondAppHostViewListener =
                    mock(MyAppWidgetHostView.OnUpdateAppWidgetListener.class);
            doAnswer(new Answer<Void>() {
                @Override
                public Void answer(InvocationOnMock invocation) throws Throwable {
                    synchronized (mLock) {
                        secondAppWidgetCallCounter.incrementAndGet();
                        mLock.notifyAll();
                    }
                    return null;
                }
            }).when(secondAppHostViewListener).onUpdateAppWidget(any(RemoteViews.class));
            secondHostView.setOnUpdateAppWidgetListener(secondAppHostViewListener);

            // Set the content for all app widgets.
            RemoteViews content = new RemoteViews(
                    getInstrumentation().getContext().getPackageName(),
                    R.layout.first_initial_layout);
            getAppWidgetManager().updateAppWidget(new int[]{firstAppWidgetId,
                    secondAppWidgetId}, content);

            waitForCallCount(firstAppWidgetCallCounter, 1);
            waitForCallCount(secondAppWidgetCallCounter, 1);

            // Partially update the content for all app widgets (pretend we changed something).
            getAppWidgetManager().partiallyUpdateAppWidget(new int[] {firstAppWidgetId,
                    secondAppWidgetId}, content);

            waitForCallCount(firstAppWidgetCallCounter, 2);
            waitForCallCount(secondAppWidgetCallCounter, 2);

            // Verify the expected callbacks.
            InOrder firstInOrder = inOrder(firstAppHostViewListener);
            firstInOrder.verify(firstAppHostViewListener, times(2)).onUpdateAppWidget(
                    argThat(new RemoteViewsMatcher(content.getLayoutId(),
                            provider.provider.getPackageName())));

            InOrder secondInOrder = inOrder(secondAppHostViewListener);
            secondInOrder.verify(secondAppHostViewListener, times(2)).onUpdateAppWidget(
                    argThat(new RemoteViewsMatcher(content.getLayoutId(),
                            provider.provider.getPackageName())));
        } finally {
            // Clean up.
            host.deleteAppWidgetId(firstAppWidgetId);
            host.deleteAppWidgetId(secondAppWidgetId);
            host.deleteHost();
            revokeBindAppWidgetPermission();
        }
    }

    public void testCollectionWidgets() throws Exception {
        if (!hasAppWidgets()) {
            return;
        }

        // We want to bind widgets.
        grantBindAppWidgetPermission();

        final AtomicInteger invocationCounter = new AtomicInteger();
        final Context context = getInstrumentation().getTargetContext();

        // Create a host and start listening.
        final AppWidgetHost host = new AppWidgetHost(context, 0);
        host.deleteHost();
        host.startListening();

        final int appWidgetId;

        try {
            // Configure the provider behavior.
            AppWidgetProviderCallbacks callbacks = createAppWidgetProviderCallbacks(
                    invocationCounter);
            doAnswer(new Answer<Void>() {
                @Override
                public Void answer(InvocationOnMock invocation) throws Throwable {
                    final int appWidgetId = ((int[]) invocation.getArguments()[2])[0];

                    Intent intent = new Intent(context, MyAppWidgetService.class);
                    intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                    intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));

                    RemoteViews removeViews = new RemoteViews(context.getPackageName(),
                            R.layout.collection_widget_layout);
                    removeViews.setRemoteAdapter(R.id.stack_view, intent);

                    getAppWidgetManager().updateAppWidget(appWidgetId, removeViews);

                    synchronized (mLock) {
                        invocationCounter.incrementAndGet();
                        mLock.notifyAll();
                    }

                    return null;
                }
            }).when(callbacks).onUpdate(any(Context.class), any(AppWidgetManager.class),
                    any(int[].class));
            FirstAppWidgetProvider.setCallbacks(callbacks);

            // Grab the provider to be bound.
            final AppWidgetProviderInfo provider = getFirstAppWidgetProviderInfo();

            // Allocate a widget id to bind.
            appWidgetId = host.allocateAppWidgetId();

            // Bind the app widget.
            getAppWidgetManager().bindAppWidgetIdIfAllowed(appWidgetId,
                    provider.getProfile(), provider.provider, null);

            // Wait for onEnabled and onUpdate
            waitForCallCount(invocationCounter, 2);

            // Configure the app widget service behavior.
            RemoteViewsFactory factory = mock(RemoteViewsFactory.class);
            doAnswer(new Answer<Integer>() {
                @Override
                public Integer answer(InvocationOnMock invocation) throws Throwable {
                    return 1;
                }
            }).when(factory).getCount();
            doAnswer(new Answer<RemoteViews>() {
                @Override
                public RemoteViews answer(InvocationOnMock invocation) throws Throwable {
                    RemoteViews remoteViews = new RemoteViews(context.getPackageName(),
                            R.layout.collection_widget_item_layout);
                    remoteViews.setTextViewText(R.id.text_view, context.getText(R.string.foo));
                    synchronized (mLock) {
                        invocationCounter.incrementAndGet();
                    }
                    return remoteViews;
                }
            }).when(factory).getViewAt(any(int.class));
            doAnswer(new Answer<Integer>() {
                @Override
                public Integer answer(InvocationOnMock invocation) throws Throwable {
                    return 1;
                }
            }).when(factory).getViewTypeCount();
            MyAppWidgetService.setFactory(factory);

            getInstrumentation().runOnMainSync(new Runnable() {
                @Override
                public void run() {
                    host.createView(context, appWidgetId, provider);
                }
            });

            // Wait for the interactions to occur.
            waitForCallCount(invocationCounter, 3);

            // Verify the interactions.
            verify(factory, atLeastOnce()).hasStableIds();
            verify(factory, atLeastOnce()).getViewTypeCount();
            verify(factory, atLeastOnce()).getCount();
            verify(factory, atLeastOnce()).getLoadingView();
            verify(factory, atLeastOnce()).getViewAt(same(0));
        } finally {
            // Clean up.
            FirstAppWidgetProvider.setCallbacks(null);
            host.deleteHost();
            revokeBindAppWidgetPermission();
        }
    }

    private void waitForCallCount(AtomicInteger counter, int expectedCount) {
        synchronized (mLock) {
            final long startTimeMillis = SystemClock.uptimeMillis();
            while (counter.get() < expectedCount) {
                final long elapsedTimeMillis = SystemClock.uptimeMillis() - startTimeMillis;
                final long remainingTimeMillis = OPERATION_TIMEOUT - elapsedTimeMillis;
                if (remainingTimeMillis <= 0) {
                    fail("Did not get expected call");
                }
                try {
                    mLock.wait(remainingTimeMillis);
                } catch (InterruptedException ie) {
                        /* ignore */
                }
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void assertExpectedInstalledProviders(List<AppWidgetProviderInfo> providers) {
        boolean[] verifiedWidgets = verifyInstalledProviders(providers);
        assertTrue(verifiedWidgets[0]);
        assertTrue(verifiedWidgets[1]);
    }

    private void grantBindAppWidgetPermission() throws Exception {
        runShellCommand(GRANT_BIND_APP_WIDGET_PERMISSION_COMMAND);
    }

    private void revokeBindAppWidgetPermission() throws Exception {
        runShellCommand(REVOKE_BIND_APP_WIDGET_PERMISSION_COMMAND);
    }

    private AppWidgetProviderInfo getFirstAppWidgetProviderInfo() {
        return getProviderInfo(getFirstWidgetComponent());
    }

    private AppWidgetProviderInfo getSecondAppWidgetProviderInfo() {
        return getProviderInfo(getSecondWidgetComponent());
    }

    private AppWidgetProviderInfo getProviderInfo(ComponentName componentName) {
        List<AppWidgetProviderInfo> providers = getAppWidgetManager().getInstalledProviders();

        final int providerCount = providers.size();
        for (int i = 0; i < providerCount; i++) {
            AppWidgetProviderInfo provider = providers.get(i);
            if (componentName.equals(provider.provider)
                    && Process.myUserHandle().equals(provider.getProfile())) {
                return provider;

            }
        }

        return null;
    }

    private AppWidgetManager getAppWidgetManager() {
        return (AppWidgetManager) getInstrumentation().getTargetContext()
                .getSystemService(Context.APPWIDGET_SERVICE);
    }

    private AppWidgetProviderCallbacks createAppWidgetProviderCallbacks(
            final AtomicInteger callCounter) {
        // Set a mock to intercept provider callbacks.
        AppWidgetProviderCallbacks callbacks = mock(AppWidgetProviderCallbacks.class);

        // onEnabled
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                synchronized (mLock) {
                    callCounter.incrementAndGet();
                    mLock.notifyAll();
                }
                return null;
            }
        }).when(callbacks).onEnabled(any(Context.class));

        // onUpdate
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                synchronized (mLock) {
                    callCounter.incrementAndGet();
                    mLock.notifyAll();
                }
                return null;
            }
        }).when(callbacks).onUpdate(any(Context.class), any(AppWidgetManager.class),
                any(int[].class));

        // onAppWidgetOptionsChanged
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                synchronized (mLock) {
                    callCounter.incrementAndGet();
                    mLock.notifyAll();
                }
                return null;
            }
        }).when(callbacks).onAppWidgetOptionsChanged(any(Context.class),
                any(AppWidgetManager.class), any(int.class), any(Bundle.class));

        // onDeleted
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                synchronized (mLock) {
                    callCounter.incrementAndGet();
                    mLock.notifyAll();
                }
                return null;
            }
        }).when(callbacks).onDeleted(any(Context.class), any(int[].class));

        // onDisabled
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                synchronized (mLock) {
                    callCounter.incrementAndGet();
                    mLock.notifyAll();
                }
                return null;
            }
        }).when(callbacks).onDisabled(any(Context.class));

        // onRestored
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                synchronized (mLock) {
                    callCounter.incrementAndGet();
                    mLock.notifyAll();
                }
                return null;
            }
        }).when(callbacks).onRestored(any(Context.class), any(int[].class),
                any(int[].class));

        return callbacks;
    }

    private static boolean equalOptions(Bundle first, Bundle second) {
        return first.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                       == second.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                && first.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
                       == second.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
                && first.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
                       == second.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
                && first.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
                       == second.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT);
    }

    private static final class OptionsMatcher extends BaseMatcher<Bundle> {
        private Bundle mOptions;

        public OptionsMatcher(Bundle options) {
            mOptions = options;
        }

        @Override
        public boolean matches(Object item) {
            Bundle options = (Bundle) item;
            return equalOptions(mOptions, options);
        }

        @Override
        public void describeTo(Description description) {
            /* do nothing */
        }
    }

    private static final class WidgetIdsMatcher extends BaseMatcher<int[]> {
        private final int[] mWidgetIds;

        public WidgetIdsMatcher(int[] widgetIds) {
            mWidgetIds = widgetIds;
        }

        @Override
        public boolean matches(Object item) {
            final int[] widgetIds = (int[]) item;
            return Arrays.equals(widgetIds, mWidgetIds);
        }

        @Override
        public void describeTo(Description description) {
            /* do nothing */
        }
    }

    private static final class RemoteViewsMatcher extends BaseMatcher<RemoteViews> {
        private final int mLayoutId;
        private final String mPackageName;

        public RemoteViewsMatcher(int layoutId, String packageName) {
            mLayoutId = layoutId;
            mPackageName = packageName;
        }

        @Override
        public boolean matches(Object item) {
            final RemoteViews remoteViews = (RemoteViews) item;
            return remoteViews != null && remoteViews.getLayoutId() == mLayoutId
                    && remoteViews.getPackage().equals(mPackageName);
        }

        @Override
        public void describeTo(Description description) {
            /* do nothing */
        }
    }

    private static class MyAppWidgetHostView extends AppWidgetHostView {
        private OnUpdateAppWidgetListener mOnUpdateAppWidgetListener;


        public interface OnUpdateAppWidgetListener {
            public void onUpdateAppWidget(RemoteViews remoteViews);
        }

        private MyAppWidgetHostView(Context context) {
            super(context);
        }

        public void setOnUpdateAppWidgetListener(OnUpdateAppWidgetListener listener) {
            mOnUpdateAppWidgetListener = listener;
        }

        @Override
        public void updateAppWidget(RemoteViews remoteViews) {
            super.updateAppWidget(remoteViews);
            if (mOnUpdateAppWidgetListener != null) {
                mOnUpdateAppWidgetListener.onUpdateAppWidget(remoteViews);
            }
        }
    }
}
