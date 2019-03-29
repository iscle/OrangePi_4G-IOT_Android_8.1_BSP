/* Copyright (c) 2015, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.codeaurora.ims.internal;

/**
 * Used by client application to get the result from lower layer by
 * communicating with vendor.
 * {@hide}
 */
oneway interface IQtiImsExtListener {
    /**
     * Notifies client the value of the set operation result.
     *
     * @param status To return status of request.
     * @return void.
     */
    void onSetCallForwardUncondTimer(int status);

     /**
     * Notifies client the value of the get operation result.
     *
     * @param startHour indicates starting hour
     * @param startMinute indicates starting minute
     * @param endHour indicates ending hour
     * @param endMinute indicates ending minute
     * @param reason is one of the valid call forwarding
     *        CF_REASONS, as defined in
     *        <code>com.android.internal.telephony.CommandsInterface.</code>
     * @param status indicates status of CF service.
     * @param number is the target phone number to forward calls to
     * @param serviceClass indicates serviceClass type that is supported.
     * @return void.
     */
    void onGetCallForwardUncondTimer(int startHour, int endHour, int startMinute,
            int endMinute, int reason, int status, String number, int serviceClass);

   /**
     * Notifies client with any UT failure result.
     *
     * @param errCode contains error code
     * @param errString contains error string if any.
     * @return void.
     */
    void onUTReqFailed(int errCode, String errString);

   /**
     * Notifies client the value of the get operation result on get packet count item.
     *
     * @param status. as defined in com.android.ims.ImsConfig#OperationStatusConstants.
     * @param packetCount. total number of packets sent or received
     * @return void
     */
    void onGetPacketCount(int status, long packetCount);

   /**
     * Notifies client the value of the get operation result on get packet error count item.
     *
     * @param status. as defined in com.android.ims.ImsConfig#OperationStatusConstants.
     * @param packetErrorCount. total number of packet errors encountered
     * @return void
     */
   void onGetPacketErrorCount(int status, long packetErrorCount);

    /**
     * Notifies client the result of call deflect request
     *
     * @param <result> is one of the values QTIIMS_REQUEST_*, as defined in
     *        <code>org.codeaurora.ims.utils.QtiImsExtUtils.</code>
     * @return void.
     */
    void receiveCallDeflectResponse(int result);

    /**
     * Notifies client the result of call transfer request
     *
     * @param <result> is one of the values QTI_IMS_REQUEST_*, as defined in
     *        <code>org.codeaurora.ims.qtiims.QtiImsInterfaceUtils.</code>
     * @return void.
     */
    void receiveCallTransferResponse(int result);
}
