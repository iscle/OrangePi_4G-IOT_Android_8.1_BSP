/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 */
/* MediaTek Inc. (C) 2017. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */
/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.internal.telephony.uicc;

import android.os.Environment;
import android.util.Xml;
import android.telephony.Rlog;

import java.util.HashMap;
import java.io.FileReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import com.android.internal.util.XmlUtils;

/**
 * {@hide}
 */
// MTK SATRT: add-on
public class VoiceMailConstants {
// MTK END
    private HashMap<String, String[]> CarrierVmMap;


    static final String LOG_TAG = "VoiceMailConstants";
    static final String PARTNER_VOICEMAIL_PATH ="etc/voicemail-conf.xml";

    static final int NAME = 0;
    static final int NUMBER = 1;
    static final int TAG = 2;
    static final int SIZE = 3;

    VoiceMailConstants () {
        CarrierVmMap = new HashMap<String, String[]>();
        loadVoiceMail();
    }
    // MTK SATRT: add-on
    public boolean containsCarrier(String carrier) {
    // MTK END
        return CarrierVmMap.containsKey(carrier);
    }

    String getCarrierName(String carrier) {
        String[] data = CarrierVmMap.get(carrier);
        return data[NAME];
    }

    String getVoiceMailNumber(String carrier) {
        String[] data = CarrierVmMap.get(carrier);
        return data[NUMBER];
    }

    String getVoiceMailTag(String carrier) {
        String[] data = CarrierVmMap.get(carrier);
        return data[TAG];
    }

    private void loadVoiceMail() {
        FileReader vmReader;

        final File vmFile = new File(Environment.getRootDirectory(),
                PARTNER_VOICEMAIL_PATH);

        try {
            vmReader = new FileReader(vmFile);
        } catch (FileNotFoundException e) {
            Rlog.w(LOG_TAG, "Can't open " +
                    Environment.getRootDirectory() + "/" + PARTNER_VOICEMAIL_PATH);
            return;
        }

        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(vmReader);

            XmlUtils.beginDocument(parser, "voicemail");

            while (true) {
                XmlUtils.nextElement(parser);

                String name = parser.getName();
                if (!"voicemail".equals(name)) {
                    break;
                }

                String[] data = new String[SIZE];
                String numeric = parser.getAttributeValue(null, "numeric");
                data[NAME]     = parser.getAttributeValue(null, "carrier");
                data[NUMBER]   = parser.getAttributeValue(null, "vmnumber");
                data[TAG]      = parser.getAttributeValue(null, "vmtag");

                CarrierVmMap.put(numeric, data);
            }
        } catch (XmlPullParserException e) {
            Rlog.w(LOG_TAG, "Exception in Voicemail parser " + e);
        } catch (IOException e) {
            Rlog.w(LOG_TAG, "Exception in Voicemail parser " + e);
        } finally {
            try {
                if (vmReader != null) {
                    vmReader.close();
                }
            } catch (IOException e) {}
        }
    }
}
