/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.hardware.cts;

import junit.framework.Assert;

import android.content.Context;
import android.hardware.SensorManager;
import android.os.PowerManager;

import java.util.Random;

public class SensorManagerStaticTest extends SensorTestCase {
    private static final String TAG = "SensorManagerTest";

    // local float version of PI
    private static final float FLOAT_PI = (float) Math.PI;


    private PowerManager.WakeLock mWakeLock;

    @Override
    protected void setUp() throws Exception {
        Context context = getContext();
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);

        mWakeLock.acquire();
    }

    @Override
    protected void tearDown(){
        if (mWakeLock != null && mWakeLock.isHeld()) {
            mWakeLock.release();
        }
    }

    // SensorManager Tests
    public void testGetAltitude() throws Exception {
        float r, q;
        float altitude;

        // identity property
        for (r = 0.5f; r < 1.3f; r += 0.1f) {

            altitude = SensorManager.getAltitude(r * SensorManager.PRESSURE_STANDARD_ATMOSPHERE,
                                                 r * SensorManager.PRESSURE_STANDARD_ATMOSPHERE);
            assertRoughlyEqual("getAltitude identity property violated.", altitude, 0.0f, 0.1f);
        }

        // uniform increasing as pressure decreases property
        float prevAltitude = 1e5f; // 100km ceiling
        for (r = 0.5f; r < 1.3f; r += 0.01f) {
            altitude = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE,
                                                 r * SensorManager.PRESSURE_STANDARD_ATMOSPHERE);

            assertTrue("getAltitude result has to decrease as p increase.", prevAltitude > altitude);
            prevAltitude = altitude;
        }

        // compare to a reference algorithm
        final float coef = 1.0f / 5.255f;
        for (r = 0.8f; r < 1.3f; r += 0.1f) {
            for (q = 1.1f * r; q > 0.5f * r; q -= 0.1f * r) {
                float p0 = r * SensorManager.PRESSURE_STANDARD_ATMOSPHERE;
                float p  = q * SensorManager.PRESSURE_STANDARD_ATMOSPHERE;

                float t1 = SensorManager.getAltitude(p0, p);
                float t2 = 44330.f*(1.0f- (float) Math.pow(p/p0, coef));

                assertRoughlyEqual(
                      String.format("getAltitude comparing to reference algorithm failed. " +
                          "Detail: getAltitude(%f, %f) => %f, reference => %f",
                          p0, p, t1, t2),
                      t1, t2, 100.f);
            }
        }

    }

    public void testGetAngleChange() throws Exception {
        TestDataGenerator data = new TestDataGenerator();

        int i;
        float [] rotv = new float[3];
        float [] rotv2 = new float[3];

        // test many instances
        for (i=0; i<100; ++i) {
            float [] R1, R12, R2;
            // azimuth(yaw) pitch roll
            data.nextRotationAngles(rotv);
            R1 = mat9VRot(rotv); // random base

            // azimuth(yaw) pitch roll
            data.nextRotationAngles(rotv);
            R12 = mat9VRot(rotv);
            R2 = mat9Mul(R1, R12); // apply another random rotation

            // test different variations of input matrix format
            switch(i & 3) {
                case 0:
                    SensorManager.getAngleChange(rotv2, R2, R1);
                    break;
                case 1:
                    SensorManager.getAngleChange(rotv2, mat9to16(R2), R1);
                    break;
                case 2:
                    SensorManager.getAngleChange(rotv2, R2, mat9to16(R1));
                    break;
                case 3:
                    SensorManager.getAngleChange(rotv2, mat9to16(R2), mat9to16(R1));
                    break;
            }

            // check range
            assertRotationAnglesValid("getAngleChange result out of range.", rotv2);

            // avoid directly checking the rotation angles to avoid corner cases
            float [] R12rt = mat9T(mat9VRot(rotv2));
            float [] RI = mat9Mul(R12rt, R12);

            assertRoughlyEqual(
                String.format("getAngleChange result is incorrect. Details: case %d, " +
                    "truth = [%f, %f, %f], result = [%f, %f, %f]", i, rotv[0], rotv[1], rotv[2],
                    rotv2[0], rotv2[1], rotv2[2]),
                RI[0] + RI[4] + RI[8], 3.f, 1e-4f);
        }
    }

    public void testGetInclination() throws Exception {
        TestDataGenerator data = new TestDataGenerator();

        int i;
        float [] rotv = new float[3];
        float [] rotv2 = new float[3];
        float [] rotv3;

        // test many instances
        for (i = 0; i < 100; ++i) {
            float [] R;
            float angle;
            angle = (data.nextFloat()-0.5f) * FLOAT_PI;
            R = mat9Rot(SensorManager.AXIS_X, -angle);

            float angler = ((i&1) != 0) ?
                    SensorManager.getInclination(mat9to16(R)) : SensorManager.getInclination(R);
            assertRoughlyEqual(
                String.format(
                    "getInclination return incorrect result. Detail: case %d, truth %f, result %f.",
                    i, angle, angler),
                angle, angler, 1e-4f);
        }
    }

    public void testGetOrientation() throws Exception {
        TestDataGenerator data = new TestDataGenerator();

        int i;
        float [] rotv = new float[3];
        float [] rotv2 = new float[3];
        float [] rotv3;

        // test many instances
        for (i=0; i<100; ++i) {
            float [] R;
            // yaw pitch roll
            data.nextRotationAngles(rotv);
            R = mat9VRot(rotv);

            rotv3 = SensorManager.getOrientation( ((i&1) != 0) ? R : mat9to16(R), rotv2);
            assertTrue("getOrientaion has to return the array passed in argument", rotv3 == rotv2);

            // check range
            assertRotationAnglesValid("getOrientation result out of range.", rotv2);

            // Avoid directly comparing rotation angles. Instead, compare the rotation matrix.
            float [] Rr = mat9T(mat9VRot(rotv2));
            float [] RI = mat9Mul(Rr, R);

            assertRoughlyEqual(
                String.format("getOrientation result is incorrect. Details: case %d, " +
                    "truth = [%f, %f, %f], result = [%f, %f, %f]", i, rotv[0], rotv[1], rotv[2],
                    rotv2[0], rotv2[1], rotv2[2]),
                RI[0] + RI[4] + RI[8], 3.f, 1e-4f);
        }
    }

    public void testGetQuaternionFromVector() throws Exception {
        TestDataGenerator data = new TestDataGenerator();

        int i;
        float [] v;
        float [] q = new float[4];
        float [] q2 = new float[4];
        float [] v3 = new float[3];
        float [] v4 = new float[4];
        float [] v5 = new float[5];
        float [][] vs = new float[][] {v3, v4, v5};

        float [] xyzth = new float[4];
        for (i = 0; i < 100; ++i) {
            float c, s;

            data.nextRotationAxisAngle(xyzth);

            c = (float) Math.cos(xyzth[3]);
            s = (float) Math.sin(xyzth[3]);
            if (c < 0.f) {
                c = -c;
                s = -s;
            }

            v = vs[i%3];
            switch(i%3) {
                case 2:
                    v[4] = data.nextBoolean() ? data.nextFloat() : -1.f;
                case 1:
                    v[3] = c;
                case 0:
                    v[0] = s * xyzth[0];
                    v[1] = s * xyzth[1];
                    v[2] = s * xyzth[2];
            }

            q2[0] = c;
            q2[1] = v[0];
            q2[2] = v[1];
            q2[3] = v[2];

            SensorManager.getQuaternionFromVector(q, v);
            assertVectorRoughlyEqual(
                String.format("getQuaternionFromVector returns wrong results, Details: case %d, " +
                    "truth = (%f, %f, %f, %f), result = (%f, %f, %f, %f).",
                    i, q2[0], q2[1], q2[2], q2[3], q[0], q[1], q[2], q[3]),
                q, q2, 1e-4f);
        }
    }

    public void testGetRotationMatrix() throws Exception {
        TestDataGenerator data = new TestDataGenerator();
        final float gravity = 9.81f;
        final float magStrength = 50.f;

        int i;
        float [] gm = new float[9];
        float [] rotv = new float[3];
        float [] gI = null;
        float [] mI = null;
        float [] Rr = new float[9];
        float [] Ir = new float[9];

        gm[6] = gravity; // m/s^2, first column gravity

        // test many instances
        for (i=0; i<100; ++i) {
            float [] Rt;
            float incline;
            // yaw pitch roll
            data.nextRotationAngles(rotv);
            Rt = mat9T(mat9VRot(rotv)); // from world frame to phone frame
            //Rt = mat9I();

            incline = -0.9f * (data.nextFloat() - 0.5f) * FLOAT_PI; // ~ +-80 degrees
            //incline = 0.f;
            gm[4] = magStrength * (float) Math.cos(-incline); // positive means rotate downwards
            gm[7] = magStrength * (float) Math.sin(-incline);

            float [] gmb = mat9Mul(Rt, gm); // do not care about right most column
            gI = mat9Axis(gmb, SensorManager.AXIS_X);
            mI = mat9Axis(gmb, SensorManager.AXIS_Y);

            assertTrue("getRotationMatrix returns false on valid inputs",
                SensorManager.getRotationMatrix(Rr, Ir, gI, mI));

            float [] n = mat9Mul(Rr, Rt);
            assertRoughlyEqual(
                String.format("getRotationMatrix returns incorrect R matrix. " +
                    "Details: case %d, truth R = %s, result R = %s.",
                    i, mat9ToStr(mat9T(Rt)), mat9ToStr(Rr)),
                n[0] + n[4] + n[8], 3.f, 1e-4f);


            // Magnetic incline is defined so that it means the magnetic field lines is formed
            // by rotate local y axis around -x axis by incline angle. However, I matrix is
            // defined as (according to document):
            //     [0 m 0] = I * R * geomagnetic,
            // which means,
            //     I' * [0 m 0] = R * geomagnetic.
            // Thus, I' = Rot(-x, incline) and I = Rot(-x, incline)' = Rot(x, incline)
            float [] Ix = mat9Rot(SensorManager.AXIS_X, incline);
            assertVectorRoughlyEqual(
                String.format("getRotationMatrix returns incorrect I matrix. " +
                    "Details: case %d, truth I = %s, result I = %s.",
                    i, mat9ToStr(Ix), mat9ToStr(Ir)),
                Ix, Ir, 1e-4f);
        }

        // test 16 element inputs
        float [] Rr2 = new float[16];
        float [] Ir2 = new float[16];

        assertTrue("getRotationMatrix returns false on valid inputs",
            SensorManager.getRotationMatrix(Rr2, Ir2, gI, mI));

        assertVectorRoughlyEqual(
            "getRotationMatrix acts inconsistent with 9- and 16- elements matrix buffer",
            mat16to9(Rr2), Rr, 1e-4f);

        assertVectorRoughlyEqual(
            "getRotationMatrix acts inconsistent with 9- and 16- elements matrix buffer",
            mat16to9(Ir2), Ir, 1e-4f);

        // test null inputs
        assertTrue("getRotationMatrix does not handle null inputs",
            SensorManager.getRotationMatrix(Rr, null, gI, mI));

        assertTrue("getRotationMatrix does not handle null inputs",
            SensorManager.getRotationMatrix(null, Ir, gI, mI));

        assertTrue("getRotationMatrix does not handle null inputs",
            SensorManager.getRotationMatrix(null, null, gI, mI));

        // test fail cases
        // free fall, if the acc reading is less than 10% of gravity
        gI[0] = gI[1] = gI[2] = data.nextFloat() * gravity * 0.05f; // sqrt(3) * 0.05 < 0.1
         assertFalse("getRotationMatrix does not fail when it supposed to fail (gravity too small)",
            SensorManager.getRotationMatrix(Rr, Ir, gI, mI));

        // wrong input
        assertFalse("getRotationMatrix does not fail when it supposed to fail (singular axis)",
            SensorManager.getRotationMatrix(Rr, Ir, gI, gI));
    }

    public void testGetRotationMatrixFromVector() throws Exception {
        TestDataGenerator data = new TestDataGenerator();

        int i;
        float [] v;
        float [] q = new float[4];

        float [] v3 = new float[3];
        float [] v4 = new float[4];
        float [] v5 = new float[5];
        float [][] vs = new float[][]{v3, v4, v5};

        float [] m9 = new float[9];
        float [] m16 = new float[16];

        // format: x y z theta/2
        float [] xyzth = new float[4];
        // test the orthogonal property of returned matrix
        for (i=0; i<20; ++i) {
            float c, s;
            data.nextRotationAxisAngle(xyzth);

            c = (float) Math.cos(xyzth[3]);
            s = (float) Math.sin(xyzth[3]);
            if (c < 0.f) {
                c = -c;
                s = -s;
            }

            v = vs[i%3];
            switch(i%3) {
                case 2:
                    v[4] = data.nextBoolean() ? data.nextFloat() : -1.f;
                case 1:
                    v[3] = c;
                case 0:
                    v[0] = s * xyzth[0];
                    v[1] = s * xyzth[1];
                    v[2] = s * xyzth[2];
            }

            if ((i % 1) != 0) {
                SensorManager.getRotationMatrixFromVector(m16, v);
                m9 = mat16to9(m16);
            }else {
                SensorManager.getRotationMatrixFromVector(m9, v);
            }

            float [] n = mat9Mul(m9, mat9T(m9));
            assertRoughlyEqual("getRotationMatrixFromVector do not return proper matrix",
                    n[0]+ n[4] + n[8], 3.f, 1e-4f);
        }

        // test if multiple rotation (total 2pi) about an axis result in identity
        v = v3;
        float [] Rr = new float[9];

        for (i=0; i<20; ++i) {
            float j, halfTheta, residualHalfTheta = FLOAT_PI;
            float [] R = mat9I();
            float c, s;

            data.nextRotationAxisAngle(xyzth);  // half theta is ignored

            j = data.nextInt(5) + 2;  // 2 ~ 6 rotations

            while(j-- > 0) {
                if (j == 0) {
                    halfTheta = residualHalfTheta;
                } else {
                    halfTheta = data.nextFloat() * FLOAT_PI;
                }

                c = (float) Math.cos(halfTheta);
                s = (float) Math.sin(halfTheta);
                if (c < 0.f) {
                    c = -c;
                    s = -s;
                }

                v[0] = s * xyzth[0];
                v[1] = s * xyzth[1];
                v[2] = s * xyzth[2];

                SensorManager.getRotationMatrixFromVector(Rr, v);
                R = mat9Mul(Rr, R);

                residualHalfTheta -= halfTheta;
            }

            assertRoughlyEqual("getRotationMatrixFromVector returns incorrect matrix",
                    R[0] + R[4] + R[8], 3.f, 1e-4f);
        }

        // test if rotation about trival axis works
        v = v3;
        for (i=0; i<20; ++i) {
            int axis = (i % 3) + 1;
            float theta = data.nextFloat() * 2.f * FLOAT_PI;
            float [] R;

            v[0] = v[1] = v[2] = 0.f;
            v[axis - 1] = (float) Math.sin(theta / 2.f);
            if ( (float) Math.cos(theta / 2.f) < 0.f) {
                v[axis-1] = -v[axis-1];
            }

            SensorManager.getRotationMatrixFromVector(m9, v);
            R = mat9Rot(axis, theta);

            assertVectorRoughlyEqual(
                String.format("getRotationMatrixFromVector returns incorrect matrix with "+
                    "simple rotation. Details: case %d, truth R = %s, result R = %s.",
                    i, mat9ToStr(R), mat9ToStr(m9)),
                R, m9, 1e-4f);
        }
    }

    public void testRemapCoordinateSystem() throws Exception {
        TestDataGenerator data = new TestDataGenerator();

        int i, j, k;
        float [] rotv = new float[3];
        float [] Rout = new float[9];
        float [] Rout2 = new float[16];
        int a1, a2; // AXIS_X/Y/Z
        int b1, b2, b3; // AXIS_X/Y/Z w/ or w/o MINUS

        // test a few instances
        for (i=0; i<10; ++i) {
            float [] R;
            // yaw pitch roll
            data.nextRotationAngles(rotv);
            R = mat9VRot(rotv);

            // total of 6*4 = 24 variations
            // 6 = A(3,2)
            for (j=0; j<9; ++j) {
                // axis without minus
                a1 = j/3 + 1;
                a2 = j%3 + 1;

                // skip cases when two axis are the same
                if (a1 == a2) continue;

                for (k=0; k<3; ++k) {
                    // test all minus axis combination: ++, +-, -+, --
                    b1 = a1 | (((k & 2) != 0) ? 0x80 : 0);
                    b2 = a2 | (((k & 1) != 0) ? 0x80 : 0);
                    // the third axis
                    b3 = (6 - a1 -a2) |
                         ( (((a2 + 3 - a1) % 3 == 2) ? 0x80 : 0) ^ (b1 & 0x80) ^ (b2 & 0x80));

                    // test both input formats
                    if ( (i & 1) != 0 ) {
                      assertTrue(SensorManager.remapCoordinateSystem(R, b1, b2, Rout));
                    } else {
                      assertTrue(SensorManager.remapCoordinateSystem(mat9to16(R), b1, b2, Rout2));
                      Rout = mat16to9(Rout2);
                    }

                    float [] v1, v2;

                    String detail = String.format(
                            "Details: case %d (%x %x %x), original R = %s, result R = %s.",
                            i, b1, b2, b3, mat9ToStr(R), mat9ToStr(Rout));

                    v1 = mat9Axis(R, SensorManager.AXIS_X);
                    v2 = mat9Axis(Rout, b1);
                    assertVectorRoughlyEqual(
                        "remapCoordinateSystem gives incorrect result (x)." + detail,
                        v1, v2, 1e-4f);

                    v1 = mat9Axis(R, SensorManager.AXIS_Y);
                    v2 = mat9Axis(Rout, b2);
                    assertVectorRoughlyEqual(
                        "remapCoordinateSystem gives incorrect result (y)." + detail,
                        v1, v2, 1e-4f);

                    v1 = mat9Axis(R, SensorManager.AXIS_Z);
                    v2 = mat9Axis(Rout, b3);
                    assertVectorRoughlyEqual(
                        "remapCoordinateSystem gives incorrect result (z)." + detail,
                        v1, v2, 1e-4f);
                }
            }

        }

        // test cases when false should be returned
        assertTrue("remapCoordinateSystem should return false with mismatch size input and output",
                   !SensorManager.remapCoordinateSystem(Rout,
                     SensorManager.AXIS_Y, SensorManager.AXIS_Z, Rout2));
        assertTrue("remapCoordinateSystem should return false with invalid axis setting",
                   !SensorManager.remapCoordinateSystem(Rout,
                     SensorManager.AXIS_X, SensorManager.AXIS_X, Rout));
        assertTrue("remapCoordinateSystem should return false with invalid axis setting",
                   !SensorManager.remapCoordinateSystem(Rout,
                     SensorManager.AXIS_X, SensorManager.AXIS_MINUS_X, Rout));

    }

    // Utilities class & functions

    private class TestDataGenerator {
        // carry out test deterministically without manually picking numbers
        private final long DEFAULT_SEED = 0xFEDCBA9876543210l;

        private Random mRandom;

        TestDataGenerator(long seed) {
            mRandom = new Random(seed);
        }

        TestDataGenerator() {
            mRandom = new Random(DEFAULT_SEED);
        }

        void nextRotationAngles(float [] rotv) {
            assertTrue(rotv.length == 3);

            rotv[0] = (mRandom.nextFloat()-0.5f) * 2.0f * FLOAT_PI; // azimuth(yaw) -pi ~ pi
            rotv[1] = (mRandom.nextFloat()-0.5f) * FLOAT_PI; // pitch -pi/2 ~ +pi/2
            rotv[2] = (mRandom.nextFloat()-0.5f) * 2.f * FLOAT_PI; // roll -pi ~ +pi
        }

        void nextRotationAxisAngle(float [] aa) {
            assertTrue(aa.length == 4);

            aa[0] = (mRandom.nextFloat() - 0.5f) * 2.f;
            aa[1] = (mRandom.nextFloat() - 0.5f ) * 2.f * (float) Math.sqrt(1.f - aa[0] * aa[0]);
            aa[2] = (mRandom.nextBoolean() ? 1.f : -1.f) *
                        (float) Math.sqrt(1.f - aa[0] * aa[0] - aa[1] * aa[1]);
            aa[3] = mRandom.nextFloat() * FLOAT_PI;
        }

        int nextInt(int i) {
            return mRandom.nextInt(i);
        }

        float nextFloat() {
            return mRandom.nextFloat();
        }

        boolean nextBoolean() {
            return mRandom.nextBoolean();
        }
    }

    private static void assertRotationAnglesValid(String message, float[] ra) {

        assertTrue(message, ra.length == 3 &&
            ra[0] >= -FLOAT_PI && ra[0] <= FLOAT_PI &&         // azimuth
            ra[1] >= -FLOAT_PI / 2.f && ra[1] <= FLOAT_PI / 2.f && // pitch
            ra[2] >= -FLOAT_PI && ra[2] <= FLOAT_PI);          // roll
    }

    private static void assertRoughlyEqual(String message, float a, float b, float bound) {
        assertTrue(message, Math.abs(a-b) < bound);
    }

    private static void assertVectorRoughlyEqual(String message, float [] v1, float [] v2,
                                                 float bound) {
        assertTrue(message, v1.length == v2.length);
        int i;
        float sum = 0.f;
        for (i=0; i<v1.length; ++i) {
            sum += (v1[i] - v2[i]) * (v1[i] - v2[i]);
        }
        assertRoughlyEqual(message, (float)Math.sqrt(sum), 0.f, bound);
    }

    private static float [] mat9to16(float [] m) {
        assertTrue(m.length == 9);

        float [] n  = new float[16];
        int i;
        for (i=0; i<9; ++i) {
            n[i+i/3] = m[i];
        }
        n[15] = 1.f;
        return n;
    }

    private static float [] mat16to9(float [] m) {
        assertTrue(m.length == 16);

        float [] n = new float[9];
        int i;
        for (i=0; i<9; ++i) {
            n[i] = m[i + i/3];
        }
        return n;
    }

    private static float [] mat9Mul(float [] m, float [] n) {
        assertTrue(m.length == 9 && n.length == 9);

        float [] r = new float[9];
        int i, j, k;

        for (i = 0; i < 3; ++i)
            for (j = 0; j < 3; ++j)
                for (k = 0; k < 3; ++k)
                    r[i * 3 + j] += m[i * 3 + k] * n[k * 3 + j];

        return r;
    }

    private static float [] mat9T(float [] m) {
        assertTrue(m.length == 9);

        int i, j;
        float [] n = new float[9];

        for (i = 0; i < 3; ++i)
            for (j = 0; j < 3; ++j)
                n[i * 3 + j] = m[j * 3 + i];

        return n;
    }

    private static float [] mat9I() {
        float [] m = new float[9];
        m[0] = m[4] = m[8] = 1.f;
        return m;
    }

    private static float [] mat9Rot(int axis, float angle) {
        float [] m = new float[9];
        switch (axis) {
            case SensorManager.AXIS_X:
                m[0] = 1.f;
                m[4] = m[8] = (float) Math.cos(angle);
                m[5] = - (m[7] = (float) Math.sin(angle));
                break;
            case SensorManager.AXIS_Y:
                m[4] = 1.f;
                m[0] = m[8] = (float) Math.cos(angle);
                m[6] = - (m[2] = (float) Math.sin(angle));
                break;
            case SensorManager.AXIS_Z:
                m[8] = 1.f;
                m[0] = m[4] = (float) Math.cos(angle);
                m[1] = - (m[3] = (float) Math.sin(angle));
                break;
            default:
                // should never be here
                assertTrue(false);
        }
        return m;
    }

    private static float [] mat9VRot(float [] angles) {
        assertTrue(angles.length == 3);
        // yaw, android yaw rotate to -z
        float [] R = mat9Rot(SensorManager.AXIS_Z, -angles[0]);
        // pitch, android pitch rotate to -x
        R = mat9Mul(R, mat9Rot(SensorManager.AXIS_X, -angles[1]));
        // roll
        R = mat9Mul(R, mat9Rot(SensorManager.AXIS_Y, angles[2]));

        return R;
    }

    private static float [] mat9Axis(float m[], int axis) {
        assertTrue(m.length == 9);

        boolean negative = (axis & 0x80) != 0;
        float [] v = new float[3];
        int offset;

        offset = (axis & ~0x80) - 1;
        v[0] = negative ? -m[offset]   : m[offset];
        v[1] = negative ? -m[offset+3] : m[offset+3];
        v[2] = negative ? -m[offset+6] : m[offset+6];
        return v;
    }

    private static float vecInner(float u[], float v[]) {
        assertTrue(u.length == v.length);

        int i;
        float sum = 0.f;

        for (i=0; i < v.length; ++i) {
            sum += u[i]*v[i];
        }
        return (float)Math.sqrt(sum);
    }

    private static String vecToStr(float u[]) {
        int i;
        String s;
        switch (u.length) {
            case 3:
                return String.format("[%f, %f, %f]", u[0], u[1], u[2]);
            case 4:
                return String.format("(%f, %f, %f, %f)", u[0], u[1], u[2], u[3]);
            default:
                s = "[";
                for (i = 0; i < u.length-1; ++i) {
                    s += String.format("%f, ", u[i]);
                }
                s += String.format("%f]", u[i]);
                return s;
        }
    }

    private static String mat9ToStr(float m[]) {
        assertTrue(m.length == 9);
        return String.format("[%f, %f, %f; %f, %f, %f; %f, %f, %f]",
            m[0], m[1], m[2],
            m[3], m[4], m[5],
            m[6], m[7], m[8]);
    }

    private static String mat16ToStr(float m[]) {
        assertTrue(m.length == 16);
        return String.format("[%f, %f, %f, %f; %f, %f, %f, %f; %f, %f, %f, %f; %f, %f, %f, %f]",
            m[0], m[1], m[2], m[3],
            m[4], m[5], m[6], m[7],
            m[8], m[9], m[10], m[11],
            m[12], m[13], m[14], m[15]);
    }

}

