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

import android.location.GnssClock;
import android.location.GnssMeasurement;
import android.location.GnssMeasurementsEvent;
import android.location.GnssNavigationMessage;
import android.location.GnssStatus;
import android.util.Log;
import android.location.cts.pseudorange.Ecef2EnuConverter.EnuValues;
import android.location.cts.pseudorange.Ecef2LlaConverter.GeodeticLlaValues;
import android.location.cts.nano.Ephemeris.GpsEphemerisProto;
import android.location.cts.nano.Ephemeris.GpsNavMessageProto;
import android.location.cts.pseudorange.GpsTime;
import android.location.cts.suplClient.SuplRrlpController;
import java.io.BufferedReader;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Helper class for calculating GPS position and velocity solution using weighted least squares
 * where the raw GPS measurements are parsed as a {@link BufferedReader}.
 *
 */
public class PseudorangePositionVelocityFromRealTimeEvents {

  private static final String TAG = "PseudorangePositionVelocityFromRealTimeEvents";
  private static final double SECONDS_PER_NANO = 1.0e-9;
  private static final int TOW_DECODED_MEASUREMENT_STATE_BIT = 3;
  /** Average signal travel time from GPS satellite and earth */
  private static final int VALID_ACCUMULATED_DELTA_RANGE_STATE = 1;
  private static final int MINIMUM_NUMBER_OF_USEFUL_SATELLITES = 4;
  private static final int C_TO_N0_THRESHOLD_DB_HZ = 18;
  /** Maximum possible number of GPS satellites */
  private static final int MAX_NUMBER_OF_SATELLITES = 32;

  private static final String SUPL_SERVER_NAME = "supl.google.com";
  private static final int SUPL_SERVER_PORT = 7276;

  private final double[] mPositionSolutionLatLngDeg = {Double.NaN, Double.NaN, Double.NaN};
  private final double[] mVelocitySolutionEnuMps = {Double.NaN, Double.NaN, Double.NaN};
  private final double[] mPositionVelocityUncertaintyEnu = {
      Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN
  };
  private boolean mFirstUsefulMeasurementSet = true;
  private int[] mReferenceLocation = null;
  private long mLastReceivedSuplMessageTimeMillis = 0;
  private long mDeltaTimeMillisToMakeSuplRequest = TimeUnit.MINUTES.toMillis(30);
  private boolean mFirstSuplRequestNeeded = true;
  private GpsNavMessageProto mGpsNavMessageProtoUsed = null;

  private final UserPositionVelocityWeightedLeastSquare mUserPositionVelocityLeastSquareCalculator =
      new UserPositionVelocityWeightedLeastSquare();
  private GpsMeasurement[] mUsefulSatellitesToReceiverMeasurements =
      new GpsMeasurement[MAX_NUMBER_OF_SATELLITES];
  private Long[] mUsefulSatellitesToTowNs = new Long[MAX_NUMBER_OF_SATELLITES];
  private long mLargestTowNs = Long.MIN_VALUE;
  private double mArrivalTimeSinceGPSWeekNs = 0.0;
  private int mDayOfYear1To366 = 0;
  private int mGpsWeekNumber = 0;
  private long mArrivalTimeSinceGpsEpochNs = 0;

  /**
   * Computes Weighted least square position and velocity solutions from a received
   * {@link GnssMeasurementsEvent} and store the result in {@link
   * PseudorangePositionVelocityFromRealTimeEvents#mPositionSolutionLatLngDeg} and
   * {@link PseudorangePositionVelocityFromRealTimeEvents#mVelocitySolutionEnuMps}
   */
  public void computePositionVelocitySolutionsFromRawMeas(GnssMeasurementsEvent event)
      throws Exception {
    if (mReferenceLocation == null) {
      // If no reference location is received, we can not get navigation message from SUPL and hence
      // we will not try to compute location.
      Log.d(TAG, " No reference Location ..... no position is calculated");
      return;
    }
    for (int i = 0; i < MAX_NUMBER_OF_SATELLITES; i++) {
      mUsefulSatellitesToReceiverMeasurements[i] = null;
      mUsefulSatellitesToTowNs[i] = null;
    }
    
      GnssClock gnssClock = event.getClock();
    mArrivalTimeSinceGpsEpochNs = gnssClock.getTimeNanos() - gnssClock.getFullBiasNanos();
      for (GnssMeasurement measurement : event.getMeasurements()) {
      // ignore any measurement if it is not from GPS constellation
      if (measurement.getConstellationType() != GnssStatus.CONSTELLATION_GPS) {
          continue;
        }
        // ignore raw data if time is zero, if signal to noise ratio is below threshold or if
        // TOW is not yet decoded
        if (measurement.getCn0DbHz() >= C_TO_N0_THRESHOLD_DB_HZ
            && (measurement.getState() & (1L << TOW_DECODED_MEASUREMENT_STATE_BIT)) != 0) {
          // calculate day of year and Gps week number needed for the least square
          GpsTime gpsTime = new GpsTime(mArrivalTimeSinceGpsEpochNs);
          // Gps weekly epoch in Nanoseconds: defined as of every Sunday night at 00:00:000
          long gpsWeekEpochNs = GpsTime.getGpsWeekEpochNano(gpsTime);
          mArrivalTimeSinceGPSWeekNs = mArrivalTimeSinceGpsEpochNs - gpsWeekEpochNs;
          mGpsWeekNumber = gpsTime.getGpsWeekSecond().first;
          // calculate day of the year between 1 and 366
          Calendar cal = gpsTime.getTimeInCalendar();
          mDayOfYear1To366 = cal.get(Calendar.DAY_OF_YEAR);

          long receivedGPSTowNs = measurement.getReceivedSvTimeNanos();
          if (receivedGPSTowNs > mLargestTowNs) {
            mLargestTowNs = receivedGPSTowNs;
          }
          mUsefulSatellitesToTowNs[measurement.getSvid() - 1] = receivedGPSTowNs;
          GpsMeasurement gpsReceiverMeasurement =
              new GpsMeasurement(
                  (long) mArrivalTimeSinceGPSWeekNs,
                  measurement.getAccumulatedDeltaRangeMeters(),
                  measurement.getAccumulatedDeltaRangeState()
                      == VALID_ACCUMULATED_DELTA_RANGE_STATE,
                  measurement.getPseudorangeRateMetersPerSecond(),
                  measurement.getCn0DbHz(),
                  measurement.getAccumulatedDeltaRangeUncertaintyMeters(),
                  measurement.getPseudorangeRateUncertaintyMetersPerSecond());
          mUsefulSatellitesToReceiverMeasurements[measurement.getSvid() - 1] =
              gpsReceiverMeasurement;
        }
      }

    Log.d(TAG, "Using navigation message from SUPL server");
    if (mFirstSuplRequestNeeded
        || (System.currentTimeMillis() - mLastReceivedSuplMessageTimeMillis)
            > mDeltaTimeMillisToMakeSuplRequest) {
      // The following line is blocking call for SUPL connection and back. But it is fast enough
      mGpsNavMessageProtoUsed = getSuplNavMessage(mReferenceLocation[0], mReferenceLocation[1]);
      if (!isEmptyNavMessage(mGpsNavMessageProtoUsed)) {
        mFirstSuplRequestNeeded = false;
        mLastReceivedSuplMessageTimeMillis = System.currentTimeMillis();
      } else {
        return;
      }
    }


    // some times the SUPL server returns less satellites than the visible ones, so remove those
    // visible satellites that are not returned by SUPL
    for (int i = 0; i < MAX_NUMBER_OF_SATELLITES; i++) {
      if (mUsefulSatellitesToReceiverMeasurements[i] != null
          && !navMessageProtoContainsSvid(mGpsNavMessageProtoUsed, i + 1)) {
        mUsefulSatellitesToReceiverMeasurements[i] = null;
        mUsefulSatellitesToTowNs[i] = null;
      }
    }
      
      // calculate the number of useful satellites
      int numberOfUsefulSatellites = 0;
      for (int i = 0; i < mUsefulSatellitesToReceiverMeasurements.length; i++) {
        if (mUsefulSatellitesToReceiverMeasurements[i] != null) {
          numberOfUsefulSatellites++;
        }
      }
      if (numberOfUsefulSatellites >= MINIMUM_NUMBER_OF_USEFUL_SATELLITES) {
        // ignore first set of > 4 satellites as they often result in erroneous position
        if (!mFirstUsefulMeasurementSet) {
          // start with last known position and velocity of zero. Following the structure:
          // [X position, Y position, Z position, clock bias,
          //  X Velocity, Y Velocity, Z Velocity, clock bias rate]
          double[] positionVeloctySolutionEcef = {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0};
          double[] positionVelocityUncertaintyEnu = new double[] {0.0, 0.0, 0.0, 0.0, 0.0, 0.0};
          performPositionVelocityComputationEcef(
              mUserPositionVelocityLeastSquareCalculator,
              mUsefulSatellitesToReceiverMeasurements,
              mUsefulSatellitesToTowNs,
              mLargestTowNs,
              mArrivalTimeSinceGPSWeekNs,
              mDayOfYear1To366,
              mGpsWeekNumber,
              positionVeloctySolutionEcef,
              positionVelocityUncertaintyEnu);
          // convert the position solution from ECEF to latitude, longitude and altitude
          GeodeticLlaValues latLngAlt =
              Ecef2LlaConverter.convertECEFToLLACloseForm(
                  positionVeloctySolutionEcef[0],
                  positionVeloctySolutionEcef[1],
                  positionVeloctySolutionEcef[2]);
          mPositionSolutionLatLngDeg[0] = Math.toDegrees(latLngAlt.latitudeRadians);
          mPositionSolutionLatLngDeg[1] = Math.toDegrees(latLngAlt.longitudeRadians);
          mPositionSolutionLatLngDeg[2] = latLngAlt.altitudeMeters;
          mPositionVelocityUncertaintyEnu[0] = positionVelocityUncertaintyEnu[0];
          mPositionVelocityUncertaintyEnu[1] = positionVelocityUncertaintyEnu[1];
          mPositionVelocityUncertaintyEnu[2] = positionVelocityUncertaintyEnu[2];
          Log.d(TAG,
              "Position Uncertainty ENU Meters :"
                  + mPositionVelocityUncertaintyEnu[0]
                  + " "
                  + mPositionVelocityUncertaintyEnu[1]
                  + " "
                  + mPositionVelocityUncertaintyEnu[2]);
          Log.d(
              TAG,
              "Latitude, Longitude, Altitude: "
                  + mPositionSolutionLatLngDeg[0]
                  + " "
                  + mPositionSolutionLatLngDeg[1]
                  + " "
                  + mPositionSolutionLatLngDeg[2]);
          EnuValues velocityEnu = Ecef2EnuConverter.convertEcefToEnu(
              positionVeloctySolutionEcef[4],
              positionVeloctySolutionEcef[5],
              positionVeloctySolutionEcef[6],
              latLngAlt.latitudeRadians,
              latLngAlt.longitudeRadians
          );

          mVelocitySolutionEnuMps[0] = velocityEnu.enuEast;
          mVelocitySolutionEnuMps[1] = velocityEnu.enuNorth;
          mVelocitySolutionEnuMps[2] = velocityEnu.enuUP;
          Log.d(
              TAG,
              "Velocity ENU Mps: "
                  + mVelocitySolutionEnuMps[0]
                  + " "
                  + mVelocitySolutionEnuMps[1]
                  + " "
                  + mVelocitySolutionEnuMps[2]);
          mPositionVelocityUncertaintyEnu[3] = positionVelocityUncertaintyEnu[3];
          mPositionVelocityUncertaintyEnu[4] = positionVelocityUncertaintyEnu[4];
          mPositionVelocityUncertaintyEnu[5] = positionVelocityUncertaintyEnu[5];
          Log.d(TAG,
              "Velocity Uncertainty ENU Mps :"
                  + mPositionVelocityUncertaintyEnu[3]
                  + " "
                  + mPositionVelocityUncertaintyEnu[4]
                  + " "
                  + mPositionVelocityUncertaintyEnu[5]);
        }
        mFirstUsefulMeasurementSet = false;
      } else {
        Log.d(
            TAG,
            "Less than four satellites with SNR above threshold visible ... "
                + "no position is calculated!");

        mPositionSolutionLatLngDeg[0] = Double.NaN;
        mPositionSolutionLatLngDeg[1] = Double.NaN;
        mPositionSolutionLatLngDeg[2] = Double.NaN;
        mVelocitySolutionEnuMps[0] = Double.NaN;
        mVelocitySolutionEnuMps[1] = Double.NaN;
        mVelocitySolutionEnuMps[2] = Double.NaN;
    }
  }

  private boolean isEmptyNavMessage(GpsNavMessageProto navMessageProto) {
    if(navMessageProto.iono == null)return true;
    if(navMessageProto.ephemerids.length ==0)return true;
    return  false;
  }

  private boolean navMessageProtoContainsSvid(GpsNavMessageProto navMessageProto, int svid) {
    List<GpsEphemerisProto> ephemeridesList =
        new ArrayList<GpsEphemerisProto>(Arrays.asList(navMessageProto.ephemerids));
    for (GpsEphemerisProto ephProtoFromList : ephemeridesList) {
      if (ephProtoFromList.prn == svid) {
        return true;
      }
    }
    return false;
  }

  /**
   * Calculates ECEF least square position and velocity solutions from an array of
   * {@link GpsMeasurement} in meters and meters per second and store the result in
   * {@code positionVelocitySolutionEcef}
   */
  private void performPositionVelocityComputationEcef(
      UserPositionVelocityWeightedLeastSquare userPositionVelocityLeastSquare,
      GpsMeasurement[] usefulSatellitesToReceiverMeasurements,
      Long[] usefulSatellitesToTOWNs,
      long largestTowNs,
      double arrivalTimeSinceGPSWeekNs,
      int dayOfYear1To366,
      int gpsWeekNumber,
      double[] positionVelocitySolutionEcef,
      double[] positionVelocityUncertaintyEnu)
      throws Exception {

    List<GpsMeasurementWithRangeAndUncertainty> usefulSatellitesToPseudorangeMeasurements =
        UserPositionVelocityWeightedLeastSquare.computePseudorangeAndUncertainties(
            Arrays.asList(usefulSatellitesToReceiverMeasurements),
            usefulSatellitesToTOWNs,
            largestTowNs);

    // calculate iterative least square position solution and velocity solutions
    userPositionVelocityLeastSquare.calculateUserPositionVelocityLeastSquare(
        mGpsNavMessageProtoUsed,
        usefulSatellitesToPseudorangeMeasurements,
        arrivalTimeSinceGPSWeekNs * SECONDS_PER_NANO,
        gpsWeekNumber,
        dayOfYear1To366,
        positionVelocitySolutionEcef,
        positionVelocityUncertaintyEnu);

    Log.d(
        TAG,
        "Least Square Position Solution in ECEF meters: "
            + positionVelocitySolutionEcef[0]
            + " "
            + positionVelocitySolutionEcef[1]
            + " "
            + positionVelocitySolutionEcef[2]);
    Log.d(TAG, "Estimated Receiver clock offset in meters: " + positionVelocitySolutionEcef[3]);

    Log.d(TAG, "Velocity Solution in ECEF Mps: "
        + positionVelocitySolutionEcef[4]
        + " "
        + positionVelocitySolutionEcef[5]
        + " "
        + positionVelocitySolutionEcef[6]);
    Log.d(TAG, "Estimated Reciever clock offset rate in mps: " + positionVelocitySolutionEcef[7]);
  }

  /**
   * Reads the navigation message from the SUPL server by creating a Stubby client to Stubby server
   * that wraps the SUPL server. The input is the time in nanoseconds since the GPS epoch at which
   * the navigation message is required and the output is a {@link GpsNavMessageProto}
   *
   * @throws IOException
   * @throws UnknownHostException
   */
  private GpsNavMessageProto getSuplNavMessage(long latE7, long lngE7)
      throws UnknownHostException, IOException {
    SuplRrlpController suplRrlpController =
        new SuplRrlpController(SUPL_SERVER_NAME, SUPL_SERVER_PORT);
    GpsNavMessageProto navMessageProto = suplRrlpController.generateNavMessage(latE7, lngE7);

    return navMessageProto;
  }

  /** Sets a rough location of the receiver that can be used to request SUPL assistance data */
  public void setReferencePosition(int latE7, int lngE7, int altE7) {
    if (mReferenceLocation == null) {
      mReferenceLocation = new int[3];
    }
    mReferenceLocation[0] = latE7;
    mReferenceLocation[1] = lngE7;
    mReferenceLocation[2] = altE7;
  }

  /** Returns the last computed weighted least square position solution */
  public double[] getPositionSolutionLatLngDeg() {
    return mPositionSolutionLatLngDeg;
  }

  /** Returns the last computed Velocity solution */
  public double[] getVelocitySolutionEnuMps() {
    return mVelocitySolutionEnuMps;
  }

  /** Returns the last computed position velocity uncertainties in meters and meter per seconds,
   * consecutively.  */
  public double[] getPositionVelocityUncertaintyEnu() {
    return mPositionVelocityUncertaintyEnu;
  }
}
