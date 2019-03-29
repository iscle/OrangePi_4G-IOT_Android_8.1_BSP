## 10.2\. CTS Verifier

Device implementations MUST correctly execute all applicable cases in the CTS
Verifier. The CTS Verifier is included with the Compatibility Test Suite, and
is intended to be run by a human operator to test functionality that cannot be
tested by an automated system, such as correct functioning of a camera and
sensors.

The CTS Verifier has tests for many kinds of hardware, including some hardware
that is optional. Device implementations MUST pass all tests for hardware that
they possess; for instance, if a device possesses an accelerometer, it MUST
correctly execute the Accelerometer test case in the CTS Verifier. Test cases
for features noted as optional by this Compatibility Definition Document MAY be
skipped or omitted.

Every device and every build MUST correctly run the CTS Verifier, as noted
above. However, since many builds are very similar, device implementers are not
expected to explicitly run the CTS Verifier on builds that differ only in
trivial ways. Specifically, device implementations that differ from an
implementation that has passed the CTS Verifier only by the set of included
locales, branding, etc. MAY omit the CTS Verifier test.

