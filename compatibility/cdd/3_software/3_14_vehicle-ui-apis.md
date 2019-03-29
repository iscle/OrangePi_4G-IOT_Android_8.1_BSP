## 3.14\. Media UI


If device implementations include the UI framework that supports third-party
apps that depend on [`MediaBrowser`](
http://developer.android.com/reference/android/media/browse/MediaBrowser.html)
and [`MediaSession`](
http://developer.android.com/reference/android/media/session/MediaSession.html)
, they:

*    [C-1-1] MUST display [MediaItem](
     http://developer.android.com/reference/android/media/browse/MediaBrowser.MediaItem.html)
     icons and notification icons unaltered.
*    [C-1-2] MUST display those items as described by MediaSession, e.g.,
     metadata, icons, imagery.
*    [C-1-3] MUST show app title.
*    [C-1-4] MUST have drawer to present [MediaBrowser](
     http://developer.android.com/reference/android/media/browse/MediaBrowser.html)
     hierarchy.
