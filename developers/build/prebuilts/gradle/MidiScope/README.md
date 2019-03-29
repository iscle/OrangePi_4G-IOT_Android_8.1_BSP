
Android MidiScope Sample
===================================

Sample demonstrating how to use the MIDI API to receive and process MIDI signals coming from an
attached device.

Introduction
------------

The Android MIDI API ([android.media.midi][1]) allows developers to connect a MIDI device to Android
and process MIDI signals coming from it. This sample demonstrates some basic features of the MIDI
API, such as enumeration of currently available devices (Information includes name, vendor,
capabilities, etc), notification when MIDI devices are plugged in or unplugged, and receiving MIDI
signals. This sample simply shows all the received MIDI signals to the screen log and does not play
any sound for them.
[1]: https://developer.android.com/reference/android/media/midi/package-summary.html

Pre-requisites
--------------

- Android SDK 26
- Android Build Tools v26.0.1
- Android Support Repository

Screenshots
-------------

<img src="screenshots/1-main.png" height="400" alt="Screenshot"/> <img src="screenshots/2-signals.png" height="400" alt="Screenshot"/> 

Getting Started
---------------

This sample uses the Gradle build system. To build this project, use the
"gradlew build" command or use "Import Project" in Android Studio.

Support
-------

- Google+ Community: https://plus.google.com/communities/105153134372062985968
- Stack Overflow: http://stackoverflow.com/questions/tagged/android

If you've found an error in this sample, please file an issue:
https://github.com/googlesamples/android-MidiScope

Patches are encouraged, and may be submitted by forking this project and
submitting a pull request through GitHub. Please see CONTRIBUTING.md for more details.

License
-------

Copyright 2017 The Android Open Source Project, Inc.

Licensed to the Apache Software Foundation (ASF) under one or more contributor
license agreements.  See the NOTICE file distributed with this work for
additional information regarding copyright ownership.  The ASF licenses this
file to you under the Apache License, Version 2.0 (the "License"); you may not
use this file except in compliance with the License.  You may obtain a copy of
the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
License for the specific language governing permissions and limitations under
the License.
