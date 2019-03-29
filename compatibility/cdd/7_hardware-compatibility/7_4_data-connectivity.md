## 7.4\. Data Connectivity

### 7.4.1\. Telephony

“Telephony” as used by the Android APIs and this document refers specifically
to hardware related to placing voice calls and sending SMS messages via a GSM
or CDMA network. While these voice calls may or may not be packet-switched,
they are for the purposes of Android considered independent of any data
connectivity that may be implemented using the same network. In other words,
the Android “telephony” functionality and APIs refer specifically to voice
calls and SMS. For instance, device implementations that cannot place calls or
send/receive SMS messages are not considered a telephony device, regardless of
whether they use a cellular network for data connectivity.

*    Android MAY be used on devices that do not include telephony hardware. That
is, Android is compatible with devices that are not phones.

If device implementations include GSM or CDMA telephony, they:

*   [C-1-1] MUST declare the `android.hardware.telephony` feature flag and
other sub-feature flags according to the technology.
*   [C-1-2] MUST implement full support for the API for that technology.

If device implementations do not include telephony hardware, they:

*    [C-2-1] MUST implement the full APIs as no-ops.

#### 7.4.1.1\. Number Blocking Compatibility

If device implementations report the `android.hardware.telephony feature`, they:

*    [C-1-1] MUST include number blocking support
*    [C-1-2] MUST fully implement [`BlockedNumberContract`](
http://developer.android.com/reference/android/provider/BlockedNumberContract.html)
and the corresponding API as described in the SDK documentation.
*    [C-1-3] MUST block all calls and messages from a phone number in
'BlockedNumberProvider' without any interaction with apps. The only exception
is when number blocking is temporarily lifted as described in the SDK
documentation.
*    [C-1-4] MUST NOT write to the [platform call log provider](
http://developer.android.com/reference/android/provider/CallLog.html)
for a blocked call.
*    [C-1-5] MUST NOT write to the [Telephony provider](
http://developer.android.com/reference/android/provider/Telephony.html)
for a blocked message.
*    [C-1-6] MUST implement a blocked numbers management UI, which is opened
with the intent returned by `TelecomManager.createManageBlockedNumbersIntent()`
method.
*    [C-1-7] MUST NOT allow secondary users to view or edit the blocked numbers
on the device as the Android platform assumes the primary user to have full
control of the telephony services, a single instance, on the device. All
blocking related UI MUST be hidden for secondary users and the blocked list MUST
still be respected.
*   SHOULD migrate the blocked numbers into the provider when a device updates
to Android 7.0.

### 7.4.2\. IEEE 802.11 (Wi-Fi)

Device implementations:

*   SHOULD include support for one or more forms of 802.11\.

If device implementations include support for 802.11 and expose the
functionality to a third-party application, they

*   [C-1-1] MUST implement the corresponding Android API.
*   [C-1-2] MUST report the hardware feature flag `android.hardware.wifi`.
*   [C-1-3] MUST implement the [multicast API](
http://developer.android.com/reference/android/net/wifi/WifiManager.MulticastLock.html)
as described in the SDK documentation.
*   [C-1-4] MUST support multicast DNS (mDNS) and MUST NOT filter mDNS packets
(224.0.0.251) at any time of operation including:
    *   Even when the screen is not in an active state.
    *   For Android Television device implementations, even when in standby
power states.
*   SHOULD randomize the source MAC address and sequence number of probe
request frames, once at the beginning of each scan, while STA is disconnected.
    * Each group of probe request frames comprising one scan should use one
    consistent MAC address (SHOULD NOT randomize MAC address halfway through a
    scan).
    * Probe request sequence number should iterate as normal (sequentially)
    between the probe requests in a scan
    * Probe request sequence number should randomize between the last probe
    request of a scan and the first probe request of the next scan
*   SHOULD only allow the following information elements in probe request
frames, while STA is disconnected:
    * SSID Parameter Set (0)
    * DS Parameter Set (3)

#### 7.4.2.1\. Wi-Fi Direct

Device implementations:

*   SHOULD include support for Wi-Fi Direct (Wi-Fi peer-to-peer).

If device implementations include support for Wi-Fi Direct, they:

*   [C-1-1] MUST implement the [corresponding Android API](
    http://developer.android.com/reference/android/net/wifi/p2p/WifiP2pManager.html)
    as described in the SDK documentation.
*   [C-1-2] MUST report the hardware feature `android.hardware.wifi.direct`.
*   [C-1-3] MUST support regular Wi-Fi operation.
*   SHOULD support Wi-Fi and Wi-Fi Direct operations concurrently.

#### 7.4.2.2\. Wi-Fi Tunneled Direct Link Setup

Device implementations:

*    SHOULD include support for
     [Wi-Fi Tunneled Direct Link Setup (TDLS)](
     http://developer.android.com/reference/android/net/wifi/WifiManager.html)
     as described in the Android SDK Documentation.

If device implementations include support for TDLS and TDLS is enabled by the
WiFiManager API, they:

*   [C-1-1] MUST declare support for TDLS through [`WifiManager.isTdlsSupported`]
(https://developer.android.com/reference/android/net/wifi/WifiManager.html#isTdlsSupported%28%29).
*   SHOULD use TDLS only when it is possible AND beneficial.
*   SHOULD have some heuristic and NOT use TDLS when its performance might be
    worse than going through the Wi-Fi access point.

#### 7.4.2.3\. Wi-Fi Aware

Device implementations:

*    SHOULD include support for [Wi-Fi Aware](
     http://www.wi-fi.org/discover-wi-fi/wi-fi-aware).

If device implementations include support for Wi-Fi Aware and expose the
functionality to third-party apps, then they:

*   [C-1-1] MUST implement the `WifiAwareManager` APIs as described in the
[SDK documentation](
http://developer.android.com/reference/android/net/wifi/aware/WifiAwareManager.html).
*   [C-1-2] MUST declare the `android.hardware.wifi.aware` feature flag.
*   [C-1-3] MUST support Wi-Fi and Wi-Fi Aware operations concurrently.
*   [C-1-4] MUST randomize the Wi-Fi Aware management interface address at intervals
    no longer then 30 minutes and whenever Wi-Fi Aware is enabled.

#### 7.4.2.4\. Wi-Fi Passpoint

Device implementations:

*    SHOULD include support for [Wi-Fi Passpoint](
     http://www.wi-fi.org/discover-wi-fi/wi-fi-certified-passpoint).

If device implementations include support for Wi-Fi Passpoint, they:

*  [C-1-1] MUST implement the Passpoint related `WifiManager` APIs as
described in the [SDK documentation](
http://developer.android.com/reference/android/net/wifi/WifiManager.html).
*  [C-1-2] MUST support IEEE 802.11u standard, specifically related
   to Network Discovery and Selection, such as Generic Advertisement
   Service (GAS) and Access Network Query Protocol (ANQP).

Conversely if device implementations do not include support for Wi-Fi
Passpoint:

*    [C-2-1] The implementation of the Passpoint related `WifiManager`
APIs MUST throw an `UnsupportedOperationException`.

### 7.4.3\. Bluetooth

*    [W-0-1] Watch device implementations MUST support Bluetooth.
*    [T-0-1] Television device implementations MUST support Bluetooth and
Bluetooth LE.

If device implementations support Bluetooth Audio profile, they:

*    SHOULD support Advanced Audio Codecs and Bluetooth Audio Codecs
(e.g. LDAC).

If device implementations declare `android.hardware.vr.high_performance`
feature, they:

*    [C-1-1] MUST support Bluetooth 4.2 and Bluetooth LE Data Length Extension.

Android includes support for [Bluetooth and Bluetooth Low Energy](
http://developer.android.com/reference/android/bluetooth/package-summary.html).

If device implementations include support for Bluetooth and Bluetooth
Low Energy, they:

*    [C-2-1] MUST declare the relevant platform features
(`android.hardware.bluetooth` and `android.hardware.bluetooth_le`
respectively) and implement the platform APIs.
*    SHOULD implement relevant Bluetooth profiles such as
     A2DP, AVCP, OBEX, etc. as appropriate for the device.

Automotive device implementations:
*    [A-0-1] Automotive device implementations MUST support Bluetooth and
SHOULD support Bluetooth LE.
*    [A-0-2] Android Automotive implementations MUST support the following
Bluetooth profiles:

     * Phone calling over Hands-Free Profile (HFP).
     * Media playback over Audio Distribution Profile (A2DP).
     * Media playback control over Remote Control Profile (AVRCP).
     * Contact sharing using the Phone Book Access Profile (PBAP).
*    SHOULD support Message Access Profile (MAP).

If device implementations include support for Bluetooth Low Energy, they:

*   [C-3-1] MUST declare the hardware feature `android.hardware.bluetooth_le`.
*   [C-3-2] MUST enable the GATT (generic attribute profile) based Bluetooth
APIs as described in the SDK documentation and
[android.bluetooth](
http://developer.android.com/reference/android/bluetooth/package-summary.html).
*   [C-3-3] MUST report the correct value for
`BluetoothAdapter.isOffloadedFilteringSupported()` to indicate whether the
filtering logic for the [ScanFilter](
https://developer.android.com/reference/android/bluetooth/le/ScanFilter.html)
API classes is implemented.
*   [C-3-4] MUST report the correct value for
`BluetoothAdapter.isMultipleAdvertisementSupported()` to indicate
whether Low Energy Advertising is supported.
*   SHOULD support offloading of the filtering logic to the bluetooth chipset
when implementing the [ScanFilter API](
https://developer.android.com/reference/android/bluetooth/le/ScanFilter.html).
*   SHOULD support offloading of the batched scanning to the bluetooth chipset.
*   SHOULD support multi advertisement with at least 4 slots.


*   [SR] STRONGLY RECOMMENDED to implement a Resolvable Private Address (RPA)
timeout no longer than 15 minutes and rotate the address at timeout to protect
user privacy.

### 7.4.4\. Near-Field Communications

Device implementations:

*    SHOULD include a transceiver and related hardware for Near-Field
Communications (NFC).
*    [C-0-1] MUST implement `android.nfc.NdefMessage` and
`android.nfc.NdefRecord` APIs even if they do not include support for NFC or
declare the `android.hardware.nfc` feature as the classes represent a
protocol-independent data representation format.


If device implementations include NFC hardware and plan to make it available to
third-party apps, they:

*   [C-1-1] MUST report the `android.hardware.nfc` feature from the
[`android.content.pm.PackageManager.hasSystemFeature()` method](
http://developer.android.com/reference/android/content/pm/PackageManager.html).
*   MUST be capable of reading and writing NDEF messages via the following NFC
standards as below:
*   [C-1-2] MUST be capable of acting as an NFC Forum reader/writer
(as defined by the NFC Forum technical specification
NFCForum-TS-DigitalProtocol-1.0) via the following NFC standards:
   *   NfcA (ISO14443-3A)
   *   NfcB (ISO14443-3B)
   *   NfcF (JIS X 6319-4)
   *   IsoDep (ISO 14443-4)
   *   NFC Forum Tag Types 1, 2, 3, 4, 5 (defined by the NFC Forum)
*   [SR] STRONGLY RECOMMENDED to be capable of reading and writing NDEF
    messages as well as raw data via the following NFC standards. Note that
    while the NFC standards are stated as STRONGLY RECOMMENDED, the
    Compatibility Definition for a future version is planned to change these
    to MUST. These standards are optional in this version but will be required
    in future versions. Existing and new devices that run this version of
    Android are very strongly encouraged to meet these requirements now so
    they will be able to upgrade to the future platform releases.

*   [C-1-3] MUST be capable of transmitting and receiving data via the
    following peer-to-peer standards and protocols:
   *   ISO 18092
   *   LLCP 1.2 (defined by the NFC Forum)
   *   SDP 1.0 (defined by the NFC Forum)
   *   [NDEF Push Protocol](
        http://static.googleusercontent.com/media/source.android.com/en/us/compatibility/ndef-push-protocol.pdf)
   *   SNEP 1.0 (defined by the NFC Forum)
*   [C-1-4] MUST include support for [Android Beam](
    http://developer.android.com/guide/topics/connectivity/nfc/nfc.html) and
    SHOULD enable Android Beam by default.
*   [C-1-5] MUST be able to send and receive using Android Beam,
    when Android Beam is enabled or another proprietary NFC P2p mode is
    turned on.
*   [C-1-6] MUST implement the SNEP default server. Valid NDEF messages
received by the default SNEP server MUST be dispatched to applications using
the `android.nfc.ACTION_NDEF_DISCOVERED` intent. Disabling Android Beam in
settings MUST NOT disable dispatch of incoming NDEF message.
*   [C-1-7] MUST honor the `android.settings.NFCSHARING_SETTINGS` intent to
    show [NFC sharing settings](
    http://developer.android.com/reference/android/provider/Settings.html#ACTION_NFCSHARING_SETTINGS).
*   [C-1-8] MUST implement the NPP server. Messages received by the NPP
    server MUST be processed the same way as the SNEP default server.
*   [C-1-9] MUST implement a SNEP client and attempt to send outbound P2P
    NDEF to the default SNEP server when Android Beam is enabled. If no default
    SNEP server is found then the client MUST attempt to send to an NPP server.
*   [C-1-10] MUST allow foreground activities to set the outbound P2P NDEF
message using `android.nfc.NfcAdapter.setNdefPushMessage`, and
`android.nfc.NfcAdapter.setNdefPushMessageCallback`, and
`android.nfc.NfcAdapter.enableForegroundNdefPush`.
*   SHOULD use a gesture or on-screen confirmation, such as 'Touch to
Beam', before sending outbound P2P NDEF messages.
*   [C-1-11] MUST support NFC Connection handover to Bluetooth when the
    device supports Bluetooth Object Push Profile.
*   [C-1-12] MUST support connection handover to Bluetooth when using
    `android.nfc.NfcAdapter.setBeamPushUris`, by implementing the
    “[Connection Handover version 1.2](
    http://members.nfc-forum.org/specs/spec_list/#conn_handover)” and
    “[Bluetooth Secure Simple Pairing Using NFC version 1.0](
    http://members.nfc-forum.org/apps/group_public/download.php/18688/NFCForum-AD-BTSSP_1_1.pdf)”
    specs from the NFC Forum. Such an implementation MUST implement the handover
    LLCP service with service name “urn:nfc:sn:handover” for exchanging the
    handover request/select records over NFC, and it MUST use the Bluetooth Object
    Push Profile for the actual Bluetooth data transfer. For legacy reasons (to
    remain compatible with Android 4.1 devices), the implementation SHOULD still
   accept SNEP GET requests for exchanging the handover request/select records
   over NFC. However an implementation itself SHOULD NOT send SNEP GET requests
   for performing connection handover.
*   [C-1-13] MUST poll for all supported technologies while in NFC discovery
    mode.
*   SHOULD be in NFC discovery mode while the device is awake with the
screen active and the lock-screen unlocked.
*   SHOULD be capable of reading the barcode and URL (if encoded) of
    [Thinfilm NFC Barcode](
    http://developer.android.com/reference/android/nfc/tech/NfcBarcode.html)
    products.

(Note that publicly available links are not available for the JIS, ISO, and NFC
Forum specifications cited above.)

Android includes support for NFC Host Card Emulation (HCE) mode.

If device implementations include an NFC controller chipset capable of HCE (for
NfcA and/or NfcB) and support Application ID (AID) routing, they:

*   [C-2-1] MUST report the `android.hardware.nfc.hce` feature constant.
*   [C-2-2] MUST support [NFC HCE APIs](
http://developer.android.com/guide/topics/connectivity/nfc/hce.html) as
defined in the Android SDK.

If device implementations include an NFC controller chipset capable of HCE
for NfcF, and implement the feature for third-party applications, they:

*   [C-3-1] MUST report the `android.hardware.nfc.hcef` feature constant.
*   [C-3-2] MUST implement the [NfcF Card Emulation APIs]
(https://developer.android.com/reference/android/nfc/cardemulation/NfcFCardEmulation.html)
as defined in the Android SDK.


If device implementations include general NFC support as described in this
section and support MIFARE technologies (MIFARE Classic,
MIFARE Ultralight, NDEF on MIFARE Classic) in the reader/writer role, they:

*   [C-4-1] MUST implement the corresponding Android APIs as documented by
the Android SDK.
*   [C-4-2] MUST report the feature `com.nxp.mifare` from the
[`android.content.pm.PackageManager.hasSystemFeature`()](
http://developer.android.com/reference/android/content/pm/PackageManager.html)
method. Note that this is not a standard Android feature and as such does not
appear as a constant in the `android.content.pm.PackageManager` class.

### 7.4.5\. Minimum Network Capability

Device implementations:

*   [C-0-1] MUST include support for one or more forms of
data networking. Specifically, device implementations MUST include support for
at least one data standard capable of 200Kbit/sec or greater. Examples of
    technologies that satisfy this requirement include EDGE, HSPA, EV-DO,
    802.11g, Ethernet, Bluetooth PAN, etc.
*   [C-0-2] MUST include an IPv6 networking stack and support IPv6
communication using the managed APIs, such as `java.net.Socket` and
`java.net.URLConnection`, as well as the native APIs, such as `AF_INET6`
sockets.
*   [C-0-3] MUST enable IPv6 by default.
   *   MUST ensure that IPv6 communication is as reliable as IPv4, for example.
   *   [C-0-4] MUST maintain IPv6 connectivity in doze mode.
   *   [C-0-5] Rate-limiting MUST NOT cause the device to lose IPv6
   connectivity on any IPv6-compliant network that uses RA lifetimes of
   at least 180 seconds.
*   SHOULD also include support for at least one common wireless data
standard, such as 802.11 (Wi-Fi) when a physical networking standard (such as
Ethernet) is the primary data connection
*   MAY implement more than one form of data connectivity.


The required level of IPv6 support depends on the network type, as follows:

If devices implementations support Wi-Fi networks, they:

*   [C-1-1] MUST support dual-stack and IPv6-only operation on Wi-Fi.

If device impelementations support Ethernet networks, they:

*   [C-2-1] MUST support dual-stack operation on Ethernet.

If device implementations support cellular data, they:

*   [C-3-1] MUST simultaneously meet these requirements on each network to which
it is connected when a device is simultaneously connected to more than one
network (e.g., Wi-Fi and cellular data), .
*   SHOULD support IPv6 operation (IPv6-only and possibly dual-stack) on
cellular data.


### 7.4.6\. Sync Settings

Device implementations:

*   [C-0-1] MUST have the master auto-sync setting on by default so that
the method [`getMasterSyncAutomatically()`](
    http://developer.android.com/reference/android/content/ContentResolver.html)
    returns “true”.

### 7.4.7\. Data Saver

If device implementations include a metered connection, they are:

*   [SR] STRONGLY RECOMMENDED to provide the data saver mode.

If Handheld device implementations include a metered connection, they:

*   [H-1-1] MUST provide the data saver mode.

If device implementations provide the data saver mode, they:

*   [C-1-1] MUST support all the APIs in the `ConnectivityManager`
class as described in the [SDK documentation](
https://developer.android.com/training/basics/network-ops/data-saver.html)
*   [C-1-2] MUST provide a user interface in the settings, that handles the
    [`Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS`](
    https://developer.android.com/reference/android/provider/Settings.html#ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS)
    intent, allowing users to add applications to or remove applications
    from the whitelist.

If device implementations do not provide the data saver mode, they:

*   [C-2-1] MUST return the value `RESTRICT_BACKGROUND_STATUS_DISABLED` for
    [`ConnectivityManager.getRestrictBackgroundStatus()`](
    https://developer.android.com/reference/android/net/ConnectivityManager.html#getRestrictBackgroundStatus%28%29)
*   [C-2-2] MUST NOT broadcast
`ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED`.
*   [C-2-3] MUST have an activity that handles the
`Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS`
    intent but MAY implement it as a no-op.
