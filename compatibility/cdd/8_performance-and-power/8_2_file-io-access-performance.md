## 8.2\. File I/O Access Performance

Providing a common baseline for a consistent file access performance on the
application private data storage (`/data` partition) allows app developers
to set a proper expectation that would help their software design. Device
implementations, depending on the device type, MAY have certain requirements
described in [section 2](#2_device-type) for the following read
and write operations:


*    **Sequential write performance**. Measured by writing a 256MB file using
10MB write buffer.
*    **Random write performance**. Measured by writing a 256MB file using 4KB
write buffer.
*    **Sequential read performance**. Measured by reading a 256MB file using
10MB write buffer.
*    **Random read performance**. Measured by reading a 256MB file using 4KB
write buffer.

Handheld device implementations:

   *   [H-0-1] MUST ensure a sequential write performance of at least 5MB/s.
   *   [H-0-2] MUST ensure a random write performance of at least 0.5MB/s.
   *   [H-0-3] MUST ensure a sequential read performance of at least 15MB/s.
   *   [H-0-4] MUST ensure a random read performance of at least 3.5MB/s.

Television device implementations:

   *   [T-0-1] MUST ensure a sequential write performance of at least 5MB/s.
   *   [T-0-2] MUST ensure a random write performance of at least 0.5MB/s.
   *   [T-0-3] MUST ensure a sequential read performance of at least 15MB/s.
   *   [T-0-4] MUST ensure a random read performance of at least 3.5MB/s.