## 5.8\. Secure Media

If device implementations support secure video output and are capable of
supporting secure surfaces, they:

*    [C-1-1] MUST declare support for `Display.FLAG_SECURE`.

If device implementations declare support for `Display.FLAG_SECURE` and support
wireless display protocol, they:

*    [C-2-1] MUST secure the link with a cryptographically strong mechanism such
as HDCP 2.x or higher for the displays connected through wireless protocols
such as Miracast.

If device implementations declare support for `Display.FLAG_SECURE` and
support wired external display, they:

*    [C-3-1] MUST support HDCP 1.2 or higher for all wired external displays.

If device implementations are Android Television devices and support 4K
resolution, they:

*    [T-1-1] MUST support HDCP 2.2 for all wired external displays.

If Television device implementations don't support 4K resolution, they:

*    [T-2-1] MUST support HDCP 1.4 for all wired external displays.

*    [T-SR] Television device implementations are STRONGLY RECOMMENDED to
support simulataneous decoding of secure streams. At minimum, simultaneous
decoding of two steams is STRONGLY RECOMMENDED.