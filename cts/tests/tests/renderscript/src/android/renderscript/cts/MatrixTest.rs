#include "shared.rsh"

#define EXPECT(row, col, a, b)                                    \
    do {                                                          \
        if (fabs((a) - (b)) > 0.00001f) {                         \
            failed = true;                                        \
            rsDebug("Matrix operation FAILED at line", __LINE__); \
            rsDebug("  row: ", row);                              \
            rsDebug("  col: ", col);                              \
            rsDebug("  " #a, (a));                                \
            rsDebug("  " #b, (b));                                \
        }                                                         \
    } while (0)

static bool testMatrixSetAndGet() {
  bool failed = false;
  rsDebug("Testing MatrixSetAndGet", 0);

  rs_matrix2x2 m2;
  rs_matrix3x3 m3;
  rs_matrix4x4 m4;

  // Set each cell to 100 * row + col
  for (int row = 0; row < 2; row++) {
    for (int col = 0; col < 2; col++) {
      rsMatrixSet(&m2, col, row, row * 100.f + col);
    }
  }

  for (int row = 0; row < 3; row++) {
    for (int col = 0; col < 3; col++) {
      rsMatrixSet(&m3, col, row, row * 100.f + col);
    }
  }

  for (int row = 0; row < 4; row++) {
    for (int col = 0; col < 4; col++) {
      rsMatrixSet(&m4, col, row, row * 100.f + col);
    }
  }

  // Verify these values.
  for (int row = 0; row < 2; row++) {
    for (int col = 0; col < 2; col++) {
      EXPECT(row, col, rsMatrixGet(&m2, col, row), row * 100.f + col);
    }
  }

  for (int row = 0; row < 3; row++) {
    for (int col = 0; col < 3; col++) {
      EXPECT(row, col, rsMatrixGet(&m3, col, row), row * 100.f + col);
    }
  }

  for (int row = 0; row < 4; row++) {
    for (int col = 0; col < 4; col++) {
      EXPECT(row, col, rsMatrixGet(&m4, col, row), row * 100.f + col);
    }
  }

  return failed;
}

static bool testMatrixLoadFromArray() {
  bool failed = false;
  rsDebug("Testing MatrixLoadFromArray", 0);

  rs_matrix2x2 m2;
  rs_matrix3x3 m3;
  rs_matrix4x4 m4;

  // Matrix are loaded by rsMatrixLoad in a column-major format.
  const float m2Values[] = { 11.f, 21.f,
                             12.f, 22.f };
  const float m3Values[] = { 11.f, 21.f, 31.f,
                             12.f, 22.f, 32.f,
                             13.f, 23.f, 33.f };
  const float m4Values[] = { 11.f, 21.f, 31.f, 41.f,
                             12.f, 22.f, 32.f, 42.f,
                             13.f, 23.f, 33.f, 43.f,
                             14.f, 24.f, 34.f, 44.f };

  // Test loading from arrays.
  rsMatrixLoad(&m2, m2Values);
  rsMatrixLoad(&m3, m3Values);
  rsMatrixLoad(&m4, m4Values);

  // Rows and columns are 0 indexed.
  for (int row = 0; row < 2; row++) {
    for (int col = 0; col < 2; col++) {
      EXPECT(row, col, rsMatrixGet(&m2, col, row), (row + 1) * 10 + (col + 1));
    }
  }

  for (int row = 0; row < 3; row++) {
    for (int col = 0; col < 3; col++) {
      EXPECT(row, col, rsMatrixGet(&m3, col, row), (row + 1) * 10 + (col + 1));
    }
  }

  for (int row = 0; row < 4; row++) {
    for (int col = 0; col < 4; col++) {
      EXPECT(row, col, rsMatrixGet(&m4, col, row), (row + 1) * 10 + (col + 1));
    }
  }

  return failed;
}

/* Load the matrix with the values of v, where the values are in row major.
 * This makes the tests below easier to read than if they were column major,
 * as rsLoadMatrix does.
 */
static void loadByRow2(rs_matrix2x2* m2, const float* v) {
  for (int row = 0; row < 2; row++) {
    for (int col = 0; col < 2; col++) {
      rsMatrixSet(m2, col, row, v[2 * row + col]);
    }
  }
}

static void loadByRow3(rs_matrix3x3* m3, const float* v) {
  for (int row = 0; row < 3; row++) {
    for (int col = 0; col < 3; col++) {
      rsMatrixSet(m3, col, row, v[3 * row + col]);
    }
  }
}

static void loadByRow4(rs_matrix4x4* m4, const float* v) {
  for (int row = 0; row < 4; row++) {
    for (int col = 0; col < 4; col++) {
      rsMatrixSet(m4, col, row, v[4 * row + col]);
    }
  }
}

static bool testMatrixLoadFromMatrix() {
  bool failed = false;
  rsDebug("Testing MatrixLoadFromMatrix", 0);

  rs_matrix2x2 m2;
  rs_matrix3x3 m3;
  rs_matrix4x4 m4;
  const float m2Values[] = { 11.f, 12.f,
                             21.f, 22.f };
  const float m3Values[] = { 11.f, 12.f, 13.f,
                             21.f, 22.f, 23.f,
                             31.f, 32.f, 33.f };
  const float m4Values[] = { 11.f, 12.f, 13.f, 14.f,
                             21.f, 22.f, 23.f, 24.f,
                             31.f, 32.f, 33.f, 34.f,
                             41.f, 42.f, 43.f, 44.f };
  loadByRow2(&m2, m2Values);
  loadByRow3(&m3, m3Values);
  loadByRow4(&m4, m4Values);

  rs_matrix2x2 m2CopyOfM2;
  rs_matrix3x3 m3CopyOfM3;
  rs_matrix4x4 m4CopyOfM2;
  rs_matrix4x4 m4CopyOfM3;
  rs_matrix4x4 m4CopyOfM4;

  rsMatrixLoad(&m2CopyOfM2, &m2);
  rsMatrixLoad(&m3CopyOfM3, &m3);
  rsMatrixLoad(&m4CopyOfM2, &m2);
  rsMatrixLoad(&m4CopyOfM3, &m3);
  rsMatrixLoad(&m4CopyOfM4, &m4);

  for (int row = 0; row < 2; row++) {
    for (int col = 0; col < 2; col++) {
      EXPECT(row, col, rsMatrixGet(&m2CopyOfM2, col, row), m2Values[row * 2 + col]);
    }
  }

  for (int row = 0; row < 3; row++) {
    for (int col = 0; col < 3; col++) {
      EXPECT(row, col, rsMatrixGet(&m3CopyOfM3, col, row), m3Values[row * 3 + col]);
    }
  }

  for (int row = 0; row < 4; row++) {
    for (int col = 0; col < 4; col++) {
      if (col < 2 && row < 2) {
        EXPECT(row, col, rsMatrixGet(&m4CopyOfM2, col, row), m2Values[row * 2 + col]);
      } else {
        EXPECT(row, col, rsMatrixGet(&m4CopyOfM2, col, row), row == col ? 1.f : 0.f);
      }
    }
  }

  for (int row = 0; row < 4; row++) {
    for (int col = 0; col < 4; col++) {
      if (col < 3 && row < 3) {
        EXPECT(row, col, rsMatrixGet(&m4CopyOfM3, col, row), m3Values[row * 3 + col]);
      } else {
        EXPECT(row, col, rsMatrixGet(&m4CopyOfM3, col, row), row == col ? 1.f : 0.f);
      }
    }
  }

  for (int row = 0; row < 4; row++) {
    for (int col = 0; col < 4; col++) {
      EXPECT(row, col, rsMatrixGet(&m4CopyOfM4, col, row), m4Values[row * 4 + col]);
    }
  }

  return failed;
}

static bool testMatrixIdentity() {
  bool failed = false;
  rsDebug("Testing MatrixIdentity", 0);

  rs_matrix2x2 m2;
  rs_matrix3x3 m3;
  rs_matrix4x4 m4;

  rsMatrixLoadIdentity(&m2);
  rsMatrixLoadIdentity(&m3);
  rsMatrixLoadIdentity(&m4);

  for (int row = 0; row < 2; row++) {
    for (int col = 0; col < 2; col++) {
      EXPECT(row, col, rsMatrixGet(&m2, col, row), row == col ? 1.f : 0.f);
    }
  }

  for (int row = 0; row < 3; row++) {
    for (int col = 0; col < 3; col++) {
      EXPECT(row, col, rsMatrixGet(&m3, col, row), row == col ? 1.f : 0.f);
    }
  }

  for (int row = 0; row < 4; row++) {
    for (int col = 0; col < 4; col++) {
      EXPECT(row, col, rsMatrixGet(&m4, col, row), row == col ? 1.f : 0.f);
    }
  }

  return failed;
}

static bool testMatrixVectorMultiply() {
  bool failed = false;
  rsDebug("Testing MatrixVectorMultiply", 0);

  rs_matrix2x2 m2;
  rs_matrix3x3 m3;
  rs_matrix4x4 m4;
  const float m2Values[] = { 11.f, 12.f,
                             21.f, 22.f };
  const float m3Values[] = { 11.f, 12.f, 13.f,
                             21.f, 22.f, 23.f,
                             31.f, 32.f, 33.f };
  const float m4Values[] = { 11.f, 12.f, 13.f, 14.f,
                             21.f, 22.f, 23.f, 24.f,
                             31.f, 32.f, 33.f, 34.f,
                             41.f, 42.f, 43.f, 44.f };
  loadByRow2(&m2, m2Values);
  loadByRow3(&m3, m3Values);
  loadByRow4(&m4, m4Values);

  const float2 f2 = {-2.f, 3.f};
  const float3 f3 = {-7.f, 6.f, 2.f};
  const float4 f4 = {4.f, -2.f, 6.f, 5.f};

  const float2 f2r = rsMatrixMultiply(&m2, f2);
  const float3 f3r = rsMatrixMultiply(&m3, f3);
  const float4 f4r = rsMatrixMultiply(&m4, f4);
  const float4 f3m4r = rsMatrixMultiply(&m4, f3);

  // rsMatrixMultiply returns (matrix * vector)
  const float2 f2rExpectedValues = { 14.f, 24.f };
  const float3 f3rExpectedValues = { 21.f, 31.f, 41.f };
  const float4 f4rExpectedValues = {168.f, 298.f, 428.f, 558.f};
  const float4 f3m4rExpectedValues = {35.0, 55.0, 75.0, 95.0};

  for (int row = 0; row < 2; row++) {
    EXPECT(row, 0, f2r[row], f2rExpectedValues[row]);
  }

  for (int row = 0; row < 3; row++) {
    EXPECT(row, 0, f3r[row], f3rExpectedValues[row]);
  }

  for (int row = 0; row < 4; row++) {
    EXPECT(row, 0, f4r[row], f4rExpectedValues[row]);
  }

  for (int row = 0; row < 4; row++) {
    EXPECT(row, 0, f3m4r[row], f3m4rExpectedValues[row]);
  }

  return failed;
}

static bool testMatrixMultiply() {
  bool failed = false;
  rsDebug("Testing MatrixMultiply", 0);

  const float m2LeftValues[] = { 3.f, -2.f,
                                 7.f,  4.f };
  const float m2RightValues[] = { -2.f,  3.f,
                                  -7.f,  6.f };
  const float m2LeftTimesRightValues[] = {  8.f, -3.f,
                                          -42.f, 45.f };

  const float m3LeftValues[] = { 3.f, -2.f,  8.f,
                                 7.f,  4.f, -9.f,
                                 5.f, -3.f,  6.f };
  const float m3RightValues[] = { -2.f,  3.f,  7.f,
                                  -7.f,  6.f, -5.f,
                                   4.f, -4.f,  2.f };
  const float m3LeftTimesRightValues[] = { 40.f, -35.f, 47.f,
                                          -78.f,  81.f, 11.f,
                                           35.f, -27.f, 62.f };

  const float m4LeftValues[] = { 3.f, -2.f,  8.f, -5.f,
                                 7.f,  4.f, -9.f, -4.f,
                                 5.f, -3.f,  6.f, -6.f,
                                 2.f, -8.f,  1.f, -1.f };
  const float m4RightValues[] = { -2.f,  3.f,  7.f, -6.f,
                                  -7.f,  6.f, -5.f,  7.f,
                                   4.f, -4.f,  2.f,  5.f,
                                  -1.f,  1.f,  8.f, -8.f };
  const float m4LeftTimesRightValues[] = {  45.f, -40.f,   7.f,  48.f,
                                           -74.f,  77.f, -21.f, -27.f,
                                            41.f, -33.f,  14.f,  27.f,
                                            57.f, -47.f,  48.f, -55.f };

  rs_matrix2x2 m2, m2l, m2r;
  rs_matrix3x3 m3, m3l, m3r;
  rs_matrix4x4 m4, m4l, m4r;
  loadByRow2(&m2l, m2LeftValues);
  loadByRow2(&m2r, m2RightValues);
  loadByRow3(&m3l, m3LeftValues);
  loadByRow3(&m3r, m3RightValues);
  loadByRow4(&m4l, m4LeftValues);
  loadByRow4(&m4r, m4RightValues);

  // Test the two versions of multiply.
  rsMatrixLoadMultiply (&m2, &m2l, &m2r);
  rsMatrixLoadMultiply (&m3, &m3l, &m3r);
  rsMatrixLoadMultiply (&m4, &m4l, &m4r);

  rsMatrixMultiply (&m2l, &m2r);
  rsMatrixMultiply (&m3l, &m3r);
  rsMatrixMultiply (&m4l, &m4r);

  // rsMatrixMultiply returns (left * right).
  for (int row = 0; row < 2; row++) {
    for (int col = 0; col < 2; col++) {
      EXPECT(row, col, rsMatrixGet(&m2, col, row), m2LeftTimesRightValues[row * 2 + col]);
      EXPECT(row, col, rsMatrixGet(&m2l, col, row), m2LeftTimesRightValues[row * 2 + col]);
    }
  }

  for (int row = 0; row < 3; row++) {
    for (int col = 0; col < 3; col++) {
      EXPECT(row, col, rsMatrixGet(&m3, col, row), m3LeftTimesRightValues[row * 3 + col]);
      EXPECT(row, col, rsMatrixGet(&m3l, col, row), m3LeftTimesRightValues[row * 3 + col]);
    }
  }

  for (int row = 0; row < 4; row++) {
    for (int col = 0; col < 4; col++) {
      EXPECT(row, col, rsMatrixGet(&m4, col, row), m4LeftTimesRightValues[row * 4 + col]);
      EXPECT(row, col, rsMatrixGet(&m4l, col, row), m4LeftTimesRightValues[row * 4 + col]);
    }
  }

  // Verify that rsLoadMultiply can store the result in its inputs.
  loadByRow2(&m2l, m2LeftValues);
  loadByRow2(&m2r, m2RightValues);
  loadByRow3(&m3l, m3LeftValues);
  loadByRow3(&m3r, m3RightValues);
  loadByRow4(&m4l, m4LeftValues);
  loadByRow4(&m4r, m4RightValues);

  rsMatrixLoadMultiply (&m2r, &m2l, &m2r);
  rsMatrixLoadMultiply (&m3r, &m3l, &m3r);
  rsMatrixLoadMultiply (&m4r, &m4l, &m4r);

  for (int row = 0; row < 2; row++) {
    for (int col = 0; col < 2; col++) {
      EXPECT(row, col, rsMatrixGet(&m2r, col, row), m2LeftTimesRightValues[row * 2 + col]);
    }
  }

  for (int row = 0; row < 3; row++) {
    for (int col = 0; col < 3; col++) {
      EXPECT(row, col, rsMatrixGet(&m3r, col, row), m3LeftTimesRightValues[row * 3 + col]);
    }
  }

  for (int row = 0; row < 4; row++) {
    for (int col = 0; col < 4; col++) {
      EXPECT(row, col, rsMatrixGet(&m4r, col, row), m4LeftTimesRightValues[row * 4 + col]);
    }
  }

  loadByRow2(&m2l, m2LeftValues);
  loadByRow2(&m2r, m2RightValues);
  loadByRow3(&m3l, m3LeftValues);
  loadByRow3(&m3r, m3RightValues);
  loadByRow4(&m4l, m4LeftValues);
  loadByRow4(&m4r, m4RightValues);

  rsMatrixLoadMultiply (&m2l, &m2l, &m2r);
  rsMatrixLoadMultiply (&m3l, &m3l, &m3r);
  rsMatrixLoadMultiply (&m4l, &m4l, &m4r);

  for (int row = 0; row < 2; row++) {
    for (int col = 0; col < 2; col++) {
      EXPECT(row, col, rsMatrixGet(&m2l, col, row), m2LeftTimesRightValues[row * 2 + col]);
    }
  }

  for (int row = 0; row < 3; row++) {
    for (int col = 0; col < 3; col++) {
      EXPECT(row, col, rsMatrixGet(&m3l, col, row), m3LeftTimesRightValues[row * 3 + col]);
    }
  }

  for (int row = 0; row < 4; row++) {
    for (int col = 0; col < 4; col++) {
      EXPECT(row, col, rsMatrixGet(&m4l, col, row), m4LeftTimesRightValues[row * 4 + col]);
    }
  }

  return failed;
}

static bool testMatrixTranspose() {
  bool failed = false;
  rsDebug("Testing MatrixTranspose", 0);

  rs_matrix2x2 m2;
  rs_matrix3x3 m3;
  rs_matrix4x4 m4;
  const float m2Values[] = { 11.f, 12.f,
                             21.f, 22.f };
  const float m3Values[] = { 11.f, 12.f, 13.f,
                             21.f, 22.f, 23.f,
                             31.f, 32.f, 33.f };
  const float m4Values[] = { 11.f, 12.f, 13.f, 14.f,
                             21.f, 22.f, 23.f, 24.f,
                             31.f, 32.f, 33.f, 34.f,
                             41.f, 42.f, 43.f, 44.f };
  loadByRow2(&m2, m2Values);
  loadByRow3(&m3, m3Values);
  loadByRow4(&m4, m4Values);

  rsMatrixTranspose(&m4);
  rsMatrixTranspose(&m3);
  rsMatrixTranspose(&m2);

  const float m2ExpectedValues[] = { 11.f, 21.f,
                                     12.f, 22.f };
  const float m3ExpectedValues[] = { 11.f, 21.f, 31.f,
                                     12.f, 22.f, 32.f,
                                     13.f, 23.f, 33.f };
  const float m4ExpectedValues[] = { 11.f, 21.f, 31.f, 41.f,
                                     12.f, 22.f, 32.f, 42.f,
                                     13.f, 23.f, 33.f, 43.f,
                                     14.f, 24.f, 34.f, 44.f };

  for (int row = 0; row < 2; row++) {
    for (int col = 0; col < 2; col++) {
      EXPECT(row, col, rsMatrixGet(&m2, col, row), m2ExpectedValues[row * 2 + col]);
    }
  }

  for (int row = 0; row < 3; row++) {
    for (int col = 0; col < 3; col++) {
      EXPECT(row, col, rsMatrixGet(&m3, col, row), m3ExpectedValues[row * 3 + col]);
    }
  }

  for (int row = 0; row < 4; row++) {
    for (int col = 0; col < 4; col++) {
      EXPECT(row, col, rsMatrixGet(&m4, col, row), m4ExpectedValues[row * 4 + col]);
    }
  }

  return failed;
}

static bool testMatrixInverse() {
  bool failed = false;
  rsDebug("Testing MatrixInverse", 0);

  rs_matrix4x4 m4;
  const float m4Values[] = { 3.f, -2.f,  8.f, -5.f,
                             7.f,  4.f, -9.f, -4.f,
                             5.f, -3.f,  6.f, -6.f,
                             2.f, -8.f,  1.f, -1.f };
  const float m4Inverse[] = { -585.f / 16.f, -135.f / 16.f, 602.f / 16.f, -147.f / 16.f,
                              -91.f / 16.f,  -21.f / 16.f,  94.f / 16.f,  -25.f / 16.f,
                              -207.f / 16.f,  -49.f / 16.f, 214.f / 16.f,  -53.f / 16.f,
                              -649.f / 16.f, -151.f / 16.f, 666.f / 16.f, -163.f / 16.f };
  const float m4InverseTranspose[] = { -585.f / 16.f, -91.f / 16.f, -207.f / 16.f, -649.f / 16.f,
                                       -135.f / 16.f, -21.f / 16.f,  -49.f / 16.f, -151.f / 16.f,
                                        602.f / 16.f,  94.f / 16.f,  214.f / 16.f,  666.f / 16.f,
                                       -147.f / 16.f, -25.f / 16.f,  -53.f / 16.f, -163.f / 16.f };

  loadByRow4(&m4, m4Values);
  rsMatrixInverse(&m4);
  for (int row = 0; row < 4; row++) {
    for (int col = 0; col < 4; col++) {
      EXPECT(row, col, rsMatrixGet(&m4, col, row), m4Inverse[row * 4 + col]);
    }
  }

  loadByRow4(&m4, m4Values);
  rsMatrixInverseTranspose(&m4);
  for (int row = 0; row < 4; row++) {
    for (int col = 0; col < 4; col++) {
      EXPECT(row, col, rsMatrixGet(&m4, col, row), m4InverseTranspose[row * 4 + col]);
    }
  }

  return failed;
}

static bool testMatrixScale() {
  bool failed = false;
  rsDebug("Testing MatrixScale", 0);

  // Build a scaling matrix
  const float x = 2.0f;
  const float y = 0.5f;
  const float z = 3.0f;
  rs_matrix4x4 m4ScalingMatrix;
  rsMatrixLoadScale(&m4ScalingMatrix, x, y, z);
  const float m4ScalingMatrixExpectedValues[] = {   x, 0.f, 0.f, 0.f,
                                                  0.f,   y, 0.f, 0.f,
                                                  0.f, 0.f,   z, 0.f,
                                                  0.f, 0.f, 0.f, 1.f };
  for (int row = 0; row < 4; row++) {
    for (int col = 0; col < 4; col++) {
      EXPECT(row, col, rsMatrixGet(&m4ScalingMatrix, col, row),
             m4ScalingMatrixExpectedValues[row * 4 + col]);
    }
  }

  // Check that the scaling does what we expect.
  const float3 f3 = {4.f, -2.f, 6.f};
  const float4 f4r = rsMatrixMultiply(&m4ScalingMatrix, f3);
  const float4 f4rExpectedValues = {4.f * x, -2.f * y, 6.f * z, 1.f};

  for (int row = 0; row < 4; row++) {
    EXPECT(row, 0, f4r[row], f4rExpectedValues[row]);
  }

  // Test combining scale with another transformation.
  rs_matrix4x4 m4;
  const float m4Values[] = { 11.f, 12.f, 13.f, 14.f,
                             21.f, 22.f, 23.f, 24.f,
                             31.f, 32.f, 33.f, 34.f,
                             41.f, 42.f, 43.f, 44.f };
  loadByRow4(&m4, m4Values);

  // Method 1:
  rs_matrix4x4 m4s;
  rsMatrixLoadMultiply(&m4s, &m4, &m4ScalingMatrix);

  // Method 2:
  rsMatrixScale(&m4, x, y, z);

  // Verify the results

  /* We are creating a matrix that scales first then does the remaining operation.
   * It's a bit inverse of what I would have expected.
   */
  const float expectedValues[] = { x * 11.f, y * 12.f, z * 13.f, 14.f,
                                   x * 21.f, y * 22.f, z * 23.f, 24.f,
                                   x * 31.f, y * 32.f, z * 33.f, 34.f,
                                   x * 41.f, y * 42.f, z * 43.f, 44.f };
  for (int row = 0; row < 4; row++) {
    for (int col = 0; col < 4; col++) {
      EXPECT(row, col, rsMatrixGet(&m4, col, row), expectedValues[row * 4 + col]);
      EXPECT(row, col, rsMatrixGet(&m4s, col, row), expectedValues[row * 4 + col]);
    }
  }

  return failed;
}

static bool testMatrixTranslate() {
  bool failed = false;
  rsDebug("Testing MatrixTranslate", 0);

  // Build a translating matrix
  rs_matrix4x4 m4TranslatingMatrix;
  const float x = 2.0f;
  const float y = 0.5f;
  const float z = 3.0f;
  rsMatrixLoadTranslate(&m4TranslatingMatrix, x, y, z);

  const float m4TranslatingMatrixExpectedValues[] = { 1.f, 0.f, 0.f,   x,
                                                      0.f, 1.f, 0.f,   y,
                                                      0.f, 0.f, 1.f,   z,
                                                      0.f, 0.f, 0.f, 1.f };
  for (int row = 0; row < 4; row++) {
    for (int col = 0; col < 4; col++) {
      EXPECT(row, col, rsMatrixGet(&m4TranslatingMatrix, col, row),
               m4TranslatingMatrixExpectedValues[row * 4 + col]);
    }
  }

  // Check that the translation does what we expect.
  const float3 f3 = {4.f, -2.f, 6.f};
  const float4 f4r = rsMatrixMultiply(&m4TranslatingMatrix, f3);
  const float4 f4rExpectedValues = {4.f + x, -2.f + y, 6.f + z, 1.f};

  for (int row = 0; row < 4; row++) {
    EXPECT(row, 0, f4r[row], f4rExpectedValues[row]);
  }

  // Test combining translate with another transformation.
  rs_matrix4x4 m4;
  const float m4Values[] = { 11.f, 12.f, 13.f, 14.f,
                             21.f, 22.f, 23.f, 24.f,
                             31.f, 32.f, 33.f, 34.f,
                             41.f, 42.f, 43.f, 44.f };
  loadByRow4(&m4, m4Values);

  // Method 1:
  rs_matrix4x4 m4s;
  rsMatrixLoadMultiply(&m4s, &m4, &m4TranslatingMatrix);

  // Method 2:
  rsMatrixTranslate(&m4, x, y, z);

  // Verify the results

  /* We are creating a matrix that translates first then does the remaining operation.
   * It's a bit inverse of what I would have expected.
   */
  float m4ExpectedValues[] = { 11.f, 12.f, 13.f, x * 11.f + y * 12.f + z * 13.f + 14.f,
                               21.f, 22.f, 23.f, x * 21.f + y * 22.f + z * 23.f + 24.f,
                               31.f, 32.f, 33.f, x * 31.f + y * 32.f + z * 33.f + 34.f,
                               41.f, 42.f, 43.f, x * 41.f + y * 42.f + z * 43.f + 44.f };
  for (int row = 0; row < 4; row++) {
    for (int col = 0; col < 4; col++) {
      EXPECT(row, col, rsMatrixGet(&m4, col, row), m4ExpectedValues[row * 4 + col]);
      EXPECT(row, col, rsMatrixGet(&m4s, col, row), m4ExpectedValues[row * 4 + col]);
    }
  }

  return failed;
}

static bool testMatrixTranslateAndScale() {
  bool failed = false;
  rsDebug("Testing MatrixTranslateAndScale", 0);

  /* Create a matrix that will scale by (4, -2, 3) then translate by (2, 3, -1).
   * Applied to the vector (5, 6, 7), we should get:
   *   step 1: (20, -12, 21)
   *   step 2: (22, -9, 20)
   */
  const float xs = 4.f;
  const float ys = -2.f;
  const float zs = 3.f;

  const float xt = 2.f;
  const float yt = 3.f;
  const float zt = -1.f;

  rs_matrix4x4 m4;
  // Start by the second operation (yes, it's counter intuitive)
  rsMatrixLoadTranslate(&m4, xt, yt, zt);
  rsMatrixScale(&m4, xs, ys, zs);

  // Check that the transformation does what we expect.
  const float3 f3 = {5.f, 6.f, 7.f};
  const float4 f4 = rsMatrixMultiply(&m4, f3);
  const float4 f4ExpectedValues = {22.f, -9.f, 20.f, 1.f};

  for (int row = 0; row < 4; row++) {
    EXPECT(row, 0, f4[row], f4ExpectedValues[row]);
  }

  return failed;
}


static bool testMatrixRotate() {
  bool failed = false;
  rsDebug("Testing MatrixRotate", 0);

  // Build a rotating matrix.
  rs_matrix4x4 m4RotatingMatrix;
  const float rotation = 90.f; // Rotation is in degrees.
  const float x = 1.f;
  const float y = 4.f;
  const float z = 8.f;
  rsMatrixLoadRotate(&m4RotatingMatrix, rotation, x, y, z);

  /* See http://en.wikipedia.org/wiki/Rotation_matrix
   */
  const float inRadians = radians(rotation); // rotation * float(M_PI) / 180.0f;
  const float co = cos(inRadians);
  const float si = sin(inRadians);
  const float ux = 1.f / 9.f;
  const float uy = 4.f / 9.f;
  const float uz = 8.f / 9.f;
  const float m4RotatingMatrixExpectedValues[] = {
    co + ux * ux * (1.f - co), ux * uy * (1.f - co) - uz * si, ux * uz * (1.f - co) + uy * si, 0.f,
    uy * ux * (1.f - co) + uz * si, co + uy * uy * (1.f - co), uy * uz * (1.f - co) - ux * si, 0.f,
    uz * ux * (1.f - co) - uy * si, uz * uy * (1.f - co) + ux * si, co + uz * uz * (1.f - co), 0.f,
    0.f, 0.f, 0.f, 1.f
  };

  for (int row = 0; row < 4; row++) {
    for (int col = 0; col < 4; col++) {
      EXPECT(row, col, rsMatrixGet(&m4RotatingMatrix, col, row), m4RotatingMatrixExpectedValues[row * 4 + col]);
    }
  }

  // Test combining rotate with another transformation.
  rs_matrix4x4 m4;
  const float m4Values[] = { 11.f, 12.f, 13.f, 14.f,
                             21.f, 22.f, 23.f, 24.f,
                             31.f, 32.f, 33.f, 34.f,
                             41.f, 42.f, 43.f, 44.f };
  loadByRow4(&m4, m4Values);

  // Method 1:
  rs_matrix4x4 m4s;
  rsMatrixLoadMultiply(&m4s, &m4, &m4RotatingMatrix);

  // Method 2:
  rsMatrixRotate(&m4, rotation, x, y, z);

  // Verify the results
  for (int row = 0; row < 4; row++) {
    for (int col = 0; col < 4; col++) {
      EXPECT(row, col, rsMatrixGet(&m4, col, row), rsMatrixGet(&m4s, col, row));
    }
  }

  // Verify that rsMatrixRotate does what we expect on something simple to understand.
  rsMatrixLoadRotate(&m4RotatingMatrix, 60.f, 1.f, 0.f, 1.f);
  const float3 f3 = {0.f, 1.f, 0.f};
  const float4 f4r = rsMatrixMultiply(&m4RotatingMatrix, f3);
  const float k = sqrt(3.f) * sqrt(2.f) / 4.f;
  const float4 f4rExpectedValues = {-k, 0.5f, k, 1.f};

  for (int row = 0; row < 4; row++) {
    EXPECT(row, 0, f4r[row], f4rExpectedValues[row]);
  }

  return failed;
}

static bool testMatrixProjections() {
  bool failed = false;
  rsDebug("Testing MatrixProjections", 0);

  rs_matrix4x4 m4;
  const float left = -2.f;
  const float right = 4.f;
  const float bottom = -3.f;
  const float top = 5.f;
  const float near = 2.f;
  const float far = 7.f;
  const float fovy = 60.f;
  const float aspect = 2.f;

  /* Orthographic projection that remaps to the unit cube.  See
   * http://unspecified.wordpress.com/2012/06/21/calculating-the-gluperspective-matrix-and-other-opengl-matrix-maths/
   */
  rsMatrixLoadOrtho(&m4, left, right, bottom, top, near, far);
  const float orthoExpectedValues[] = {
    2.f / (right - left), 0.f, 0.f, -(right + left) / (right - left),
    0.f, 2.f / (top - bottom), 0.f, -(top + bottom) / (top - bottom),
    0.f, 0.f, -2.f / (far - near), -(far + near) / (far - near),
    0.f, 0.f, 0.f, 1.f,
  };
  for (int row = 0; row < 4; row++) {
    for (int col = 0; col < 4; col++) {
      EXPECT(row, col, rsMatrixGet(&m4, col, row), orthoExpectedValues[row * 4 + col]);
    }
  }

  /* Frustum projection.  See
   * http://www.scratchapixel.com/lessons/3d-advanced-lessons/perspective-and-orthographic-projection-matrix/opengl-perspective-projection-matrix/
   */
  rsMatrixLoadFrustum(&m4, left, right, bottom, top, near, far);
  const float frustumExpectedValues[] = {
    2.f * near / (right - left), 0.f, (right + left) / (right - left), 0.f,
    0.f, 2.f * near / (top - bottom), (top + bottom) / (top - bottom), 0.f,
    0.f, 0.f, -(far + near) / (far - near), -2.f * far * near / (far - near),
    0.f, 0.f, -1.f, 0.f,
  };
  for (int row = 0; row < 4; row++) {
    for (int col = 0; col < 4; col++) {
      EXPECT(row, col, rsMatrixGet(&m4, col, row), frustumExpectedValues[row * 4 + col]);
    }
  }

  /* Perspective projection.  See
   * http://unspecified.wordpress.com/2012/06/21/calculating-the-gluperspective-matrix-and-other-opengl-matrix-maths/
   */
  rsMatrixLoadPerspective(&m4, fovy, aspect, near, far);
  const float invTan = 1.f / tan(radians(fovy / 2.f));
  const float perspectiveExpectedValues[] = {
    invTan / aspect, 0.f, 0.f, 0.f,
    0.f, invTan, 0.f, 0.f,
    0.f, 0.f, (near + far) / (near - far), 2.f * far * near / (near - far),
    0.f, 0.f, -1.f, 0.f,
  };
  for (int row = 0; row < 4; row++) {
    for (int col = 0; col < 4; col++) {
      EXPECT(row, col, rsMatrixGet(&m4, col, row), perspectiveExpectedValues[row * 4 + col]);
    }
  }

  return failed;
}

void matrixTests() {
  bool failed = false;
  failed |= testMatrixSetAndGet();
  failed |= testMatrixLoadFromArray();
  failed |= testMatrixLoadFromMatrix();
  failed |= testMatrixIdentity();
  failed |= testMatrixVectorMultiply();
  failed |= testMatrixMultiply();
  failed |= testMatrixTranspose();
  failed |= testMatrixInverse();
  failed |= testMatrixScale();
  failed |= testMatrixTranslate();
  failed |= testMatrixTranslateAndScale();
  failed |= testMatrixRotate();
  failed |= testMatrixProjections();

  if (failed) {
    rsSendToClientBlocking(RS_MSG_TEST_FAILED);
  } else {
    rsSendToClientBlocking(RS_MSG_TEST_PASSED);
  }
}
