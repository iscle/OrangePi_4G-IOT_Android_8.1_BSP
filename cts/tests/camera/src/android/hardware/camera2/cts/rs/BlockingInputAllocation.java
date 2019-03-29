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

import static android.hardware.camera2.cts.helpers.Preconditions.*;

import android.hardware.camera2.cts.helpers.UncheckedCloseable;
import android.renderscript.Allocation;
import android.util.Log;

import com.android.ex.camera2.exceptions.TimeoutRuntimeException;

/**
 * An {@link Allocation} wrapper that can be used to block until new buffers are available.
 *
 * <p>Can only be used only with {@link Allocation#USAGE_IO_INPUT} usage Allocations.</p>
 *
 * <p>When used with a {@link android.hardware.camera2.CameraDevice CameraDevice} this
 * must be used as an output surface.</p>
 */
class BlockingInputAllocation implements UncheckedCloseable {

    private static final String TAG = BlockingInputAllocation.class.getSimpleName();
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);

    private final Allocation mAllocation;
    private final OnBufferAvailableListener mListener;
    private boolean mClosed;

    /**
     * Wrap an existing Allocation with this {@link BlockingInputAllocation}.
     *
     * <p>Doing this will clear any existing associated buffer listeners and replace
     * it with a new one.</p>
     *
     * @param allocation A non-{@code null} {@link Allocation allocation}
     * @return a new {@link BlockingInputAllocation} instance
     *
     * @throws NullPointerException
     *           If {@code allocation} was {@code null}
     * @throws IllegalArgumentException
     *           If {@code allocation}'s usage did not have one of USAGE_IO_INPUT or USAGE_IO_OUTPUT
     * @throws IllegalStateException
     *           If this object has already been {@link #close closed}
     */
    public static BlockingInputAllocation wrap(Allocation allocation) {
        checkNotNull("allocation", allocation);
        checkBitFlags("usage", allocation.getUsage(), "USAGE_IO_INPUT", Allocation.USAGE_IO_INPUT);

        return new BlockingInputAllocation(allocation);
    }

    /**
     * Get the Allocation backing this {@link BlockingInputAllocation}.
     *
     * @return Allocation instance (non-{@code null}).
     *
     * @throws IllegalStateException If this object has already been {@link #close closed}
     */
    public Allocation getAllocation() {
        checkNotClosed();

        return mAllocation;
    }

    /**
     * Waits for a buffer to become available, then immediately
     * {@link Allocation#ioReceive receives} it.
     *
     * <p>After calling this, the next script used with this allocation will use the
     * newer buffer.</p>
     *
     * @throws TimeoutRuntimeException If waiting for the buffer has timed out.
     * @throws IllegalStateException If this object has already been {@link #close closed}
     */
    public synchronized void waitForBufferAndReceive() {
        checkNotClosed();

        if (VERBOSE) Log.v(TAG, "waitForBufferAndReceive - begin");

        mListener.waitForBuffer();
        mAllocation.ioReceive();

        if (VERBOSE) Log.v(TAG, "waitForBufferAndReceive - Allocation#ioReceive");
    }

    /**
     * If there are multiple pending buffers, {@link Allocation#ioReceive receive} the latest one.
     *
     * <p>Does not block if there are no currently pending buffers.</p>
     *
     * @return {@code true} only if any buffers were received.
     *
     * @throws IllegalStateException If this object has already been {@link #close closed}
     */
    public synchronized boolean receiveLatestAvailableBuffers() {
        checkNotClosed();

        int updatedBuffers = 0;
        while (mListener.isBufferPending()) {
            mListener.waitForBuffer();
            mAllocation.ioReceive();
            updatedBuffers++;
        }

        if (VERBOSE) Log.v(TAG, "receiveLatestAvailableBuffers - updated = " + updatedBuffers);

        return updatedBuffers > 0;
    }

    /**
     * Closes the object and detaches the listener from the {@link Allocation}.
     *
     * <p>This has a side effect of calling {@link #receiveLatestAvailableBuffers}
     *
     * <p>Does <i>not</i> destroy the underlying {@link Allocation}.</p>
     */
    @Override
    public synchronized void close() {
        if (mClosed) return;

        receiveLatestAvailableBuffers();
        mAllocation.setOnBufferAvailableListener(/*callback*/null);
        mClosed = true;
    }

    protected void checkNotClosed() {
        if (mClosed) {
            throw new IllegalStateException(TAG + " has been closed");
        }
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }

    private BlockingInputAllocation(Allocation allocation) {
        mAllocation = allocation;

        mListener = new OnBufferAvailableListener();
        mAllocation.setOnBufferAvailableListener(mListener);
    }

    // TODO: refactor with the ImageReader Listener code to use a LinkedBlockingQueue
    private static class OnBufferAvailableListener implements Allocation.OnBufferAvailableListener {
        private int mPendingBuffers = 0;
        private final Object mBufferSyncObject = new Object();
        private static final int TIMEOUT_MS = 20000;

        public boolean isBufferPending() {
            synchronized (mBufferSyncObject) {
                return (mPendingBuffers > 0);
            }
        }

        /**
         * Waits for a buffer. Caller must call ioReceive exactly once after calling this.
         *
         * @throws TimeoutRuntimeException If waiting for the buffer has timed out.
         */
        public void waitForBuffer() {
            synchronized (mBufferSyncObject) {
                while (mPendingBuffers == 0) {
                    try {
                        if (VERBOSE) Log.v(TAG, "waiting for next buffer");
                        mBufferSyncObject.wait(TIMEOUT_MS);
                        if (mPendingBuffers == 0) {
                            throw new TimeoutRuntimeException("wait for buffer image timed out");
                        }
                    } catch (InterruptedException ie) {
                        throw new AssertionError(ie);
                    }
                }
                mPendingBuffers--;
            }
        }

        @Override
        public void onBufferAvailable(Allocation a) {
            if (VERBOSE) Log.v(TAG, "new buffer in allocation available");
            synchronized (mBufferSyncObject) {
                mPendingBuffers++;
                mBufferSyncObject.notifyAll();
            }
        }
    }
}
