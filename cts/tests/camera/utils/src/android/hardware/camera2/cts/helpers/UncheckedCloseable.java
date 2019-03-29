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

/**
 * Defines an interface for classes that can (or need to) be closed once they
 * are not used any longer; calling the {@code close} method releases resources
 * that the object holds.</p>
 *
 * <p>This signifies that the implementor will never throw checked exceptions when closing,
 * allowing for more fine grained exception handling at call sites handling this interface
 * generically.</p>
 *
 * <p>A common pattern for using an {@code UncheckedCloseable} resource:
 * <pre>   {@code
 *   // where <Foo extends UncheckedCloseable>
 *   UncheckedCloseable foo = new Foo();
 *   try {
 *      ...;
 *   } finally {
 *      foo.close();
 *   }
 * }</pre>
 */
public interface UncheckedCloseable extends AutoCloseable {

    /**
     * Closes the object and release any system resources it holds.
     *
     * <p>Does not throw any checked exceptions.</p>
     */
    @Override
    void close();
}
