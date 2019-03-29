# 1\. Introduction

This document enumerates the requirements that must be met in order for devices
to be compatible with Android ANDROID_VERSION.

The use of “MUST”, “MUST NOT”, “REQUIRED”, “SHALL”, “SHALL NOT”, “SHOULD”,
“SHOULD NOT”, “RECOMMENDED”, “MAY”, and “OPTIONAL” is per the IETF standard
defined in [RFC2119](http://www.ietf.org/rfc/rfc2119.txt).

As used in this document, a “device implementer” or “implementer” is a person
or organization developing a hardware/software solution running Android
ANDROID_VERSION. A “device implementation” or “implementation is the
hardware/software solution so developed.

To be considered compatible with Android ANDROID_VERSION, device
implementations MUST meet the requirements presented in this Compatibility
Definition, including any documents incorporated via reference.

Where this definition or the software tests described in [section
10](#10_software_compatibility_testing) is silent, ambiguous, or incomplete, it
is the responsibility of the device implementer to ensure compatibility with
existing implementations.

For this reason, the [Android Open Source Project](http://source.android.com/)
is both the reference and preferred implementation of Android. Device
implementers are STRONGLY RECOMMENDED to base their implementations to the
greatest extent possible on the “upstream” source code available from the
Android Open Source Project. While some components can hypothetically be
replaced with alternate implementations, it is STRONGLY RECOMMENDED to not
follow this practice, as passing the software tests will become substantially
more difficult. It is the implementer’s responsibility to ensure full
behavioral compatibility with the standard Android implementation, including
and beyond the Compatibility Test Suite. Finally, note that certain component
substitutions and modifications are explicitly forbidden by this document.

Many of the resources linked to in this document are derived directly or
indirectly from the Android SDK and will be functionally identical to the
information in that SDK’s documentation. In any cases where this Compatibility
Definition or the Compatibility Test Suite disagrees with the SDK
documentation, the SDK documentation is considered authoritative. Any technical
details provided in the linked resources throughout this document are
considered by inclusion to be part of this Compatibility Definition.

