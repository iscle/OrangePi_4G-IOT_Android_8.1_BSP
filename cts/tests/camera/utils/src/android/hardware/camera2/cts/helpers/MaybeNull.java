/*
 * Copyright 2014 The Android Open Source Project
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

package android.hardware.camera2.cts.helpers;

import java.io.Closeable;
import java.io.IOException;

/**
 * Helper set of methods for dealing with objects that are sometimes {@code null}.
 *
 * <p>Used to remove common patterns like: <pre>{@code
 * if (obj != null) {
 *     obj.doSomething();
 * }</pre>
 *
 * If this is common, consider adding {@code doSomething} to this class so that the code
 * looks more like <pre>{@code
 * MaybeNull.doSomething(obj);
 * }</pre>
 */
public class MaybeNull {
    /**
     * Close the underlying {@link AutoCloseable}, if it's not {@code null}.
     *
     * @param closeable An object which implements {@link AutoCloseable}.
     * @throws Exception If {@link AutoCloseable#close} fails.
     */
    public static <T extends AutoCloseable> void close(T closeable) throws Exception {
        if (closeable != null) {
            closeable.close();
        }
    }

    /**
     * Close the underlying {@link UncheckedCloseable}, if it's not {@code null}.
     *
     * <p>No checked exceptions are thrown. An unknown runtime exception might still
     * be raised.</p>
     *
     * @param closeable An object which implements {@link UncheckedCloseable}.
     */
    public static <T extends UncheckedCloseable> void close(T closeable) {
        if (closeable != null) {
            closeable.close();
        }
    }

    /**
     * Close the underlying {@link Closeable}, if it's not {@code null}.
     *
     * @param closeable An object which implements {@link Closeable}.
     * @throws Exception If {@link Closeable#close} fails.
     */
    public static <T extends Closeable> void close(T closeable) throws IOException {
        if (closeable != null) {
            closeable.close();
        }
    }

    // Suppress default constructor for noninstantiability
    private MaybeNull() { throw new AssertionError(); }
}
