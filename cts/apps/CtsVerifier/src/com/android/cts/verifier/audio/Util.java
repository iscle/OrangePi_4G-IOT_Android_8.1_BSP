package com.android.cts.verifier.audio;

import org.apache.commons.math.complex.Complex;
import org.apache.commons.math.stat.descriptive.moment.Mean;
import org.apache.commons.math.stat.descriptive.moment.StandardDeviation;
import org.apache.commons.math.stat.descriptive.rank.Median;
import org.apache.commons.math.transform.FastFourierTransformer;

/**
 * This class contains util functions used in the WavAnalyzer.
 */
public class Util {

  /**
   * Convert time in second to sample array length.
   */
  public static int toLength(double duration, int sampleRate) {
    return (int) Math.round(duration * sampleRate);
  }

  /**
   * Calculate mean of data.
   */
  public static double mean(double[] data) {
    Mean mean = new Mean();
    return mean.evaluate(data);
  }

  /**
   * Calculate standard deviation of data.
   */
  public static double std(double[] data) {
    StandardDeviation std = new StandardDeviation();
    return std.evaluate(data);
  }

  /**
   * Calculate median of data.
   */
  public static double median(double[] data) {
    Median median = new Median();
    median.setData(data);
    return median.evaluate();
  }

  /**
   * Pad zeros at the end, total length of array will be specified as length. If length is smaller
   * than the length of the data, it returns the data truncated to the length.
   */
  public static Complex[] padZeros(Complex[] data, int length) {
    Complex[] result = new Complex[length];
    if (length < data.length) {
      System.arraycopy(data, 0, result, 0, length);
    } else {
      System.arraycopy(data, 0, result, 0, data.length);
      for (int i = data.length; i < result.length; i++) {
        result[i] = new Complex(0, 0);
      }
    }
    return result;
  }

  /**
   * Calculate cross correlation using FFT with periodic boundary handling.
   */
  public static double[] computeCrossCorrelation(Complex[] data1, Complex[] data2) {
    FastFourierTransformer fft = new FastFourierTransformer();
    int n = nextPowerOfTwo(Math.max(data1.length, data2.length));
    Complex[] data1Fft = fft.transform(padZeros(data1, n));
    Complex[] data2Fft = fft.transform(padZeros(data2, n));
    Complex[] dottedData = new Complex[n];
    for (int i = 0; i < n; i++) {
      dottedData[i] = data1Fft[i].multiply(data2Fft[i].conjugate());
    }
    Complex[] resultComplex = fft.inversetransform(dottedData);
    double[] resultDouble = new double[resultComplex.length];
    for (int i = 0; i < resultComplex.length; i++) {
      resultDouble[i] = resultComplex[i].abs();
    }
    return resultDouble;
  }

  /**
   * Convert an short array to a double array.
   */
  public static double[] toDouble(short[] data) {
    double[] result = new double[data.length];
    for (int i = 0; i < data.length; i++) {
      result[i] = data[i];
    }
    return result;
  }

  /**
   * Convert a double array to a complex array.
   */
  public static Complex[] toComplex(double[] data) {
    Complex[] result = new Complex[data.length];
    for (int i = 0; i < data.length; i++) {
      result[i] = new Complex(data[i], 0.0);
    }
    return result;
  }

  /**
   * Calculates the next power of 2, greater than or equal to the input positive integer. If the
   * input is not a positive integer, it returns 1.
   */
  public static int nextPowerOfTwo(int n) {
    return 1 << (32 - Integer.numberOfLeadingZeros(n - 1));
  }

  /**
   * Find the index with the max value in an array.
   */
  public static int findMaxIndex(double[] data) {
    return findMaxIndex(data, 0, data.length - 1);
  }

  /**
   * Find the index with the max value in a sub-array.
   */
  public static int findMaxIndex(double[] data, int startIndex, int endIndex) {
    int maxIndex = startIndex;
    for (int i = startIndex + 1; i <= endIndex; i++) {
      if (data[i] > data[maxIndex]) {
        maxIndex = i;
      }
    }
    return maxIndex;
  }

  /**
   * Returns the index of an array with the array value closest to the desired value.
   */
  public static int findClosest(double[] array, double value) {
    double[] diffArray = new double[array.length];
    for (int i = 0; i < array.length; i++) {
      diffArray[i] = Math.abs(value - array[i]);
    }
    int index = 0;
    for (int i = 1; i < array.length; i++) {
      if (diffArray[i] < diffArray[index]) {
        index = i;
        if (diffArray[index] == 0) {
          break;
        }
      }
    }
    return index;
  }
}
