## 9.4\. Alternate Execution Environments

Device implementations MUST keep consistency of the Android security and
permission model, even if they include runtime environments that execute
applications using some other software or technology than the Dalvik Executable
Format or native code. In other words:

*    [C-0-1] Alternate runtimes MUST themselves be Android applications,
and abide by the standard Android security model, as described elsewhere
in [section 9](#9_security_model_compatibility).

*    [C-0-2] Alternate runtimes MUST NOT be granted access to resources
protected by permissions not requested in the runtime’s `AndroidManifest.xml`
file via the &lt;`uses-permission`&gt; mechanism.

*    [C-0-3] Alternate runtimes MUST NOT permit applications to make use of
features protected by Android permissions restricted to system applications.

*    [C-0-4] Alternate runtimes MUST abide by the Android sandbox model
and installed applications using an alternate runtime MUST NOT
reuse the sandbox of any other app installed on the device, except through
the standard Android mechanisms of shared user ID and signing certificate.

*    [C-0-5] Alternate runtimes MUST NOT launch with, grant, or be granted
access to the sandboxes corresponding to other Android applications.

*    [C-0-6] Alternate runtimes MUST NOT be launched with, be granted, or grant
to other applications any privileges of the superuser (root), or of any other
user ID.

*    [C-0-7] When the `.apk` files of alternate runtimes are included in the
system image of device implementations, it MUST be signed with a key distinct
from the key used to sign other applications included with the device
implementations.

*    [C-0-8] When installing applications, alternate runtimes MUST obtain
user consent for the Android permissions used by the application.

*    [C-0-9] When an application needs to make use of a device resource for
which there is a corresponding Android permission (such as Camera, GPS, etc.),
the alternate runtime MUST inform the user that the application will be able to
access that resource.

*    [C-0-10] When the runtime environment does not record application
capabilities in this manner, the runtime environment MUST list all permissions
held by the runtime itself when installing any application using that runtime.

*    Alternate runtimes SHOULD install apps via the `PackageManager` into
separate Android sandboxes (Linux user IDs, etc.).

*    Alternate runtimes MAY provide a single Android sandbox shared by all
applications using the alternate runtime.