Scripting Layer For Native
=============================

### Introduction
Scripting Layer for Native, SL4N, is an automation toolset for calling Android native Binder
APIs and accessing the HAL layer in an platform-independent manner. This tool enables remote
automation of these layers via ADB.

### Build Instructions
Building SL4N requires a system build.

For the initial build of Android:

    cd <ANDROID_SOURCE_ROOT>
    source build/envsetup.sh
    lunch aosp_<TARGET>
    make [-j15]

*where `<ANDROID_SOURCE_ROOT>` is the root directory of the android tree and `<TARGET>` is the lunch
target name*

Then Build SL4N:

    cd <ANDROID_SOURCE_ROOT>/packages/apps/Test/connectivity/sl4n
    mm [-j15]

### Install Instructions
Run the following command:

    adb push <ANDROID_SOURCE_ROOT>/out/target/product/<TARGET>/system/bin/sl4n /system/bin

Library dependencies required:

    adb push <ANDROID_SOURCE_ROOT>/out/target/product/<TARGET>/system/lib/libbinder.so /system/lib
    adb push <ANDROID_SOURCE_ROOT>/out/target/product/<TARGET>/system/lib/libchrome.so /system/lib
    adb push <ANDROID_SOURCE_ROOT>/out/target/product/<TARGET>/system/lib/libevent.so /system/lib

Optional library dependency for running tests that exercise the Bluetoothtbd service:

    cd <ANDROID_SOURCE_ROOT>/system/bt/service
    mm [-j15]
    adb push <ANDROID_SOURCE_ROOT>/out/target/product/<TARGET>/system/bin/bluetoothtbd /system/bin

### Run Instructions
a) SL4N is launched from ADB shell; or  
b) To enable RPC access from the command prompt:

    adb forward tcp:<HOST_PORT_NUM> tcp:<DEVICE_PORT_NUM>
    adb shell -c "/system/bin/sl4n" &
*where `<HOST_PORT_NUM>` and `<DEVICE_PORT_NUM>` are the tcp ports on the host computer and device.*
