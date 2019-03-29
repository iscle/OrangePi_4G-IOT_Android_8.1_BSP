## 8.1\. User Experience Consistency

A smooth user interface can be provided to the end user if there are certain
minimum requirements to ensure a consistent frame rate and response times for
applications and games. Device implementations, depending on the device type,
MAY have measurable requirements for the user interface latency and task
switching as described in [section 2](#2_device-types).

   *   [H-0-1] **Consistent frame latency**. Inconsistent frame latency or a
delay to render frames MUST NOT happen more often than 5 frames in a second,
and SHOULD be below 1 frames in a second.
   *   [H-0-2] **User interface latency**. Device implementations MUST ensure
low latency user experience by scrolling a list of 10K list entries as defined
by the Android Compatibility Test Suite (CTS) in less than 36 secs.
   *   [H-0-3] **Task switching**. When multiple applications have been
launched, re-launching an already-running application after it has been
launched MUST take less than 1 second.
   *   [T-0-1] **Consistent frame latency**. Inconsistent frame latency or a
delay to render frames MUST NOT happen more often than 5 frames in a second,
and SHOULD be below 1 frames in a second.
