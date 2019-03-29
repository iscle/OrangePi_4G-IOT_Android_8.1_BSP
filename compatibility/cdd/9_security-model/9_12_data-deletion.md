## 9.12\. Data Deletion

All device implementations:

*   [C-0-1] MUST provide users a mechanism to perform a "Factory Data Reset".
*   [C-0-2] MUST delete all user-generated data. That is, all data except for
    the following:
     *    The system image
     *    Any operating system files required by the system image
*   [C-0-3] MUST delete the data in such a way that will satisfy relevant
    industry standards such as NIST SP800-88\.
*   [C-0-4] MUST trigger the above "Factory Data Reset" process when the
    [`DevicePolicyManager.wipeData()`](
    https://developer.android.com/reference/android/app/admin/DevicePolicyManager.html#wipeData%28int%29)
    API is called by the primary user's Device Policy Controller app.
*   MAY provide a fast data wipe option that conducts only a logical data erase.