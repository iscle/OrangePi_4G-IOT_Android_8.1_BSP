/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.bluetooth.gatt;
import android.annotation.Nullable;

/** @hide */
public class AdvtFilterOnFoundOnLostInfo {
    private int mClientIf;

    private int mAdvPktLen;
    @Nullable
    private byte[] mAdvPkt;

    private int mScanRspLen;

    @Nullable
    private byte[] mScanRsp;

    private int mFiltIndex;
    private int mAdvState;
    private int mAdvInfoPresent;
    private String mAddress;

    private int mAddrType;
    private int mTxPower;
    private int mRssiValue;
    private int mTimeStamp;

    public AdvtFilterOnFoundOnLostInfo(int client_if, int adv_pkt_len, byte[] adv_pkt,
                    int scan_rsp_len, byte[] scan_rsp, int filt_index, int adv_state,
                    int adv_info_present, String address, int addr_type, int tx_power,
                    int rssi_value, int time_stamp){

        mClientIf = client_if;
        mAdvPktLen = adv_pkt_len;
        mAdvPkt = adv_pkt;
        mScanRspLen = scan_rsp_len;
        mScanRsp = scan_rsp;
        mFiltIndex = filt_index;
        mAdvState = adv_state;
        mAdvInfoPresent = adv_info_present;
        mAddress = address;
        mAddrType = addr_type;
        mTxPower = tx_power;
        mRssiValue = rssi_value;
        mTimeStamp = time_stamp;
    }

    public int getClientIf () {
        return mClientIf;
    }

    public int getFiltIndex () {
        return mFiltIndex;
    }

    public int getAdvState () {
        return mAdvState;
    }

    public int getTxPower () {
        return mTxPower;
    }

    public int getTimeStamp () {
        return mTimeStamp;
    }

    public int getRSSIValue () {
        return mRssiValue;
    }

    public int getAdvInfoPresent () {
        return mAdvInfoPresent;
    }

    public String getAddress() {
        return mAddress;
    }

    public int getAddressType() {
        return mAddrType;
    }

    public byte[] getAdvPacketData() {
        return mAdvPkt;
    }

    public int getAdvPacketLen() {
        return mAdvPktLen;
    }

    public byte[] getScanRspData() {
        return mScanRsp;
    }

    public int getScanRspLen() {
        return mScanRspLen;
    }

    public byte [] getResult() {
        int resultLength = mAdvPkt.length + ((mScanRsp != null) ? mScanRsp.length : 0);
        byte result[] = new byte[resultLength];
        System.arraycopy(mAdvPkt, 0, result, 0,  mAdvPkt.length);
        if (mScanRsp != null) {
            System.arraycopy(mScanRsp, 0, result, mAdvPkt.length, mScanRsp.length);
        }
        return result;
    }

}

