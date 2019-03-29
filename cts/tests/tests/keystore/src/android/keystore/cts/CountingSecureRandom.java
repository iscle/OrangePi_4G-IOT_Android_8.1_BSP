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

package android.keystore.cts;

import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@link SecureRandom} which counts how many bytes it has output.
 */
public class CountingSecureRandom extends SecureRandom {

    private final SecureRandom mDelegate = new SecureRandom();
    private final AtomicLong mOutputSizeBytes = new AtomicLong();

    public long getOutputSizeBytes() {
        return mOutputSizeBytes.get();
    }

    public void resetCounters() {
        mOutputSizeBytes.set(0);
    }

    @Override
    public byte[] generateSeed(int numBytes) {
        if (numBytes > 0) {
            mOutputSizeBytes.addAndGet(numBytes);
        }
        return mDelegate.generateSeed(numBytes);
    }

    @Override
    public String getAlgorithm() {
        return mDelegate.getAlgorithm();
    }

    @Override
    public synchronized void nextBytes(byte[] bytes) {
        if ((bytes != null) && (bytes.length > 0)) {
            mOutputSizeBytes.addAndGet(bytes.length);
        }
        mDelegate.nextBytes(bytes);
    }

    @Override
    public synchronized void setSeed(byte[] seed) {
        // Ignore seeding -- not needed in tests and may impact the quality of the output of the
        // delegate SecureRandom by preventing it from self-seeding
    }

    @Override
    public void setSeed(long seed) {
        // Ignore seeding -- not needed in tests and may impact the quality of the output of the
        // delegate SecureRandom by preventing it from self-seeding
    }

    @Override
    public boolean nextBoolean() {
        throw new UnsupportedOperationException();
    }

    @Override
    public double nextDouble() {
        throw new UnsupportedOperationException();
    }

    @Override
    public float nextFloat() {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized double nextGaussian() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int nextInt() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int nextInt(int n) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long nextLong() {
        throw new UnsupportedOperationException();
    }
}
