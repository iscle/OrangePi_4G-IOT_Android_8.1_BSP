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

package android.hardware.camera2.cts.rs;

import android.content.Context;
import android.renderscript.RenderScript;
import android.util.Log;

// TODO : Replace with dependency injection
/**
 * Singleton to hold {@link RenderScript} and {@link AllocationCache} objects.
 *
 * <p>The test method must call {@link #setContext} before attempting to retrieve
 * the underlying objects.</p> *
 */
public class RenderScriptSingleton {

    private static final String TAG = "RenderScriptSingleton";

    private static Context sContext;
    private static RenderScript sRS;
    private static AllocationCache sCache;

    /**
     * Initialize the singletons from the given context; the
     * {@link RenderScript} and {@link AllocationCache} objects are instantiated.
     *
     * @param context a non-{@code null} Context.
     *
     * @throws IllegalStateException If this was called repeatedly without {@link #clearContext}
     */
    public static synchronized void setContext(Context context) {
        if (context.equals(sContext)) {
            return;
        } else if (sContext != null) {
            Log.v(TAG,
                    "Trying to set new context " + context +
                    ", before clearing previous "+ sContext);
            throw new IllegalStateException(
                    "Call #clearContext before trying to set a new context");
        }

        sRS = RenderScript.create(context);
        sContext = context;
        sCache = new AllocationCache(sRS);
    }

    /**
     * Clean up the singletons from the given context; the
     * {@link RenderScript} and {@link AllocationCache} objects are destroyed.
     *
     * <p>Safe to call multiple times; subsequent invocations have no effect.</p>
     */
    public static synchronized void clearContext() {
        if (sContext != null) {
            sCache.close();
            sCache = null;

            sRS.releaseAllContexts();
            sRS = null;
            sContext = null;
        }
    }

    /**
     * Get the current {@link RenderScript} singleton.
     *
     * @return A non-{@code null} {@link RenderScript} object.
     *
     * @throws IllegalStateException if {@link #setContext} was not called prior to this
     */
    public static synchronized RenderScript getRS() {
        if (sRS == null) {
            throw new IllegalStateException("Call #setContext before using #get");
        }

        return sRS;
    }
    /**
     * Get the current {@link AllocationCache} singleton.
     *
     * @return A non-{@code null} {@link AllocationCache} object.
     *
     * @throws IllegalStateException if {@link #setContext} was not called prior to this
     */
    public static synchronized AllocationCache getCache() {
        if (sCache == null) {
            throw new IllegalStateException("Call #setContext before using #getCache");
        }

        return sCache;
    }

    // Suppress default constructor for noninstantiability
    private RenderScriptSingleton() { throw new AssertionError(); }
}
