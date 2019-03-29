package com.android.cts.verifier.audio;

import android.media.AudioManager;
import android.media.AudioTrack;

import java.util.ArrayList;
import java.util.Random;

/**
 * This class stores common constants and methods.
 */
public class Common {

  public static final int RECORDING_SAMPLE_RATE_HZ
      = AudioRecordHelper.getInstance().getSampleRate();
  public static final int PLAYING_SAMPLE_RATE_HZ
      = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC);

  // Default constants.
  public static final double PASSING_THRESHOLD_DB = -40.0;
  public static final double PIP_DURATION_S = 0.004;
  public static final double PAUSE_DURATION_S = 0.016;
  public static final int PREFIX_NUM_CHIPS = 1023;
  public static final int PREFIX_SAMPLES_PER_CHIP = 4;
  public static final double PREFIX_LENGTH_S = 0.1;
  public static final double PAUSE_BEFORE_PREFIX_DURATION_S = 0.5;
  public static final double PAUSE_AFTER_PREFIX_DURATION_S = 0.4;
  public static final double MIN_FREQUENCY_HZ = 500;
  public static final double MAX_FREQUENCY_HZ = 21000;
  public static final double FREQUENCY_STEP_HZ = 100;
  public static final int SIGNAL_MIN_STRENGTH_DB_ABOVE_NOISE = 10;
  public static final int REPETITIONS = 5;
  public static final int NOISE_SAMPLES = 3;

  public static final double[] FREQUENCIES_ORIGINAL = originalFrequencies();
  public static final int PIP_NUM = FREQUENCIES_ORIGINAL.length;
  public static final int[] ORDER = order();
  public static final double[] FREQUENCIES = frequencies();

  public static final double[] WINDOW_FOR_RECORDER =
      hann(Util.toLength(PIP_DURATION_S, RECORDING_SAMPLE_RATE_HZ));
  public static final double[] WINDOW_FOR_PLAYER =
      hann(Util.toLength(PIP_DURATION_S, PLAYING_SAMPLE_RATE_HZ));

  public static final double[] PREFIX_FOR_RECORDER = prefix(RECORDING_SAMPLE_RATE_HZ);
  public static final double[] PREFIX_FOR_PLAYER = prefix(PLAYING_SAMPLE_RATE_HZ);

  /**
   * Get a Hann window.
   */
  private static double[] hann(int windowWidth) {
    double[] envelopeArray = new double[windowWidth];
    for (int i = 0; i < windowWidth; i++) {
      envelopeArray[i] = 0.5
          * (1 - Math.cos(2 * Math.PI * i / windowWidth));
    }
    return envelopeArray;
  }

  /**
   * Get a maximum length sequence, used as prefix to indicate start of signal.
   */
  private static double[] prefix(int rate) {
    double[] codeSequence = new double[PREFIX_NUM_CHIPS];
    for (int i = 0; i < PREFIX_NUM_CHIPS; i++) {
      if (i < 10) {
        codeSequence[i] = 1;
      } else {
        codeSequence[i] = -codeSequence[i - 6] * codeSequence[i - 7]
            * codeSequence[i - 9] * codeSequence[i - 10];
      }
    }
    double[] prefixArray = new double[PREFIX_NUM_CHIPS * PREFIX_SAMPLES_PER_CHIP];
    int offset = 0;
    for (int i = 0; i < PREFIX_NUM_CHIPS; i++) {
      double value = codeSequence[i];
      for (int j = 0; j < PREFIX_SAMPLES_PER_CHIP; j++) {
        prefixArray[offset + j] = value;
      }
      offset += PREFIX_SAMPLES_PER_CHIP;
    }
    int prefixLength = (int) Math.round(PREFIX_LENGTH_S * rate);
    double[] samplePrefixArray = new double[prefixLength];
    for (int i = 0; i < prefixLength; i++) {
      double index = (double) i / prefixLength * (prefixArray.length - 1);
      samplePrefixArray[i] = (1 - index + Math.floor(index)) * prefixArray[(int) Math.floor(index)]
          + (1 + index - Math.ceil(index)) * prefixArray[(int) Math.ceil(index)];
    }
    return samplePrefixArray;
  }

  /**
   * Returns array consists the frequencies of the test pips in the order that will be used in test.
   */
  private static double[] frequencies() {
    double[] originalFrequencies = originalFrequencies();

    double[] randomFrequencies = new double[Common.REPETITIONS * originalFrequencies.length];
    for (int i = 0; i < REPETITIONS * originalFrequencies.length; i++) {
      randomFrequencies[i] = originalFrequencies[ORDER[i] % originalFrequencies.length];
    }

    return randomFrequencies;
  }

  /**
   * Returns array consists the frequencies of the test pips.
   */
  private static double[] originalFrequencies() {
    ArrayList<Double> frequencies = new ArrayList<Double>();
    double frequency = Common.MIN_FREQUENCY_HZ;
    while (frequency <= Common.MAX_FREQUENCY_HZ) {
      frequencies.add(new Double(frequency));
      if ((frequency >= 18500) && (frequency < 20000)) {
        frequency += Common.FREQUENCY_STEP_HZ;
      } else {
        frequency += Common.FREQUENCY_STEP_HZ * 10;
      }
    }
    Double[] frequenciesArray = frequencies.toArray(new Double[frequencies.size()]);
    double[] frequenciesPrimitiveArray = new double[frequenciesArray.length];
    for (int i = 0; i < frequenciesArray.length; i++) {
      frequenciesPrimitiveArray[i] = frequenciesArray[i];
    }
    return frequenciesPrimitiveArray;
  }

  /**
   * Fisher-Yates shuffle.
   */
  private static int[] order() {
    int[] order = new int[REPETITIONS * PIP_NUM];
    long seed = 0;
    Random generator = new Random(seed);
    for (int i = 0; i < REPETITIONS * PIP_NUM; i++) {
      int j = generator.nextInt(i + 1);
      order[i] = order[j];
      order[j] = i;
    }
    return order;
  }
}
