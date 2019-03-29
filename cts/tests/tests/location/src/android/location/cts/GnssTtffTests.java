package android.location.cts;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.SystemClock;
import android.telephony.TelephonyManager;
import android.util.Log;
import com.android.compatibility.common.util.CddTest;
import java.util.concurrent.TimeUnit;

/**
 * Tests for the ttff (time to the first fix) validating whether TTFF is
 * below the expected thresholds in differnt scenario
 */
public class GnssTtffTests extends GnssTestCase {

  private static final String TAG = "GnssTtffTests";
  private static final int LOCATION_TO_COLLECT_COUNT = 1;
  private static final int STATUS_TO_COLLECT_COUNT = 3;
  private static final int AIDING_DATA_RESET_DELAY_SECS = 10;
  // Threshold values
  private static final int TTFF_HOT_TH_SECS = 5;
  private static final int TTFF_WITH_WIFI_CELLUAR_WARM_TH_SECS = 10;
  // The worst case we saw in the Nexus 6p device is 15sec,
  // adding 20% margin to the threshold
  private static final int TTFF_WITH_WIFI_ONLY_WARM_TH_SECS = 18;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    mTestLocationManager = new TestLocationManager(getContext());
  }

  /**
   * Test the TTFF in the case where there is a network connection for both warm and hot start TTFF
   * cases.
   * We first test the "WARM" start where different TTFF thresholds are chosen based on network
   * connection (cellular vs Wifi). Then we test the "HOT" start where the type of network
   * connection should not matter hence one threshold is used.
   * @throws Exception
   */
  @CddTest(requirement="7.3.3")
  public void testTtffWithNetwork() throws Exception {
    ensureNetworkStatus();
    if (hasCellularData()) {
      checkTtffWarmWithWifiOn(TTFF_WITH_WIFI_CELLUAR_WARM_TH_SECS);
    }
    else {
      checkTtffWarmWithWifiOn(TTFF_WITH_WIFI_ONLY_WARM_TH_SECS);
    }
    checkTtffHotWithWifiOn(TTFF_HOT_TH_SECS);
  }

  /**
   * Test Scenario 1
   * Check whether TTFF is below the threshold on the warm start with Wifi ON
   * 1) Delete the aiding data.
   * 2) Get GPS, check the TTFF value
   * @param threshold, the threshold for the TTFF value
   */
  private void checkTtffWarmWithWifiOn(long threshold) throws Exception {
    SoftAssert softAssert = new SoftAssert(TAG);
    mTestLocationManager.sendExtraCommand("delete_aiding_data");
    Thread.sleep(TimeUnit.SECONDS.toMillis(AIDING_DATA_RESET_DELAY_SECS));
    checkTtffByThreshold("checkTtffWarmWithWifiOn",
        TimeUnit.SECONDS.toMillis(threshold), softAssert);
    softAssert.assertAll();
  }

  /**
   * Test Scenario 2
   * Check whether TTFF is below the threhold on the hot start with wifi ON
   * TODO(tccyp): to test the hot case with network connection off
   * @param threshold, the threshold for the TTFF value
   */
  private void checkTtffHotWithWifiOn(long threshold) throws Exception {
    SoftAssert softAssert = new SoftAssert(TAG);
    checkTtffByThreshold("checkTtffHotWithWifiOn",
        TimeUnit.SECONDS.toMillis(threshold), softAssert);
    softAssert.assertAll();
  }

  /**
   * Make sure the device has either wifi data or cellular connection
   */
  private void ensureNetworkStatus(){
    assertTrue("Device has to connect to Wifi or Cellular to complete this test.",
        isConnectedToWifiOrCellular(getContext()));

  }

  private boolean hasCellularData() {
    ConnectivityManager connManager = getConnectivityManager(getContext());
    NetworkInfo cellularNetworkInfo = connManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
    // check whether the cellular data is ON if the device has cellular capability
    if (cellularNetworkInfo == null) {
      Log.i(TAG, "This is a wifi only device.");
      return false;
    }
    TelephonyManager telephonyManager = (TelephonyManager) getContext().getApplicationContext()
        .getSystemService(getContext().TELEPHONY_SERVICE);
    if (!telephonyManager.isDataEnabled()) {
      Log.i(TAG, "Device doesn't have cellular data.");
      return false;
    }
    return true;
  }

  /*
   * Check whether TTFF is below the threshold
   * @param testName
   * @param threshold, the threshold for the TTFF value
   */
  private void checkTtffByThreshold(String testName,
      long threshold, SoftAssert softAssert) throws Exception {
    TestLocationListener networkLocationListener
        = new TestLocationListener(LOCATION_TO_COLLECT_COUNT);
    // fetch the networklocation first to make sure the ttff is not flaky
    mTestLocationManager.requestNetworkLocationUpdates(networkLocationListener);
    networkLocationListener.await();

    TestGnssStatusCallback testGnssStatusCallback =
        new TestGnssStatusCallback(TAG, STATUS_TO_COLLECT_COUNT);
    mTestLocationManager.registerGnssStatusCallback(testGnssStatusCallback);

    TestLocationListener locationListener = new TestLocationListener(LOCATION_TO_COLLECT_COUNT);
    mTestLocationManager.requestLocationUpdates(locationListener);


    long startTimeMillis = SystemClock.elapsedRealtime();
    boolean success = testGnssStatusCallback.awaitTtff();
    long ttffTimeMillis = SystemClock.elapsedRealtime() - startTimeMillis;

    SoftAssert.failOrWarning(isMeasurementTestStrict(),
            "Test case:" + testName
            + ". Threshold exceeded without getting a location."
            + " Possibly, the test has been run deep indoors."
            + " Consider retrying test outdoors.",
        success);
    mTestLocationManager.removeLocationUpdates(locationListener);
    mTestLocationManager.unregisterGnssStatusCallback(testGnssStatusCallback);
    softAssert.assertTrue("Test case: " + testName +", TTFF should be less than " + threshold
        + " . In current test, TTFF value is: " + ttffTimeMillis, ttffTimeMillis < threshold);
  }

  /**
   * Returns whether the device is currently connected to a wifi or cellular.
   *
   * @param context {@link Context} object
   * @return {@code true} if connected to Wifi or Cellular; {@code false} otherwise
   */
  public static boolean isConnectedToWifiOrCellular(Context context) {
    NetworkInfo info = getActiveNetworkInfo(context);
    return info != null
        && info.isConnected()
        && (info.getType() == ConnectivityManager.TYPE_WIFI
                 || info.getType() == ConnectivityManager.TYPE_MOBILE);
  }

  /**
   * Gets the active network info.
   *
   * @param context {@link Context} object
   * @return {@link NetworkInfo}
   */
  private static NetworkInfo getActiveNetworkInfo(Context context) {
    ConnectivityManager cm = getConnectivityManager(context);
    if (cm != null) {
      return cm.getActiveNetworkInfo();
    }
    return null;
  }

  /**
   * Gets the connectivity manager.
   *
   * @param context {@link Context} object
   * @return {@link ConnectivityManager}
   */
  private static ConnectivityManager getConnectivityManager(Context context) {
    return (ConnectivityManager) context.getApplicationContext()
        .getSystemService(Context.CONNECTIVITY_SERVICE);
  }

}