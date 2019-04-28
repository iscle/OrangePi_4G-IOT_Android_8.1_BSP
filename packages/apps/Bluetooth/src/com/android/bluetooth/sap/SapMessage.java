package com.android.bluetooth.sap;

import android.hardware.radio.V1_0.ISap;
import android.hardware.radio.V1_0.SapApduType;
import android.hardware.radio.V1_0.SapTransferProtocol;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.android.btsap.SapApi;
import org.android.btsap.SapApi.*;
import com.google.protobuf.micro.*;

import android.os.RemoteException;
import android.util.Log;

/**
 * SapMessage is used for incoming and outgoing messages.
 *
 * For incoming messages
 *
 */
public class SapMessage {

    public static final String TAG = "SapMessage";
    public static final boolean DEBUG = SapService.DEBUG;
    public static final boolean VERBOSE = SapService.VERBOSE;
    public static final boolean TEST = false;

    /* Message IDs - SAP specification */
    public static final int ID_CONNECT_REQ        = 0x00;
    public static final int ID_CONNECT_RESP       = 0x01;

    public static final int ID_DISCONNECT_REQ     = 0x02;
    public static final int ID_DISCONNECT_RESP    = 0x03;
    public static final int ID_DISCONNECT_IND     = 0x04;

    public static final int ID_TRANSFER_APDU_REQ  = 0x05;
    public static final int ID_TRANSFER_APDU_RESP = 0x06;

    public static final int ID_TRANSFER_ATR_REQ   = 0x07;
    public static final int ID_TRANSFER_ATR_RESP  = 0x08;

    public static final int ID_POWER_SIM_OFF_REQ  = 0x09;
    public static final int ID_POWER_SIM_OFF_RESP = 0x0A;

    public static final int ID_POWER_SIM_ON_REQ   = 0x0B;
    public static final int ID_POWER_SIM_ON_RESP  = 0x0C;

    public static final int ID_RESET_SIM_REQ      = 0x0D;
    public static final int ID_RESET_SIM_RESP     = 0x0E;

    public static final int ID_TRANSFER_CARD_READER_STATUS_REQ  = 0x0F;
    public static final int ID_TRANSFER_CARD_READER_STATUS_RESP = 0x10;

    public static final int ID_STATUS_IND         = 0x11;
    public static final int ID_ERROR_RESP         = 0x12;

    public static final int ID_SET_TRANSPORT_PROTOCOL_REQ  = 0x13;
    public static final int ID_SET_TRANSPORT_PROTOCOL_RESP = 0x14;

    /* Message IDs - RIL specific unsolicited */
    // First RIL message id
    public static final int ID_RIL_BASE                    = 0x100;
    // RIL_UNSOL_RIL_CONNECTED
    public static final int ID_RIL_UNSOL_CONNECTED         = 0x100;
    // A disconnect ind from RIL will be converted after handled locally
    public static final int ID_RIL_UNSOL_DISCONNECT_IND    = 0x102;
    // All others
    public static final int ID_RIL_UNKNOWN                 = 0x1ff;

    /* Message IDs - RIL specific solicited */
    public static final int ID_RIL_GET_SIM_STATUS_REQ      = 0x200; // RIL_REQUEST_GET_SIM_STATUS
    /* Test signals used to set the reference ril in test mode */
    public static final int ID_RIL_SIM_ACCESS_TEST_REQ     = 0x201; // RIL_REQUEST_SIM_ACCESS_TEST
    public static final int ID_RIL_SIM_ACCESS_TEST_RESP    = 0x202; /* response for
                                                                    RIL_REQUEST_SIM_ACCESS_TEST */

    /* Parameter IDs and lengths */
    public static final int PARAM_MAX_MSG_SIZE_ID        = 0x00;
    public static final int PARAM_MAX_MSG_SIZE_LENGTH    = 2;

    public static final int PARAM_CONNECTION_STATUS_ID   = 0x01;
    public static final int PARAM_CONNECTION_STATUS_LENGTH = 1;

    public static final int PARAM_RESULT_CODE_ID         = 0x02;
    public static final int PARAM_RESULT_CODE_LENGTH     = 1;

    public static final int PARAM_DISCONNECT_TYPE_ID     = 0x03;
    public static final int PARAM_DISCONNECT_TYPE_LENGTH = 1;

    public static final int PARAM_COMMAND_APDU_ID        = 0x04;

    public static final int PARAM_COMMAND_APDU7816_ID    = 0x10;

    public static final int PARAM_RESPONSE_APDU_ID       = 0x05;

    public static final int PARAM_ATR_ID                 = 0x06;

    public static final int PARAM_CARD_READER_STATUS_ID  = 0x07;
    public static final int PARAM_CARD_READER_STATUS_LENGTH = 1;

    public static final int PARAM_STATUS_CHANGE_ID       = 0x08;
    public static final int PARAM_STATUS_CHANGE_LENGTH   = 1;

    public static final int PARAM_TRANSPORT_PROTOCOL_ID        = 0x09;
    public static final int PARAM_TRANSPORT_PROTOCOL_LENGTH    = 1;

    /* Result codes */
    public static final int RESULT_OK                        = 0x00;
    public static final int RESULT_ERROR_NO_REASON           = 0x01;
    public static final int RESULT_ERROR_CARD_NOT_ACCESSIBLE = 0x02;
    public static final int RESULT_ERROR_CARD_POWERED_OFF    = 0x03;
    public static final int RESULT_ERROR_CARD_REMOVED        = 0x04;
    public static final int RESULT_ERROR_CARD_POWERED_ON     = 0x05;
    public static final int RESULT_ERROR_DATA_NOT_AVAILABLE  = 0x06;
    public static final int RESULT_ERROR_NOT_SUPPORTED       = 0x07;

    /* Connection Status codes */
    public static final int CON_STATUS_OK                             = 0x00;
    public static final int CON_STATUS_ERROR_CONNECTION               = 0x01;
    public static final int CON_STATUS_ERROR_MAX_MSG_SIZE_UNSUPPORTED = 0x02;
    public static final int CON_STATUS_ERROR_MAX_MSG_SIZE_TOO_SMALL   = 0x03;
    public static final int CON_STATUS_OK_ONGOING_CALL                = 0x04;

    /* Disconnection type */
    public static final int DISC_GRACEFULL                = 0x00;
    public static final int DISC_IMMEDIATE                = 0x01;
    public static final int DISC_FORCED                   = 0x100; // Used internal only
    public static final int DISC_RFCOMM                   = 0x101; // Used internal only

    /* Status Change */
    public static final int STATUS_UNKNOWN_ERROR       = 0x00;
    public static final int STATUS_CARD_RESET          = 0x01;
    public static final int STATUS_CARD_NOT_ACCESSIBLE = 0x02;
    public static final int STATUS_CARD_REMOVED        = 0x03;
    public static final int STATUS_CARD_INSERTED       = 0x04;
    public static final int STATUS_RECOVERED           = 0x05;

    /* Transport Protocol */
    public static final int TRANS_PROTO_T0           = 0x00;
    public static final int TRANS_PROTO_T1           = 0x01;

    /* Test Mode */
    public static final int TEST_MODE_DISABLE        = 0x00;
    public static final int TEST_MODE_ENABLE         = 0x01;

    /* Used to detect uninitialized values */
    public static final int INVALID_VALUE = -1;

    /* Stuff related to communicating with rild-bt */
    static final int RESPONSE_SOLICITED = 0;
    static final int RESPONSE_UNSOLICITED = 1;
    static AtomicInteger sNextSerial = new AtomicInteger(1);

    // Map<rilSerial, RequestType> - HashTable is synchronized
    static Map<Integer, Integer> sOngoingRequests = new Hashtable<Integer, Integer>();
    private boolean mSendToRil = false; // set to true for messages that needs to go to the RIL
    private boolean mClearRilQueue = false; /* set to true for messages that needs to cause the
                                              sOngoingRequests to be cleared. */

    /* Instance members */
    private int mMsgType = INVALID_VALUE; // The SAP message ID

    private int mMaxMsgSize = INVALID_VALUE;
    private int mConnectionStatus = INVALID_VALUE;
    private int mResultCode = INVALID_VALUE;
    private int mDisconnectionType = INVALID_VALUE;
    private int mCardReaderStatus = INVALID_VALUE;
    private int mStatusChange = INVALID_VALUE;
    private int mTransportProtocol = INVALID_VALUE;
    private int mTestMode = INVALID_VALUE;
    private byte[] mApdu = null;
    private byte[] mApdu7816 = null;
    private byte[] mApduResp = null;
    private byte[] mAtr = null;

    /**
     * Create a SapMessage
     * @param msgType the SAP message type
     */
    public SapMessage(int msgType){
        this.mMsgType = msgType;
    }

    private static void resetPendingRilMessages() {
        int numMessages = sOngoingRequests.size();
        if(numMessages != 0) {
            Log.w(TAG, "Clearing message queue with size: " + numMessages);
            sOngoingRequests.clear();
        }
    }

    public static int getNumPendingRilMessages() {
        return sOngoingRequests.size();
    }

    public int getMsgType() {
        return mMsgType;
    }

    public void setMsgType(int msgType) {
        this.mMsgType = msgType;
    }

    public int getMaxMsgSize() {
        return mMaxMsgSize;
    }

    public void setMaxMsgSize(int maxMsgSize) {
        this.mMaxMsgSize = maxMsgSize;
    }

    public int getConnectionStatus() {
        return mConnectionStatus;
    }

    public void setConnectionStatus(int connectionStatus) {
        this.mConnectionStatus = connectionStatus;
    }

    public int getResultCode() {
        return mResultCode;
    }

    public void setResultCode(int resultCode) {
        this.mResultCode = resultCode;
    }

    public int getDisconnectionType() {
        return mDisconnectionType;
    }

    public void setDisconnectionType(int disconnectionType) {
        this.mDisconnectionType = disconnectionType;
    }

    public int getCardReaderStatus() {
        return mCardReaderStatus;
    }

    public void setCardReaderStatus(int cardReaderStatus) {
        this.mCardReaderStatus = cardReaderStatus;
    }

    public int getStatusChange() {
        return mStatusChange;
    }

    public void setStatusChange(int statusChange) {
        this.mStatusChange = statusChange;
    }

    public int getTransportProtocol() {
        return mTransportProtocol;
    }

    public void setTransportProtocol(int transportProtocol) {
        this.mTransportProtocol = transportProtocol;
    }

    public byte[] getApdu() {
        return mApdu;
    }

    public void setApdu(byte[] apdu) {
        this.mApdu = apdu;
    }

    public byte[] getApdu7816() {
        return mApdu7816;
    }

    public void setApdu7816(byte[] apdu) {
        this.mApdu7816 = apdu;
    }

    public byte[] getApduResp() {
        return mApduResp;
    }

    public void setApduResp(byte[] apduResp) {
        this.mApduResp = apduResp;
    }

    public byte[] getAtr() {
        return mAtr;
    }

    public void setAtr(byte[] atr) {
        this.mAtr = atr;
    }

    public boolean getSendToRil() {
        return mSendToRil;
    }

    public void setSendToRil(boolean sendToRil) {
        this.mSendToRil = sendToRil;
    }

    public boolean getClearRilQueue() {
        return mClearRilQueue;
    }

    public void setClearRilQueue(boolean clearRilQueue) {
        this.mClearRilQueue = clearRilQueue;
    }

    public int getTestMode() {
        return mTestMode;
    }

    public void setTestMode(int testMode) {
        this.mTestMode = testMode;
    }

    private int getParamCount() {
        int paramCount = 0;
        if(mMaxMsgSize != INVALID_VALUE)
            paramCount++;
        if(mConnectionStatus != INVALID_VALUE)
            paramCount++;
        if(mResultCode != INVALID_VALUE)
            paramCount++;
        if(mDisconnectionType != INVALID_VALUE)
            paramCount++;
        if(mCardReaderStatus != INVALID_VALUE)
            paramCount++;
        if(mStatusChange != INVALID_VALUE)
            paramCount++;
        if(mTransportProtocol != INVALID_VALUE)
            paramCount++;
        if(mApdu != null)
            paramCount++;
        if(mApdu7816 != null)
            paramCount++;
        if(mApduResp != null)
            paramCount++;
        if(mAtr != null)
            paramCount++;
        return paramCount;
    }

    /**
     * Construct a SapMessage based on the incoming rfcomm request.
     * @param requestType The type of the request
     * @param is the input stream to read the data from
     * @return the resulting message, or null if an error occurs
     */
    @SuppressWarnings("unused")
    public static SapMessage readMessage(int requestType, InputStream is) {
        SapMessage newMessage = new SapMessage(requestType);

        /* Read in all the parameters (if any) */
        int paramCount;
        try {
            paramCount = is.read();
            skip(is, 2); // Skip the 2 padding bytes
            if(paramCount > 0) {
                if(VERBOSE) Log.i(TAG, "Parsing message with paramCount: " + paramCount);
                if(newMessage.parseParameters(paramCount, is) == false)
                    return null;
            }
        } catch (IOException e) {
            Log.w(TAG, e);
            return null;
        }
        if(DEBUG) Log.i(TAG, "readMessage() Read message: " + getMsgTypeName(requestType));

        /* Validate parameters */
        switch(requestType) {
        case ID_CONNECT_REQ:
            if(newMessage.getMaxMsgSize() == INVALID_VALUE) {
                Log.e(TAG, "Missing MaxMsgSize parameter in CONNECT_REQ");
                return null;
            }
            break;
        case ID_TRANSFER_APDU_REQ:
            if(newMessage.getApdu() == null &&
                   newMessage.getApdu7816() == null) {
                Log.e(TAG, "Missing Apdu parameter in TRANSFER_APDU_REQ");
                return null;
            }
            newMessage.setSendToRil(true);
            break;
        case ID_SET_TRANSPORT_PROTOCOL_REQ:
            if(newMessage.getTransportProtocol() == INVALID_VALUE) {
                Log.e(TAG, "Missing TransportProtocol parameter in SET_TRANSPORT_PROTOCOL_REQ");
                return null;
            }
            newMessage.setSendToRil(true);
            break;
        case ID_TRANSFER_ATR_REQ:  /* No params */
        case ID_POWER_SIM_OFF_REQ: /* No params */
        case ID_POWER_SIM_ON_REQ:  /* No params */
        case ID_RESET_SIM_REQ:     /* No params */
        case ID_TRANSFER_CARD_READER_STATUS_REQ: /* No params */
            newMessage.setSendToRil(true);
            break;
        case ID_DISCONNECT_REQ:    /* No params */
            break;
        default:
            Log.e(TAG, "Unknown request type");
            return null;
        }
        return newMessage;
    }

    /**
     * Blocking read of an entire array of data.
     * @param is the input stream to read from
     * @param buffer the buffer to read into - the length of the buffer will
     *        determine how many bytes will be read.
     */
    private static void read(InputStream is, byte[] buffer) throws IOException {
        int bytesToRead = buffer.length;
        int bytesRead = 0;
        int tmpBytesRead;
        while (bytesRead < bytesToRead) {
            tmpBytesRead = is.read(buffer, bytesRead, bytesToRead-bytesRead);
            if(tmpBytesRead == -1)
                throw new IOException("EOS reached while reading a byte array.");
            else
                bytesRead += tmpBytesRead;
        }
    }

    /**
     * Skip a number of bytes in an InputStream.
     * @param is the input stream
     * @param count the number of bytes to skip
     * @throws IOException In case of reaching EOF or a stream error
     */
    private static void skip(InputStream is, int count) throws IOException {
        for(int i = 0; i < count; i++) {
            is.read(); // Do not use the InputStream.skip as it fails for some stream types
        }
    }

    /**
     * Read the parameters from the stream and update the relevant members.
     * This function will ensure that all parameters are read from the stream, even
     * if an error is detected.
     * @param count the number of parameters to read
     * @param is the input stream
     * @return True if all parameters were successfully parsed, False if an error were detected.
     * @throws IOException
     */
    private boolean parseParameters(int count, InputStream is) throws IOException {
        int paramId;
        int paramLength;
        boolean success = true;
        int skipLen = 0;

        for(int i = 0; i < count; i++) {
            paramId = is.read();
            is.read(); // Skip the reserved byte
            paramLength = is.read();
            paramLength = paramLength << 8 | is.read();

            // As per SAP spec padding should be 0-3 bytes
            if ((paramLength % 4) != 0)
                skipLen = 4 - (paramLength % 4);

            if(VERBOSE) Log.i(TAG, "parsing paramId: " + paramId + " with length: " + paramLength);
            switch(paramId) {
            case PARAM_MAX_MSG_SIZE_ID:
                if(paramLength != PARAM_MAX_MSG_SIZE_LENGTH) {
                    Log.e(TAG, "Received PARAM_MAX_MSG_SIZE with wrong length: " +
                            paramLength + " skipping this parameter.");
                    skip(is, paramLength + skipLen);
                    success = false;
                } else {
                    mMaxMsgSize = is.read();
                    mMaxMsgSize = mMaxMsgSize << 8 | is.read();
                    skip(is, 4 - PARAM_MAX_MSG_SIZE_LENGTH);
                }
                break;
            case PARAM_COMMAND_APDU_ID:
                mApdu = new byte[paramLength];
                read(is, mApdu);
                skip(is, skipLen);
                break;
            case PARAM_COMMAND_APDU7816_ID:
                mApdu7816 = new byte[paramLength];
                read(is, mApdu7816);
                skip(is, skipLen);
                break;
            case PARAM_TRANSPORT_PROTOCOL_ID:
                if(paramLength != PARAM_TRANSPORT_PROTOCOL_LENGTH) {
                    Log.e(TAG, "Received PARAM_TRANSPORT_PROTOCOL with wrong length: " +
                            paramLength + " skipping this parameter.");
                    skip(is, paramLength + skipLen);
                    success = false;
                } else {
                    mTransportProtocol = is.read();
                    skip(is, 4 - PARAM_TRANSPORT_PROTOCOL_LENGTH);
                }
                break;
            case PARAM_CONNECTION_STATUS_ID:
                // not needed for server role, but used for module test
                if(paramLength != PARAM_CONNECTION_STATUS_LENGTH) {
                    Log.e(TAG, "Received PARAM_CONNECTION_STATUS with wrong length: " +
                            paramLength + " skipping this parameter.");
                    skip(is, paramLength + skipLen);
                    success = false;
                } else {
                    mConnectionStatus = is.read();
                    skip(is, 4 - PARAM_CONNECTION_STATUS_LENGTH);
                }
                break;
            case PARAM_CARD_READER_STATUS_ID:
                // not needed for server role, but used for module test
                if(paramLength != PARAM_CARD_READER_STATUS_LENGTH) {
                    Log.e(TAG, "Received PARAM_CARD_READER_STATUS with wrong length: " +
                            paramLength + " skipping this parameter.");
                    skip(is, paramLength + skipLen);
                    success = false;
                } else {
                    mCardReaderStatus = is.read();
                    skip(is, 4 - PARAM_CARD_READER_STATUS_LENGTH);
                }
                break;
            case PARAM_STATUS_CHANGE_ID:
                // not needed for server role, but used for module test
                if(paramLength != PARAM_STATUS_CHANGE_LENGTH) {
                    Log.e(TAG, "Received PARAM_STATUS_CHANGE with wrong length: " +
                            paramLength + " skipping this parameter.");
                    skip(is, paramLength + skipLen);
                    success = false;
                } else {
                    mStatusChange = is.read();
                    skip(is, 4 - PARAM_STATUS_CHANGE_LENGTH);
                }
                break;
            case PARAM_RESULT_CODE_ID:
                // not needed for server role, but used for module test
                if(paramLength != PARAM_RESULT_CODE_LENGTH) {
                    Log.e(TAG, "Received PARAM_RESULT_CODE with wrong length: " +
                            paramLength + " skipping this parameter.");
                    skip(is, paramLength + skipLen);
                    success = false;
                } else {
                    mResultCode = is.read();
                    skip(is, 4 - PARAM_RESULT_CODE_LENGTH);
                }
                break;
            case PARAM_DISCONNECT_TYPE_ID:
                // not needed for server role, but used for module test
                if(paramLength != PARAM_DISCONNECT_TYPE_LENGTH) {
                    Log.e(TAG, "Received PARAM_DISCONNECT_TYPE_ID with wrong length: " +
                            paramLength + " skipping this parameter.");
                    skip(is, paramLength + skipLen);
                    success = false;
                } else {
                    mDisconnectionType = is.read();
                    skip(is, 4 - PARAM_DISCONNECT_TYPE_LENGTH);
                }
                break;
            case PARAM_RESPONSE_APDU_ID:
                // not needed for server role, but used for module test
                mApduResp = new byte[paramLength];
                read(is, mApduResp);
                skip(is, skipLen);
                break;
            case PARAM_ATR_ID:
                // not needed for server role, but used for module test
                mAtr = new byte[paramLength];
                read(is, mAtr);
                skip(is, skipLen);
                break;
            default:
                Log.e(TAG, "Received unknown parameter ID: " + paramId + " length: " +
                        paramLength + " skipping this parameter.");
                skip(is, paramLength + skipLen);
            }
        }
        return success;
    }

    /**
     * Writes a single value parameter of 1 or 2 bytes in length.
     * @param os The BufferedOutputStream to write to.
     * @param id The Parameter ID
     * @param value The parameter value
     * @param length The length of the parameter value
     * @throws IOException if the write to os fails
     */
    private static void writeParameter(OutputStream os, int id, int value, int length)
                throws IOException {

        /* Parameter Header*/
        os.write(id);
        os.write(0);
        os.write(0);
        os.write(length);

        switch(length) {
        case 1:
            os.write(value & 0xff);
            os.write(0); // Padding
            os.write(0); // Padding
            os.write(0); // padding
            break;
        case 2:
            os.write((value >> 8) & 0xff);
            os.write(value & 0xff);
            os.write(0); // Padding
            os.write(0); // padding
            break;
        default:
            throw new IOException("Unable to write value of length: " + length);
        }
    }

    /**
     * Writes a byte[] parameter of any length.
     * @param os The BufferedOutputStream to write to.
     * @param id The Parameter ID
     * @param value The byte array to write, the length will be extracted from the array.
     * @throws IOException if the write to os fails
     */
    private static void writeParameter(OutputStream os, int id, byte[] value) throws IOException {

        /* Parameter Header*/
        os.write(id);
        os.write(0); // reserved
        os.write((value.length >> 8) & 0xff);
        os.write(value.length & 0xff);

        /* Payload */
        os.write(value);
        if (value.length % 4 != 0) {
            for (int i = 0; i < (4 - (value.length % 4)); ++i) {
                os.write(0); // Padding
            }
        }
    }

    public void write(OutputStream os) throws IOException {
        /* Write the header */
        os.write(mMsgType);
        os.write(getParamCount());
        os.write(0); // padding
        os.write(0); // padding

        /* write the parameters */
        if(mConnectionStatus != INVALID_VALUE) {
            writeParameter(os,PARAM_CONNECTION_STATUS_ID, mConnectionStatus,
                            PARAM_CONNECTION_STATUS_LENGTH);
        }
        if(mMaxMsgSize != INVALID_VALUE) {
            writeParameter(os, PARAM_MAX_MSG_SIZE_ID, mMaxMsgSize,
                            PARAM_MAX_MSG_SIZE_LENGTH);
        }
        if(mResultCode != INVALID_VALUE) {
            writeParameter(os, PARAM_RESULT_CODE_ID, mResultCode,
                            PARAM_RESULT_CODE_LENGTH);
        }
        if(mDisconnectionType != INVALID_VALUE) {
            writeParameter(os, PARAM_DISCONNECT_TYPE_ID, mDisconnectionType,
                            PARAM_DISCONNECT_TYPE_LENGTH);
        }
        if(mCardReaderStatus != INVALID_VALUE) {
            writeParameter(os, PARAM_CARD_READER_STATUS_ID, mCardReaderStatus,
                            PARAM_CARD_READER_STATUS_LENGTH);
        }
        if(mStatusChange != INVALID_VALUE) {
            writeParameter(os, PARAM_STATUS_CHANGE_ID, mStatusChange,
                            PARAM_STATUS_CHANGE_LENGTH);
        }
        if(mTransportProtocol != INVALID_VALUE) {
            writeParameter(os, PARAM_TRANSPORT_PROTOCOL_ID, mTransportProtocol,
                            PARAM_TRANSPORT_PROTOCOL_LENGTH);
        }
        if(mApdu != null) {
            writeParameter(os, PARAM_COMMAND_APDU_ID, mApdu);
        }
        if(mApdu7816 != null) {
            writeParameter(os, PARAM_COMMAND_APDU7816_ID, mApdu7816);
        }
        if(mApduResp != null) {
            writeParameter(os, PARAM_RESPONSE_APDU_ID, mApduResp);
        }
        if(mAtr != null) {
            writeParameter(os, PARAM_ATR_ID, mAtr);
        }
    }

    /***************************************************************************
     * RILD Interface message conversion functions.
     ***************************************************************************/

    /**
     * We use this function to
     * @param length
     * @param rawOut
     * @throws IOException
     */
    private void writeLength(int length, CodedOutputStreamMicro out) throws IOException {
        byte[] dataLength = new byte[4];
        dataLength[0] = dataLength[1] = 0;
        dataLength[2] = (byte)((length >> 8) & 0xff);
        dataLength[3] = (byte)((length) & 0xff);
        out.writeRawBytes(dataLength);
    }

    private ArrayList<Byte> primitiveArrayToContainerArrayList(byte[] arr) {
        ArrayList<Byte> arrayList = new ArrayList<>(arr.length);
        for (byte b : arr) {
            arrayList.add(b);
        }
        return arrayList;
    }

    /**
     * Send the message by calling corresponding ISap api.
     */
    public void send(ISap sapProxy) throws RemoteException, RuntimeException {
        int rilSerial = sNextSerial.getAndIncrement();

        Log.e(TAG, "callISapReq: called for mMsgType " + mMsgType + " rilSerial " + rilSerial);

        /* Update the ongoing requests queue */
        if (mClearRilQueue == true) {
            resetPendingRilMessages();
        }
        // No need to synchronize this, as the HashList is already doing this.
        sOngoingRequests.put(rilSerial, mMsgType);

        switch(mMsgType) {
        case ID_CONNECT_REQ:
        {
            sapProxy.connectReq(rilSerial, mMaxMsgSize);
            break;
        }
        case ID_DISCONNECT_REQ:
        {
            sapProxy.disconnectReq(rilSerial);
            break;
        }
        case ID_TRANSFER_APDU_REQ:
        {
            int type;
            ArrayList<Byte> command;
            if(mApdu != null) {
                type = SapApduType.APDU;
                command = primitiveArrayToContainerArrayList(mApdu);
            } else if (mApdu7816 != null) {
                type = SapApduType.APDU7816;
                command = primitiveArrayToContainerArrayList(mApdu7816);
            } else {
                Log.e(TAG, "Missing Apdu parameter in TRANSFER_APDU_REQ");
                throw new IllegalArgumentException();
            }
            sapProxy.apduReq(rilSerial, type, command);
            break;
        }
        case ID_SET_TRANSPORT_PROTOCOL_REQ:
        {
            int transportProtocol;
            if(mTransportProtocol == TRANS_PROTO_T0) {
                transportProtocol = SapTransferProtocol.T0;
            } else if(mTransportProtocol == TRANS_PROTO_T1) {
                transportProtocol = SapTransferProtocol.T1;
            } else {
                Log.e(TAG, "Missing or invalid TransportProtocol parameter in"
                                + " SET_TRANSPORT_PROTOCOL_REQ: " + mTransportProtocol);
                throw new IllegalArgumentException();
            }
            sapProxy.setTransferProtocolReq(rilSerial, transportProtocol);
            break;
        }
        case ID_TRANSFER_ATR_REQ:
        {
            sapProxy.transferAtrReq(rilSerial);
            break;
        }
        case ID_POWER_SIM_OFF_REQ:
        {
            sapProxy.powerReq(rilSerial, false);
            break;
        }
        case ID_POWER_SIM_ON_REQ:
        {
            sapProxy.powerReq(rilSerial, true);
            break;
        }
        case ID_RESET_SIM_REQ:
        {
            sapProxy.resetSimReq(rilSerial);
            break;
        }
        case ID_TRANSFER_CARD_READER_STATUS_REQ:
        {
            sapProxy.transferCardReaderStatusReq(rilSerial);
            break;
        }
        default:
            Log.e(TAG, "Unknown request type");
            throw new IllegalArgumentException();
        }
        if (VERBOSE) Log.e(TAG, "callISapReq: done without exceptions");
    }

    public static SapMessage newInstance(MsgHeader msg) throws IOException {
        return new SapMessage(msg);
    }

    private SapMessage(MsgHeader msg) throws IOException {
        // All header members are "required" hence the hasXxxx() is not needed for those
        try{
            switch(msg.getType()){
            case SapApi.UNSOL_RESPONSE:
                createUnsolicited(msg);
                break;
            case SapApi.RESPONSE:
                createSolicited(msg);
                break;
            default:
                throw new IOException("Wrong msg header received: Type: " + msg.getType());
            }
        } catch (InvalidProtocolBufferMicroException e) {
            Log.w(TAG, "Error occured parsing a RIL message", e);
            throw new IOException("Error occured parsing a RIL message");
        }
    }

    private void createUnsolicited(MsgHeader msg)
                    throws IOException, InvalidProtocolBufferMicroException {
        switch(msg.getId()) {
// TODO:
//        Not sure when we use these?        case RIL_UNSOL_RIL_CONNECTED:
//            if(VERBOSE) Log.i(TAG, "RIL_UNSOL_RIL_CONNECTED received, ignoring");
//            msgType = ID_RIL_UNSOL_CONNECTED;
//            break;
        case SapApi.RIL_SIM_SAP_STATUS:
        {
            if(VERBOSE) Log.i(TAG, "RIL_SIM_SAP_STATUS_IND received");
            RIL_SIM_SAP_STATUS_IND indMsg =
                    RIL_SIM_SAP_STATUS_IND.parseFrom(msg.getPayload().toByteArray());
            mMsgType = ID_STATUS_IND;
            if(indMsg.hasStatusChange()) {
                setStatusChange(indMsg.getStatusChange());
                if(VERBOSE) Log.i(TAG, "RIL_UNSOL_SIM_SAP_STATUS_IND received value = "
                        + mStatusChange);
            } else {
                if(VERBOSE) Log.i(TAG, "Wrong number of parameters in SAP_STATUS_IND, ignoring...");
                mMsgType = ID_RIL_UNKNOWN;
            }
            break;
        }
        case SapApi.RIL_SIM_SAP_DISCONNECT:
        {
            if(VERBOSE) Log.i(TAG, "RIL_SIM_SAP_DISCONNECT_IND received");

            RIL_SIM_SAP_DISCONNECT_IND indMsg =
                    RIL_SIM_SAP_DISCONNECT_IND.parseFrom(msg.getPayload().toByteArray());
            mMsgType = ID_RIL_UNSOL_DISCONNECT_IND; // don't use ID_DISCONNECT_IND;
            if(indMsg.hasDisconnectType()) {
                setDisconnectionType(indMsg.getDisconnectType());
                if(VERBOSE) Log.i(TAG, "RIL_UNSOL_SIM_SAP_STATUS_IND received value = "
                                                                + mDisconnectionType);
            } else {
                if(VERBOSE) Log.i(TAG, "Wrong number of parameters in SAP_STATUS_IND, ignoring...");
                mMsgType = ID_RIL_UNKNOWN;
            }
            break;
        }
        default:
            if(VERBOSE) Log.i(TAG, "Unused unsolicited message received, ignoring: " + msg.getId());
            mMsgType = ID_RIL_UNKNOWN;
        }
    }

    private void createSolicited(MsgHeader msg) throws IOException,
                                                       InvalidProtocolBufferMicroException{
        /* re-evaluate if we should just ignore these - we could simply catch the exception? */
        if(msg.hasToken() == false) throw new IOException("Token is missing");
        if(msg.hasError() == false) throw new IOException("Error code is missing");
        int serial = msg.getToken();
        int error = msg.getError();
        Integer reqType = null;
        reqType = sOngoingRequests.remove(serial);
        if(VERBOSE) Log.i(TAG, "RIL SOLICITED serial: " + serial + ", error: " + error
                + " SapReqType: " + ((reqType== null)?"null":getMsgTypeName(reqType)));

        if(reqType == null) {
            /* This can happen if we get a resp. for a canceled request caused by a power off,
             *  reset or disconnect
             */
            Log.w(TAG, "Solicited response received on a command not initiated - ignoring.");
            return;
        }
        mResultCode = mapRilErrorCode(error);

        switch(reqType) {
        case ID_CONNECT_REQ:
        {
            RIL_SIM_SAP_CONNECT_RSP resMsg =
                    RIL_SIM_SAP_CONNECT_RSP.parseFrom(msg.getPayload().toByteArray());
            mMsgType = ID_CONNECT_RESP;
            if(resMsg.hasMaxMessageSize()) {
                mMaxMsgSize = resMsg.getMaxMessageSize();

            }
            switch(resMsg.getResponse()) {
            case RIL_SIM_SAP_CONNECT_RSP.RIL_E_SUCCESS:
                mConnectionStatus = CON_STATUS_OK;
                break;
            case RIL_SIM_SAP_CONNECT_RSP.RIL_E_SAP_CONNECT_OK_CALL_ONGOING:
                mConnectionStatus = CON_STATUS_OK_ONGOING_CALL;
                break;
            case RIL_SIM_SAP_CONNECT_RSP.RIL_E_SAP_CONNECT_FAILURE:
                mConnectionStatus = CON_STATUS_ERROR_CONNECTION;
                break;
            case RIL_SIM_SAP_CONNECT_RSP.RIL_E_SAP_MSG_SIZE_TOO_LARGE:
                mConnectionStatus = CON_STATUS_ERROR_MAX_MSG_SIZE_UNSUPPORTED;
                break;
            case RIL_SIM_SAP_CONNECT_RSP.RIL_E_SAP_MSG_SIZE_TOO_SMALL:
                mConnectionStatus = CON_STATUS_ERROR_MAX_MSG_SIZE_TOO_SMALL;
                break;
            default:
                mConnectionStatus = CON_STATUS_ERROR_CONNECTION; // Cannot happen!
                break;
            }
            mResultCode = INVALID_VALUE;
            if(VERBOSE) Log.v(TAG, "  ID_CONNECT_REQ: mMaxMsgSize: " + mMaxMsgSize
                    + "  mConnectionStatus: " + mConnectionStatus);
            break;
        }
        case ID_DISCONNECT_REQ:
            mMsgType = ID_DISCONNECT_RESP;
            mResultCode = INVALID_VALUE;
            break;
        case ID_TRANSFER_APDU_REQ:
        {
            RIL_SIM_SAP_APDU_RSP resMsg =
                    RIL_SIM_SAP_APDU_RSP.parseFrom(msg.getPayload().toByteArray());
            mMsgType = ID_TRANSFER_APDU_RESP;
            switch(resMsg.getResponse()) {
            case RIL_SIM_SAP_APDU_RSP.RIL_E_SUCCESS:
                mResultCode = RESULT_OK;
                /* resMsg.getType is unused as the client knows the type of request used. */
                if(resMsg.hasApduResponse()){
                    mApduResp = resMsg.getApduResponse().toByteArray();
                }
                break;
            case RIL_SIM_SAP_APDU_RSP.RIL_E_GENERIC_FAILURE:
                mResultCode = RESULT_ERROR_NO_REASON;
                break;
            case RIL_SIM_SAP_APDU_RSP.RIL_E_SIM_ABSENT:
                mResultCode = RESULT_ERROR_CARD_NOT_ACCESSIBLE;
                break;
            case RIL_SIM_SAP_APDU_RSP.RIL_E_SIM_ALREADY_POWERED_OFF:
                mResultCode = RESULT_ERROR_CARD_POWERED_OFF;
                break;
            case RIL_SIM_SAP_APDU_RSP.RIL_E_SIM_NOT_READY:
                mResultCode = RESULT_ERROR_CARD_REMOVED;
                break;
            default:
                mResultCode = RESULT_ERROR_NO_REASON;
                break;
            }
            break;
        }
        case ID_SET_TRANSPORT_PROTOCOL_REQ:
        {
            RIL_SIM_SAP_SET_TRANSFER_PROTOCOL_RSP resMsg =
                        RIL_SIM_SAP_SET_TRANSFER_PROTOCOL_RSP.parseFrom(
                                msg.getPayload().toByteArray());
            mMsgType = ID_SET_TRANSPORT_PROTOCOL_RESP;
            switch(resMsg.getResponse()) {
            case RIL_SIM_SAP_SET_TRANSFER_PROTOCOL_RSP.RIL_E_SUCCESS:
                mResultCode = RESULT_OK;
                break;
            case RIL_SIM_SAP_SET_TRANSFER_PROTOCOL_RSP.RIL_E_GENERIC_FAILURE:
                mResultCode = RESULT_ERROR_NOT_SUPPORTED;
                break;
            case RIL_SIM_SAP_SET_TRANSFER_PROTOCOL_RSP.RIL_E_SIM_ABSENT:
                mResultCode = RESULT_ERROR_CARD_NOT_ACCESSIBLE;
                break;
            case RIL_SIM_SAP_SET_TRANSFER_PROTOCOL_RSP.RIL_E_SIM_ALREADY_POWERED_OFF:
                mResultCode = RESULT_ERROR_CARD_POWERED_OFF;
                break;
            case RIL_SIM_SAP_SET_TRANSFER_PROTOCOL_RSP.RIL_E_SIM_NOT_READY:
                mResultCode = RESULT_ERROR_CARD_REMOVED;
                break;
            default:
                mResultCode = RESULT_ERROR_NOT_SUPPORTED;
                break;
            }
            break;
        }
        case ID_TRANSFER_ATR_REQ:
        {
            RIL_SIM_SAP_TRANSFER_ATR_RSP resMsg =
                    RIL_SIM_SAP_TRANSFER_ATR_RSP.parseFrom(msg.getPayload().toByteArray());
            mMsgType =ID_TRANSFER_ATR_RESP;
            if(resMsg.hasAtr()) {
                mAtr = resMsg.getAtr().toByteArray();
            }
            switch(resMsg.getResponse()) {
            case RIL_SIM_SAP_TRANSFER_ATR_RSP.RIL_E_SUCCESS:
                mResultCode = RESULT_OK;
                break;
            case RIL_SIM_SAP_TRANSFER_ATR_RSP.RIL_E_GENERIC_FAILURE:
                mResultCode = RESULT_ERROR_NO_REASON;
                break;
            case RIL_SIM_SAP_TRANSFER_ATR_RSP.RIL_E_SIM_ABSENT:
                mResultCode = RESULT_ERROR_CARD_NOT_ACCESSIBLE;
                break;
            case RIL_SIM_SAP_TRANSFER_ATR_RSP.RIL_E_SIM_ALREADY_POWERED_OFF:
                mResultCode = RESULT_ERROR_CARD_POWERED_OFF;
                break;
            case RIL_SIM_SAP_TRANSFER_ATR_RSP.RIL_E_SIM_ALREADY_POWERED_ON:
                mResultCode = RESULT_ERROR_CARD_POWERED_ON;
                break;
            case RIL_SIM_SAP_TRANSFER_ATR_RSP.RIL_E_SIM_DATA_NOT_AVAILABLE:
                mResultCode = RESULT_ERROR_DATA_NOT_AVAILABLE;
                break;
            default:
                mResultCode = RESULT_ERROR_NO_REASON;
                break;
            }
            break;
        }
        case ID_POWER_SIM_OFF_REQ:
        {
            RIL_SIM_SAP_POWER_RSP resMsg =
                    RIL_SIM_SAP_POWER_RSP.parseFrom(msg.getPayload().toByteArray());
            mMsgType = ID_POWER_SIM_OFF_RESP;
            switch(resMsg.getResponse()) {
            case RIL_SIM_SAP_POWER_RSP.RIL_E_SUCCESS:
                mResultCode = RESULT_OK;
                break;
            case RIL_SIM_SAP_POWER_RSP.RIL_E_GENERIC_FAILURE:
                mResultCode = RESULT_ERROR_NO_REASON;
                break;
            case RIL_SIM_SAP_POWER_RSP.RIL_E_SIM_ABSENT:
                mResultCode = RESULT_ERROR_CARD_NOT_ACCESSIBLE;
                break;
            case RIL_SIM_SAP_POWER_RSP.RIL_E_SIM_ALREADY_POWERED_OFF:
                mResultCode = RESULT_ERROR_CARD_POWERED_OFF;
                break;
            case RIL_SIM_SAP_POWER_RSP.RIL_E_SIM_ALREADY_POWERED_ON:
                mResultCode = RESULT_ERROR_CARD_POWERED_ON;
                break;
            default:
                mResultCode = RESULT_ERROR_NO_REASON;
                break;
            }
            break;
        }
        case ID_POWER_SIM_ON_REQ:
        {
            RIL_SIM_SAP_POWER_RSP resMsg =
                    RIL_SIM_SAP_POWER_RSP.parseFrom(msg.getPayload().toByteArray());
            mMsgType = ID_POWER_SIM_ON_RESP;
            switch(resMsg.getResponse()) {
            case RIL_SIM_SAP_POWER_RSP.RIL_E_SUCCESS:
                mResultCode = RESULT_OK;
                break;
            case RIL_SIM_SAP_POWER_RSP.RIL_E_GENERIC_FAILURE:
                mResultCode = RESULT_ERROR_NO_REASON;
                break;
            case RIL_SIM_SAP_POWER_RSP.RIL_E_SIM_ABSENT:
                mResultCode = RESULT_ERROR_CARD_NOT_ACCESSIBLE;
                break;
            case RIL_SIM_SAP_POWER_RSP.RIL_E_SIM_ALREADY_POWERED_OFF:
                mResultCode = RESULT_ERROR_CARD_POWERED_OFF;
                break;
            case RIL_SIM_SAP_POWER_RSP.RIL_E_SIM_ALREADY_POWERED_ON:
                mResultCode = RESULT_ERROR_CARD_POWERED_ON;
                break;
            default:
                mResultCode = RESULT_ERROR_NO_REASON;
                break;
            }
            break;
        }
        case ID_RESET_SIM_REQ:
        {
            RIL_SIM_SAP_RESET_SIM_RSP resMsg =
                    RIL_SIM_SAP_RESET_SIM_RSP.parseFrom(msg.getPayload().toByteArray());
            mMsgType = ID_RESET_SIM_RESP;
            switch(resMsg.getResponse()) {
            case RIL_SIM_SAP_RESET_SIM_RSP.RIL_E_SUCCESS:
                mResultCode = RESULT_OK;
                break;
            case RIL_SIM_SAP_RESET_SIM_RSP.RIL_E_GENERIC_FAILURE:
                mResultCode = RESULT_ERROR_NO_REASON;
                break;
            case RIL_SIM_SAP_RESET_SIM_RSP.RIL_E_SIM_ABSENT:
                mResultCode = RESULT_ERROR_CARD_NOT_ACCESSIBLE;
                break;
            case RIL_SIM_SAP_RESET_SIM_RSP.RIL_E_SIM_ALREADY_POWERED_OFF:
                mResultCode = RESULT_ERROR_CARD_POWERED_OFF;
                break;
            default:
                mResultCode = RESULT_ERROR_NO_REASON;
                break;
            }
            break;
        }
        case ID_TRANSFER_CARD_READER_STATUS_REQ:
        {
            RIL_SIM_SAP_TRANSFER_CARD_READER_STATUS_RSP resMsg =
                    RIL_SIM_SAP_TRANSFER_CARD_READER_STATUS_RSP.parseFrom(
                            msg.getPayload().toByteArray());
            mMsgType = ID_TRANSFER_CARD_READER_STATUS_RESP;
            switch(resMsg.getResponse()) {
            case RIL_SIM_SAP_TRANSFER_CARD_READER_STATUS_RSP.RIL_E_SUCCESS:
                mResultCode = RESULT_OK;
                if(resMsg.hasCardReaderStatus()) {
                    mCardReaderStatus = resMsg.getCardReaderStatus();
                } else {
                    mResultCode = RESULT_ERROR_DATA_NOT_AVAILABLE;
                }
                break;
            case RIL_SIM_SAP_TRANSFER_CARD_READER_STATUS_RSP.RIL_E_GENERIC_FAILURE:
                mResultCode = RESULT_ERROR_NO_REASON;
                break;
            case RIL_SIM_SAP_TRANSFER_CARD_READER_STATUS_RSP.RIL_E_SIM_DATA_NOT_AVAILABLE:
                mResultCode = RESULT_ERROR_DATA_NOT_AVAILABLE;
                break;
            default:
                mResultCode = RESULT_ERROR_NO_REASON;
                break;
            }
            break;
        }

        case ID_RIL_SIM_ACCESS_TEST_REQ: // TODO: implement in RILD
            mMsgType = ID_RIL_SIM_ACCESS_TEST_RESP;
            break;
        default:
            Log.e(TAG, "Unknown request type: " + reqType);

        }
    }



    /* Map from RIL header error codes to SAP error codes */
    private static int mapRilErrorCode(int rilErrorCode) {
        switch(rilErrorCode) {
        case SapApi.RIL_E_SUCCESS:
            return RESULT_OK;
        case SapApi.RIL_E_CANCELLED:
            return RESULT_ERROR_NO_REASON;
        case SapApi.RIL_E_GENERIC_FAILURE:
            return RESULT_ERROR_NO_REASON;
        case SapApi.RIL_E_RADIO_NOT_AVAILABLE:
            return RESULT_ERROR_CARD_NOT_ACCESSIBLE;
        case SapApi.RIL_E_INVALID_PARAMETER:
            return RESULT_ERROR_NO_REASON;
        case SapApi.RIL_E_REQUEST_NOT_SUPPORTED:
            return RESULT_ERROR_NOT_SUPPORTED;
        default:
            return RESULT_ERROR_NO_REASON;
        }
    }



    public static String getMsgTypeName(int msgType) {
        if(DEBUG || VERBOSE) {
            switch (msgType)
            {
                case ID_CONNECT_REQ: return "ID_CONNECT_REQ";
                case ID_CONNECT_RESP: return "ID_CONNECT_RESP";
                case ID_DISCONNECT_REQ: return "ID_DISCONNECT_REQ";
                case ID_DISCONNECT_RESP: return "ID_DISCONNECT_RESP";
                case ID_DISCONNECT_IND: return "ID_DISCONNECT_IND";
                case ID_TRANSFER_APDU_REQ: return "ID_TRANSFER_APDU_REQ";
                case ID_TRANSFER_APDU_RESP: return "ID_TRANSFER_APDU_RESP";
                case ID_TRANSFER_ATR_REQ: return "ID_TRANSFER_ATR_REQ";
                case ID_TRANSFER_ATR_RESP: return "ID_TRANSFER_ATR_RESP";
                case ID_POWER_SIM_OFF_REQ: return "ID_POWER_SIM_OFF_REQ";
                case ID_POWER_SIM_OFF_RESP: return "ID_POWER_SIM_OFF_RESP";
                case ID_POWER_SIM_ON_REQ: return "ID_POWER_SIM_ON_REQ";
                case ID_POWER_SIM_ON_RESP: return "ID_POWER_SIM_ON_RESP";
                case ID_RESET_SIM_REQ: return "ID_RESET_SIM_REQ";
                case ID_RESET_SIM_RESP: return "ID_RESET_SIM_RESP";
                case ID_TRANSFER_CARD_READER_STATUS_REQ:
                    return "ID_TRANSFER_CARD_READER_STATUS_REQ";
                case ID_TRANSFER_CARD_READER_STATUS_RESP:
                    return "ID_TRANSFER_CARD_READER_STATUS_RESP";
                case ID_STATUS_IND: return "ID_STATUS_IND";
                case ID_ERROR_RESP: return "ID_ERROR_RESP";
                case ID_SET_TRANSPORT_PROTOCOL_REQ: return "ID_SET_TRANSPORT_PROTOCOL_REQ";
                case ID_SET_TRANSPORT_PROTOCOL_RESP: return "ID_SET_TRANSPORT_PROTOCOL_RESP";
                case ID_RIL_UNSOL_CONNECTED: return "ID_RIL_UNSOL_CONNECTED";
                case ID_RIL_UNKNOWN: return "ID_RIL_UNKNOWN";
                case ID_RIL_GET_SIM_STATUS_REQ: return "ID_RIL_GET_SIM_STATUS_REQ";
                case ID_RIL_SIM_ACCESS_TEST_REQ: return "ID_RIL_SIM_ACCESS_TEST_REQ";
                case ID_RIL_SIM_ACCESS_TEST_RESP: return "ID_RIL_SIM_ACCESS_TEST_RESP";
                default: return "Unknown Message Type (" + msgType + ")";
            }
        } else {
            return null;
        }
    }
}
