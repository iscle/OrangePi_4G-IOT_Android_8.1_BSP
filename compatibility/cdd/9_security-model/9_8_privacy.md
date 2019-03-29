## 9.8\. Privacy

### 9.8.1\. Usage History

Android stores the history of the user's choices and manages such history by
[UsageStatsManager](https://developer.android.com/reference/android/app/usage/UsageStatsManager.html).

Device implementations:

*   [C-1-1] MUST keep a reasonable retention period of such user history.
*   [SR] Are STRONGLY RECOMMENDED to keep the 14 days retention period as
    configured by default in the AOSP implementation.


### 9.8.2\. Recording

If device implementations include functionality in the system that captures
the contents displayed on the screen and/or records the audio stream played
on the device, they:

*   [C-1-1] MUST have an ongoing notification to the user whenever this
    functionality is enabled and actively capturing/recording.

If device implementations include a component enabled out-of-box, capable of
recording ambient audio to infer useful information about user’s context, they:

*   [C-2-1] MUST NOT store in persistent on-device storage or transmit off the
    device the recorded raw audio or any format that can be converted back into
    the original audio or a near facsimile, except with explicit user consent.

### 9.8.3\. Connectivity

If device implementations have a USB port with USB peripheral mode support,
they:

*   [C-1-1] MUST present a user interface asking for the user's consent before
allowing access to the contents of the shared storage over the USB port.


### 9.8.4\. Network Traffic

Device implementations:

*   [C-0-1] MUST preinstall the same root certificates for the system-trusted
    Certificate Authority (CA) store as [provided](
    https://source.android.com/security/overview/app-security.html#certificate-authorities)
    in the upstream Android Open Source Project.
*   [C-0-2] MUST ship with an empty user root CA store.
*   [C-0-3] MUST display a warning to the user indicating the network traffic
    may be monitored, when a user root CA is added.

If device traffic is routed through a VPN, device implementations:

*   [C-1-1] MUST display a warning to the user indicating either:
    *   That network traffic may be monitored.
    *   That network traffic is being routed through the specific VPN
        application providing the VPN.

If device implementations have a mechanism, enabled out-of-box by default, that
routes network data traffic through a proxy server or VPN gateway (for example,
preloading a VPN service with `android.permission.CONTROL_VPN` granted), they:

*    [C-2-1] MUST ask for the user's consent before enabling that mechanism,
     unless that VPN is enabled by the Device Policy Controller via the
     [`DevicePolicyManager.setAlwaysOnVpnPackage()`](
     https://developer.android.com/reference/android/app/admin/DevicePolicyManager.html#setAlwaysOnVpnPackage%28android.content.ComponentName, java.lang.String, boolean%29)
     , in which case the user does not need to provide a separate consent, but
     MUST only be notified.
