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

package android.media.cts;

import android.content.Context;
import android.media.RemoteController;
import android.media.RemoteController.OnClientUpdateListener;
import android.test.InstrumentationTestCase;
import android.test.UiThreadTest;
import android.view.KeyEvent;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tests for {@link RemoteController}.
 */
public class RemoteControllerTest extends InstrumentationTestCase {

    private static final Set<Integer> MEDIA_KEY_EVENT = new HashSet<Integer>();
    static {
        MEDIA_KEY_EVENT.add(KeyEvent.KEYCODE_MEDIA_PLAY);
        MEDIA_KEY_EVENT.add(KeyEvent.KEYCODE_MEDIA_PAUSE);
        MEDIA_KEY_EVENT.add(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        MEDIA_KEY_EVENT.add(KeyEvent.KEYCODE_MUTE);
        MEDIA_KEY_EVENT.add(KeyEvent.KEYCODE_HEADSETHOOK);
        MEDIA_KEY_EVENT.add(KeyEvent.KEYCODE_MEDIA_STOP);
        MEDIA_KEY_EVENT.add(KeyEvent.KEYCODE_MEDIA_NEXT);
        MEDIA_KEY_EVENT.add(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        MEDIA_KEY_EVENT.add(KeyEvent.KEYCODE_MEDIA_REWIND);
        MEDIA_KEY_EVENT.add(KeyEvent.KEYCODE_MEDIA_RECORD);
        MEDIA_KEY_EVENT.add(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD);
    }

    static OnClientUpdateListener listener = new OnClientUpdateListener() {
            @Override
            public void onClientChange(boolean clearing) {}
            @Override
            public void onClientPlaybackStateUpdate(int state) {}
            @Override
            public void onClientPlaybackStateUpdate(
                int state, long stateChangeTimeMs, long currentPosMs, float speed) {}
            @Override
            public void onClientTransportControlUpdate(int transportControlFlags) {}
            @Override
            public void onClientMetadataUpdate(RemoteController.MetadataEditor metadataEditor) {}
        };

    private Context mContext;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getInstrumentation().getTargetContext();
    }

    private RemoteController createRemoteController() {
        return new RemoteController(mContext, listener);
    }

    @UiThreadTest
    public void testGetEstimatedMediaPosition() {
        assertTrue(createRemoteController().getEstimatedMediaPosition() < 0);
    }

    @UiThreadTest
    public void testSendMediaKeyEvent() {
        RemoteController remoteController = createRemoteController();
        for (Integer mediaKeyEvent : MEDIA_KEY_EVENT) {
            assertFalse(remoteController.sendMediaKeyEvent(
                  new KeyEvent(KeyEvent.ACTION_DOWN, mediaKeyEvent)));
        }
    }

    @UiThreadTest
    public void testSeekTo_negativeValues() {
        try {
            createRemoteController().seekTo(-1);
            fail("timeMs must be >= 0");
        } catch (IllegalArgumentException expected) {}
    }

    @UiThreadTest
    public void testSeekTo() {
        assertTrue(createRemoteController().seekTo(0));
    }

    @UiThreadTest
    public void testSetArtworkConfiguration() {
        assertTrue(createRemoteController().setArtworkConfiguration(1, 1));
    }

    @UiThreadTest
    public void testClearArtworkConfiguration() {
        assertTrue(createRemoteController().clearArtworkConfiguration());
    }

    @UiThreadTest
    public void testSetSynchronizationMode_unregisteredRemoteController() {
        RemoteController remoteController = createRemoteController();
        assertFalse(remoteController.setSynchronizationMode(
                RemoteController.POSITION_SYNCHRONIZATION_NONE));
        assertFalse(remoteController.setSynchronizationMode(
                RemoteController.POSITION_SYNCHRONIZATION_CHECK));
    }

    @UiThreadTest
    public void testEditMetadata() {
        assertNotNull(createRemoteController().editMetadata());
    }

    @UiThreadTest
    public void testOnClientUpdateListenerUnchanged() throws Exception {
        Map<String, List<Method>> methodMap = new HashMap<String, List<Method>>();
        for (Method method : listener.getClass().getDeclaredMethods()) {
          if (!methodMap.containsKey(method.getName())) {
              methodMap.put(method.getName(), new ArrayList<Method>());
          }
          methodMap.get(method.getName()).add(method);
        }

        for (Method method : OnClientUpdateListener.class.getDeclaredMethods()) {
            assertTrue("Method not found: " + method.getName(),
                    methodMap.containsKey(method.getName()));
            List<Method> implementedMethodList = methodMap.get(method.getName());
            assertTrue("Method signature changed: " + method,
                    matchMethod(method, implementedMethodList));
        }
    }

    private static boolean matchMethod(Method method, List<Method> potentialMatches) {
        for (Method potentialMatch : potentialMatches) {
            if (method.getName().equals(potentialMatch.getName()) &&
                    method.getReturnType().equals(potentialMatch.getReturnType()) &&
                            Arrays.equals(method.getTypeParameters(),
                                    potentialMatch.getTypeParameters())) {
                return true;
            }
        }
        return false;
    }
}
