## 3.10\. Accessibility

Android provides an accessibility layer that helps users with disabilities to
navigate their devices more easily. In addition, Android provides platform APIs
that enable accessibility service implementations to receive callbacks for user
and system events and generate alternate feedback mechanisms, such as
text-to-speech, haptic feedback, and trackball/d-pad navigation.

If device implementations support third-party accessibility services, they:

*   [C-1-1] MUST provide an implementation of the Android accessibility
    framework as described in the [accessibility APIs](
    http://developer.android.com/reference/android/view/accessibility/package-summary.html)
    SDK documentation.
*   [C-1-2] MUST generate accessibility events and deliver the appropriate
    `AccessibilityEvent` to all registered [`AccessibilityService`](
    http://developer.android.com/reference/android/accessibilityservice/AccessibilityService.html)
    implementations as documented in the SDK.
*   [C-1-3] MUST honor the `android.settings.ACCESSIBILITY_SETTINGS` intent to
    provide a user-accessible mechanism to enable and disable the third-party
    accessibility services alongside the preloaded accessibility services.
*   [C-1-4] MUST add a button in the system's navigation bar allowing the user
    to control the accessibility service when the enabled accessibility services
    declare the [`AccessibilityServiceInfo.FLAG_REQUEST_ACCESSIBILITY_BUTTON`](
    https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo.html#FLAG%5FREQUEST%5FACCESSIBILITY%5FBUTTON)
    . Note that for device implementations with no system navigation bar, this
    requirement is not applicable, but device implementations SHOULD provide a
    user affordance to control these accessibility services.


If device implementations include preloaded accessibility services, they:

*   [C-2-1] MUST implement these preloaded accessibility services as [Direct Boot aware]
    (https://developer.android.com/reference/android/content/pm/ComponentInfo.html#directBootAware)
    apps when the data storage is encrypted with File Based Encryption (FBE).
*   SHOULD provide a mechanism in the out-of-box setup flow for users to enable
    relevant accessibility services, as well as options to adjust the font size,
    display size and magnification gestures.
