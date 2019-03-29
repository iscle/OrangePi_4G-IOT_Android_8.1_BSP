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

package android.telephony.cts;

import java.util.Arrays;
import java.util.List;

public class CarrierCapability {

    // List of network operators that don't support SMS delivery report
    public static final List<String> NO_DELIVERY_REPORTS =
            Arrays.asList(
                    "310410",   // AT&T Mobility
                    "44010",    // NTT DOCOMO
                    "45005",    // SKT Mobility
                    "45002",    // SKT Mobility
                    "45008",    // KT Mobility
                    "45028",    // KT Safety Network
                    "45006",    // LGT
                    "311660",   // MetroPCS
                    "310120",   // Sprint
                    "44050",    // KDDI
                    "44051",    // KDDI
                    "44053",    // KDDI
                    "44054",    // KDDI
                    "44070",    // KDDI
                    "44071",    // KDDI
                    "44072",    // KDDI
                    "44073",    // KDDI
                    "44074",    // KDDI
                    "44075",    // KDDI
                    "44076",    // KDDI
                    "50502",    // OPS
                    "51502",    // Globe Telecoms
                    "51503",    // Smart Communications
                    "51505",    // Sun Cellular
                    "53001",    // Vodafone New Zealand
                    "53024",    // NZC
                    "311870",   // Boost Mobile
                    "311220",   // USCC
                    "311225",   // USCC LTE
                    "311580",   // USCC LTE
                    "302720",   // Rogers
                    "30272",    // Rogers
                    "302370",   // Fido
                    "30237",    // Fido
                    "311490",   // Virgin Mobile
                    "312530",   // Sprint Prepaid
                    "310000",   // Tracfone
                    "46003",    // China Telecom
                    "311230",   // C SPire Wireless + Celluar South
                    "310600",    // Cellcom
                    "31000",     // Republic Wireless US
                    "310260",    // Republic Wireless US
                    "310026",     // T-Mobile US
                    "330120", // OpenMobile communication
                    // Verizon
                    "310004",
                    "310012",
                    "311280",
                    "311281",
                    "311282",
                    "311283",
                    "311284",
                    "311285",
                    "311286",
                    "311287",
                    "311288",
                    "311289",
                    "311480",
                    "311481",
                    "311482",
                    "311483",
                    "311484",
                    "311485",
                    "311486",
                    "311487",
                    "311488",
                    "311489"
            );

    // List of network operators that doesn't support Data(binary) SMS message
    public static final List<String> UNSUPPORT_DATA_SMS_MESSAGES =
            Arrays.asList(
                    "44010",    // NTT DOCOMO
                    "44020",    // SBM
                    "44051",    // KDDI
                    "302720",   // Rogers
                    "30272",    // Rogers
                    "302370",   // Fido
                    "30237",    // Fido
                    "45008",    // KT
                    "45005",    // SKT Mobility
                    "45002",     // SKT Mobility
                    "45006",    // LGT
                    "310260",   // Republic Wireless US
                    // Verizon
                    "310004",
                    "310012",
                    "311280",
                    "311281",
                    "311282",
                    "311283",
                    "311284",
                    "311285",
                    "311286",
                    "311287",
                    "311288",
                    "311289",
                    "311480",
                    "311481",
                    "311482",
                    "311483",
                    "311484",
                    "311485",
                    "311486",
                    "311487",
                    "311488",
                    "311489"
            );

    // List of network operators that doesn't support Maltipart SMS message
    public static final List<String> UNSUPPORT_MULTIPART_SMS_MESSAGES =
            Arrays.asList(
                    "44010",    // NTT DOCOMO
                    "44020",    // SBM
                    "44051",    // KDDI
                    "302720",   // Rogers
                    "30272",    // Rogers
                    "302370",   // Fido
                    "30237",    // Fido
                    "45006",    // LGT
                    "45008"     // KT
            );
}
