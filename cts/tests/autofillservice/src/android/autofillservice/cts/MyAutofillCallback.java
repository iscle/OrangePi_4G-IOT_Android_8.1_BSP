/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.autofillservice.cts;

import static android.autofillservice.cts.Helper.CONNECTION_TIMEOUT_MS;

import static com.google.common.truth.Truth.assertWithMessage;

import android.view.View;
import android.view.autofill.AutofillManager.AutofillCallback;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Custom {@link AutofillCallback} used to recover events during tests.
 */
final class MyAutofillCallback extends AutofillCallback {

    private final BlockingQueue<MyEvent> mEvents = new LinkedBlockingQueue<>();

    @Override
    public void onAutofillEvent(View view, int event) {
        mEvents.offer(new MyEvent(view, event));
    }

    @Override
    public void onAutofillEvent(View view, int childId, int event) {
        mEvents.offer(new MyEvent(view, childId, event));
    }

    /**
     * Gets the next available event or fail if it times out.
     */
    MyEvent getEvent() throws InterruptedException {
        final MyEvent event = mEvents.poll(CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        if (event == null) {
            throw new RetryableException("no event in %d ms", CONNECTION_TIMEOUT_MS);
        }
        return event;
    }

    /**
     * Used to assert there is no event left behind.
     */
    void assertNumberUnhandledEvents(int expected) {
        assertWithMessage("Invalid number of events left").that(mEvents.size()).isEqualTo(expected);
    }

    /**
     * Convenience method to assert an UI shown event for the given view was received.
     */
    MyEvent assertUiShownEvent(View expectedView) throws InterruptedException {
        final MyEvent event = getEvent();
        assertWithMessage("Invalid type on event %s", event).that(event.event)
                .isEqualTo(EVENT_INPUT_SHOWN);
        assertWithMessage("Invalid view on event %s", event).that(event.view)
            .isSameAs(expectedView);
        return event;
    }

    /**
     * Convenience method to assert an UI shown event for the given virtual view was received.
     */
    void assertUiShownEvent(View expectedView, int expectedChildId) throws InterruptedException {
        final MyEvent event = assertUiShownEvent(expectedView);
        assertWithMessage("Invalid child on event %s", event).that(event.childId)
            .isEqualTo(expectedChildId);
    }

    /**
     * Convenience method to assert an UI shown event a virtual view was received.
     *
     * @return virtual child id
     */
    int assertUiShownEventForVirtualChild(View expectedView) throws InterruptedException {
        final MyEvent event = assertUiShownEvent(expectedView);
        return event.childId;
    }

    /**
     * Convenience method to assert an UI hidden event for the given view was received.
     */
    MyEvent assertUiHiddenEvent(View expectedView) throws InterruptedException {
        final MyEvent event = getEvent();
        assertWithMessage("Invalid type on event %s", event).that(event.event)
                .isEqualTo(EVENT_INPUT_HIDDEN);
        assertWithMessage("Invalid view on event %s", event).that(event.view)
                .isSameAs(expectedView);
        return event;
    }

    /**
     * Convenience method to assert an UI hidden event for the given view was received.
     */
    void assertUiHiddenEvent(View expectedView, int expectedChildId) throws InterruptedException {
        final MyEvent event = assertUiHiddenEvent(expectedView);
        assertWithMessage("Invalid child on event %s", event).that(event.childId)
                .isEqualTo(expectedChildId);
    }

    /**
     * Convenience method to assert an UI unavailable event for the given view was received.
     */
    MyEvent assertUiUnavailableEvent(View expectedView) throws InterruptedException {
        final MyEvent event = getEvent();
        assertWithMessage("Invalid type on event %s", event).that(event.event)
                .isEqualTo(EVENT_INPUT_UNAVAILABLE);
        assertWithMessage("Invalid view on event %s", event).that(event.view)
                .isSameAs(expectedView);
        return event;
    }

    /**
     * Convenience method to assert an UI unavailable event for the given view was received.
     */
    void assertUiUnavailableEvent(View expectedView, int expectedChildId)
            throws InterruptedException {
        final MyEvent event = assertUiUnavailableEvent(expectedView);
        assertWithMessage("Invalid child on event %s", event).that(event.childId)
                .isEqualTo(expectedChildId);
    }

    private static final class MyEvent {
        public final View view;
        public final int childId;
        public final int event;

        MyEvent(View view, int event) {
            this(view, View.NO_ID, event);
        }

        MyEvent(View view, int childId, int event) {
            this.view = view;
            this.childId = childId;
            this.event = event;
        }

        @Override
        public String toString() {
            return event + ": " + view + " (childId: " + childId + ")";
        }
    }
}
