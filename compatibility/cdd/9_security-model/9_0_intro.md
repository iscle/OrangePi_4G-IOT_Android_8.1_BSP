# 9\. Security Model Compatibility

Device implementations:

*    [C-0-1] MUST implement a security model consistent
with the Android platform security model as defined in [Security and Permissions reference document](http://developer.android.com/guide/topics/security/permissions.html)
in the APIs in the Android developer documentation.

*    [C-0-2] MUST support installation of self-signed
applications without requiring any additional permissions/certificates from any
third parties/authorities. Specifically, compatible devices MUST support the
security mechanisms described in the follow subsections.
