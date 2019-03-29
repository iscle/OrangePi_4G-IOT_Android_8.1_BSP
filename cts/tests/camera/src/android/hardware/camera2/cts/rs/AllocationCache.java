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

import android.hardware.camera2.cts.helpers.UncheckedCloseable;
import android.renderscript.Allocation;
import android.renderscript.RenderScript;
import android.renderscript.Type;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static android.hardware.camera2.cts.helpers.Preconditions.*;

/**
 * Cache {@link Allocation} objects based on their type and usage.
 *
 * <p>This avoids expensive re-allocation of objects when they are used over and over again
 * by different scripts.</p>
 */
public class AllocationCache implements UncheckedCloseable {

    private static final String TAG = "AllocationCache";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    private static int sDebugHits = 0;
    private static int sDebugMisses = 0;

    private final RenderScript mRS;
    private final HashMap<AllocationKey, List<Allocation>> mAllocationMap =
            new HashMap<AllocationKey, List<Allocation>>();
    private boolean mClosed = false;

    /**
     * Create a new cache with the specified RenderScript context.
     *
     * @param rs A non-{@code null} RenderScript context.
     *
     * @throws NullPointerException if rs was null
     */
    public AllocationCache(RenderScript rs) {
        mRS = checkNotNull("rs", rs);
    }

    /**
     * Returns the {@link RenderScript} context associated with this AllocationCache.
     *
     * @return A non-{@code null} RenderScript value.
     */
    public RenderScript getRenderScript() {
        return mRS;
    }

    /**
     * Try to lookup a compatible Allocation from the cache, create one if none exist.
     *
     * @param type A non-{@code null} RenderScript Type.
     * @throws NullPointerException if type was null
     * @throws IllegalStateException if the cache was closed with {@link #close}
     */
    public Allocation getOrCreateTyped(Type type, int usage) {
        synchronized (this) {
          checkNotNull("type", type);
          checkNotClosed();

          AllocationKey key = new AllocationKey(type, usage);
          List<Allocation> list = mAllocationMap.get(key);

          if (list != null && !list.isEmpty()) {
              Allocation alloc = list.remove(list.size() - 1);
              if (DEBUG) {
                  sDebugHits++;
                  Log.d(TAG, String.format(
                      "Cache HIT (%d): type = '%s', usage = '%x'", sDebugHits, type, usage));
              }
              return alloc;
          }
          if (DEBUG) {
              sDebugMisses++;
              Log.d(TAG, String.format(
                  "Cache MISS (%d): type = '%s', usage = '%x'", sDebugMisses, type, usage));
          }
        }
        return Allocation.createTyped(mRS, type, usage);
    }

    /**
     * Return the Allocation to the cache.
     *
     * <p>Future calls to getOrCreateTyped with the same type and usage may
     * return this allocation.</p>
     *
     * <p>Allocations that have usage {@link Allocation#USAGE_IO_INPUT} get their
     * listeners reset. Those that have {@link Allocation#USAGE_IO_OUTPUT} get their
     * surfaces reset.</p>
     *
     * @param allocation A non-{@code null} RenderScript {@link Allocation}
     * @throws NullPointerException if allocation was null
     * @throws IllegalArgumentException if the allocation was already returned previously
     * @throws IllegalStateException if the cache was closed with {@link #close}
     */
    public synchronized void returnToCache(Allocation allocation) {
        checkNotNull("allocation", allocation);
        checkNotClosed();

        int usage = allocation.getUsage();
        AllocationKey key = new AllocationKey(allocation.getType(), usage);
        List<Allocation> value = mAllocationMap.get(key);

        if (value != null && value.contains(allocation)) {
            throw new IllegalArgumentException("allocation was already returned to the cache");
        }

        if ((usage & Allocation.USAGE_IO_INPUT) != 0) {
            allocation.setOnBufferAvailableListener(null);
        }
        if ((usage & Allocation.USAGE_IO_OUTPUT) != 0) {
            allocation.setSurface(null);
        }

        if (value == null) {
            value = new ArrayList<Allocation>(/*capacity*/1);
            mAllocationMap.put(key, value);
        }

        value.add(allocation);

        // TODO: Evict existing allocations from cache when we get too many items in it,
        // to avoid running out of memory

        // TODO: move to using android.util.LruCache under the hood
    }

    /**
     * Return the allocation to the cache, if it wasn't {@code null}.
     *
     * <p>Future calls to getOrCreateTyped with the same type and usage may
     * return this allocation.</p>
     *
     * <p>Allocations that have usage {@link Allocation#USAGE_IO_INPUT} get their
     * listeners reset. Those that have {@link Allocation#USAGE_IO_OUTPUT} get their
     * surfaces reset.</p>
     *
     * <p>{@code null} values are a no-op.</p>
     *
     * @param allocation A potentially {@code null} RenderScript {@link Allocation}
     * @throws IllegalArgumentException if the allocation was already returned previously
     * @throws IllegalStateException if the cache was closed with {@link #close}
     */
    public synchronized void returnToCacheIfNotNull(Allocation allocation) {
        if (allocation != null) {
            returnToCache(allocation);
        }
    }

    /**
     * Closes the object and destroys any Allocations still in the cache.
     */
    @Override
    public synchronized void close() {
        if (mClosed) return;

        for (Map.Entry<AllocationKey, List<Allocation>> entry : mAllocationMap.entrySet()) {
            List<Allocation> value = entry.getValue();

            for (Allocation alloc : value) {
                alloc.destroy();
            }

            value.clear();
        }

        mAllocationMap.clear();
        mClosed = true;
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }

    /**
     * Holder class to check if one allocation is compatible with another.
     *
     * <p>An Allocation is considered compatible if both it's Type and usage is equivalent.</p>
     */
    private static class AllocationKey {
        private final Type mType;
        private final int mUsage;

        public AllocationKey(Type type, int usage) {
            mType = type;
            mUsage = usage;
        }

        @Override
        public int hashCode() {
            return mType.hashCode() ^ mUsage;
        }

        @Override
        public boolean equals(Object other) {
            if (other instanceof AllocationKey){
                AllocationKey otherKey = (AllocationKey) other;

                return otherKey.mType.equals(mType) && otherKey.mUsage == otherKey.mUsage;
            }

            return false;
        }
    }

    private void checkNotClosed() {
        if (mClosed == true) {
            throw new IllegalStateException("AllocationCache has already been closed");
        }
    }
}
