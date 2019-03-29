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

package android.location.cts.pseudorange;


/**
 * Helper class containing the basic vector and matrix operations used for calculating the position
 * solution from pseudoranges
 * TODO: use standard matrix library to replace the operations in this class.
 *
 */
public class GpsMathOperations {

  /**
   * Calculates the norm of a vector
   */
  public static double vectorNorm(double[] inputVector) {
    double normSqured = 0;
    for (int i = 0; i < inputVector.length; i++) {
      normSqured = Math.pow(inputVector[i], 2) + normSqured;
    }

    return Math.sqrt(normSqured);
  }

  /**
   * Subtract two vectors {@code firstVector} - {@code secondVector}. Both vectors should be of the
   * same length.
   */
  public static double[] subtractTwoVectors(double[] firstVector, double[] secondVector)
      throws ArithmeticException {
    double[] result = new double[firstVector.length];
    if (firstVector.length != secondVector.length) {
      throw new ArithmeticException("Input vectors are of different lengths");
    }

    for (int i = 0; i < firstVector.length; i++) {
      result[i] = firstVector[i] - secondVector[i];
    }

    return result;
  }

  /**
   * Multiply a matrix {@code matrix} by a column vector {@code vector}
   * ({@code matrix} * {@code vector}) and return the resulting vector {@resultVector}.
   * {@code matrix} and {@vector} dimensions must match.
   */
  public static double[] matrixByColVectMultiplication(double[][] matrix, double[] vector)
      throws ArithmeticException {
    double result[] = new double[matrix.length];
    int matrixLength = matrix.length;
    int vectorLength = vector.length;
    if (vectorLength != matrix[0].length) {
      throw new ArithmeticException("Matrix and vector dimensions do not match");
    }

    for (int i = 0; i < matrixLength; i++) {
      for (int j = 0; j < vectorLength; j++) {
        result[i] += matrix[i][j] * vector[j];
      }
    }

    return result;
  }

  /**
   * Dot product of a raw vector {@code firstVector} and a column vector {@code secondVector}.
   * Both vectors should be of the same length.
   */
  public static double dotProduct(double[] firstVector, double[] secondVector)
      throws ArithmeticException {
    if (firstVector.length != secondVector.length) {
      throw new ArithmeticException("Input vectors are of different lengths");
    }
    double result = 0;
    for (int i = 0; i < firstVector.length; i++) {
      result = firstVector[i] * secondVector[i] + result;
    }
    return result;
  }

}
