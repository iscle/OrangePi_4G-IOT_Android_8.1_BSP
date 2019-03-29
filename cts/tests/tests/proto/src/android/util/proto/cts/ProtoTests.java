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

package android.util.proto.cts;

import junit.framework.TestSuite;

public class ProtoTests {
    public static TestSuite suite() {
        TestSuite suite = new TestSuite(ProtoTests.class.getName());

        suite.addTestSuite(EncodedBufferTest.class);
        suite.addTestSuite(ProtoOutputStreamTagTest.class);
        suite.addTestSuite(ProtoOutputStreamDoubleTest.class);
        suite.addTestSuite(ProtoOutputStreamFloatTest.class);
        suite.addTestSuite(ProtoOutputStreamInt32Test.class);
        suite.addTestSuite(ProtoOutputStreamInt64Test.class);
        suite.addTestSuite(ProtoOutputStreamUInt32Test.class);
        suite.addTestSuite(ProtoOutputStreamUInt64Test.class);
        suite.addTestSuite(ProtoOutputStreamSInt32Test.class);
        suite.addTestSuite(ProtoOutputStreamSInt64Test.class);
        suite.addTestSuite(ProtoOutputStreamFixed32Test.class);
        suite.addTestSuite(ProtoOutputStreamFixed64Test.class);
        suite.addTestSuite(ProtoOutputStreamSFixed32Test.class);
        suite.addTestSuite(ProtoOutputStreamSFixed64Test.class);
        suite.addTestSuite(ProtoOutputStreamBoolTest.class);
        suite.addTestSuite(ProtoOutputStreamStringTest.class);
        suite.addTestSuite(ProtoOutputStreamBytesTest.class);
        suite.addTestSuite(ProtoOutputStreamEnumTest.class);
        suite.addTestSuite(ProtoOutputStreamObjectTest.class);

        return suite;
    }
}
