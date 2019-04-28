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

package com.android.car.obd2.test;

import com.android.car.obd2.IntegerArrayStream;
import com.android.car.obd2.Obd2Connection;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MockObd2UnderlyingTransport implements Obd2Connection.UnderlyingTransport {
    private class MockInputStream extends InputStream {
        private final IntegerArrayStream mStream;

        MockInputStream(int... data) {
            mStream = new IntegerArrayStream(data);
        }

        @Override
        public int read() throws IOException {
            if (mStream.hasAtLeast(1)) return mStream.consume();
            else throw new EOFException();
        }
    }

    private class MockOutputStream extends OutputStream {
        private final IntegerArrayStream mStream;

        MockOutputStream(int... data) {
            mStream = new IntegerArrayStream(data);
        }

        @Override
        public void write(int b) throws IOException {
            if (mStream.hasAtLeast(1)) {
                int expected = mStream.consume();
                if (expected != b) {
                    throw new IOException("data mismatch. expected: " + expected + ", seen: " + b);
                }
            } else {
                throw new IOException("data write past expectation.");
            }
        }
    }

    private final MockInputStream mMockInput;
    private final MockOutputStream mMockOutput;

    public MockObd2UnderlyingTransport(int[] requestData, int[] responseData) {
        mMockInput = new MockInputStream(responseData);
        mMockOutput = new MockOutputStream(requestData);
    }

    @Override
    public String getAddress() {
        return MockObd2UnderlyingTransport.class.getSimpleName();
    }

    @Override
    public boolean reconnect() {
        return true;
    }

    @Override
    public boolean isConnected() { return true; }

    @Override
    public InputStream getInputStream() {
        return mMockInput;
    }

    @Override
    public OutputStream getOutputStream() {
        return mMockOutput;
    }
}
