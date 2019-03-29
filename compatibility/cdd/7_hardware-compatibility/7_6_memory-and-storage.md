## 7.6\. Memory and Storage

### 7.6.1\. Minimum Memory and Storage

Device implementations:

*   [C-0-1] MUST include a [Download Manager](
    http://developer.android.com/reference/android/app/DownloadManager.html)
    that applications MAY use to download data files and they MUST be capable of
    downloading individual files of at least 100MB in size to the default
    “cache” location.

Television device implementations:

*   [T-0-1] MUST have at least 4GB of non-volatile storage available for
    application private data (a.k.a. "/data" partition)

Automotive device implementations:

*   [A-0-1] MUST have at least 4GB of non-volatile storage available for
    application private data (a.k.a. "/data" partition)

Watch device implementations:

*   [W-0-1] MUST have at least 1GB of non-volatile storage available for
    application private data (a.k.a. "/data" partition)
*   [W-0-2] MUST have at least 416MB memory available to the kernel and
    userspace.

Handheld device implementations:

*   [H-0-1] MUST have at least 4GB of non-volatile storage available for
    application private data (a.k.a. "/data" partition)
*   [H-0-2] MUST return “true” for `ActivityManager.isLowRamDevice()` when there
    is less than 1GB of memory available to the kernel and userspace.


If Handheld device implementations are 32-bit:

*   [H-1-1] The memory available to the kernel and userspace MUST
be at least: 512MB if any of the following densities are used:

   *   280dpi or lower on small/normal screens
   *   ldpi or lower on extra large screens
   *   mdpi or lower on large screens

*   [H-2-1] The memory available to the kernel and userspace MUST
be at least: 608MB if any of the following densities are used:

   *   xhdpi or higher on small/normal screens
   *   hdpi or higher on large screens
   *   mdpi or higher on extra large screens

*   [H-3-1] The memory available to the kernel and userspace MUST
be at least: 896MB if any of the following densities are used:

   *   400dpi or higher on small/normal screens
   *   xhdpi or higher on large screens
   *   tvdpi or higher on extra large screens

*    [H-4-1] The memory available to the kernel and userspace MUST
be at least: 1344MB if any of the following densities are used:

   *   560dpi or higher on small/normal screens
   *   400dpi or higher on large screens
   *   xhdpi or higher on extra large screens

If Handheld device implementations are 64-bit:

*   [H-5-1] The memory available to the kernel and userspace MUST
be at least: 816MB if any of the following densities are used:

   *   280dpi or lower on small/normal screens
   *   ldpi or lower on extra large screens
   *   mdpi or lower on large screens


*   [H-6-1] The memory available to the kernel and userspace MUST
be at least: 944MB if any of the following densities are used:

   *   xhdpi or higher on small/normal screens
   *   hdpi or higher on large screens
   *   mdpi or higher on extra large screens

*   [H-7-1] The memory available to the kernel and userspace MUST
be at least: 1280MB if any of the following densities are used:

   *  400dpi or higher on small/normal screens
   *  xhdpi or higher on large screens
   *  tvdpi or higher on extra large screens

*    [H-8-1] The memory available to the kernel and userspace MUST
be at least: 1824MB if any of the following densities are used:

   *   560dpi or higher on small/normal screens
   *   400dpi or higher on large screens
   *   xhdpi or higher on extra large screens

Note that the "memory available to the kernel and userspace" above refers to the
memory space provided in addition to any memory already dedicated to hardware
components such as radio, video, and so on that are not under the kernel’s
control on device implementations.

### 7.6.2\. Application Shared Storage

Device implementations:

*   [C-0-1] MUST offer storage to be shared by applications, also often referred
    as “shared external storage”, "application shared storage" or by the Linux
    path "/sdcard" it is mounted on.
*   [C-0-2] MUST be configured with shared storage mounted by default, in other
    words “out of the box”, regardless of whether the storage is implemented on
    an internal storage component or a removable storage medium (e.g. Secure
    Digital card slot).
*   [C-0-3] MUST mount the application shared storage directly on the Linux path
    `sdcard` or include a Linux symbolic link from `sdcard` to the actual mount
    point.
*   [C-0-4] MUST enforce the `android.permission.WRITE_EXTERNAL_STORAGE`
    permission on this shared storage as documented in the SDK. Shared storage
    MUST otherwise be writable by any application that obtains that permission.

Android handheld device implementations:

*   [H-0-1] MUST NOT provide an application shared storage smaller than 1GiB.

Device implementations MAY meet the above requirements using either:

* a user-accessible removable storage, such as a Secure Digital (SD) card slot.
* a portion of the internal (non-removable) storage as implemented in the
  Android Open Source Project (AOSP).

If device implementations use removable storage to satisfy the above
requirements, they:

*   [C-1-1] MUST implement a toast or pop-up user interface warning the user
    when there is no storage medium inserted in the slot.
*   [C-1-2] MUST include a FAT-formatted storage medium (e.g. SD card) or show
    on the box and other material available at time of purchase that the storage
    medium has to be purchased separately.

If device implementations use a protion of the non-removable storage to satisfy
the above requirements, they:

*   SHOULD use the AOSP implementation of the internal application shared
    storage.
*   MAY share the storage space with the application private data.

If device implementations include multiple shared storage paths (such
as both an SD card slot and shared internal storage), they:

*   [C-3-1] MUST allow only pre-installed and privileged Android
applications with the `WRITE_EXTERNAL_STORAGE` permission to
write to the secondary external storage, except when writing to their
package-specific directories or within the `URI` returned by firing the
`ACTION_OPEN_DOCUMENT_TREE` intent.

If device implementations have a USB port with USB peripheral mode support,
they:

*   [C-3-1] MUST provide a mechanism to access the data on the application
    shared storage from a host computer.
*   SHOULD expose content from both storage paths transparently through
    Android’s media scanner service and `android.provider.MediaStore`.
*   MAY use USB mass storage, but SHOULD use Media Transfer Protocol to satisfy
    this requirement.

If device implementations have a USB port with USB peripheral mode and support
Media Transfer Protocol, they:

*   SHOULD be compatible with the reference Android MTP host,
[Android File Transfer](http://www.android.com/filetransfer).
*   SHOULD report a USB device class of 0x00.
*   SHOULD report a USB interface name of 'MTP'.

### 7.6.3\. Adoptable Storage

If the device is expected to be mobile in nature unlike Television,
device implementations are:

*   [SR] STRONGLY RECOMMENDED to implement the adoptable storage in
a long-term stable location, since accidentally disconnecting them can
cause data loss/corruption.

If the removable storage device port is in a long-term stable location,
such as within the battery compartment or other protective cover,
device implementations are:

*   [SR] STRONGLY RECOMMENDED to implement
[adoptable storage](http://source.android.com/devices/storage/adoptable.html).
