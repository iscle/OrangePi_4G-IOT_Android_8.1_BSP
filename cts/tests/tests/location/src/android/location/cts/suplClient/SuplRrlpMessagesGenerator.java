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

import android.location.cts.asn1.base.PacketBuilder;
import android.location.cts.asn1.supl2.rrlp_messages.PDU;
import android.location.cts.asn1.supl2.supl_pos.PosPayLoad;
import android.location.cts.asn1.supl2.supl_pos.SUPLPOS;
import android.location.cts.asn1.supl2.supl_pos_init.NavigationModel;
import android.location.cts.asn1.supl2.supl_pos_init.RequestedAssistData;
import android.location.cts.asn1.supl2.supl_pos_init.SUPLPOSINIT;
import android.location.cts.asn1.supl2.supl_start.PosProtocol;
import android.location.cts.asn1.supl2.supl_start.PosTechnology;
import android.location.cts.asn1.supl2.supl_start.PrefMethod;
import android.location.cts.asn1.supl2.supl_start.SETCapabilities;
import android.location.cts.asn1.supl2.supl_start.SUPLSTART;
import android.location.cts.asn1.supl2.ulp.ULP_PDU;
import android.location.cts.asn1.supl2.ulp.UlpMessage;
import android.location.cts.asn1.supl2.ulp_components.CellInfo;
import android.location.cts.asn1.supl2.ulp_components.LocationId;
import android.location.cts.asn1.supl2.ulp_components.Position;
import android.location.cts.asn1.supl2.ulp_components.Position.timestampType;
import android.location.cts.asn1.supl2.ulp_components.PositionEstimate;
import android.location.cts.asn1.supl2.ulp_components.PositionEstimate.latitudeSignType;
import android.location.cts.asn1.supl2.ulp_components.SessionID;
import android.location.cts.asn1.supl2.ulp_components.SetSessionID;
import android.location.cts.asn1.supl2.ulp_components.Status;
import android.location.cts.asn1.supl2.ulp_components.Version;
import android.location.cts.asn1.supl2.ulp_components.WcdmaCellInformation;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.BitSet;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Random;
import java.util.TimeZone;

import javax.annotation.Nullable;

/**
 * A class that generates several types of GPS SUPL client payloads that can be transmitted over a
 * GPS socket.
 *
 * <p>Two types of SUPL payloads are supported in this version: Local Location and WCDMA versions.
 * However, it should be straightforward to extend this class to support other types of SUPL
 * requests.
 */
public class SuplRrlpMessagesGenerator {
  // Scale factors used for conversion from latitude and longitude in SUPL protocol format
  // to decimal format
  private static final double POSITION_ESTIMATE_LAT_SCALE_FACTOR = 90.0 / 8388608.0;
  private static final double POSITION_ESTIMATE_LNG_SCALE_FACTOR = 180.0 / 8388608.0;

  /**
   * Generate a SUPL START message that can be send by the SUPL client to the server in the case
   * that device location is known via a latitude and a longitude.
   *
   * <p>SUPL START is the first message to be send from the client to the server. The server should
   * response to the SUPL START message with a SUPL RESPONSE message containing a SessionID.
   *
   */
  public static byte[] generateSuplStartLocalLocationMessage(@Nullable InetAddress ipAddress)
      throws UnknownHostException {

    ULP_PDU ulpPdu = new ULP_PDU();
    Version version = ulpPdu.setVersionToNewInstance();
    version.setMinToNewInstance().setInteger(BigInteger.ZERO);
    version.setMajToNewInstance().setInteger(BigInteger.valueOf(2));
    version.setServindToNewInstance().setInteger(BigInteger.ZERO);
    ulpPdu.setVersion(version);

    SessionID sessionId = ulpPdu.setSessionIDToNewInstance();

    SetSessionID setSessionId = sessionId.setSetSessionIDToNewInstance();
    setSessionId.setSessionIdToNewInstance()
        .setInteger(BigInteger.valueOf(new Random().nextInt(65536)));
    if (ipAddress == null){
      ipAddress = InetAddress.getLocalHost();
    }
    byte[] ipAsbytes = ipAddress.getAddress();
    setSessionId.setSetIdToNewInstance().setIPAddressToNewInstance().setIpv4AddressToNewInstance()
        .setValue(ipAsbytes);

    UlpMessage message = new UlpMessage();
    SUPLSTART suplStart = message.setMsSUPLSTARTToNewInstance();
    SETCapabilities setCapabilities = suplStart.setSETCapabilitiesToNewInstance();
    PosTechnology posTechnology = setCapabilities.setPosTechnologyToNewInstance();
    posTechnology.setAgpsSETassistedToNewInstance().setValue(false);
    posTechnology.setAgpsSETBasedToNewInstance().setValue(true);
    posTechnology.setAutonomousGPSToNewInstance().setValue(true);
    posTechnology.setAFLTToNewInstance().setValue(false);
    posTechnology.setECIDToNewInstance().setValue(false);
    posTechnology.setEOTDToNewInstance().setValue(false);
    posTechnology.setOTDOAToNewInstance().setValue(false);

    setCapabilities.setPrefMethodToNewInstance().setValue(PrefMethod.Value.agpsSETBasedPreferred);

    PosProtocol posProtocol = setCapabilities.setPosProtocolToNewInstance();
    posProtocol.setTia801ToNewInstance().setValue(false);
    posProtocol.setRrlpToNewInstance().setValue(true);
    posProtocol.setRrcToNewInstance().setValue(false);

    LocationId locationId = suplStart.setLocationIdToNewInstance();
    CellInfo cellInfo = locationId.setCellInfoToNewInstance();
    cellInfo.setExtensionVer2_CellInfo_extensionToNewInstance();
    // FF-FF-FF-FF-FF-FF
    final String macBinary = "111111111111111111111111111111111111111111111111";
    BitSet bits = new BitSet(macBinary.length());
    for (int i = 0; i < macBinary.length(); ++i) {
      if (macBinary.charAt(i) == '1') {
        bits.set(i);
      }
    }
    cellInfo.getExtensionVer2_CellInfo_extension().setWlanAPToNewInstance()
        .setApMACAddressToNewInstance().setValue(bits);
    locationId.setStatusToNewInstance().setValue(Status.Value.current);

    message.setMsSUPLSTART(suplStart);

    ulpPdu.setMessage(message);
    return encodeUlp(ulpPdu);
  }

  /**
   * Generate a SUPL POS INIT message that can be send by the SUPL client to the server in the case
   * that device location is known via a latitude and a longitude.
   *
   * <p>SUPL POS INIT is the second message to be send from the client to the server after receiving
   * a SUPL RESPONSE containing a SessionID from the server. The SessionID received
   * from the server response should set in the SUPL POS INIT message.
   *
   */
  public static byte[] generateSuplPositionInitLocalLocationMessage(SessionID sessionId, long latE7,
      long lngE7) {

    ULP_PDU ulpPdu = new ULP_PDU();
    Version version = ulpPdu.setVersionToNewInstance();
    version.setMinToNewInstance().setInteger(BigInteger.ZERO);
    version.setMajToNewInstance().setInteger(BigInteger.valueOf(2));
    version.setServindToNewInstance().setInteger(BigInteger.ZERO);
    ulpPdu.setVersion(version);

    ulpPdu.setSessionID(sessionId);

    UlpMessage message = new UlpMessage();
    SUPLPOSINIT suplPosInit = message.setMsSUPLPOSINITToNewInstance();
    SETCapabilities setCapabilities = suplPosInit.setSETCapabilitiesToNewInstance();
    PosTechnology posTechnology = setCapabilities.setPosTechnologyToNewInstance();
    posTechnology.setAgpsSETassistedToNewInstance().setValue(false);
    posTechnology.setAgpsSETBasedToNewInstance().setValue(true);
    posTechnology.setAutonomousGPSToNewInstance().setValue(true);
    posTechnology.setAFLTToNewInstance().setValue(false);
    posTechnology.setECIDToNewInstance().setValue(false);
    posTechnology.setEOTDToNewInstance().setValue(false);
    posTechnology.setOTDOAToNewInstance().setValue(false);

    setCapabilities.setPrefMethodToNewInstance().setValue(PrefMethod.Value.agpsSETBasedPreferred);

    PosProtocol posProtocol = setCapabilities.setPosProtocolToNewInstance();
    posProtocol.setTia801ToNewInstance().setValue(false);
    posProtocol.setRrlpToNewInstance().setValue(true);
    posProtocol.setRrcToNewInstance().setValue(false);

    RequestedAssistData reqAssistData = suplPosInit.setRequestedAssistDataToNewInstance();

    reqAssistData.setAlmanacRequestedToNewInstance().setValue(false);
    reqAssistData.setUtcModelRequestedToNewInstance().setValue(false);
    reqAssistData.setIonosphericModelRequestedToNewInstance().setValue(true);
    reqAssistData.setDgpsCorrectionsRequestedToNewInstance().setValue(false);
    reqAssistData.setReferenceLocationRequestedToNewInstance().setValue(false);
    reqAssistData.setReferenceTimeRequestedToNewInstance().setValue(true);
    reqAssistData.setAcquisitionAssistanceRequestedToNewInstance().setValue(false);
    reqAssistData.setRealTimeIntegrityRequestedToNewInstance().setValue(false);
    reqAssistData.setNavigationModelRequestedToNewInstance().setValue(true);
    NavigationModel navigationModelData = reqAssistData.setNavigationModelDataToNewInstance();
    navigationModelData.setGpsWeekToNewInstance().setInteger(BigInteger.ZERO);
    navigationModelData.setGpsToeToNewInstance().setInteger(BigInteger.ZERO);
    navigationModelData.setNSATToNewInstance().setInteger(BigInteger.ZERO);
    navigationModelData.setToeLimitToNewInstance().setInteger(BigInteger.ZERO);

    LocationId locationId = suplPosInit.setLocationIdToNewInstance();
    CellInfo cellInfo = locationId.setCellInfoToNewInstance();
    cellInfo.setExtensionVer2_CellInfo_extensionToNewInstance();
    // FF-FF-FF-FF-FF-FF
    final String macBinary = "111111111111111111111111111111111111111111111111";
    BitSet bits = new BitSet(macBinary.length());
    for (int i = 0; i < macBinary.length(); ++i) {
      if (macBinary.charAt(i) == '1') {
        bits.set(i);
      }
    }
    cellInfo.getExtensionVer2_CellInfo_extension().setWlanAPToNewInstance()
        .setApMACAddressToNewInstance().setValue(bits);
    locationId.setStatusToNewInstance().setValue(Status.Value.current);

    Position pos = suplPosInit.setPositionToNewInstance();
    timestampType utcTime = pos.setTimestampToNewInstance();
    Calendar currentTime = GregorianCalendar.getInstance(TimeZone.getTimeZone("UTC"));
    utcTime.setYear(currentTime.get(Calendar.YEAR));
    utcTime.setMonth(currentTime.get(Calendar.MONTH) + 1); // Calendar's MONTH starts from 0.
    utcTime.setDay(currentTime.get(Calendar.DAY_OF_MONTH));
    utcTime.setHour(currentTime.get(Calendar.HOUR_OF_DAY));
    utcTime.setMinute(currentTime.get(Calendar.MINUTE));
    utcTime.setSecond(currentTime.get(Calendar.SECOND));

    PositionEstimate posEstimate = pos.setPositionEstimateToNewInstance();

    long latSuplFormat = (long) (Math.abs(latE7) / (POSITION_ESTIMATE_LAT_SCALE_FACTOR * 1E7));
    long lngSuplFormat = (long) (lngE7 / (POSITION_ESTIMATE_LNG_SCALE_FACTOR * 1E7));
    posEstimate.setLatitudeToNewInstance().setInteger(BigInteger.valueOf(latSuplFormat));
    posEstimate.setLongitudeToNewInstance().setInteger(BigInteger.valueOf(lngSuplFormat));
    posEstimate.setLatitudeSignToNewInstance()
        .setValue(latE7 > 0 ? latitudeSignType.Value.north : latitudeSignType.Value.south);

    message.setMsSUPLPOSINIT(suplPosInit);

    ulpPdu.setMessage(message);
    return encodeUlp(ulpPdu);
  }

  public static byte[] generateAssistanceDataAckMessage(SessionID sessionId) {
    ULP_PDU ulpPdu = new ULP_PDU();
    Version version = ulpPdu.setVersionToNewInstance();
    version.setMinToNewInstance().setInteger(BigInteger.ZERO);
    version.setMajToNewInstance().setInteger(BigInteger.valueOf(2));
    version.setServindToNewInstance().setInteger(BigInteger.ZERO);
    ulpPdu.setVersion(version);

    ulpPdu.setSessionID(sessionId);

    PDU pdu = new PDU();
    pdu.setReferenceNumberToNewInstance();
    pdu.getReferenceNumber().setInteger(BigInteger.ONE);
    pdu.setComponentToNewInstance();
    pdu.getComponent().setAssistanceDataAckToNewInstance();

    PacketBuilder payloadBuilder = new PacketBuilder();
    try {
      payloadBuilder.appendAll(pdu.encodePerUnaligned());
    } catch (IllegalArgumentException | IllegalStateException | IndexOutOfBoundsException
        | UnsupportedOperationException e) {
      throw new RuntimeException(e);
    }
    PosPayLoad.rrlpPayloadType rrlpPayload = new PosPayLoad.rrlpPayloadType();
    rrlpPayload.setValue(payloadBuilder.getPaddedBytes());

    UlpMessage message = new UlpMessage();
    SUPLPOS suplPos = message.setMsSUPLPOSToNewInstance();
    suplPos.setPosPayLoadToNewInstance();
    suplPos.getPosPayLoad().setRrlpPayload(rrlpPayload);

    ulpPdu.setMessage(message);

    return encodeUlp(ulpPdu);
  }

  /** Encodes a ULP_PDU message into bytes and sets the length field. */
  public static byte[] encodeUlp(ULP_PDU message) {
    message.setLengthToNewInstance();
    message.getLength().setInteger(BigInteger.ZERO);
    PacketBuilder messageBuilder = new PacketBuilder();
    messageBuilder.appendAll(message.encodePerUnaligned());
    byte[] result = messageBuilder.getPaddedBytes();
    ByteBuffer buffer = ByteBuffer.wrap(result);
    buffer.order(ByteOrder.BIG_ENDIAN);
    buffer.putShort((short) result.length);
    return buffer.array();
  }

}
