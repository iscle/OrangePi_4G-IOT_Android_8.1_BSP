package android.location.cts;

import android.location.GnssStatus;
import android.util.Log;

public class GnssStatusTest extends GnssTestCase  {

    private static final String TAG = "GnssStatusTest";
    private static final int LOCATION_TO_COLLECT_COUNT = 1;
    private static final int STATUS_TO_COLLECT_COUNT = 3;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    mTestLocationManager = new TestLocationManager(getContext());
  }

  /**
   * Tests that one can listen for {@link GnssStatus}.
   */
  public void testGnssStatusChanges() throws Exception {
    // Checks if GPS hardware feature is present, skips test (pass) if not,
    // and hard asserts that Location/GPS (Provider) is turned on if is Cts Verifier.
    if (!TestMeasurementUtil.canTestRunOnCurrentDevice(mTestLocationManager,
        TAG, MIN_HARDWARE_YEAR_MEASUREMENTS_REQUIRED, isCtsVerifierTest())) {
      return;
    }

    // Register Gps Status Listener.
    TestGnssStatusCallback testGnssStatusCallback =
        new TestGnssStatusCallback(TAG, STATUS_TO_COLLECT_COUNT);
    checkGnssChange(testGnssStatusCallback);
  }

  private void checkGnssChange(TestGnssStatusCallback testGnssStatusCallback)
      throws InterruptedException {
    mTestLocationManager.registerGnssStatusCallback(testGnssStatusCallback);

    TestLocationListener locationListener = new TestLocationListener(LOCATION_TO_COLLECT_COUNT);
    mTestLocationManager.requestLocationUpdates(locationListener);

    boolean success = testGnssStatusCallback.awaitStart();
    success = success ? testGnssStatusCallback.awaitStatus() : false;
    success = success ? testGnssStatusCallback.awaitTtff() : false;
    mTestLocationManager.removeLocationUpdates(locationListener);
    success = success ? testGnssStatusCallback.awaitStop() : false;
    mTestLocationManager.unregisterGnssStatusCallback(testGnssStatusCallback);

    SoftAssert.failOrWarning(isMeasurementTestStrict(),
        "Time elapsed without getting the right status changes."
            + " Possibly, the test has been run deep indoors."
            + " Consider retrying test outdoors.",
        success);
  }

  /**
   * Tests values of {@link GnssStatus}.
   */
  public void testGnssStatusValues() throws InterruptedException {
    // Checks if GPS hardware feature is present, skips test (pass) if not,
    // and hard asserts that Location/GPS (Provider) is turned on if is Cts Verifier.
    if (!TestMeasurementUtil.canTestRunOnCurrentDevice(mTestLocationManager,
        TAG, MIN_HARDWARE_YEAR_MEASUREMENTS_REQUIRED, isCtsVerifierTest())) {
      return;
    }
    SoftAssert softAssert = new SoftAssert(TAG);
    // Register Gps Status Listener.
    TestGnssStatusCallback testGnssStatusCallback =
        new TestGnssStatusCallback(TAG, STATUS_TO_COLLECT_COUNT);
    checkGnssChange(testGnssStatusCallback);
    validateGnssStatus(testGnssStatusCallback.getGnssStatus(), softAssert);
    softAssert.assertAll();
  }

  /**
   * To validate the fields in GnssStatus class, the value is got from device
   * @param status, GnssStatus
   * @param softAssert, customized assert class.
   */
  public static void validateGnssStatus(GnssStatus status, SoftAssert softAssert) {
    int sCount = status.getSatelliteCount();
    Log.i(TAG, "Total satellite:" + sCount);
    // total number of satellites for all constellation is less than 200
    softAssert.assertTrue("Satellite count test sCount : " + sCount , sCount < 200);
    for (int i = 0; i < sCount; ++i) {
      softAssert.assertTrue("azimuth_degrees: Azimuth in degrees: ",
          "0.0 <= X <= 360.0",
          String.valueOf(status.getAzimuthDegrees(i)),
          status.getAzimuthDegrees(i) >= 0.0 && status.getAzimuthDegrees(i) <= 360.0);

      if (status.hasCarrierFrequencyHz(i)) {
        softAssert.assertTrue("carrier_frequency_hz: Carrier frequency in hz",
            "X > 0.0",
            String.valueOf(status.getCarrierFrequencyHz(i)),
            status.getCarrierFrequencyHz(i) > 0.0);
      }

      softAssert.assertTrue("c_n0_dbhz: Carrier-to-noise density",
          "0.0 >= X <= 63",
          String.valueOf(status.getCn0DbHz(i)),
          status.getCn0DbHz(i) >= 0.0 &&
              status.getCn0DbHz(i) <= 63.0);

      softAssert.assertTrue("elevation_degrees: Elevation in Degrees :",
          "0.0 <= X <= 90.0",
          String.valueOf(status.getElevationDegrees(i)),
          status.getElevationDegrees(i) >= 0.0 && status.getElevationDegrees(i) <= 90.0);

      // in validateSvidSub, it will validate ConstellationType, svid
      // however, we don't have the event time in the current scope, pass in "-1" instead
      TestMeasurementUtil.validateSvidSub(softAssert, null,
          status.getConstellationType(i),status.getSvid(i));

      // For those function with boolean type return, just simplly call the function
      // to make sure those function won't crash, also increase the test coverage.
      Log.i(TAG, "hasAlmanacData: " + status.hasAlmanacData(i));
      Log.i(TAG, "hasEphemerisData: " + status.hasEphemerisData(i));
      Log.i(TAG, "usedInFix: " + status.usedInFix(i));
    }
  }
}
