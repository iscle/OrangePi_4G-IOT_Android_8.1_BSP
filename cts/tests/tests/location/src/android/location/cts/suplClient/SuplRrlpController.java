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

package android.location.cts.suplClient;

import android.location.cts.asn1.supl2.rrlp_components.IonosphericModel;
import android.location.cts.asn1.supl2.rrlp_components.NavModelElement;
import android.location.cts.asn1.supl2.rrlp_components.NavigationModel;
import android.location.cts.asn1.supl2.rrlp_components.SatStatus;
import android.location.cts.asn1.supl2.rrlp_components.UncompressedEphemeris;
import android.location.cts.asn1.supl2.rrlp_messages.PDU;
import android.location.cts.asn1.supl2.supl_pos.PosPayLoad;
import android.location.cts.asn1.supl2.ulp.ULP_PDU;
import android.location.cts.asn1.supl2.ulp.UlpMessage;
import android.location.cts.asn1.supl2.ulp_components.SessionID;
import android.location.cts.nano.Ephemeris.GpsEphemerisProto;
import android.location.cts.nano.Ephemeris.GpsNavMessageProto;
import android.location.cts.nano.Ephemeris.IonosphericModelProto;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

/**
 * A class that applies the SUPL protocol call flow to obtain GPS assistance data over a TCP
 * connection.
 *
 * <p>A rough location of the receiver has to be known in advance which is passed to the method
 * #generateNavMessage to obtain a GpsNavMessageProto containing the GPS assistance
 * data.
 *
 * <p>The SUPL protocol flaw is made over a TCP socket to a server specified by SUPL_SERVER_NAME 
 * at port SUPL_SERVER_PORT.
 */
public class SuplRrlpController {
  // Details of the following constants can be found in hte IS-GPS-200F which can be found at:
  // http://www.navcen.uscg.gov/pdf/is-gps-200f.pdf
  private static final double NAVIGATION_TGD_SCALE_FACTOR = Math.pow(2, -31);
  private static final double NAVIGATION_TOC_SCALE_FACTOR = Math.pow(2, 4);
  private static final double NAVIGATION_AF2_SCALE_FACTOR = Math.pow(2, -55);
  private static final double NAVIGATION_AF1_SCALE_FACTOR = Math.pow(2, -43);
  private static final double NAVIGATION_AF0_SCALE_FACTOR = Math.pow(2, -31);
  private static final double NAVIGATION_CRS_SCALE_FACTOR = Math.pow(2, -5);
  private static final double NAVIGATION_DELTA_N_SCALE_FACTOR = Math.pow(2, -43) * Math.PI;
  private static final double NAVIGATION_M0_SCALE_FACTOR = Math.pow(2, -31) * Math.PI;
  private static final double NAVIGATION_CUC_SCALE_FACTOR = Math.pow(2, -29);
  private static final double NAVIGATION_E_SCALE_FACTOR = Math.pow(2, -33);
  private static final double NAVIGATION_CUS_SCALE_FACTOR = Math.pow(2, -29);
  private static final double NAVIGATION_A_POWER_HALF_SCALE_FACTOR = Math.pow(2, -19);
  private static final double NAVIGATION_TOE_SCALE_FACTOR = Math.pow(2, 4);
  private static final double NAVIGATION_CIC_SCALE_FACTOR = Math.pow(2, -29);
  private static final double NAVIGATION_OMEGA0_SCALE_FACTOR = Math.pow(2, -31) * Math.PI;
  private static final double NAVIGATION_CIS_SCALE_FACTOR = Math.pow(2, -29);
  private static final double NAVIGATION_I0_SCALE_FACTOR = Math.pow(2, -31) * Math.PI;
  private static final double NAVIGATION_CRC_SCALE_FACTOR = Math.pow(2, -5);
  private static final double NAVIGATION_W_SCALE_FACTOR = Math.pow(2, -31) * Math.PI;
  private static final double NAVIGATION_OMEGA_A_DOT_SCALE_FACTOR = Math.pow(2, -43) * Math.PI;
  private static final double NAVIGATION_I_DOT_SCALE_FACTOR = Math.pow(2, -43) * Math.PI;
  private static final double IONOSPHERIC_ALFA_0_SCALE_FACTOR = Math.pow(2, -30);
  private static final double IONOSPHERIC_ALFA_1_SCALE_FACTOR = Math.pow(2, -27);
  private static final double IONOSPHERIC_ALFA_2_SCALE_FACTOR = Math.pow(2, -24);
  private static final double IONOSPHERIC_ALFA_3_SCALE_FACTOR = Math.pow(2, -24);
  private static final double IONOSPHERIC_BETA_0_SCALE_FACTOR = Math.pow(2, 11);
  private static final double IONOSPHERIC_BETA_1_SCALE_FACTOR = Math.pow(2, 14);
  private static final double IONOSPHERIC_BETA_2_SCALE_FACTOR = Math.pow(2, 16);
  private static final double IONOSPHERIC_BETA_3_SCALE_FACTOR = Math.pow(2, 16);

  // 3657 is the number of days between the unix epoch and GPS epoch as the GPS epoch started on
  // Jan 6, 1980
  private static final long GPS_EPOCH_AS_UNIX_EPOCH_MS = TimeUnit.DAYS.toMillis(3657);
  // A GPS Cycle is 1024 weeks, or 7168 days
  private static final long GPS_CYCLE_MS = TimeUnit.DAYS.toMillis(7168);
  private static final int GPS_CYCLE_WEEKS = 1024;

  private final String suplServerName;
  private final int suplServerPort;

  public SuplRrlpController(String suplServerName, int suplServerPort) {
    this.suplServerName = suplServerName;
    this.suplServerPort = suplServerPort;
  }

  /**
   * Applies the SUPL protocol call flaw to obtain the assistance data and store the result in a
   * GpsNavMessageProto
   */
  public GpsNavMessageProto generateNavMessage(long latE7, long lngE7)
      throws UnknownHostException, IOException {
    // Establishes a TCP socket that is used to send and receive SUPL messages
    SuplTcpClient tcpClient = new SuplTcpClient(suplServerName, suplServerPort);

    // Send a SUPL START message from the client to server
    byte[] suplStartMessage = SuplRrlpMessagesGenerator.generateSuplStartLocalLocationMessage(null);
    tcpClient.sendSuplRequest(suplStartMessage);
    // Receive a SUPL RESPONSE from the server and obtain the Session ID send by the server
    byte[] response = tcpClient.getSuplResponse();
    if (response == null) {
      return new GpsNavMessageProto();
    }
    ULP_PDU decodedMessage = ULP_PDU.fromPerUnaligned(response);

    if (!decodedMessage.getMessage().isMsSUPLRESPONSE()) {
      return new GpsNavMessageProto();
    }
    SessionID sessionId = decodedMessage.getSessionID();

    // Send a SUPL POS INIT message from the client to the server requesting GPS assistance data
    // for the location specified by the given latitude and longitude
    byte[] suplPosInitMessage = SuplRrlpMessagesGenerator
        .generateSuplPositionInitLocalLocationMessage(sessionId, latE7, lngE7);
    tcpClient.sendSuplRequest(suplPosInitMessage);

    // Receive a SUPL POS message from the server containing all the assitance data requested
    response = tcpClient.getSuplResponse();
    if (response == null) {
      return new GpsNavMessageProto();
    }
    decodedMessage = ULP_PDU.fromPerUnaligned(response);

    if (!decodedMessage.getMessage().isMsSUPLPOS()) {
      return new GpsNavMessageProto();
    }
    // build a NavMessageProto out of the received decoded payload from the SUPL server
    GpsNavMessageProto navMessageProto = buildNavMessageProto(decodedMessage);

    tcpClient.closeSocket();

    return navMessageProto;
  }

  /** Fills GpsNavMessageProto with the assistance data obtained in ULP_PDU */
  private GpsNavMessageProto buildNavMessageProto(ULP_PDU decodedMessage) {
    UlpMessage message = decodedMessage.getMessage();

    PosPayLoad.rrlpPayloadType rrlpPayload =
        message.getMsSUPLPOS().getPosPayLoad().getRrlpPayload();
    PDU pdu = PDU.fromPerUnaligned(rrlpPayload.getValue());
    IonosphericModel ionoModel = pdu.getComponent().getAssistanceData().getGps_AssistData()
        .getControlHeader().getIonosphericModel();
    NavigationModel navModel = pdu.getComponent().getAssistanceData().getGps_AssistData()
        .getControlHeader().getNavigationModel();
    int gpsWeek = pdu.getComponent().getAssistanceData().getGps_AssistData().getControlHeader()
        .getReferenceTime().getGpsTime().getGpsWeek().getInteger().intValue();
    gpsWeek = getGpsWeekWithRollover(gpsWeek);
    Iterable<NavModelElement> navModelElements = navModel.getNavModelList().getValues();

    GpsNavMessageProto gpsNavMessageProto = new GpsNavMessageProto();
    gpsNavMessageProto.rpcStatus = GpsNavMessageProto.UNKNOWN_RPC_STATUS;

    // Set Iono Model.
    IonosphericModelProto ionosphericModelProto = new IonosphericModelProto();
    double[] alpha = new double[4];
    alpha[0] = ionoModel.getAlfa0().getInteger().byteValue() * IONOSPHERIC_ALFA_0_SCALE_FACTOR;
    alpha[1] = ionoModel.getAlfa1().getInteger().byteValue() * IONOSPHERIC_ALFA_1_SCALE_FACTOR;
    alpha[2] = ionoModel.getAlfa2().getInteger().byteValue() * IONOSPHERIC_ALFA_2_SCALE_FACTOR;
    alpha[3] = ionoModel.getAlfa3().getInteger().byteValue() * IONOSPHERIC_ALFA_3_SCALE_FACTOR;
    ionosphericModelProto.alpha = alpha;

    double[] beta = new double[4];
    beta[0] = ionoModel.getBeta0().getInteger().byteValue() * IONOSPHERIC_BETA_0_SCALE_FACTOR;
    beta[1] = ionoModel.getBeta1().getInteger().byteValue() * IONOSPHERIC_BETA_1_SCALE_FACTOR;
    beta[2] = ionoModel.getBeta2().getInteger().byteValue() * IONOSPHERIC_BETA_2_SCALE_FACTOR;
    beta[3] = ionoModel.getBeta3().getInteger().byteValue() * IONOSPHERIC_BETA_3_SCALE_FACTOR;
    ionosphericModelProto.beta = beta;

    gpsNavMessageProto.iono = ionosphericModelProto;

    ArrayList<GpsEphemerisProto> ephemerisList = new ArrayList<>();
    for (NavModelElement navModelElement : navModelElements) {
      int satID = navModelElement.getSatelliteID().getInteger().intValue();
      SatStatus satStatus = navModelElement.getSatStatus();
      UncompressedEphemeris ephemeris = satStatus.getNewSatelliteAndModelUC();

      GpsEphemerisProto gpsEphemerisProto = new GpsEphemerisProto();
      toSingleEphemeris(satID, gpsWeek, ephemeris, gpsEphemerisProto);
      ephemerisList.add(gpsEphemerisProto);
    }

    gpsNavMessageProto.ephemerids =
        ephemerisList.toArray(new GpsEphemerisProto[ephemerisList.size()]);
    gpsNavMessageProto.rpcStatus = GpsNavMessageProto.SUCCESS;

    return gpsNavMessageProto;
  }

  /**
   * Calculates the GPS week with rollovers. A rollover happens every 1024 weeks, beginning from GPS
   * epoch (January 6, 1980).
   *
   * @param gpsWeek The modulo-1024 GPS week.
   *
   * @return The absolute GPS week.
   */
  private int getGpsWeekWithRollover(int gpsWeek) {
    long nowMs = System.currentTimeMillis();
    long elapsedTimeFromGpsEpochMs = nowMs - GPS_EPOCH_AS_UNIX_EPOCH_MS;
    long rolloverCycles = elapsedTimeFromGpsEpochMs / GPS_CYCLE_MS;
    int rolloverWeeks = (int) rolloverCycles * GPS_CYCLE_WEEKS;
    return gpsWeek + rolloverWeeks;
  }

  /**
   * Fills GpsEphemerisProto with the assistance data obtained in UncompressedEphemeris for the
   * given satellite id.
   */
  private void toSingleEphemeris(
      int satId, int gpsWeek, UncompressedEphemeris ephemeris,
      GpsEphemerisProto gpsEphemerisProto) {

    gpsEphemerisProto.prn = satId + 1;
    gpsEphemerisProto.week = gpsWeek;
    gpsEphemerisProto.l2Code = ephemeris.getEphemCodeOnL2().getInteger().intValue();
    gpsEphemerisProto.l2Flag = ephemeris.getEphemL2Pflag().getInteger().intValue();
    gpsEphemerisProto.svHealth = ephemeris.getEphemSVhealth().getInteger().intValue();

    gpsEphemerisProto.iode = ephemeris.getEphemIODC().getInteger().intValue();
    gpsEphemerisProto.iodc = ephemeris.getEphemIODC().getInteger().intValue();
    gpsEphemerisProto.toc = ephemeris.getEphemToc().getInteger().intValue() * NAVIGATION_TOC_SCALE_FACTOR;
    gpsEphemerisProto.toe = ephemeris.getEphemToe().getInteger().intValue() * NAVIGATION_TOE_SCALE_FACTOR;
    gpsEphemerisProto.af0 = ephemeris.getEphemAF0().getInteger().intValue() * NAVIGATION_AF0_SCALE_FACTOR;
    gpsEphemerisProto.af1 = ephemeris.getEphemAF1().getInteger().shortValue() * NAVIGATION_AF1_SCALE_FACTOR;
    gpsEphemerisProto.af2 = ephemeris.getEphemAF2().getInteger().byteValue() * NAVIGATION_AF2_SCALE_FACTOR;
    gpsEphemerisProto.tgd = ephemeris.getEphemTgd().getInteger().byteValue() * NAVIGATION_TGD_SCALE_FACTOR;
    gpsEphemerisProto.rootOfA = ephemeris.getEphemAPowerHalf().getInteger().longValue()
        * NAVIGATION_A_POWER_HALF_SCALE_FACTOR;

    gpsEphemerisProto.e = ephemeris.getEphemE().getInteger().longValue() * NAVIGATION_E_SCALE_FACTOR;
    gpsEphemerisProto.i0 = ephemeris.getEphemI0().getInteger().intValue() * NAVIGATION_I0_SCALE_FACTOR;
    gpsEphemerisProto.iDot = ephemeris.getEphemIDot().getInteger().intValue() * NAVIGATION_I_DOT_SCALE_FACTOR;
    gpsEphemerisProto.omega = ephemeris.getEphemW().getInteger().intValue() * NAVIGATION_W_SCALE_FACTOR;
    gpsEphemerisProto.omega0 = ephemeris.getEphemOmegaA0().getInteger().intValue() * NAVIGATION_OMEGA0_SCALE_FACTOR;
    gpsEphemerisProto.omegaDot = ephemeris.getEphemOmegaADot().getInteger().intValue()
        * NAVIGATION_OMEGA_A_DOT_SCALE_FACTOR;
    gpsEphemerisProto.m0 = ephemeris.getEphemM0().getInteger().intValue() * NAVIGATION_M0_SCALE_FACTOR;
    gpsEphemerisProto.deltaN = ephemeris.getEphemDeltaN().getInteger().shortValue() * NAVIGATION_DELTA_N_SCALE_FACTOR;
    gpsEphemerisProto.crc = ephemeris.getEphemCrc().getInteger().shortValue() * NAVIGATION_CRC_SCALE_FACTOR;
    gpsEphemerisProto.crs = ephemeris.getEphemCrs().getInteger().shortValue() * NAVIGATION_CRS_SCALE_FACTOR;
    gpsEphemerisProto.cuc = ephemeris.getEphemCuc().getInteger().shortValue() * NAVIGATION_CUC_SCALE_FACTOR;
    gpsEphemerisProto.cus = ephemeris.getEphemCus().getInteger().shortValue() * NAVIGATION_CUS_SCALE_FACTOR;
    gpsEphemerisProto.cic = ephemeris.getEphemCic().getInteger().shortValue() * NAVIGATION_CIC_SCALE_FACTOR;
    gpsEphemerisProto.cis = ephemeris.getEphemCis().getInteger().shortValue() * NAVIGATION_CIS_SCALE_FACTOR;

  }

}
