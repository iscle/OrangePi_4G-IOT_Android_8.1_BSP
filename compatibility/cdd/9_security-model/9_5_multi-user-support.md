## 9.5\. Multi-User Support

Android includes [support for multiple users](
http://developer.android.com/reference/android/os/UserManager.html)
and provides support for full user isolation.

*    Device implementations MAY but SHOULD NOT enable multi-user if they use
[removable media](
http://developer.android.com/reference/android/os/Environment.html)
for primary external storage.

If Automotive device implementations include multiple users, they:

*   [A-1-1] MUST include a guest account that allows all functions provided
by the vehicle system without requiring a user to log in.

If device implementations include multiple users, they:

*   [C-1-1] MUST meet the following requirements related to
[multi-user support](
http://source.android.com/devices/storage/traditional.html).
*   [C-1-2] MUST, for each user, implement a security
model consistent with the Android platform security model as defined in
[Security and Permissions reference document](
http://developer.android.com/guide/topics/security/permissions.html)
in the APIs.
*   [C-1-3] MUST have separate and isolated shared application storage
(a.k.a. `/sdcard`) directories for each user instance.
*   [C-1-4] MUST ensure that applications owned by and running on behalf a
given user cannot list, read, or write to the files owned by any other user,
even if the data of both users are stored on the same volume or filesystem.
*   [C-1-5] MUST encrypt the contents of the SD card when multiuser is enabled
using a key stored only on non-removable media accessible only to the system if
device implementations use removable media for the external storage APIs.
As this will make the media unreadable by a host PC, device implementations
will be required to switch to MTP or a similar system to provide host PCs with
access to the current user’s data.

If device implementations include multiple users and
do not declare the `android.hardware.telephony` feature flag, they:

*   [C-2-1] MUST support restricted profiles,
a feature that allows device owners to manage additional users and their
capabilities on the device. With restricted profiles, device owners can quickly
set up separate environments for additional users to work in, with the ability
to manage finer-grained restrictions in the apps that are available in those
environments.

If device implementations include multiple users and
declare the `android.hardware.telephony` feature flag, they:

*   [C-3-1] MUST NOT support restricted profiles but MUST align with the AOSP
implementation of controls to enable /disable other users from accessing the
voice calls and SMS.

