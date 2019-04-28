Copyright 2017 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

# Wi-Fi Aware (NAN) HAL API Usage

The Wi-Fi Aware (NAN) HAL API is defined in (<i>hardware/interfaces/wifi/\<version\>/</i>):

* IWifiNanIface.hal
* IWifiNanIfaceEventCallback.hal
* types.hal (structure definitions)

The Wi-Fi Aware (NAN) HAL API surface is very large - only a subset is used from the framework.

Understanding of the HAL API subset which is actively used by the Android framework can be deduced
by reviewing framework code, specifically (<i>frameworks/opt/net/wif/</i>):

* WifiAwareNativeApi.java
* WifiAwareNativeCallback.java

The above framework files determine the API usage - and should be consulted as the authoritative
reference. Please consult the primary HAL file for documentation - they will not be replicated
in this document. APIs which are in the HAL but are not listed in this README file are not used by
the framework.

Note: the HAL API is translated to the legacy HAL API (<i>wifi_nan.h</i>). This README file covers
the new HAL API only. To understand the mapping between new and legacy HALs please consult
<i>hardware/interfaces/wifi/\<version\>/default/hidl_struct_util.cpp</i>.

## IWifiNanIface

Format:
* Hard-coded values are in <b>bold</b>, e.g. <b>true</b> or <b>5</b>
* Assigned but not fixed value are specified using the <i>variable</i> keyword, possibly with some
details/constraints
* Unassigned values are specified using the <i>N/A</i> keyword. Unassigned usually means initialized
to 0.

APIs:

* registerEventCallback(IWifiNanIfaceEventCallback callback)
* getCapabilitiesRequest
* enableRequest
  * NanEnableRequest
    * bool[2] operateInBand
        * Index [NanBandIndex.NAN_BAND_24GHZ] = <b>true</b>
        * Index [NanBandIndex.NAN_BAND_5GHZ] = <i>variable</i>
    * uint8_t hopCountMax = <b>2</b>
    * NanConfigRequest configParams
        * uint8_t masterPref = <i>variable</i>
        * bool disableDiscoveryAddressChangeIndication = <i>variable</i>
        * bool disableStartedClusterIndication = <i>variable</i>
        * bool disableJoinedClusterIndication = <i>variable</i>
        * bool includePublishServiceIdsInBeacon = <b>true</b>
        * uint8_t numberOfPublishServiceIdsInBeacon = <b>0</b>
        * bool includeSubscribeServiceIdsInBeacon = <b>true</b>
        * uint8_t numberOfSubscribeServiceIdsInBeacon = <b>0</b>
        * uint16_t rssiWindowSize = <b>8</b>
        * uint32_t macAddressRandomizationIntervalSec = <i>variable</i>
            * Normal run-time: set to <b>1800</b> (30 minutes)
            * Tests: set to <b>120</b> (2 minutes)
        * NanBandSpecificConfig[2] bandSpecificConfig
            * Index [NanBandIndex.NAN_BAND_24GHZ]
                * uint8_t rssiClose = <b>60</b>
                * uint8_t rssiMiddle = <b>70</b>
                * uint8_t rssiCloseProximity = <b>60</b>
                * uint8_t dwellTimeMs = <b>200</b>
                * uint16_t scanPeriodSec = <b>20</b>
                * bool validDiscoveryWindowIntervalVal = <i>variable</i>
                * uint8_t discoveryWindowIntervalVal = <i>variable</i>
            * Index [NanBandIndex.NAN_BAND_5GHZ]
                * uint8_t rssiClose = <b>60</b>
                * uint8_t rssiMiddle = <b>75</b>
                * uint8_t rssiCloseProximity = <b>60</b>
                * uint8_t dwellTimeMs = <b>200</b>
                * uint16_t scanPeriodSec = <b>20</b>
                * bool validDiscoveryWindowIntervalVal = <i>variable</i>
                * uint8_t discoveryWindowIntervalVal = <i>variable</i>
    * NanDebugConfig debugConfigs
        * bool validClusterIdVals = <b>true</b>
        * uint16_t clusterIdBottomRangeVal = <i>variable</i>
        * uint16_t clusterIdTopRangeVal = <i>variable</i>
        * bool validIntfAddrVal = <b>false</b>
        * MacAddress intfAddrVal = <i>N/A</i>
        * bool validOuiVal = <b>false</b>
        * uint32_t ouiVal = <i>N/A</i>
        * bool validRandomFactorForceVal = <b>false</b>
        * uint8_t randomFactorForceVal = <i>N/A</i>
        * bool validHopCountForceVal = <b>false</b>
        * uint8_t hopCountForceVal = <i>N/A</i>
        * bool validDiscoveryChannelVal = <b>false</b>
        * WifiChannelInMhz[2] discoveryChannelMhzVal = <i>N/A</i>
        * bool validUseBeaconsInBandVal = <b>false</b>
        * bool[2] useBeaconsInBandVal = <i>N/A</i>
        * bool validUseSdfInBandVal = <b>false</b>
        * bool[2] useSdfInBandVal = <i>N/A</i>
* configRequest
    * NanConfigRequest: same as for <i>enableRequest</i>
* disableRequest
* startPublishRequest
    * NanPublishRequest
        * NanDiscoveryCommonConfig baseConfigs
            * uint8_t sessionId = <i>variable</i>
            * uint16_t ttlSec = <i>variable</i>
            * uint16_t discoveryWindowPeriod = <b>1</b>
            * uint8_t discoveryCount = <b>0</b>
            * vec<uint8_t> serviceName = <i>variable</i>
            * NanMatchAlg discoveryMatchIndicator = <b>NanMatchAlg.MATCH_NEVER</b>
            * vec<uint8_t> serviceSpecificInfo = <i>variable</i>
            * vec<uint8_t> extendedServiceSpecificInfo = <i>N/A</i>
            * vec<uint8_t> rxMatchFilter = <i>variable</i>
            * vec<uint8_t> txMatchFilter = <i>variable</i>
            * bool useRssiThreshold = <b>false</b>
            * bool disableDiscoveryTerminationIndication = <i>variable</i>
            * bool disableMatchExpirationIndication = <b>true</b>
            * bool disableFollowupReceivedIndication = <b>false</b>
            * NanDataPathSecurityConfig securityConfig = <b>NanDataPathSecurityType.OPEN</b>
            * bool rangingRequired = <b>false</b>
            * uint32_t rangingIntervalMsec = <i>N/A</i>
            * bitfield<NanRangingIndication> configRangingIndications = <i>N/A</i>
            * uint16_t distanceIngressCm = <i>N/A</i>
            * uint16_t distanceEgressCm = <i>N/A</i>
        * NanPublishType publishType = <i>variable</i>
        * NanTxType txType = <b>NanTxType.BROADCAST</b>
        * bool autoAcceptDataPathRequests = <b>false</b>
* stopPublishRequest
* startSubscribeRequest
    * NanSubscribeRequest
        * NanDiscoveryCommonConfig baseConfigs
            * Mostly same as <i>publish</i> above except:
            * NanMatchAlg discoveryMatchIndicator = <b>NanMatchAlg.MATCH_ONCE</b>
        * NanSubscribeType subscribeType = <i>variable</i>
        * NanSrfType srfType = <i>N/A</i>
        * bool srfRespondIfInAddressSet = <i>N/A</i>
        * bool shouldUseSrf = <i>N/A</i>
        * bool isSsiRequiredForMatch = <i>N/A</i>
        * vec<MacAddress> intfAddr = <i>N/A</i>
* stopSubscribeRequest
* transmitFollowupRequest
    * NanTransmitFollowupRequest
        * uint8_t discoverySessionId = <i>variable</i>
        * uint32_t peerId = <i>variable</i>
        * MacAddress addr = <i>variable</i>
        * bool isHighPriority = <b>false</b>
        * bool shouldUseDiscoveryWindow = <b>true</b>
        * vec<uint8_t> serviceSpecificInfo = <i>variable</i>
        * vec<uint8_t> extendedServiceSpecificInfo = <i>N/A</i>
        * bool disableFollowupResultIndication = <b>false</b>
* createDataInterfaceRequest
* deleteDataInterfaceRequest
* initiateDataPathRequest
    * NanInitiateDataPathRequest
        * uint32_t peerId = <i>variable</i>
        * MacAddress peerDiscMacAddr = <i>variable</i>
        * NanDataPathChannelCfg channelRequestType =
        <i>NanDataPathChannelCfg.CHANNEL_NOT_REQUESTED</i>
        * WifiChannelInMhz channel = <b>2437</b> (note that should be ignored though -
        CHANNEL_NOT_REQUESTED!)
        * string ifaceName = <i>variable</i>
        * NanDataPathSecurityConfig securityConfig = <i>variable</i>
        * vec<uint8_t> appInfo = <i>N/A</i>
        * vec<uint8_t> serviceNameOutOfBand = <i>variable</i>
* respondToDataPathIndicationRequest
    * NanRespondToDataPathIndicationRequest
        * bool acceptRequest = <i>variable</i>
        * uint32_t ndpInstanceId = <i>variable</i>
        * string ifaceName = <i>variable</i>
        * NanDataPathSecurityConfig securityConfig = <i>variable</i>
        * vec<uint8_t> appInfo = <i>N/A</i>
        * vec<uint8_t> serviceNameOutOfBand = <i>variable</i>
* terminateDataPathRequest

## IWifiNanIfaceEventCallback

Format:
* Parameters whose values are <i>ignored</i> will be flagged, otherwise the parameter value is used
by the framework.

API:

* notifyXxxResponse: all callbacks are used by framework
* eventClusterEvent
* eventDisabled
* eventPublishTerminated
* eventSubscribeTerminated
* eventMatch
    * NanMatchInd (all parameters are used except those listed below)
        * vec<uint8_t> extendedServiceSpecificInfo: <i>ignored</i>
        * bool matchOccuredInBeaconFlag: <i>ignored</i>
        * bool outOfResourceFlag: <i>ignored</i>
        * uint8_t rssiValue: <i>ignored</i>
        * NanCipherSuiteType peerCipherType: <i>ignored</i>
        * bool peerRequiresSecurityEnabledInNdp: <i>ignored</i>
        * bool peerRequiresRanging: <i>ignored</i>
        * uint32_t rangingMeasurementInCm: <i>ignored</i>
        * bitfield<NanRangingIndication> rangingIndicationType: <i>ignored</i>
* eventMatchExpired: <i>ignored</i>
* eventFollowupReceived
    * NanFollowupReceivedInd (all parameters are used except those listed below)
        * bool receivedInFaw: <i>ignored</i>
        * vec<uint8_t> extendedServiceSpecificInfo: <i>ignored</i>
* eventTransmitFollowup
* eventDataPathRequest
    * NanDataPathRequestInd (all parameters are used except those listed below)
        * bool securityRequired: <i>ignored</i>
        * vec<uint8_t> appInfo: <i>ignored</i>
* eventDataPathConfirm
    * NanDataPathConfirmInd (all parameters are used except those listed below)
        * vec<uint8_t> appInfo: <i>ignored</i>
* eventDataPathTerminated

