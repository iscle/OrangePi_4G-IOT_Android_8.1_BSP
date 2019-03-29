## 9.11\. Keys and Credentials

The [Android Keystore System](https://developer.android.com/training/articles/keystore.html)
allows app developers to store cryptographic keys in a container and use them in
cryptographic operations through the [KeyChain API](https://developer.android.com/reference/android/security/KeyChain.html)
or the [Keystore API](https://developer.android.com/reference/java/security/KeyStore.html).
Device implementations:

*    [C-0-1] MUST at least allow more than 8,192 keys to be imported.
*    [C-0-2] The lock screen authentication MUST rate-limit attempts and MUST
have an exponential backoff algorithm. Beyond 150 failed attempts, the delay
MUST be at least 24 hours per attempt.
*    SHOULD not limit the number of keys that can be generated

When the device implementation supports a secure lock screen, it:

*    [C-1-1] MUST back up the keystore implementation with secure hardware.
*    [C-1-2] MUST have implementations of RSA, AES, ECDSA and HMAC cryptographic
algorithms and MD5, SHA1, and SHA-2 family hash functions to properly support
the Android Keystore system's supported algorithms in an area that is securely
isolated from the code running on the kernel and above. Secure isolation MUST
block all potential mechanisms by which kernel or userspace code might access
the internal state of the isolated environment, including DMA. The upstream
Android Open Source Project (AOSP) meets this requirement by using the
[Trusty](https://source.android.com/security/trusty/) implementation, but
another ARM TrustZone-based solution or a third-party reviewed secure
implementation of a proper hypervisor-based isolation are alternative options.
*    [C-1-3] MUST perform the lock screen authentication in the isolated
execution environment and only when successful, allow the authentication-bound
keys to be used. The upstream Android Open Source Project provides the
[Gatekeeper Hardware Abstraction Layer (HAL)](http://source.android.com/devices/tech/security/authentication/gatekeeper.html)
and Trusty, which can be used to satisfy this requirement.
*    [C-1-4] MUST support key attestation where the attestation signing key is
protected by secure hardware and signing is performed in secure hardware. The
attestation signing keys MUST be shared across large enough number of devices to
prevent the keys from being used as device identifiers. One way of meeting this
requirement is to share the same attestation key unless at least 100,000 units
of a given SKU are produced. If more than 100,000 units of an SKU are produced,
a different key MAY be used for each 100,000 units.

Note that if a device implementation is already launched on an earlier Android
version, such a device is exempted from the requirement to have a
hardware-backed keystore, unless it declares the `android.hardware.fingerprint`
feature which requires a hardware-backed keystore.

### 9.11.1\. Secure Lock Screen

If device implementations have a secure lock screen and include one or more
trust agent, which implements the `TrustAgentService` System API, then they:

*    [C-1-1] MUST indicate the user in the Settings and Lock screen user
interface of situations where either the screen auto-lock is deferred or the
screen lock can be unlocked by the trust agent. The AOSP meets the requirement
by showing a text description for the "Automatically lock setting" and
"Power button instantly locks setting" menus and a distinguishable icon on
the lock screen.
*    [C-1-2] MUST respect and fully implement all trust agent APIs in the
`DevicePolicyManager` class, such as the [`KEYGUARD_DISABLE_TRUST_AGENTS`](https://developer.android.com/reference/android/app/admin/DevicePolicyManager.html#KEYGUARD&lowbarDISABLE&lowbarTRUST&lowbarAGENTS)
constant.
*    [C-1-3] MUST NOT fully implement the `TrustAgentService.addEscrowToken()`
function on a device that is used as the primary personal device
(e.g. handheld) but MAY fully implement the function on device implementations
typically shared.
*    [C-1-4] MUST encrypt the tokens added by `TrustAgentService.addEscrowToken()`
before storing them on the device.
*    [C-1-5] MUST NOT store the encryption key on the device.
*    [C-1-6] MUST inform the user about the security implications before
enabling the escrow token to decrypt the data storage.

If device implementations add or modify the authentication methods to unlock
the lock screen, then for such an authentication method to be treated as a
secure way to lock the screen, they:

*    [C-2-1] MUST be the user authentication method as described in
[Requiring User Authentication For Key Use](https://developer.android.com/training/articles/keystore.html#UserAuthentication).
*    [C-2-2] MUST unlock all keys for a third-party developer app to use when
the user unlocks the secure lock screen. For example, all keys MUST be available
for a third-party developer app through relevant APIs, such as
[`createConfirmDeviceCredentialIntent`](https://developer.android.com/reference/android/app/KeyguardManager.html#createConfirmDeviceCredentialIntent%28java.lang.CharSequence, java.lang.CharSequence%29)
and [`setUserAuthenticationRequired`](https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec.Builder.html#setUserAuthenticationRequired%28boolean%29).

If device implementations add or modify the authentication methods to unlock
the lock screen if based on a known secret then for such an authentication
method to be treated as a secure way to lock the screen, they:

*    [C-3-1] The entropy of the shortest allowed length of inputs MUST be
greater than 10 bits.
*    [C-3-2] The maximum entropy of all possible inputs MUST be greater than
18 bits.
*    [C-3-3] MUST not replace any of the existing authentication methods
(PIN,pattern, password) implemented and provided in AOSP.
*    [C-3-4] MUST be disabled when the Device Policy Controller (DPC)
application has set the password quality policy via the
[`DevicePolicyManager.setPasswordQuality()`](https://developer.android.com/reference/android/app/admin/DevicePolicyManager.html#setPasswordQuality%28android.content.ComponentName,%20int%29)
method with a more restrictive quality constant than
`PASSWORD_QUALITY_SOMETHING`.

If device implementations add or modify the authentication methods to unlock
the lock screen if based on a physical token or the location, then for such an
authentication method to be treated as a secure way to lock the screen, they:

*    [C-4-1] MUST have a fall-back mechanism to use one of the primary
authentication methods which is based on a known secret and meets the
requirements to be treated as a secure lock screen.
*    [C-4-2] MUST be disabled and only allow the primary authentication to
unlock the screen when the Device Policy Controller (DPC) application has set
the policy with either the [`DevicePolicyManager.setKeyguardDisabledFeatures(KEYGUARD_DISABLE_TRUST_AGENTS)`](http://developer.android.com/reference/android/app/admin/DevicePolicyManager.html#setKeyguardDisabledFeatures%28android.content.ComponentName,%20int%29)
method or the [`DevicePolicyManager.setPasswordQuality()`](https://developer.android.com/reference/android/app/admin/DevicePolicyManager.html#setPasswordQuality%28android.content.ComponentName,%20int%29)
method with a more restrictive quality constant than
`PASSWORD_QUALITY_UNSPECIFIED`.
*    [C-4-3] The user MUST be challenged for the primary authentication
(e.g.PIN, pattern, password) at least once every 72 hours or less.

If device implementations add or modify the authentication methods to unlock
the lock screen based on biometrics, then for such an authentication method to
be treated as a secure way to lock the screen, they:

*    [C-5-1] MUST have a fall-back mechanism to use one of the primary
authentication methods which is based on a known secret and meets the
requirements to be treated as a secure lock screen.
*    [C-5-2] MUST be disabled and only allow the primary authentication to
unlock the screen when the Device Policy Controller (DPC) application has set
the keguard feature policy by calling the method
[`DevicePolicyManager.setKeyguardDisabledFeatures(KEYGUARD_DISABLE_FINGERPRINT)`](http://developer.android.com/reference/android/app/admin/DevicePolicyManager.html#setKeyguardDisabledFeatures%28android.content.ComponentName,%20int%29).
*    [C-5-3] MUST have a false acceptance rate that is equal or stronger than
what is required for a fingerprint sensor as described in section 7.3.10, or
otherwise MUST be disabled and only allow the primary authentication to unlock
the screen when the Device Policy Controller (DPC) application has set the
password quality policy via the [`DevicePolicyManager.setPasswordQuality()`](https://developer.android.com/reference/android/app/admin/DevicePolicyManager.html\#setPasswordQuality%28android.content.ComponentName,%20int%29)
method with a more restrictive quality constant than
`PASSWORD_QUALITY_BIOMETRIC_WEAK`.
*    [C-5-4] The user MUST be challenged for the primary authentication
(e.g.PIN, pattern, password) at least once every 72 hours or less.

If device implementations add or modify the authentication methods to unlock
the lock screen and if such an authentication method will be used to unlock
the keyguard, but will not be treated as a secure lock screen, then they:

*    [C-6-1] MUST return `false` for both the [`KeyguardManager.isKeyguardSecure()`](http://developer.android.com/reference/android/app/KeyguardManager.html#isKeyguardSecure%28%29)
and the [`KeyguardManager.isDeviceSecure()`](https://developer.android.com/reference/android/app/KeyguardManager.html#isDeviceSecure%28%29)
methods.
*    [C-6-2] MUST be disabled when the Device Policy Controller (DPC)
application has set the password quality policy via the [`DevicePolicyManager.setPasswordQuality()`](https://developer.android.com/reference/android/app/admin/DevicePolicyManager.html#setPasswordQuality%28android.content.ComponentName,%20int%29)
method with a more restrictive quality constant than
`PASSWORD_QUALITY_UNSPECIFIED`.
*    [C-6-3] MUST NOT reset the password expiration timers set by
[`DevicePolicyManager.setPasswordExpirationTimeout()`](http://developer.android.com/reference/android/app/admin/DevicePolicyManager.html#setPasswordExpirationTimeout%28android.content.ComponentName,%20long%29).
*    [C-6-4] MUST NOT authenticate access to keystores if the application has
called [`KeyGenParameterSpec.Builder.setUserAuthenticationRequired(true)`](https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec.Builder.html#setUserAuthenticationRequired%28boolean%29)).