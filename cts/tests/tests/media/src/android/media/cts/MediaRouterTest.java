/*
 * Copyright (C) 2016 The Android Open Source Project
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
package android.media.cts;

import android.media.cts.R;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.MediaRouter;
import android.media.MediaRouter.RouteGroup;
import android.media.MediaRouter.RouteCategory;
import android.media.MediaRouter.RouteInfo;
import android.media.MediaRouter.UserRouteInfo;
import android.media.RemoteControlClient;
import android.test.InstrumentationTestCase;

import java.util.List;
import java.util.ArrayList;

/**
 * Test {@link android.media.MediaRouter}.
 */
public class MediaRouterTest extends InstrumentationTestCase {

    private static final int TEST_ROUTE_NAME_RESOURCE_ID = R.string.test_user_route_name;
    private static final int TEST_CATEGORY_NAME_RESOURCE_ID = R.string.test_route_category_name;
    private static final int TEST_ICON_RESOURCE_ID = R.drawable.single_face;
    private static final int TEST_MAX_VOLUME = 100;
    private static final int TEST_VOLUME = 17;
    private static final int TEST_VOLUME_DIRECTION = -2;
    private static final int TEST_PLAYBACK_STREAM = AudioManager.STREAM_ALARM;
    private static final int TEST_VOLUME_HANDLING = RouteInfo.PLAYBACK_VOLUME_VARIABLE;
    private static final int TEST_PLAYBACK_TYPE = RouteInfo.PLAYBACK_TYPE_LOCAL;
    private static final CharSequence TEST_ROUTE_DESCRIPTION = "test_user_route_description";
    private static final CharSequence TEST_STATUS = "test_user_route_status";
    private static final CharSequence TEST_GROUPABLE_CATEGORY_NAME = "test_groupable_category_name";

    private MediaRouter mMediaRouter;
    private RouteCategory mTestCategory;
    private RouteCategory mTestGroupableCategory;
    private CharSequence mTestRouteName;
    private Drawable mTestIconDrawable;
    private Context mContext;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getInstrumentation().getContext();
        mMediaRouter = (MediaRouter) mContext.getSystemService(Context.MEDIA_ROUTER_SERVICE);
        mTestCategory = mMediaRouter.createRouteCategory(TEST_CATEGORY_NAME_RESOURCE_ID, false);
        mTestGroupableCategory = mMediaRouter.createRouteCategory(TEST_GROUPABLE_CATEGORY_NAME,
                true);
        mTestRouteName = mContext.getText(TEST_ROUTE_NAME_RESOURCE_ID);
        mTestIconDrawable = mContext.getDrawable(TEST_ICON_RESOURCE_ID);
    }

    protected void tearDown() throws Exception {
        mMediaRouter.clearUserRoutes();
        super.tearDown();
    }

    /**
     * Test {@link MediaRouter#selectRoute(int, RouteInfo)}.
     */
    public void testSelectRoute() {
        RouteInfo prevSelectedRoute = mMediaRouter.getSelectedRoute(
                MediaRouter.ROUTE_TYPE_LIVE_AUDIO | MediaRouter.ROUTE_TYPE_LIVE_VIDEO
                | MediaRouter.ROUTE_TYPE_REMOTE_DISPLAY);
        assertNotNull(prevSelectedRoute);

        UserRouteInfo userRoute = mMediaRouter.createUserRoute(mTestCategory);
        mMediaRouter.addUserRoute(userRoute);
        mMediaRouter.selectRoute(userRoute.getSupportedTypes(), userRoute);

        RouteInfo nowSelectedRoute = mMediaRouter.getSelectedRoute(MediaRouter.ROUTE_TYPE_USER);
        assertEquals(userRoute, nowSelectedRoute);
        assertEquals(mTestCategory, nowSelectedRoute.getCategory());

        mMediaRouter.selectRoute(prevSelectedRoute.getSupportedTypes(), prevSelectedRoute);
    }

    /**
     * Test {@link MediaRouter#getRouteCount()}.
     */
    public void testGetRouteCount() {
        final int count = mMediaRouter.getRouteCount();
        assertTrue("By default, a media router has at least one route.", count > 0);

        UserRouteInfo userRoute0 = mMediaRouter.createUserRoute(mTestCategory);
        mMediaRouter.addUserRoute(userRoute0);
        assertEquals(count + 1, mMediaRouter.getRouteCount());

        mMediaRouter.removeUserRoute(userRoute0);
        assertEquals(count, mMediaRouter.getRouteCount());

        UserRouteInfo userRoute1 = mMediaRouter.createUserRoute(mTestCategory);
        mMediaRouter.addUserRoute(userRoute0);
        mMediaRouter.addUserRoute(userRoute1);
        assertEquals(count + 2, mMediaRouter.getRouteCount());

        mMediaRouter.clearUserRoutes();
        assertEquals(count, mMediaRouter.getRouteCount());
    }

    /**
     * Test {@link MediaRouter#getRouteAt(int)}.
     */
    public void testGetRouteAt() throws Exception {
        UserRouteInfo userRoute0 = mMediaRouter.createUserRoute(mTestCategory);
        UserRouteInfo userRoute1 = mMediaRouter.createUserRoute(mTestCategory);
        mMediaRouter.addUserRoute(userRoute0);
        mMediaRouter.addUserRoute(userRoute1);

        int count = mMediaRouter.getRouteCount();
        assertEquals(userRoute0, mMediaRouter.getRouteAt(count - 2));
        assertEquals(userRoute1, mMediaRouter.getRouteAt(count - 1));
    }

    /**
     * Test {@link MediaRouter.UserRouteInfo} with the default route.
     */
    public void testDefaultRouteInfo() {
        RouteInfo route = mMediaRouter.getDefaultRoute();

        assertNotNull(route.getCategory());
        assertNotNull(route.getName());
        assertNotNull(route.getName(mContext));
        assertTrue(route.isEnabled());
        assertFalse(route.isConnecting());
        assertEquals(RouteInfo.DEVICE_TYPE_UNKNOWN, route.getDeviceType());
        assertEquals(RouteInfo.PLAYBACK_TYPE_LOCAL, route.getPlaybackType());
        assertNull(route.getDescription());
        assertNull(route.getStatus());
        assertNull(route.getIconDrawable());
        assertNull(route.getGroup());

        Object tag = new Object();
        route.setTag(tag);
        assertEquals(tag, route.getTag());

        assertEquals(AudioManager.STREAM_MUSIC, route.getPlaybackStream());
        if (RouteInfo.PLAYBACK_VOLUME_VARIABLE == route.getVolumeHandling()) {
            int curVolume = route.getVolume();
            int maxVolume = route.getVolumeMax();
            assertTrue(curVolume <= maxVolume);
            if (!mContext.getResources().getBoolean(
                    com.android.internal.R.bool.config_safe_media_volume_enabled)) {
                route.requestSetVolume(maxVolume);
                assertEquals(maxVolume, route.getVolume());
                route.requestUpdateVolume(-maxVolume);
            } else {
                route.requestSetVolume(0);
            }
            assertEquals(0, route.getVolume());
            route.requestUpdateVolume(curVolume);
            assertEquals(curVolume, route.getVolume());
        }
    }

    /**
     * Test {@link MediaRouter.UserRouteInfo}.
     */
    public void testUserRouteInfo() {
        UserRouteInfo userRoute = mMediaRouter.createUserRoute(mTestCategory);
        assertTrue(userRoute.isEnabled());
        assertFalse(userRoute.isConnecting());
        assertEquals(mTestCategory, userRoute.getCategory());
        assertEquals(RouteInfo.DEVICE_TYPE_UNKNOWN, userRoute.getDeviceType());
        assertEquals(RouteInfo.PLAYBACK_TYPE_REMOTE, userRoute.getPlaybackType());

        // Test setName by CharSequence object.
        userRoute.setName(mTestRouteName);
        assertEquals(mTestRouteName, userRoute.getName());

        userRoute.setName(null);
        assertNull(userRoute.getName());

        // Test setName by resource ID.
        // The getName() method tries to find the resource in application resources which was stored
        // when the media router is first initialized. In contrast, getName(Context) method tries to
        // find the resource in a given context's resources. So if we call getName(Context) with a
        // context which has the same resources, two methods will return the same value.
        userRoute.setName(TEST_ROUTE_NAME_RESOURCE_ID);
        assertEquals(mTestRouteName, userRoute.getName());
        assertEquals(mTestRouteName, userRoute.getName(mContext));

        userRoute.setDescription(TEST_ROUTE_DESCRIPTION);
        assertEquals(TEST_ROUTE_DESCRIPTION, userRoute.getDescription());

        userRoute.setStatus(TEST_STATUS);
        assertEquals(TEST_STATUS, userRoute.getStatus());

        Object tag = new Object();
        userRoute.setTag(tag);
        assertEquals(tag, userRoute.getTag());

        userRoute.setPlaybackStream(TEST_PLAYBACK_STREAM);
        assertEquals(TEST_PLAYBACK_STREAM, userRoute.getPlaybackStream());

        userRoute.setIconDrawable(mTestIconDrawable);
        assertEquals(mTestIconDrawable, userRoute.getIconDrawable());

        userRoute.setIconDrawable(null);
        assertNull(userRoute.getIconDrawable());

        userRoute.setIconResource(TEST_ICON_RESOURCE_ID);
        assertTrue(getBitmap(mTestIconDrawable).sameAs(getBitmap(userRoute.getIconDrawable())));

        userRoute.setVolumeMax(TEST_MAX_VOLUME);
        assertEquals(TEST_MAX_VOLUME, userRoute.getVolumeMax());

        userRoute.setVolume(TEST_VOLUME);
        assertEquals(TEST_VOLUME, userRoute.getVolume());

        Intent intent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        PendingIntent mediaButtonIntent = PendingIntent.getBroadcast(
                mContext, 0, intent, PendingIntent.FLAG_ONE_SHOT);
        RemoteControlClient rcc = new RemoteControlClient(mediaButtonIntent);
        userRoute.setRemoteControlClient(rcc);
        assertEquals(rcc, userRoute.getRemoteControlClient());

        userRoute.setVolumeHandling(TEST_VOLUME_HANDLING);
        assertEquals(TEST_VOLUME_HANDLING, userRoute.getVolumeHandling());

        userRoute.setPlaybackType(TEST_PLAYBACK_TYPE);
        assertEquals(TEST_PLAYBACK_TYPE, userRoute.getPlaybackType());
    }

    /**
     * Test {@link MediaRouter.RouteGroup}.
     */
    public void testRouteGroup() {
        // Create a route with a groupable category.
        // A route does not belong to any group until it is added to a media router or to a group.
        UserRouteInfo userRoute0 = mMediaRouter.createUserRoute(mTestGroupableCategory);
        assertNull(userRoute0.getGroup());

        // Call addUserRoute(UserRouteInfo).
        // For the route whose category is groupable, this method does not directly add the route in
        // the media router. Instead, it creates a RouteGroup, adds the group in the media router,
        // and puts the route inside that group.
        mMediaRouter.addUserRoute(userRoute0);
        RouteGroup routeGroup = userRoute0.getGroup();
        assertNotNull(routeGroup);
        assertEquals(1, routeGroup.getRouteCount());
        assertEquals(userRoute0, routeGroup.getRouteAt(0));

        // Create another two routes with the same category.
        UserRouteInfo userRoute1 = mMediaRouter.createUserRoute(mTestGroupableCategory);
        UserRouteInfo userRoute2 = mMediaRouter.createUserRoute(mTestGroupableCategory);

        // Add userRoute2 at the end of the group.
        routeGroup.addRoute(userRoute2);
        assertSame(routeGroup, userRoute2.getGroup());
        assertEquals(2, routeGroup.getRouteCount());
        assertEquals(userRoute0, routeGroup.getRouteAt(0));
        assertEquals(userRoute2, routeGroup.getRouteAt(1));

        // To place routes in order, add userRoute1 to the group between userRoute0 and userRoute2.
        routeGroup.addRoute(userRoute1, 1);
        assertSame(routeGroup, userRoute1.getGroup());
        assertEquals(3, routeGroup.getRouteCount());
        assertEquals(userRoute0, routeGroup.getRouteAt(0));
        assertEquals(userRoute1, routeGroup.getRouteAt(1));
        assertEquals(userRoute2, routeGroup.getRouteAt(2));

        // Remove userRoute0.
        routeGroup.removeRoute(userRoute0);
        assertNull(userRoute0.getGroup());
        assertEquals(2, routeGroup.getRouteCount());
        assertEquals(userRoute1, routeGroup.getRouteAt(0));
        assertEquals(userRoute2, routeGroup.getRouteAt(1));

        // Remove userRoute1 which is the first route in the group now.
        routeGroup.removeRoute(0);
        assertNull(userRoute1.getGroup());
        assertEquals(1, routeGroup.getRouteCount());
        assertEquals(userRoute2, routeGroup.getRouteAt(0));

        // Routes in different categories cannot be added to the same group.
        UserRouteInfo userRouteInAnotherCategory = mMediaRouter.createUserRoute(mTestCategory);
        try {
            // This will throw an IllegalArgumentException.
            routeGroup.addRoute(userRouteInAnotherCategory);
            fail();
        } catch (IllegalArgumentException exception) {
            // Expected
        }

        // Set an icon for the group.
        routeGroup.setIconDrawable(mTestIconDrawable);
        assertEquals(mTestIconDrawable, routeGroup.getIconDrawable());

        routeGroup.setIconDrawable(null);
        assertNull(routeGroup.getIconDrawable());

        routeGroup.setIconResource(TEST_ICON_RESOURCE_ID);
        assertTrue(getBitmap(mTestIconDrawable).sameAs(getBitmap(routeGroup.getIconDrawable())));
    }

    /**
     * Test {@link MediaRouter.RouteCategory}.
     */
    public void testRouteCategory() {
        // Test getName() for category whose name is set with resource ID.
        RouteCategory routeCategory = mMediaRouter.createRouteCategory(
                TEST_CATEGORY_NAME_RESOURCE_ID, false);

        // The getName() method tries to find the resource in application resources which was stored
        // when the media router is first initialized. In contrast, getName(Context) method tries to
        // find the resource in a given context's resources. So if we call getName(Context) with a
        // context which has the same resources, two methods will return the same value.
        CharSequence categoryName = mContext.getText(
                TEST_CATEGORY_NAME_RESOURCE_ID);
        assertEquals(categoryName, routeCategory.getName());
        assertEquals(categoryName, routeCategory.getName(mContext));

        assertFalse(routeCategory.isGroupable());
        assertEquals(MediaRouter.ROUTE_TYPE_USER, routeCategory.getSupportedTypes());

        final int count = mMediaRouter.getCategoryCount();
        assertTrue("By default, a media router has at least one route category.", count > 0);

        UserRouteInfo userRoute = mMediaRouter.createUserRoute(routeCategory);
        mMediaRouter.addUserRoute(userRoute);
        assertEquals(count + 1, mMediaRouter.getCategoryCount());
        assertEquals(routeCategory, mMediaRouter.getCategoryAt(count));

        List<RouteInfo> routesInCategory = new ArrayList<RouteInfo>();
        routeCategory.getRoutes(routesInCategory);
        assertEquals(1, routesInCategory.size());

        RouteInfo route = routesInCategory.get(0);
        assertEquals(userRoute, route);

        // Test getName() for category whose name is set with CharSequence object.
        RouteCategory newRouteCategory = mMediaRouter.createRouteCategory(categoryName, false);
        assertEquals(categoryName, newRouteCategory.getName());
    }

    public void testCallback() {
        MediaRouterCallback callback = new MediaRouterCallback();
        MediaRouter.Callback mrc = (MediaRouter.Callback) callback;
        MediaRouter.SimpleCallback mrsc = (MediaRouter.SimpleCallback) callback;

        final int allRouteTypes = MediaRouter.ROUTE_TYPE_LIVE_AUDIO
                | MediaRouter.ROUTE_TYPE_LIVE_VIDEO | MediaRouter.ROUTE_TYPE_REMOTE_DISPLAY
                | MediaRouter.ROUTE_TYPE_USER;
        mMediaRouter.addCallback(allRouteTypes, callback);

        // Test onRouteAdded().
        callback.reset();
        UserRouteInfo userRoute = mMediaRouter.createUserRoute(mTestCategory);
        mMediaRouter.addUserRoute(userRoute);
        assertTrue(callback.mOnRouteAddedCalled);
        assertEquals(userRoute, callback.mAddedRoute);
        // Call the callback methods directly so they are marked as tested
        mrc.onRouteAdded(mMediaRouter, callback.mAddedRoute);
        mrsc.onRouteAdded(mMediaRouter, callback.mAddedRoute);

        RouteInfo prevSelectedRoute = mMediaRouter.getSelectedRoute();

        // Test onRouteSelected() and onRouteUnselected().
        callback.reset();
        mMediaRouter.selectRoute(MediaRouter.ROUTE_TYPE_USER, userRoute);
        assertTrue(callback.mOnRouteUnselectedCalled);
        assertEquals(prevSelectedRoute, callback.mUnselectedRoute);
        assertTrue(callback.mOnRouteSelectedCalled);
        assertEquals(userRoute, callback.mSelectedRoute);
        // Call the callback methods directly so they are marked as tested
        mrc.onRouteUnselected(mMediaRouter, MediaRouter.ROUTE_TYPE_USER, callback.mUnselectedRoute);
        mrc.onRouteSelected(mMediaRouter, MediaRouter.ROUTE_TYPE_USER, callback.mSelectedRoute);
        mrsc.onRouteUnselected(mMediaRouter, MediaRouter.ROUTE_TYPE_USER,
                callback.mUnselectedRoute);
        mrsc.onRouteSelected(mMediaRouter, MediaRouter.ROUTE_TYPE_USER, callback.mSelectedRoute);

        // Test onRouteChanged().
        // It is called when the route's name, description, status or tag is updated.
        callback.reset();
        userRoute.setName(mTestRouteName);
        assertTrue(callback.mOnRouteChangedCalled);
        assertEquals(userRoute, callback.mChangedRoute);
        // Call the callback methods directly so they are marked as tested
        mrc.onRouteChanged(mMediaRouter, callback.mChangedRoute);
        mrsc.onRouteChanged(mMediaRouter, callback.mChangedRoute);

        callback.reset();
        userRoute.setDescription(TEST_ROUTE_DESCRIPTION);
        assertTrue(callback.mOnRouteChangedCalled);
        assertEquals(userRoute, callback.mChangedRoute);
        // Call the callback methods directly so they are marked as tested
        mrc.onRouteChanged(mMediaRouter, callback.mChangedRoute);
        mrsc.onRouteChanged(mMediaRouter, callback.mChangedRoute);

        callback.reset();
        userRoute.setStatus(TEST_STATUS);
        assertTrue(callback.mOnRouteChangedCalled);
        assertEquals(userRoute, callback.mChangedRoute);
        // Call the callback methods directly so they are marked as tested
        mrc.onRouteChanged(mMediaRouter, callback.mChangedRoute);
        mrsc.onRouteChanged(mMediaRouter, callback.mChangedRoute);

        callback.reset();
        Object tag = new Object();
        userRoute.setTag(tag);
        assertTrue(callback.mOnRouteChangedCalled);
        assertEquals(userRoute, callback.mChangedRoute);
        // Call the callback methods directly so they are marked as tested
        mrc.onRouteChanged(mMediaRouter, callback.mChangedRoute);
        mrsc.onRouteChanged(mMediaRouter, callback.mChangedRoute);

        // Test onRouteVolumeChanged().
        userRoute.setVolumeMax(TEST_MAX_VOLUME);
        callback.reset();
        userRoute.setVolume(TEST_VOLUME);
        assertTrue(callback.mOnRouteVolumeChangedCalled);
        assertEquals(userRoute, callback.mVolumeChangedRoute);
        // Call the callback methods directly so they are marked as tested
        mrc.onRouteVolumeChanged(mMediaRouter, callback.mVolumeChangedRoute);
        mrsc.onRouteVolumeChanged(mMediaRouter, callback.mVolumeChangedRoute);

        // Test onRouteRemoved().
        callback.reset();
        mMediaRouter.removeUserRoute(userRoute);
        assertTrue(callback.mOnRouteRemovedCalled);
        assertEquals(userRoute, callback.mRemovedRoute);
        // Call the callback methods directly so they are marked as tested
        mrc.onRouteRemoved(mMediaRouter, callback.mRemovedRoute);
        mrsc.onRouteRemoved(mMediaRouter, callback.mRemovedRoute);

        // Test onRouteGrouped() and onRouteUngrouped().
        mMediaRouter.clearUserRoutes();
        UserRouteInfo groupableRoute0 = mMediaRouter.createUserRoute(mTestGroupableCategory);
        UserRouteInfo groupableRoute1 = mMediaRouter.createUserRoute(mTestGroupableCategory);

        // Adding a route of groupable category in the media router does not directly add the route.
        // Instead, it creates a RouteGroup, adds the group as a route in the media router, and puts
        // the route inside that group. Therefore onRouteAdded() is called for the group, and
        // onRouteGrouped() is called for the route.
        callback.reset();
        mMediaRouter.addUserRoute(groupableRoute0);

        RouteGroup group = groupableRoute0.getGroup();
        assertTrue(callback.mOnRouteAddedCalled);
        assertEquals(group, callback.mAddedRoute);

        assertTrue(callback.mOnRouteGroupedCalled);
        assertEquals(groupableRoute0, callback.mGroupedRoute);
        assertEquals(group, callback.mGroup);
        assertEquals(0, callback.mRouteIndexInGroup);
        // Call the callback methods directly so they are marked as tested
        mrc.onRouteGrouped(mMediaRouter, callback.mGroupedRoute, callback.mGroup,
                callback.mRouteIndexInGroup);
        mrsc.onRouteGrouped(mMediaRouter, callback.mGroupedRoute, callback.mGroup,
                callback.mRouteIndexInGroup);

        // Add another route to the group.
        callback.reset();
        group.addRoute(groupableRoute1);
        assertTrue(callback.mOnRouteGroupedCalled);
        assertEquals(groupableRoute1, callback.mGroupedRoute);
        assertEquals(1, callback.mRouteIndexInGroup);
        // Call the callback methods directly so they are marked as tested
        mrc.onRouteGrouped(mMediaRouter, callback.mGroupedRoute, callback.mGroup,
                callback.mRouteIndexInGroup);
        mrsc.onRouteGrouped(mMediaRouter, callback.mGroupedRoute, callback.mGroup,
                callback.mRouteIndexInGroup);

        // Since removing a route from the group changes the group's name, onRouteChanged() is
        // called.
        callback.reset();
        group.removeRoute(groupableRoute1);
        assertTrue(callback.mOnRouteUngroupedCalled);
        assertEquals(groupableRoute1, callback.mUngroupedRoute);
        assertTrue(callback.mOnRouteChangedCalled);
        assertEquals(group, callback.mChangedRoute);
        // Call the callback methods directly so they are marked as tested
        mrc.onRouteUngrouped(mMediaRouter, callback.mUngroupedRoute, callback.mGroup);
        mrc.onRouteChanged(mMediaRouter, callback.mChangedRoute);
        mrsc.onRouteUngrouped(mMediaRouter, callback.mUngroupedRoute, callback.mGroup);
        mrsc.onRouteChanged(mMediaRouter, callback.mChangedRoute);

        // When a group has no routes, the group is removed from the media router.
        callback.reset();
        group.removeRoute(0);
        assertTrue(callback.mOnRouteUngroupedCalled);
        assertEquals(groupableRoute0, callback.mUngroupedRoute);
        assertTrue(callback.mOnRouteRemovedCalled);
        assertEquals(group, callback.mRemovedRoute);
        // Call the callback methods directly so they are marked as tested
        mrc.onRouteUngrouped(mMediaRouter, callback.mUngroupedRoute, callback.mGroup);
        mrc.onRouteRemoved(mMediaRouter, callback.mRemovedRoute);
        mrsc.onRouteUngrouped(mMediaRouter, callback.mUngroupedRoute, callback.mGroup);
        mrsc.onRouteRemoved(mMediaRouter, callback.mRemovedRoute);

        // In this case, onRouteChanged() is not called.
        assertFalse(callback.mOnRouteChangedCalled);

        // Try removing the callback.
        mMediaRouter.removeCallback(callback);
        callback.reset();
        mMediaRouter.addUserRoute(groupableRoute0);
        assertFalse(callback.mOnRouteAddedCalled);

        mMediaRouter.selectRoute(prevSelectedRoute.getSupportedTypes(), prevSelectedRoute);
    }

    /**
     * Test {@link MediaRouter#addCallback(int, MediaRouter.Callback, int)}.
     */
    public void testAddCallbackWithFlags() {
        MediaRouterCallback callback = new MediaRouterCallback();
        mMediaRouter.addCallback(MediaRouter.ROUTE_TYPE_USER, callback);

        RouteInfo prevSelectedRoute = mMediaRouter.getSelectedRoute(
                MediaRouter.ROUTE_TYPE_LIVE_AUDIO | MediaRouter.ROUTE_TYPE_LIVE_VIDEO
                | MediaRouter.ROUTE_TYPE_REMOTE_DISPLAY);

        // Currently mCallback is set for the type MediaRouter.ROUTE_TYPE_USER.
        // Changes on prevSelectedRoute will not invoke mCallback since the types do not match.
        callback.reset();
        Object tag0 = new Object();
        prevSelectedRoute.setTag(tag0);
        assertFalse(callback.mOnRouteChangedCalled);

        // Remove mCallback and add it again with flag MediaRouter.CALLBACK_FLAG_UNFILTERED_EVENTS.
        // This flag will make the callback be invoked even when the types do not match.
        mMediaRouter.removeCallback(callback);
        mMediaRouter.addCallback(MediaRouter.ROUTE_TYPE_USER, callback,
                MediaRouter.CALLBACK_FLAG_UNFILTERED_EVENTS);

        callback.reset();
        Object tag1 = new Object();
        prevSelectedRoute.setTag(tag1);
        assertTrue(callback.mOnRouteChangedCalled);
    }

    /**
     * Test {@link MediaRouter.VolumeCallback)}.
     */
    public void testVolumeCallback() {
        UserRouteInfo userRoute = mMediaRouter.createUserRoute(mTestCategory);
        userRoute.setVolumeHandling(RouteInfo.PLAYBACK_VOLUME_VARIABLE);
        MediaRouterVolumeCallback callback = new MediaRouterVolumeCallback();
        MediaRouter.VolumeCallback mrvc = (MediaRouter.VolumeCallback) callback;
        userRoute.setVolumeCallback(callback);

        userRoute.requestSetVolume(TEST_VOLUME);
        assertTrue(callback.mOnVolumeSetRequestCalled);
        assertEquals(userRoute, callback.mRouteInfo);
        assertEquals(TEST_VOLUME, callback.mVolume);
        // Call the callback method directly so it is marked as tested
        mrvc.onVolumeSetRequest(callback.mRouteInfo, callback.mVolume);

        callback.reset();
        userRoute.requestUpdateVolume(TEST_VOLUME_DIRECTION);
        assertTrue(callback.mOnVolumeUpdateRequestCalled);
        assertEquals(userRoute, callback.mRouteInfo);
        assertEquals(TEST_VOLUME_DIRECTION, callback.mDirection);
        // Call the callback method directly so it is marked as tested
        mrvc.onVolumeUpdateRequest(callback.mRouteInfo, callback.mDirection);
    }

    private Bitmap getBitmap(Drawable drawable) {
        int width = drawable.getIntrinsicWidth();
        int height = drawable.getIntrinsicHeight();

        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);

        drawable.setBounds(0, 0, width, height);
        drawable.draw(canvas);

        return result;
    }

    private class MediaRouterVolumeCallback extends MediaRouter.VolumeCallback {
        private boolean mOnVolumeUpdateRequestCalled;
        private boolean mOnVolumeSetRequestCalled;
        private RouteInfo mRouteInfo;
        private int mDirection;
        private int mVolume;

        public void reset() {
            mOnVolumeUpdateRequestCalled = false;
            mOnVolumeSetRequestCalled = false;
            mRouteInfo = null;
            mDirection = 0;
            mVolume = 0;
        }

        @Override
        public void onVolumeUpdateRequest(RouteInfo info, int direction) {
            mOnVolumeUpdateRequestCalled = true;
            mRouteInfo = info;
            mDirection = direction;
        }

        @Override
        public void onVolumeSetRequest(RouteInfo info, int volume) {
            mOnVolumeSetRequestCalled = true;
            mRouteInfo = info;
            mVolume = volume;
        }
    }

    private class MediaRouterCallback extends MediaRouter.SimpleCallback {
        private boolean mOnRouteSelectedCalled;
        private boolean mOnRouteUnselectedCalled;
        private boolean mOnRouteAddedCalled;
        private boolean mOnRouteRemovedCalled;
        private boolean mOnRouteChangedCalled;
        private boolean mOnRouteGroupedCalled;
        private boolean mOnRouteUngroupedCalled;
        private boolean mOnRouteVolumeChangedCalled;

        private RouteInfo mSelectedRoute;
        private RouteInfo mUnselectedRoute;
        private RouteInfo mAddedRoute;
        private RouteInfo mRemovedRoute;
        private RouteInfo mChangedRoute;
        private RouteInfo mGroupedRoute;
        private RouteInfo mUngroupedRoute;
        private RouteInfo mVolumeChangedRoute;
        private RouteGroup mGroup;
        private int mRouteIndexInGroup = -1;

        public void reset() {
            mOnRouteSelectedCalled = false;
            mOnRouteUnselectedCalled = false;
            mOnRouteAddedCalled = false;
            mOnRouteRemovedCalled = false;
            mOnRouteChangedCalled = false;
            mOnRouteGroupedCalled = false;
            mOnRouteUngroupedCalled = false;
            mOnRouteVolumeChangedCalled = false;

            mSelectedRoute = null;
            mUnselectedRoute = null;
            mAddedRoute = null;
            mRemovedRoute = null;
            mChangedRoute = null;
            mGroupedRoute = null;
            mUngroupedRoute = null;
            mVolumeChangedRoute = null;
            mGroup = null;
            mRouteIndexInGroup = -1;
        }

        @Override
        public void onRouteSelected(MediaRouter router, int type, RouteInfo info) {
            mOnRouteSelectedCalled = true;
            mSelectedRoute = info;
        }

        @Override
        public void onRouteUnselected(MediaRouter router, int type, RouteInfo info) {
            mOnRouteUnselectedCalled = true;
            mUnselectedRoute = info;
        }

        @Override
        public void onRouteAdded(MediaRouter router, RouteInfo info) {
            mOnRouteAddedCalled = true;
            mAddedRoute = info;
        }

        @Override
        public void onRouteRemoved(MediaRouter router, RouteInfo info) {
            mOnRouteRemovedCalled = true;
            mRemovedRoute = info;
        }

        @Override
        public void onRouteChanged(MediaRouter router, RouteInfo info) {
            mOnRouteChangedCalled = true;
            mChangedRoute = info;
        }

        @Override
        public void onRouteGrouped(MediaRouter router, RouteInfo info, RouteGroup group,
                int index) {
            mOnRouteGroupedCalled = true;
            mGroupedRoute = info;
            mGroup = group;
            mRouteIndexInGroup = index;
        }

        @Override
        public void onRouteUngrouped(MediaRouter router, RouteInfo info, RouteGroup group) {
            mOnRouteUngroupedCalled = true;
            mUngroupedRoute = info;
            mGroup = group;
        }

        @Override
        public void onRouteVolumeChanged(MediaRouter router, RouteInfo info) {
            mOnRouteVolumeChangedCalled = true;
            mVolumeChangedRoute = info;
        }
    }
}
