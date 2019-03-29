This APK and profile file are generated from CompilationTargetActivity.java and must be
updated if that file changes:

$ (croot ; make CtsCompilationApp)
$ cp ${ANDROID_BUILD_TOP}/out/target/product/${TARGET_PRODUCT}/data/app/CtsCompilationApp/CtsCompilationApp.apk .
$ adb install CtsCompilationApp.apk

  # Now run the app manually for a couple of minutes, look for the profile:
$ adb shell ls -l /data/misc/profiles/cur/0/android.cts.compilation/primary.prof
  # once the profile appears and is nonempty, grab it:
$ adb pull /data/misc/profiles/cur/0/android.cts.compilation/primary.prof ./
