/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package android.jvmti.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

import dalvik.system.InMemoryDexClassLoader;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.junit.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import art.Main;

/**
 * Check redefineClasses-related functionality.
 */
public class JvmtiRedefineClassesTest extends JvmtiTestBase {

    @Before
    public void setUp() throws Exception {
        // make sure everything is cleared.
        setTransformationEvent(false);
        setPopTransformations(true);
        clearTransformations();
        // Make sure Transform.class is in the initial state.
        checkRedefinition(INITIAL_TRANSFORM);
    }

    static class RedefineError {
        public int expectedError;
        public Class<?> target;
        public byte[] dexData;

        public RedefineError(int err, Class<?> target, String base64string) {
            this(err, target, Base64.getDecoder().decode(base64string));
        }

        public RedefineError(int err, Class<?> klass, byte[] dex) {
            this.expectedError = err;
            this.target = klass;
            this.dexData = dex;
        }
    }

    // Just an interface.
    interface Consumer<T> {
        public void accept(T data);
    }

    static class StringCollector implements Consumer<String> {
        public ArrayList<String> reports = new ArrayList<>();

        public void accept(String data) {
            reports.add(data);
        }
    }

    /**
     * Try to redefine a class and assert that the redefinition matches whats expected.
     */
    private static void checkRedefinition(RedefineError err) {
        assertEquals(err.expectedError, redefineClass(err.target, err.dexData));
    }

    // This is a class that we will transform for tests.
    // NB This has the actual name Landroid/jvmti/cts/JvmtiRedefineClassesTest$Transform;
    static class Transform {
        // NB This field has type Landroid/jvmti/cts/JvmtiRedefineClassesTest$Consumer;
        private Consumer<String> reporter;

        public Transform(Consumer<String> reporter) {
            this.reporter = reporter;
        }

        private void Start() {
            reporter.accept("hello - private");
        }

        private void Finish() {
            reporter.accept("goodbye - private");
        }

        public void sayHi(Runnable r) {
            reporter.accept("Pre Start private method call");
            Start();
            reporter.accept("Post Start private method call");
            r.run();
            reporter.accept("Pre Finish private method call");
            Finish();
            reporter.accept("Post Finish private method call");
        }
    }

    /**
     * Base64 encoded dex file for the initial version of Transform class.
     */
    private static final RedefineError INITIAL_TRANSFORM = new RedefineError(
            JvmtiErrors.NONE, Transform.class,
            "ZGV4CjAzNQA+L+iHAAAAAAAAAAAAAAAAAAAAAAAAAACgBgAAcAAAAHhWNBIAAAAAAAAAANwFAAAi" +
            "AAAAcAAAAAkAAAD4AAAABAAAABwBAAABAAAATAEAAAcAAABUAQAAAQAAAIwBAAD0BAAArAEAAKwB" +
            "AACvAQAAsgEAALoBAAC+AQAAxAEAAMwBAADrAQAAIQIAAFgCAACQAgAAvgIAAOICAAACAwAAIQMA" +
            "ADUDAABLAwAAXwMAAIADAACgAwAAwAMAAN8DAADmAwAA8QMAAPQDAAD4AwAAAAQAAA0EAAAgBAAA" +
            "MQQAADcEAABBBAAARgQAAE0EAAAIAAAACQAAAAoAAAALAAAADAAAAA0AAAAOAAAADwAAABcAAAAX" +
            "AAAACAAAAAAAAAAYAAAACAAAAFQEAAAYAAAACAAAAFwEAAAYAAAACAAAAGQEAAABAAAAHgAAAAAA" +
            "AgAZAAAAAQABAAIAAAABAAAABQAAAAEAAAAVAAAAAQADACAAAAAGAAAAAgAAAAcAAAAfAAAAAQAA" +
            "AAAAAAAGAAAAAAAAAAYAAAC8BQAAWAUAAAAAAAABKAABPAAGPGluaXQ+AAI+OwAEPjspVgAGRmlu" +
            "aXNoAB1Kdm10aVJlZGVmaW5lQ2xhc3Nlc1Rlc3QuamF2YQA0TGFuZHJvaWQvanZtdGkvY3RzL0p2" +
            "bXRpUmVkZWZpbmVDbGFzc2VzVGVzdCRDb25zdW1lcgA1TGFuZHJvaWQvanZtdGkvY3RzL0p2bXRp" +
            "UmVkZWZpbmVDbGFzc2VzVGVzdCRDb25zdW1lcjsANkxhbmRyb2lkL2p2bXRpL2N0cy9Kdm10aVJl" +
            "ZGVmaW5lQ2xhc3Nlc1Rlc3QkVHJhbnNmb3JtOwAsTGFuZHJvaWQvanZtdGkvY3RzL0p2bXRpUmVk" +
            "ZWZpbmVDbGFzc2VzVGVzdDsAIkxkYWx2aWsvYW5ub3RhdGlvbi9FbmNsb3NpbmdDbGFzczsAHkxk" +
            "YWx2aWsvYW5ub3RhdGlvbi9Jbm5lckNsYXNzOwAdTGRhbHZpay9hbm5vdGF0aW9uL1NpZ25hdHVy" +
            "ZTsAEkxqYXZhL2xhbmcvT2JqZWN0OwAUTGphdmEvbGFuZy9SdW5uYWJsZTsAEkxqYXZhL2xhbmcv" +
            "U3RyaW5nOwAfUG9zdCBGaW5pc2ggcHJpdmF0ZSBtZXRob2QgY2FsbAAeUG9zdCBTdGFydCBwcml2" +
            "YXRlIG1ldGhvZCBjYWxsAB5QcmUgRmluaXNoIHByaXZhdGUgbWV0aG9kIGNhbGwAHVByZSBTdGFy" +
            "dCBwcml2YXRlIG1ldGhvZCBjYWxsAAVTdGFydAAJVHJhbnNmb3JtAAFWAAJWTAAGYWNjZXB0AAth" +
            "Y2Nlc3NGbGFncwARZ29vZGJ5ZSAtIHByaXZhdGUAD2hlbGxvIC0gcHJpdmF0ZQAEbmFtZQAIcmVw" +
            "b3J0ZXIAA3J1bgAFc2F5SGkABXZhbHVlAAEAAAAAAAAAAQAAAAYAAAABAAAABwAAAAwBAAcOPC0A" +
            "FQAHDocAEQAHDocAGQEABw6HPIc8hzyHAAAAAAIAAgABAAAAbAQAAAYAAABwEAUAAABbAQAADgAD" +
            "AAEAAgAAAHQEAAAJAAAAVCAAABsBGwAAAHIgAAAQAA4AAAADAAEAAgAAAHoEAAAJAAAAVCAAABsB" +
            "HAAAAHIgAAAQAA4AAAAEAAIAAgAAAIAEAAAqAAAAVCAAABsBFAAAAHIgAAAQAHAQAwACAFQgAAAb" +
            "ARIAAAByIAAAEAByEAYAAwBUIAAAGwETAAAAciAAABAAcBACAAIAVCAAABsBEQAAAHIgAAAQAA4A" +
            "AAEDAQACAYGABJAJAQKsCQEC0AkEAfQJAgMBIRgCAgQCGgQIHRcWAgUBIRwEFwcXARcQFwMCBQEh" +
            "HAUXABcHFwEXEBcEAAAAAgAAAHAFAAB2BQAAAQAAAH8FAAABAAAAjQUAAKAFAAABAAAAAQAAAAAA" +
            "AAAAAAAArAUAAAEAAAC0BQAAEAAAAAAAAAABAAAAAAAAAAEAAAAiAAAAcAAAAAIAAAAJAAAA+AAA" +
            "AAMAAAAEAAAAHAEAAAQAAAABAAAATAEAAAUAAAAHAAAAVAEAAAYAAAABAAAAjAEAAAIgAAAiAAAA" +
            "rAEAAAEQAAADAAAAVAQAAAMgAAAEAAAAbAQAAAEgAAAEAAAAkAQAAAAgAAABAAAAWAUAAAQgAAAE" +
            "AAAAcAUAAAMQAAADAAAAoAUAAAYgAAABAAAAvAUAAAAQAAABAAAA3AUAAA==");

    /**
     * Base64 encoded dex file containing the following inner class.
     * <code>
     * // NB This has the actual name Landroid/jvmti/cts/JvmtiRedefineClassesTest$Transform;
     * static class Transform {
     *     // NB This field has type Landroid/jvmti/cts/JvmtiRedefineClassesTest$Consumer;
     *     private Consumer<String> reporter;
     *     public Transform(Consumer<String> reporter) {
     *         this.reporter = reporter;
     *     }
     *     private void Start() {
     *         reporter.accept("TRANSFORMED - Hello - private");
     *     }
     *     private void Finish() {
     *         reporter.accept("TRANSFORMED - Goodbye - private");
     *     }
     *     public void sayHi(Runnable r) {
     *         reporter.accept("TRANSFORMED - pre Start private method call");
     *         Start();
     *         reporter.accept("TRANSFORMED - post Start private method call");
     *         r.run();
     *         reporter.accept("TRANSFORMED - pre Finish private method call");
     *         Finish();
     *         reporter.accept("TRANSFORMED - post Finish private method call");
     *     }
     * }
     * </code>
     */
    private static final RedefineError GOOD_TRANSFORM = new RedefineError(
            JvmtiErrors.NONE, Transform.class,
            "ZGV4CjAzNQBmR3TRAAAAAAAAAAAAAAAAAAAAAAAAAAD0BgAAcAAAAHhWNBIAAAAAAAAAADAGAAAi" +
            "AAAAcAAAAAkAAAD4AAAABAAAABwBAAABAAAATAEAAAcAAABUAQAAAQAAAIwBAABIBQAArAEAAKwB" +
            "AACvAQAAsgEAALoBAAC+AQAAxAEAAMwBAADrAQAAIQIAAFgCAACQAgAAvgIAAOICAAACAwAAIQMA" +
            "ADUDAABLAwAAXwMAAGYDAACHAwAApgMAANUDAAADBAAAMQQAAF4EAABpBAAAbAQAAHAEAAB4BAAA" +
            "hQQAAIsEAACVBAAAmgQAAKEEAAAIAAAACQAAAAoAAAALAAAADAAAAA0AAAAOAAAADwAAABkAAAAZ" +
            "AAAACAAAAAAAAAAaAAAACAAAAKgEAAAaAAAACAAAALAEAAAaAAAACAAAALgEAAABAAAAHgAAAAAA" +
            "AgAbAAAAAQABAAIAAAABAAAABQAAAAEAAAARAAAAAQADACAAAAAGAAAAAgAAAAcAAAAfAAAAAQAA" +
            "AAAAAAAGAAAAAAAAAAYAAAAQBgAArAUAAAAAAAABKAABPAAGPGluaXQ+AAI+OwAEPjspVgAGRmlu" +
            "aXNoAB1Kdm10aVJlZGVmaW5lQ2xhc3Nlc1Rlc3QuamF2YQA0TGFuZHJvaWQvanZtdGkvY3RzL0p2" +
            "bXRpUmVkZWZpbmVDbGFzc2VzVGVzdCRDb25zdW1lcgA1TGFuZHJvaWQvanZtdGkvY3RzL0p2bXRp" +
            "UmVkZWZpbmVDbGFzc2VzVGVzdCRDb25zdW1lcjsANkxhbmRyb2lkL2p2bXRpL2N0cy9Kdm10aVJl" +
            "ZGVmaW5lQ2xhc3Nlc1Rlc3QkVHJhbnNmb3JtOwAsTGFuZHJvaWQvanZtdGkvY3RzL0p2bXRpUmVk" +
            "ZWZpbmVDbGFzc2VzVGVzdDsAIkxkYWx2aWsvYW5ub3RhdGlvbi9FbmNsb3NpbmdDbGFzczsAHkxk" +
            "YWx2aWsvYW5ub3RhdGlvbi9Jbm5lckNsYXNzOwAdTGRhbHZpay9hbm5vdGF0aW9uL1NpZ25hdHVy" +
            "ZTsAEkxqYXZhL2xhbmcvT2JqZWN0OwAUTGphdmEvbGFuZy9SdW5uYWJsZTsAEkxqYXZhL2xhbmcv" +
            "U3RyaW5nOwAFU3RhcnQAH1RSQU5TRk9STUVEIC0gR29vZGJ5ZSAtIHByaXZhdGUAHVRSQU5TRk9S" +
            "TUVEIC0gSGVsbG8gLSBwcml2YXRlAC1UUkFOU0ZPUk1FRCAtIHBvc3QgRmluaXNoIHByaXZhdGUg" +
            "bWV0aG9kIGNhbGwALFRSQU5TRk9STUVEIC0gcG9zdCBTdGFydCBwcml2YXRlIG1ldGhvZCBjYWxs" +
            "ACxUUkFOU0ZPUk1FRCAtIHByZSBGaW5pc2ggcHJpdmF0ZSBtZXRob2QgY2FsbAArVFJBTlNGT1JN" +
            "RUQgLSBwcmUgU3RhcnQgcHJpdmF0ZSBtZXRob2QgY2FsbAAJVHJhbnNmb3JtAAFWAAJWTAAGYWNj" +
            "ZXB0AAthY2Nlc3NGbGFncwAEbmFtZQAIcmVwb3J0ZXIAA3J1bgAFc2F5SGkABXZhbHVlAAEAAAAA" +
            "AAAAAQAAAAYAAAABAAAABwAAAAsBAAcOPC0AFAAHDocAEAAHDocAGAEABw6HPIc8hzyHAAAAAAIA" +
            "AgABAAAAwAQAAAYAAABwEAUAAABbAQAADgADAAEAAgAAAMgEAAAJAAAAVCAAABsBEgAAAHIgAAAQ" +
            "AA4AAAADAAEAAgAAAM4EAAAJAAAAVCAAABsBEwAAAHIgAAAQAA4AAAAEAAIAAgAAANQEAAAqAAAA" +
            "VCAAABsBFwAAAHIgAAAQAHAQAwACAFQgAAAbARUAAAByIAAAEAByEAYAAwBUIAAAGwEWAAAAciAA" +
            "ABAAcBACAAIAVCAAABsBFAAAAHIgAAAQAA4AAAEDAQACAYGABOQJAQKACgECpAoEAcgKAgMBIRgC" +
            "AgQCHAQIHRcYAgUBIRwEFwcXARcQFwMCBQEhHAUXABcHFwEXEBcEAAAAAgAAAMQFAADKBQAAAQAA" +
            "ANMFAAABAAAA4QUAAPQFAAABAAAAAQAAAAAAAAAAAAAAAAYAAAEAAAAIBgAAEAAAAAAAAAABAAAA" +
            "AAAAAAEAAAAiAAAAcAAAAAIAAAAJAAAA+AAAAAMAAAAEAAAAHAEAAAQAAAABAAAATAEAAAUAAAAH" +
            "AAAAVAEAAAYAAAABAAAAjAEAAAIgAAAiAAAArAEAAAEQAAADAAAAqAQAAAMgAAAEAAAAwAQAAAEg" +
            "AAAEAAAA5AQAAAAgAAABAAAArAUAAAQgAAAEAAAAxAUAAAMQAAADAAAA9AUAAAYgAAABAAAAEAYA" +
            "AAAQAAABAAAAMAYAAA==");

    /**
     * Tests that we can redefine Transform class from INITIAL_TRANSFORM to GOOD_TRANSFORM.
     * <p>
     * It uses doRedefine to do the transformation.
     */
    private void checkRedefinedTransform(Runnable doRedefine) {
        // The consumer that we use to observe the changes to the Transform class.
        final StringCollector c = new StringCollector();
        Transform t = new Transform(c);
        // Run once without changes.
        t.sayHi(new Runnable() {
            public void run() {
                c.accept("Initial test run. No changes.");
            }
        });
        // Run once with obsolete methods.
        t.sayHi(new Runnable() {
            public void run() {
                c.accept("Redefining calling function.");
                doRedefine.run();
            }
        });
        // Run once with new definition.
        t.sayHi(new Runnable() {
            public void run() {
                c.accept("Final test run.");
            }
        });

        String[] output = c.reports.toArray(new String[0]);
        assertArrayEquals(
                new String[]{
                        // The first call to sayHi
                        "Pre Start private method call",
                        "hello - private",
                        "Post Start private method call",
                        "Initial test run. No changes.",
                        "Pre Finish private method call",
                        "goodbye - private",
                        "Post Finish private method call",

                        // The second call to sayHi.
                        "Pre Start private method call",
                        "hello - private",
                        "Post Start private method call",
                        "Redefining calling function.",
                        "Pre Finish private method call",
                        "TRANSFORMED - Goodbye - private",
                        "Post Finish private method call",

                        // The final call to sayHi.
                        "TRANSFORMED - pre Start private method call",
                        "TRANSFORMED - Hello - private",
                        "TRANSFORMED - post Start private method call",
                        "Final test run.",
                        "TRANSFORMED - pre Finish private method call",
                        "TRANSFORMED - Goodbye - private",
                        "TRANSFORMED - post Finish private method call",
                }, output);
    }

    @Test
    public void testSucessfulRedefine() throws Exception {
        checkRedefinedTransform(new Runnable() {
            public void run() {
                checkRedefinition(GOOD_TRANSFORM);
            }
        });
    }

    @Test
    public void testSucessfulRetransform() throws Exception {
        pushTransformationResult(GOOD_TRANSFORM.target, GOOD_TRANSFORM.dexData);
        checkRedefinedTransform(new Runnable() {
            public void run() {
                setTransformationEvent(true);
                assertEquals(JvmtiErrors.NONE, retransformClass(Transform.class));
            }
        });
    }

    // This is a class that we will transform for tests.
    // NB This has the actual name Landroid/jvmti/cts/JvmtiRedefineClassesTest$Transform2;
    static class Transform2 {
        public void sayHi() {
            Assert.fail("Should not be called!");
        }
    }

    /**
     * Test cases for failing redefines.
     */
    private static final RedefineError[] FAILING_DEX_FILES = {
            /**
             * Base64 for this class.
             *
             *  .class Landroid/jvmti/cts/JvmtiRedefineClassesTest$Transform2;
             *  .super Ljava/lang/Object;
             *  .source "JvmtiRedefineClassesTest.java"
             *
             *  # annotations
             *  .annotation system Ldalvik/annotation/EnclosingClass;
             *      value = Landroid/jvmti/cts/JvmtiRedefineClassesTest;
             *  .end annotation
             *
             *  .annotation system Ldalvik/annotation/InnerClass;
             *      accessFlags = 0x8
             *      name = "Transform2"
             *  .end annotation
             *
             *  # direct methods
             *  .method constructor <init>()V
             *      .registers 1
             *      .prologue
             *      .line 33
             *      invoke-direct {p0}, Ljava/lang/Object;-><init>()V
             *      return-void
             *  .end method
             *
             *  # virtual methods
             *  .method public sayHi()V
             *      .registers 1
             *      .prologue
             *      .line 35
             *      return-object v0
             *  .end method
            */
            new RedefineError(JvmtiErrors.FAILS_VERIFICATION, Transform2.class,
                    "ZGV4CjAzNQBOhefYdQRcgqmkwhWsSyzb5I3udX0SnJ44AwAAcAAAAHhWNBIAAAAAAAAAAIwCAAAN" +
                    "AAAAcAAAAAYAAACkAAAAAQAAALwAAAAAAAAAAAAAAAMAAADIAAAAAQAAAOAAAAA4AgAAAAEAAAAB" +
                    "AAAIAQAAJwEAAGABAACOAQAAsgEAANIBAADmAQAA8gEAAPUBAAACAgAACAIAAA8CAAACAAAAAwAA" +
                    "AAQAAAAFAAAABgAAAAgAAAAIAAAABQAAAAAAAAAAAAAAAAAAAAAAAAALAAAABAAAAAAAAAAAAAAA" +
                    "AAAAAAQAAAAAAAAAAQAAADgCAAB+AgAAAAAAAAY8aW5pdD4AHUp2bXRpUmVkZWZpbmVDbGFzc2Vz" +
                    "VGVzdC5qYXZhADdMYW5kcm9pZC9qdm10aS9jdHMvSnZtdGlSZWRlZmluZUNsYXNzZXNUZXN0JFRy" +
                    "YW5zZm9ybTI7ACxMYW5kcm9pZC9qdm10aS9jdHMvSnZtdGlSZWRlZmluZUNsYXNzZXNUZXN0OwAi" +
                    "TGRhbHZpay9hbm5vdGF0aW9uL0VuY2xvc2luZ0NsYXNzOwAeTGRhbHZpay9hbm5vdGF0aW9uL0lu" +
                    "bmVyQ2xhc3M7ABJMamF2YS9sYW5nL09iamVjdDsAClRyYW5zZm9ybTIAAVYAC2FjY2Vzc0ZsYWdz" +
                    "AARuYW1lAAVzYXlIaQAFdmFsdWUAAAACAwIJBAgKFwcCAgEMGAEAAAAAAAIAAAAhAgAAGAIAACwC" +
                    "AAAAAAAAAAAAAAAAAAAhAAcOACMABw4AAAABAAEAAQAAAEgCAAAEAAAAcBACAAAADgACAAEAAAAA" +
                    "AE0CAAABAAAAEQAAAAEBAICABNQEAQHsBA4AAAAAAAAAAQAAAAAAAAABAAAADQAAAHAAAAACAAAA" +
                    "BgAAAKQAAAADAAAAAQAAALwAAAAFAAAAAwAAAMgAAAAGAAAAAQAAAOAAAAACIAAADQAAAAABAAAE" +
                    "IAAAAgAAABgCAAADEAAAAgAAACgCAAAGIAAAAQAAADgCAAADIAAAAgAAAEgCAAABIAAAAgAAAFQC" +
                    "AAAAIAAAAQAAAH4CAAAAEAAAAQAAAIwCAAA="),
            /**
             * Base64 for this class.
             *
             *  static class Transform {
             *    private Consumer<String> reporter;
             *    public Transform(Consumer<String> reporter) {
             *      this.reporter = reporter;
             *    }
             *    private void Start() { }
             *    private void Finish() { }
             *    protected void sayHi(Runnable r) { }
             *  }
            */
            new RedefineError(
                    JvmtiErrors.UNSUPPORTED_REDEFINITION_METHOD_MODIFIERS_CHANGED, Transform.class,
                    "ZGV4CjAzNQAf2DrkAAAAAAAAAAAAAAAAAAAAAAAAAAAwBQAAcAAAAHhWNBIAAAAAAAAAAGwEAAAa" +
                    "AAAAcAAAAAkAAADYAAAAAwAAAPwAAAABAAAAIAEAAAUAAAAoAQAAAQAAAFABAADAAwAAcAEAAHAB" +
                    "AABzAQAAdgEAAH4BAACCAQAAiAEAAJABAACvAQAA5QEAABwCAABUAgAAggIAAKYCAADGAgAA5QIA" +
                    "APkCAAAPAwAAIwMAACoDAAA1AwAAOAMAADwDAABJAwAATwMAAFkDAABgAwAACAAAAAkAAAAKAAAA" +
                    "CwAAAAwAAAANAAAADgAAAA8AAAATAAAAEwAAAAgAAAAAAAAAFAAAAAgAAABoAwAAFAAAAAgAAABw" +
                    "AwAAAQAAABcAAAABAAEAAgAAAAEAAAAFAAAAAQAAABEAAAABAAIAGAAAAAYAAAACAAAAAQAAAAAA" +
                    "AAAGAAAAAAAAAAYAAABMBAAA6AMAAAAAAAABKAABPAAGPGluaXQ+AAI+OwAEPjspVgAGRmluaXNo" +
                    "AB1Kdm10aVJlZGVmaW5lQ2xhc3Nlc1Rlc3QuamF2YQA0TGFuZHJvaWQvanZtdGkvY3RzL0p2bXRp" +
                    "UmVkZWZpbmVDbGFzc2VzVGVzdCRDb25zdW1lcgA1TGFuZHJvaWQvanZtdGkvY3RzL0p2bXRpUmVk" +
                    "ZWZpbmVDbGFzc2VzVGVzdCRDb25zdW1lcjsANkxhbmRyb2lkL2p2bXRpL2N0cy9Kdm10aVJlZGVm" +
                    "aW5lQ2xhc3Nlc1Rlc3QkVHJhbnNmb3JtOwAsTGFuZHJvaWQvanZtdGkvY3RzL0p2bXRpUmVkZWZp" +
                    "bmVDbGFzc2VzVGVzdDsAIkxkYWx2aWsvYW5ub3RhdGlvbi9FbmNsb3NpbmdDbGFzczsAHkxkYWx2" +
                    "aWsvYW5ub3RhdGlvbi9Jbm5lckNsYXNzOwAdTGRhbHZpay9hbm5vdGF0aW9uL1NpZ25hdHVyZTsA" +
                    "EkxqYXZhL2xhbmcvT2JqZWN0OwAUTGphdmEvbGFuZy9SdW5uYWJsZTsAEkxqYXZhL2xhbmcvU3Ry" +
                    "aW5nOwAFU3RhcnQACVRyYW5zZm9ybQABVgACVkwAC2FjY2Vzc0ZsYWdzAARuYW1lAAhyZXBvcnRl" +
                    "cgAFc2F5SGkABXZhbHVlAAABAAAAAAAAAAEAAAAHAAAACwEABw48LQARAAcOAA8ABw4AEwEABw4A" +
                    "AgACAAEAAAB4AwAABgAAAHAQBAAAAFsBAAAOAAEAAQAAAAAAgAMAAAEAAAAOAAAAAQABAAAAAACF" +
                    "AwAAAQAAAA4AAAACAAIAAAAAAIoDAAABAAAADgAAAAABAwEAAgCBgASQBwECrAcBAsAHAwTUBwID" +
                    "ARkYAgIEAhUECBYXEgIFARkcBBcHFwEXEBcDAgUBGRwFFwAXBxcBFxAXBAAAAAIAAAAABAAABgQA" +
                    "AAEAAAAPBAAAAQAAAB0EAAAwBAAAAQAAAAEAAAAAAAAAAAAAADwEAAAAAAAARAQAABAAAAAAAAAA" +
                    "AQAAAAAAAAABAAAAGgAAAHAAAAACAAAACQAAANgAAAADAAAAAwAAAPwAAAAEAAAAAQAAACABAAAF" +
                    "AAAABQAAACgBAAAGAAAAAQAAAFABAAACIAAAGgAAAHABAAABEAAAAgAAAGgDAAADIAAABAAAAHgD" +
                    "AAABIAAABAAAAJADAAAAIAAAAQAAAOgDAAAEIAAABAAAAAAEAAADEAAAAwAAADAEAAAGIAAAAQAA" +
                    "AEwEAAAAEAAAAQAAAGwEAAA="),
            /**
             * Base64 for this class.
             *
             *  static final class Transform {
             *    private Consumer<String> reporter;
             *    public Transform(Consumer<String> reporter) {
             *      this.reporter = reporter;
             *    }
             *    private void Start() { }
             *    private void Finish() { }
             *    public void sayHi(Runnable r) { }
             *  }
            */
            new RedefineError(
                    JvmtiErrors.UNSUPPORTED_REDEFINITION_CLASS_MODIFIERS_CHANGED, Transform.class,
                    "ZGV4CjAzNQA82MAwAAAAAAAAAAAAAAAAAAAAAAAAAAAwBQAAcAAAAHhWNBIAAAAAAAAAAGwEAAAa" +
                    "AAAAcAAAAAkAAADYAAAAAwAAAPwAAAABAAAAIAEAAAUAAAAoAQAAAQAAAFABAADAAwAAcAEAAHAB" +
                    "AABzAQAAdgEAAH4BAACCAQAAiAEAAJABAACvAQAA5QEAABwCAABUAgAAggIAAKYCAADGAgAA5QIA" +
                    "APkCAAAPAwAAIwMAACoDAAA1AwAAOAMAADwDAABJAwAATwMAAFkDAABgAwAACAAAAAkAAAAKAAAA" +
                    "CwAAAAwAAAANAAAADgAAAA8AAAATAAAAEwAAAAgAAAAAAAAAFAAAAAgAAABoAwAAFAAAAAgAAABw" +
                    "AwAAAQAAABcAAAABAAEAAgAAAAEAAAAFAAAAAQAAABEAAAABAAIAGAAAAAYAAAACAAAAAQAAABAA" +
                    "AAAGAAAAAAAAAAYAAABMBAAA6AMAAAAAAAABKAABPAAGPGluaXQ+AAI+OwAEPjspVgAGRmluaXNo" +
                    "AB1Kdm10aVJlZGVmaW5lQ2xhc3Nlc1Rlc3QuamF2YQA0TGFuZHJvaWQvanZtdGkvY3RzL0p2bXRp" +
                    "UmVkZWZpbmVDbGFzc2VzVGVzdCRDb25zdW1lcgA1TGFuZHJvaWQvanZtdGkvY3RzL0p2bXRpUmVk" +
                    "ZWZpbmVDbGFzc2VzVGVzdCRDb25zdW1lcjsANkxhbmRyb2lkL2p2bXRpL2N0cy9Kdm10aVJlZGVm" +
                    "aW5lQ2xhc3Nlc1Rlc3QkVHJhbnNmb3JtOwAsTGFuZHJvaWQvanZtdGkvY3RzL0p2bXRpUmVkZWZp" +
                    "bmVDbGFzc2VzVGVzdDsAIkxkYWx2aWsvYW5ub3RhdGlvbi9FbmNsb3NpbmdDbGFzczsAHkxkYWx2" +
                    "aWsvYW5ub3RhdGlvbi9Jbm5lckNsYXNzOwAdTGRhbHZpay9hbm5vdGF0aW9uL1NpZ25hdHVyZTsA" +
                    "EkxqYXZhL2xhbmcvT2JqZWN0OwAUTGphdmEvbGFuZy9SdW5uYWJsZTsAEkxqYXZhL2xhbmcvU3Ry" +
                    "aW5nOwAFU3RhcnQACVRyYW5zZm9ybQABVgACVkwAC2FjY2Vzc0ZsYWdzAARuYW1lAAhyZXBvcnRl" +
                    "cgAFc2F5SGkABXZhbHVlAAABAAAAAAAAAAEAAAAHAAAACwEABw48LQARAAcOAA8ABw4AEwEABw4A" +
                    "AgACAAEAAAB4AwAABgAAAHAQBAAAAFsBAAAOAAEAAQAAAAAAgAMAAAEAAAAOAAAAAQABAAAAAACF" +
                    "AwAAAQAAAA4AAAACAAIAAAAAAIoDAAABAAAADgAAAAABAwEAAgCBgASQBwECrAcBAsAHAwHUBwID" +
                    "ARkYAgIEAhUEGBYXEgIFARkcBBcHFwEXEBcDAgUBGRwFFwAXBxcBFxAXBAAAAAIAAAAABAAABgQA" +
                    "AAEAAAAPBAAAAQAAAB0EAAAwBAAAAQAAAAEAAAAAAAAAAAAAADwEAAAAAAAARAQAABAAAAAAAAAA" +
                    "AQAAAAAAAAABAAAAGgAAAHAAAAACAAAACQAAANgAAAADAAAAAwAAAPwAAAAEAAAAAQAAACABAAAF" +
                    "AAAABQAAACgBAAAGAAAAAQAAAFABAAACIAAAGgAAAHABAAABEAAAAgAAAGgDAAADIAAABAAAAHgD" +
                    "AAABIAAABAAAAJADAAAAIAAAAQAAAOgDAAAEIAAABAAAAAAEAAADEAAAAwAAADAEAAAGIAAAAQAA" +
                    "AEwEAAAAEAAAAQAAAGwEAAA="),
            /**
             * Base64 for this class.
             *
             *  static class Transform {
             *    private Consumer<String> reporter;
             *    public Transform(Consumer<String> reporter) {
             *      this.reporter = reporter;
             *    }
             *    private void Finish() { }
             *    public void sayHi(Runnable r) { }
             *  }
            */
            new RedefineError(
                    JvmtiErrors.UNSUPPORTED_REDEFINITION_METHOD_DELETED, Transform.class,
                    "ZGV4CjAzNQBG028hAAAAAAAAAAAAAAAAAAAAAAAAAAAABQAAcAAAAHhWNBIAAAAAAAAAADwEAAAZ" +
                    "AAAAcAAAAAkAAADUAAAAAwAAAPgAAAABAAAAHAEAAAQAAAAkAQAAAQAAAEQBAACcAwAAZAEAAGQB" +
                    "AABnAQAAagEAAHIBAAB2AQAAfAEAAJsBAADRAQAACAIAAEACAABuAgAAkgIAALICAADRAgAA5QIA" +
                    "APsCAAAPAwAAFgMAACEDAAAkAwAAKAMAADUDAAA7AwAARQMAAEwDAAAHAAAACAAAAAkAAAAKAAAA" +
                    "CwAAAAwAAAANAAAADgAAABIAAAASAAAACAAAAAAAAAATAAAACAAAAFQDAAATAAAACAAAAFwDAAAB" +
                    "AAAAFgAAAAEAAQACAAAAAQAAABAAAAABAAIAFwAAAAYAAAACAAAAAQAAAAAAAAAGAAAAAAAAAAUA" +
                    "AAAcBAAAvAMAAAAAAAABKAABPAAGPGluaXQ+AAI+OwAEPjspVgAdSnZtdGlSZWRlZmluZUNsYXNz" +
                    "ZXNUZXN0LmphdmEANExhbmRyb2lkL2p2bXRpL2N0cy9Kdm10aVJlZGVmaW5lQ2xhc3Nlc1Rlc3Qk" +
                    "Q29uc3VtZXIANUxhbmRyb2lkL2p2bXRpL2N0cy9Kdm10aVJlZGVmaW5lQ2xhc3Nlc1Rlc3QkQ29u" +
                    "c3VtZXI7ADZMYW5kcm9pZC9qdm10aS9jdHMvSnZtdGlSZWRlZmluZUNsYXNzZXNUZXN0JFRyYW5z" +
                    "Zm9ybTsALExhbmRyb2lkL2p2bXRpL2N0cy9Kdm10aVJlZGVmaW5lQ2xhc3Nlc1Rlc3Q7ACJMZGFs" +
                    "dmlrL2Fubm90YXRpb24vRW5jbG9zaW5nQ2xhc3M7AB5MZGFsdmlrL2Fubm90YXRpb24vSW5uZXJD" +
                    "bGFzczsAHUxkYWx2aWsvYW5ub3RhdGlvbi9TaWduYXR1cmU7ABJMamF2YS9sYW5nL09iamVjdDsA" +
                    "FExqYXZhL2xhbmcvUnVubmFibGU7ABJMamF2YS9sYW5nL1N0cmluZzsABVN0YXJ0AAlUcmFuc2Zv" +
                    "cm0AAVYAAlZMAAthY2Nlc3NGbGFncwAEbmFtZQAIcmVwb3J0ZXIABXNheUhpAAV2YWx1ZQAAAQAA" +
                    "AAAAAAABAAAABwAAAA0BAAcOAA4ABw4AEAEABw4AAAAAAgACAAEAAABkAwAABgAAAHAQAwAAAFsB" +
                    "AAAOAAEAAQAAAAAAagMAAAEAAAAOAAAAAgACAAAAAABvAwAAAQAAAA4AAAAAAQIBAAIAgYAE+AYB" +
                    "ApQHAgGoBwIDARgYAgIEAhQECBUXEQIFARgcBBcGFwEXDxcDAgUBGBwFFwAXBhcBFw8XBAAAAAIA" +
                    "AADQAwAA1gMAAAEAAADfAwAAAQAAAO0DAAAABAAAAQAAAAEAAAAAAAAAAAAAAAwEAAAAAAAAFAQA" +
                    "ABAAAAAAAAAAAQAAAAAAAAABAAAAGQAAAHAAAAACAAAACQAAANQAAAADAAAAAwAAAPgAAAAEAAAA" +
                    "AQAAABwBAAAFAAAABAAAACQBAAAGAAAAAQAAAEQBAAACIAAAGQAAAGQBAAABEAAAAgAAAFQDAAAD" +
                    "IAAAAwAAAGQDAAABIAAAAwAAAHgDAAAAIAAAAQAAALwDAAAEIAAABAAAANADAAADEAAAAwAAAAAE" +
                    "AAAGIAAAAQAAABwEAAAAEAAAAQAAADwEAAA="),
            /**
             * Base6is class.
             *
             *  static class Transform {
             *    private Consumer<String> reporter;
             *    public Transform(Consumer<String> reporter) {
             *      this.reporter = reporter;
             *    }
             *    private void Start() { }
             *    private void Start2() { }
             *    private void Finish() { }
             *    public void sayHi(Runnable r) { }
             *  }
            */
            new RedefineError(
                    JvmtiErrors.UNSUPPORTED_REDEFINITION_METHOD_ADDED, Transform.class,
                    "ZGV4CjAzNQC43HElAAAAAAAAAAAAAAAAAAAAAAAAAABgBQAAcAAAAHhWNBIAAAAAAAAAAJwEAAAb" +
                    "AAAAcAAAAAkAAADcAAAAAwAAAAABAAABAAAAJAEAAAYAAAAsAQAAAQAAAFwBAADkAwAAfAEAAHwB" +
                    "AAB/AQAAggEAAIoBAACOAQAAlAEAAJwBAAC7AQAA8QEAACgCAABgAgAAjgIAALICAADSAgAA8QIA" +
                    "AAUDAAAbAwAALwMAADYDAAA+AwAASQMAAEwDAABQAwAAXQMAAGMDAABtAwAAdAMAAAgAAAAJAAAA" +
                    "CgAAAAsAAAAMAAAADQAAAA4AAAAPAAAAFAAAABQAAAAIAAAAAAAAABUAAAAIAAAAfAMAABUAAAAI" +
                    "AAAAhAMAAAEAAAAYAAAAAQABAAIAAAABAAAABQAAAAEAAAARAAAAAQAAABIAAAABAAIAGQAAAAYA" +
                    "AAACAAAAAQAAAAAAAAAGAAAAAAAAAAYAAAB8BAAAFAQAAAAAAAABKAABPAAGPGluaXQ+AAI+OwAE" +
                    "PjspVgAGRmluaXNoAB1Kdm10aVJlZGVmaW5lQ2xhc3Nlc1Rlc3QuamF2YQA0TGFuZHJvaWQvanZt" +
                    "dGkvY3RzL0p2bXRpUmVkZWZpbmVDbGFzc2VzVGVzdCRDb25zdW1lcgA1TGFuZHJvaWQvanZtdGkv" +
                    "Y3RzL0p2bXRpUmVkZWZpbmVDbGFzc2VzVGVzdCRDb25zdW1lcjsANkxhbmRyb2lkL2p2bXRpL2N0" +
                    "cy9Kdm10aVJlZGVmaW5lQ2xhc3Nlc1Rlc3QkVHJhbnNmb3JtOwAsTGFuZHJvaWQvanZtdGkvY3Rz" +
                    "L0p2bXRpUmVkZWZpbmVDbGFzc2VzVGVzdDsAIkxkYWx2aWsvYW5ub3RhdGlvbi9FbmNsb3NpbmdD" +
                    "bGFzczsAHkxkYWx2aWsvYW5ub3RhdGlvbi9Jbm5lckNsYXNzOwAdTGRhbHZpay9hbm5vdGF0aW9u" +
                    "L1NpZ25hdHVyZTsAEkxqYXZhL2xhbmcvT2JqZWN0OwAUTGphdmEvbGFuZy9SdW5uYWJsZTsAEkxq" +
                    "YXZhL2xhbmcvU3RyaW5nOwAFU3RhcnQABlN0YXJ0MgAJVHJhbnNmb3JtAAFWAAJWTAALYWNjZXNz" +
                    "RmxhZ3MABG5hbWUACHJlcG9ydGVyAAVzYXlIaQAFdmFsdWUAAAEAAAAAAAAAAQAAAAcAAAANAQAH" +
                    "DgAQAAcOAA4ABw4ADwAHDgARAQAHDgAAAgACAAEAAACMAwAABgAAAHAQBQAAAFsBAAAOAAEAAQAA" +
                    "AAAAkgMAAAEAAAAOAAAAAQABAAAAAACXAwAAAQAAAA4AAAABAAEAAAAAAJwDAAABAAAADgAAAAIA" +
                    "AgAAAAAAoQMAAAEAAAAOAAAAAAEEAQACAIGABKgHAQLEBwEC2AcBAuwHBAGACAIDARoYAgIEAhYE" +
                    "CBcXEwIFARocBBcHFwEXEBcDAgUBGhwFFwAXBxcBFxAXBAAAAAIAAAAwBAAANgQAAAEAAAA/BAAA" +
                    "AQAAAE0EAABgBAAAAQAAAAEAAAAAAAAAAAAAAGwEAAAAAAAAdAQAABAAAAAAAAAAAQAAAAAAAAAB" +
                    "AAAAGwAAAHAAAAACAAAACQAAANwAAAADAAAAAwAAAAABAAAEAAAAAQAAACQBAAAFAAAABgAAACwB" +
                    "AAAGAAAAAQAAAFwBAAACIAAAGwAAAHwBAAABEAAAAgAAAHwDAAADIAAABQAAAIwDAAABIAAABQAA" +
                    "AKgDAAAAIAAAAQAAABQEAAAEIAAABAAAADAEAAADEAAAAwAAAGAEAAAGIAAAAQAAAHwEAAAAEAAA" +
                    "AQAAAJwEAAA="),
            /**
             * Base64 for this class.
             *
             *  static class Transform {
             *    public Transform(Consumer<String> reporter) { }
             *    private void Start() { }
             *    private void Finish() { }
             *    public void sayHi(Runnable r) { }
             *  }
            */
            new RedefineError(
                    JvmtiErrors.UNSUPPORTED_REDEFINITION_SCHEMA_CHANGED, Transform.class,
                    "ZGV4CjAzNQCn0K9/AAAAAAAAAAAAAAAAAAAAAAAAAADkBAAAcAAAAHhWNBIAAAAAAAAAACwEAAAY" +
                    "AAAAcAAAAAkAAADQAAAAAwAAAPQAAAAAAAAAAAAAAAUAAAAYAQAAAQAAAEABAACEAwAAYAEAAGAB" +
                    "AABjAQAAZgEAAG4BAAB0AQAAfAEAAJsBAADRAQAACAIAAEACAABuAgAAkgIAALICAADRAgAA5QIA" +
                    "APsCAAAPAwAAFgMAACEDAAAkAwAAKAMAADUDAAA7AwAAQgMAAAcAAAAIAAAACQAAAAoAAAALAAAA" +
                    "DAAAAA0AAAAOAAAAEgAAABIAAAAIAAAAAAAAABMAAAAIAAAATAMAABMAAAAIAAAAVAMAAAEAAQAC" +
                    "AAAAAQAAAAQAAAABAAAAEAAAAAEAAgAWAAAABgAAAAIAAAABAAAAAAAAAAYAAAAAAAAABQAAABQE" +
                    "AADIAwAAAAAAAAEoAAE8AAY8aW5pdD4ABD47KVYABkZpbmlzaAAdSnZtdGlSZWRlZmluZUNsYXNz" +
                    "ZXNUZXN0LmphdmEANExhbmRyb2lkL2p2bXRpL2N0cy9Kdm10aVJlZGVmaW5lQ2xhc3Nlc1Rlc3Qk" +
                    "Q29uc3VtZXIANUxhbmRyb2lkL2p2bXRpL2N0cy9Kdm10aVJlZGVmaW5lQ2xhc3Nlc1Rlc3QkQ29u" +
                    "c3VtZXI7ADZMYW5kcm9pZC9qdm10aS9jdHMvSnZtdGlSZWRlZmluZUNsYXNzZXNUZXN0JFRyYW5z" +
                    "Zm9ybTsALExhbmRyb2lkL2p2bXRpL2N0cy9Kdm10aVJlZGVmaW5lQ2xhc3Nlc1Rlc3Q7ACJMZGFs" +
                    "dmlrL2Fubm90YXRpb24vRW5jbG9zaW5nQ2xhc3M7AB5MZGFsdmlrL2Fubm90YXRpb24vSW5uZXJD" +
                    "bGFzczsAHUxkYWx2aWsvYW5ub3RhdGlvbi9TaWduYXR1cmU7ABJMamF2YS9sYW5nL09iamVjdDsA" +
                    "FExqYXZhL2xhbmcvUnVubmFibGU7ABJMamF2YS9sYW5nL1N0cmluZzsABVN0YXJ0AAlUcmFuc2Zv" +
                    "cm0AAVYAAlZMAAthY2Nlc3NGbGFncwAEbmFtZQAFc2F5SGkABXZhbHVlAAAAAAEAAAAAAAAAAQAA" +
                    "AAcAAAAMAQAHDgAOAAcOAA0ABw4ADwEABw4AAAACAAIAAQAAAFwDAAAEAAAAcBAEAAAADgABAAEA" +
                    "AAAAAGIDAAABAAAADgAAAAEAAQAAAAAAZwMAAAEAAAAOAAAAAgACAAAAAABsAwAAAQAAAA4AAAAA" +
                    "AAMBAIGABPQGAQKMBwECoAcDAbQHAAACAwEXGAICBAIUBAgVFxECBQEXHAUXABcGFwEXDxcDAAIA" +
                    "AADgAwAA5gMAAAEAAADvAwAAAAQAAAAAAAABAAAAAAAAAAAAAAAMBAAADwAAAAAAAAABAAAAAAAA" +
                    "AAEAAAAYAAAAcAAAAAIAAAAJAAAA0AAAAAMAAAADAAAA9AAAAAUAAAAFAAAAGAEAAAYAAAABAAAA" +
                    "QAEAAAIgAAAYAAAAYAEAAAEQAAACAAAATAMAAAMgAAAEAAAAXAMAAAEgAAAEAAAAdAMAAAAgAAAB" +
                    "AAAAyAMAAAQgAAADAAAA4AMAAAMQAAACAAAAAAQAAAYgAAABAAAAFAQAAAAQAAABAAAALAQAAA=="),
            /**
             * Base64 for this class.
             *
             *  static class Transform3 {
             *      // NB This field has type Landroid/jvmti/cts/JvmtiRedefineClassesTest$Consumer;
             *      private Consumer<String> reporter;
             *      public Transform3(Consumer<String> reporter) { this.reporter = reporter; }
             *      private void Start() { }
             *      private void Finish() { }
             *      public void sayHi(Runnable r) { }
             *  }
            */
            new RedefineError(
                    JvmtiErrors.NAMES_DONT_MATCH, Transform.class,
                    "ZGV4CjAzNQCc2B3nAAAAAAAAAAAAAAAAAAAAAAAAAAA0BQAAcAAAAHhWNBIAAAAAAAAAAHAEAAAa" +
                    "AAAAcAAAAAkAAADYAAAAAwAAAPwAAAABAAAAIAEAAAUAAAAoAQAAAQAAAFABAADEAwAAcAEAAHAB" +
                    "AABzAQAAdgEAAH4BAACCAQAAiAEAAJABAACvAQAA5QEAABwCAABVAgAAgwIAAKcCAADHAgAA5gIA" +
                    "APoCAAAQAwAAJAMAACsDAAA3AwAAOgMAAD4DAABLAwAAUQMAAFsDAABiAwAACAAAAAkAAAAKAAAA" +
                    "CwAAAAwAAAANAAAADgAAAA8AAAATAAAAEwAAAAgAAAAAAAAAFAAAAAgAAABsAwAAFAAAAAgAAAB0" +
                    "AwAAAQAAABcAAAABAAEAAgAAAAEAAAAFAAAAAQAAABEAAAABAAIAGAAAAAYAAAACAAAAAQAAAAAA" +
                    "AAAGAAAAAAAAAAYAAABQBAAA7AMAAAAAAAABKAABPAAGPGluaXQ+AAI+OwAEPjspVgAGRmluaXNo" +
                    "AB1Kdm10aVJlZGVmaW5lQ2xhc3Nlc1Rlc3QuamF2YQA0TGFuZHJvaWQvanZtdGkvY3RzL0p2bXRp" +
                    "UmVkZWZpbmVDbGFzc2VzVGVzdCRDb25zdW1lcgA1TGFuZHJvaWQvanZtdGkvY3RzL0p2bXRpUmVk" +
                    "ZWZpbmVDbGFzc2VzVGVzdCRDb25zdW1lcjsAN0xhbmRyb2lkL2p2bXRpL2N0cy9Kdm10aVJlZGVm" +
                    "aW5lQ2xhc3Nlc1Rlc3QkVHJhbnNmb3JtMzsALExhbmRyb2lkL2p2bXRpL2N0cy9Kdm10aVJlZGVm" +
                    "aW5lQ2xhc3Nlc1Rlc3Q7ACJMZGFsdmlrL2Fubm90YXRpb24vRW5jbG9zaW5nQ2xhc3M7AB5MZGFs" +
                    "dmlrL2Fubm90YXRpb24vSW5uZXJDbGFzczsAHUxkYWx2aWsvYW5ub3RhdGlvbi9TaWduYXR1cmU7" +
                    "ABJMamF2YS9sYW5nL09iamVjdDsAFExqYXZhL2xhbmcvUnVubmFibGU7ABJMamF2YS9sYW5nL1N0" +
                    "cmluZzsABVN0YXJ0AApUcmFuc2Zvcm0zAAFWAAJWTAALYWNjZXNzRmxhZ3MABG5hbWUACHJlcG9y" +
                    "dGVyAAVzYXlIaQAFdmFsdWUAAAAAAQAAAAAAAAABAAAABwAAAAwBAAcOAA4ABw4ADQAHDgAPAQAH" +
                    "DgAAAAIAAgABAAAAfAMAAAYAAABwEAQAAABbAQAADgABAAEAAAAAAIIDAAABAAAADgAAAAEAAQAA" +
                    "AAAAhwMAAAEAAAAOAAAAAgACAAAAAACMAwAAAQAAAA4AAAAAAQMBAAIAgYAElAcBArAHAQLEBwMB" +
                    "2AcCAwEZGAICBAIVBAgWFxICBQEZHAQXBxcBFxAXAwIFARkcBRcAFwcXARcQFwQAAAACAAAABAQA" +
                    "AAoEAAABAAAAEwQAAAEAAAAhBAAANAQAAAEAAAABAAAAAAAAAAAAAABABAAAAAAAAEgEAAAQAAAA" +
                    "AAAAAAEAAAAAAAAAAQAAABoAAABwAAAAAgAAAAkAAADYAAAAAwAAAAMAAAD8AAAABAAAAAEAAAAg" +
                    "AQAABQAAAAUAAAAoAQAABgAAAAEAAABQAQAAAiAAABoAAABwAQAAARAAAAIAAABsAwAAAyAAAAQA" +
                    "AAB8AwAAASAAAAQAAACUAwAAACAAAAEAAADsAwAABCAAAAQAAAAEBAAAAxAAAAMAAAA0BAAABiAA" +
                    "AAEAAABQBAAAABAAAAEAAABwBAAA"),
            /**
             * Base64 for this class.
             *
             *  static class Transform extends Observable {
             *      // NB This field has type Landroid/jvmti/cts/JvmtiRedefineClassesTest$Consumer;
             *      private Consumer<String> reporter;
             *      public Transform(Consumer<String> reporter) { super(); this.reporter = reporter; }
             *      private void Start() { }
             *      private void Finish() { }
             *      public void sayHi(Runnable r) { }
             *  }
            */
            new RedefineError(
                    JvmtiErrors.UNSUPPORTED_REDEFINITION_HIERARCHY_CHANGED, Transform.class,
                    "ZGV4CjAzNQAV2qEZAAAAAAAAAAAAAAAAAAAAAAAAAAA0BQAAcAAAAHhWNBIAAAAAAAAAAHAEAAAa" +
                    "AAAAcAAAAAkAAADYAAAAAwAAAPwAAAABAAAAIAEAAAUAAAAoAQAAAQAAAFABAADEAwAAcAEAAHAB" +
                    "AABzAQAAdgEAAH4BAACCAQAAiAEAAJABAACvAQAA5QEAABwCAABUAgAAggIAAKYCAADGAgAA5QIA" +
                    "APsCAAAPAwAAJwMAAC4DAAA5AwAAPAMAAEADAABNAwAAUwMAAF0DAABkAwAACAAAAAkAAAAKAAAA" +
                    "CwAAAAwAAAANAAAADgAAABAAAAATAAAAEwAAAAgAAAAAAAAAFAAAAAgAAABsAwAAFAAAAAgAAAB0" +
                    "AwAAAQAAABcAAAABAAEAAgAAAAEAAAAFAAAAAQAAABEAAAABAAIAGAAAAAcAAAACAAAAAQAAAAAA" +
                    "AAAHAAAAAAAAAAYAAABQBAAA7AMAAAAAAAABKAABPAAGPGluaXQ+AAI+OwAEPjspVgAGRmluaXNo" +
                    "AB1Kdm10aVJlZGVmaW5lQ2xhc3Nlc1Rlc3QuamF2YQA0TGFuZHJvaWQvanZtdGkvY3RzL0p2bXRp" +
                    "UmVkZWZpbmVDbGFzc2VzVGVzdCRDb25zdW1lcgA1TGFuZHJvaWQvanZtdGkvY3RzL0p2bXRpUmVk" +
                    "ZWZpbmVDbGFzc2VzVGVzdCRDb25zdW1lcjsANkxhbmRyb2lkL2p2bXRpL2N0cy9Kdm10aVJlZGVm" +
                    "aW5lQ2xhc3Nlc1Rlc3QkVHJhbnNmb3JtOwAsTGFuZHJvaWQvanZtdGkvY3RzL0p2bXRpUmVkZWZp" +
                    "bmVDbGFzc2VzVGVzdDsAIkxkYWx2aWsvYW5ub3RhdGlvbi9FbmNsb3NpbmdDbGFzczsAHkxkYWx2" +
                    "aWsvYW5ub3RhdGlvbi9Jbm5lckNsYXNzOwAdTGRhbHZpay9hbm5vdGF0aW9uL1NpZ25hdHVyZTsA" +
                    "FExqYXZhL2xhbmcvUnVubmFibGU7ABJMamF2YS9sYW5nL1N0cmluZzsAFkxqYXZhL3V0aWwvT2Jz" +
                    "ZXJ2YWJsZTsABVN0YXJ0AAlUcmFuc2Zvcm0AAVYAAlZMAAthY2Nlc3NGbGFncwAEbmFtZQAIcmVw" +
                    "b3J0ZXIABXNheUhpAAV2YWx1ZQAAAQAAAAAAAAABAAAABgAAAA0BAAcOAA8ABw4ADgAHDgAQAQAH" +
                    "DgAAAAIAAgABAAAAfAMAAAYAAABwEAQAAABbAQAADgABAAEAAAAAAIIDAAABAAAADgAAAAEAAQAA" +
                    "AAAAhwMAAAEAAAAOAAAAAgACAAAAAACMAwAAAQAAAA4AAAAAAQMBAAIAgYAElAcBArAHAQLEBwMB" +
                    "2AcCAwEZGAICBAIVBAgWFxICBQEZHAQXBxcBFw8XAwIFARkcBRcAFwcXARcPFwQAAAACAAAABAQA" +
                    "AAoEAAABAAAAEwQAAAEAAAAhBAAANAQAAAEAAAABAAAAAAAAAAAAAABABAAAAAAAAEgEAAAQAAAA" +
                    "AAAAAAEAAAAAAAAAAQAAABoAAABwAAAAAgAAAAkAAADYAAAAAwAAAAMAAAD8AAAABAAAAAEAAAAg" +
                    "AQAABQAAAAUAAAAoAQAABgAAAAEAAABQAQAAAiAAABoAAABwAQAAARAAAAIAAABsAwAAAyAAAAQA" +
                    "AAB8AwAAASAAAAQAAACUAwAAACAAAAEAAADsAwAABCAAAAQAAAAEBAAAAxAAAAMAAAA0BAAABiAA" +
                    "AAEAAABQBAAAABAAAAEAAABwBAAA"),
            /**
             * Array classes are never modifiable.
             *
             * The base64 data is just an empty dex file. It has no classes associated with it.
            */
            new RedefineError(JvmtiErrors.UNMODIFIABLE_CLASS, Transform[].class,
                    "ZGV4CjAzNQCRAy8PAAAAAAAAAAAAAAAAAAAAAAAAAACMAAAAcAAAAHhWNBIAAAAAAAAAAHAAAAAA" +
                    "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAcAAAAcAAAAAIA" +
                    "AAAAAAAAAQAAAAAAAAAAEAAAAQAAAHAAAAA="),
    };

    @Test
    public void testRedefineFailures() throws Exception {
        // Just check each of the failing tests.
        for (RedefineError e : FAILING_DEX_FILES) {
            checkRedefinition(e);
        }
    }

    private void checkRetransformation(RedefineError e) {
        clearTransformations();
        pushTransformationResult(e.target, e.dexData);
        assertEquals(e.expectedError, retransformClass(e.target));
    }

    @Test
    public void testRetransformFailures() throws Exception {
        setTransformationEvent(true);
        setPopTransformations(true);
        for (RedefineError e : FAILING_DEX_FILES) {
            checkRetransformation(e);
        }
    }

    private static final String ONLOAD_TEST_CLASS_NAME =
            "android.jvmti.cts.memory_dex.TransformTarget";
    /**
     * Base64 encoded version of the following class.
     * package android.jvmti.cts.memory_dex;
     * <p>
     * public class TransformTarget {
     * public void alpha() { }
     * public void beta() { }
     * }
     */
    private static final byte[] ONLOAD_INITIAL_CLASS = Base64.getDecoder().decode(
            "ZGV4CjAzNQAQT37mO0bz7SniP0I8RLnGvVsfM5ybXmdYAgAAcAAAAHhWNBIAAAAAAAAAANABAAAI" +
            "AAAAcAAAAAMAAACQAAAAAQAAAJwAAAAAAAAAAAAAAAQAAACoAAAAAQAAAMgAAABwAQAA6AAAACgB" +
            "AAAwAQAAYAEAAHQBAACKAQAAjQEAAJQBAACaAQAAAQAAAAIAAAAEAAAABAAAAAIAAAAAAAAAAAAA" +
            "AAAAAAAAAAAABQAAAAAAAAAGAAAAAQAAAAAAAAAAAAAAAQAAAAEAAAAAAAAAAwAAAAAAAAC9AQAA" +
            "AAAAAAEAAQABAAAArgEAAAQAAABwEAMAAAAOAAEAAQAAAAAAswEAAAEAAAAOAAAAAQABAAAAAAC4" +
            "AQAAAQAAAA4AAAAGPGluaXQ+AC5MYW5kcm9pZC9qdm10aS9jdHMvbWVtb3J5X2RleC9UcmFuc2Zv" +
            "cm1UYXJnZXQ7ABJMamF2YS9sYW5nL09iamVjdDsAFFRyYW5zZm9ybVRhcmdldC5qYXZhAAFWAAVh" +
            "bHBoYQAEYmV0YQASZW1pdHRlcjogamFjay00LjI4AAIABw4AAwAHDgAEAAcOAAAAAQIAgYAE6AEB" +
            "AYACAQGUAgALAAAAAAAAAAEAAAAAAAAAAQAAAAgAAABwAAAAAgAAAAMAAACQAAAAAwAAAAEAAACc" +
            "AAAABQAAAAQAAACoAAAABgAAAAEAAADIAAAAASAAAAMAAADoAAAAAiAAAAgAAAAoAQAAAyAAAAMA" +
            "AACuAQAAACAAAAEAAAC9AQAAABAAAAEAAADQAQAA");
    /**
     * Base64 encoded version of the following class.
     * Note that this would be an illegal transformation if the class had been loaded as the first
     * one.
     * <p>
     * package android.jvmti.cts.memory_dex;
     * <p>
     * public class TransformTarget {
     * public void alpha(int abc) {
     * }
     * public int beta() {
     * return 12;
     * }
     * public void gamma() {
     * }
     * }
     */
    private static final byte[] ONLOAD_FINAL_CLASS = Base64.getDecoder().decode(
            "ZGV4CjAzNQA5VqbusSyl8/G1EXrbm9uRuiHvkP4XixrMAgAAcAAAAHhWNBIAAAAAAAAAADgCAAAL" +
            "AAAAcAAAAAQAAACcAAAAAwAAAKwAAAAAAAAAAAAAAAUAAADQAAAAAQAAAPgAAAC0AQAAGAEAAHYB" +
            "AAB+AQAAgQEAALEBAADFAQAA2wEAAN4BAADiAQAA6QEAAO8BAAADAgAAAQAAAAIAAAADAAAABQAA" +
            "AAEAAAAAAAAAAAAAAAUAAAADAAAAAAAAAAYAAAADAAAAcAEAAAEAAQAAAAAAAQACAAcAAAABAAAA" +
            "CAAAAAEAAQAKAAAAAgABAAAAAAABAAAAAQAAAAIAAAAAAAAABAAAAAAAAAAfAgAAAAAAAAEAAQAB" +
            "AAAACgIAAAQAAABwEAQAAAAOAAIAAgAAAAAADwIAAAEAAAAOAAAAAgABAAAAAAAVAgAAAwAAABMA" +
            "DAAPAAAAAQABAAAAAAAaAgAAAQAAAA4AAAABAAAAAAAGPGluaXQ+AAFJAC5MYW5kcm9pZC9qdm10" +
            "aS9jdHMvbWVtb3J5X2RleC9UcmFuc2Zvcm1UYXJnZXQ7ABJMamF2YS9sYW5nL09iamVjdDsAFFRy" +
            "YW5zZm9ybVRhcmdldC5qYXZhAAFWAAJWSQAFYWxwaGEABGJldGEAEmVtaXR0ZXI6IGphY2stNC4y" +
            "OAAFZ2FtbWEAAgAHDgAEAQAHDgAGAAcOAAkABw4AAAABAwCBgASYAgEBsAIBAcQCAQHcAgAAAAwA" +
            "AAAAAAAAAQAAAAAAAAABAAAACwAAAHAAAAACAAAABAAAAJwAAAADAAAAAwAAAKwAAAAFAAAABQAA" +
            "ANAAAAAGAAAAAQAAAPgAAAABIAAABAAAABgBAAABEAAAAQAAAHABAAACIAAACwAAAHYBAAADIAAA" +
            "BAAAAAoCAAAAIAAAAQAAAB8CAAAAEAAAAQAAADgCAAA=");

    private static class ExpectedMethod {
        public final Class<?> returnType;
        public final String name;
        public final Class<?>[] params;

        public ExpectedMethod(Class<?> returnType, String name, Class<?>... params) {
            this.returnType = returnType;
            this.name = name;
            this.params = params;
        }

        public void ensureHasMethod(Class<?> klass) throws Exception {
            try {
                assertEquals(returnType, klass.getDeclaredMethod(name, params).getReturnType());
            } catch (NoSuchMethodException e) {
                Assert.fail("Could not find method: " + klass + ": " + name +
                        " (params: " + Arrays.toString(params) + "). Reason was " + e);
            }
        }
    }

    private void checkClassHasMethods(Class<?> target, ExpectedMethod[] methods) throws Exception {
        for (ExpectedMethod m : methods) {
            m.ensureHasMethod(target);
        }
    }

    @Test
    public void testCannotRetransformOnLoadTest() throws Exception {
        // Just a sanity check along with below.
        Class<?> target_class = new InMemoryDexClassLoader(
                ByteBuffer.wrap(ONLOAD_INITIAL_CLASS),
                getClass().getClassLoader()).loadClass(ONLOAD_TEST_CLASS_NAME);
        checkClassHasMethods(
                target_class,
                new ExpectedMethod[]{
                        new ExpectedMethod(Void.TYPE, "alpha"),
                        new ExpectedMethod(Void.TYPE, "beta"),
                });
        assertTrue(JvmtiErrors.NONE != redefineClass(target_class, ONLOAD_FINAL_CLASS));
    }

    @Test
    public void testRetransformOnLoad() throws Exception {
        pushTransformationResult(ONLOAD_TEST_CLASS_NAME.replace('.', '/'), ONLOAD_FINAL_CLASS);
        setTransformationEvent(true);
        setPopTransformations(false);
        checkClassHasMethods(
                new InMemoryDexClassLoader(ByteBuffer.wrap(ONLOAD_INITIAL_CLASS),
                        getClass().getClassLoader()).loadClass(ONLOAD_TEST_CLASS_NAME),
                new ExpectedMethod[]{
                        new ExpectedMethod(Void.TYPE, "alpha", int.class),
                        new ExpectedMethod(int.class, "beta"),
                        new ExpectedMethod(Void.TYPE, "gamma"),
                });
    }

    private static native int redefineClass(Class<?> target, byte[] dex);

    private static native int retransformClass(Class<?> target);

    private static native void setTransformationEvent(boolean enable);

    private static native void clearTransformations();

    private static native void pushTransformationResult(String target, byte[] dexBytes);

    private static native void setPopTransformations(boolean enable);

    private static void pushTransformationResult(Class<?> target, byte[] dex) {
        pushTransformationResult(target.getName().replace('.', '/'), dex);
    }
}
