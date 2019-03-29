## 3.13\. Quick Settings

Android provides a Quick Settings UI component that allows quick access to
frequently used or urgently needed actions.

If device implementations include a Quick Settings UI component, they:

*    [C-1-1] MUST allow the user to add or remove the tiles provided through the
     [`quicksettings`](
     https://developer.android.com/reference/android/service/quicksettings/package-summary.html)
     APIs from a third-party app.
*    [C-1-2] MUST NOT automatically add a tile from a third-party app directly
     to the Quick Settings.
*    [C-1-3] MUST display all the user-added tiles from third-party apps
     alongside the system-provided quick setting tiles.
