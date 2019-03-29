
Android AppRestrictions Sample
===================================

A sample that demonstrates the use of the App Restriction feature on devices with multiuser support

Introduction
------------

This sample demonstrates the use of the App Restriction feature, which is available on
Android 4.3 and above tablet device with the multiuser feature.

When launched under the primary User account, you can toggle between standard app restriction
types and custom.  When launched under a restricted profile, this activity displays app
restriction settings, if available.

This sample app maintains custom app restriction settings in shared preferences.  When
the activity is invoked (from Settings > Users), the stored settings are used to initialize
the custom configuration on the user interface.  Three sample [RestrictionEntry][1] types are
shown: checkbox, single-choice, and multi-choice.  When the settings are modified by the user,
the corresponding restriction entries are saved, which are retrievable under a restricted
profile through the [UserManager][2].

[1]: https://developer.android.com/reference/android/content/RestrictionEntry.html
[2]: https://developer.android.com/reference/android/os/UserManager.html

Pre-requisites
--------------

- Android SDK 25
- Android Build Tools v25.0.2
- Android Support Repository

Screenshots
-------------

<img src="screenshots/1-application.png" height="400" alt="Screenshot"/> <img src="screenshots/2-custom-restrictions-actiivity.png" height="400" alt="Screenshot"/> 

Getting Started
---------------

This sample uses the Gradle build system. To build this project, use the
"gradlew build" command or use "Import Project" in Android Studio.

Support
-------

- Google+ Community: https://plus.google.com/communities/105153134372062985968
- Stack Overflow: http://stackoverflow.com/questions/tagged/android

If you've found an error in this sample, please file an issue:
https://github.com/googlesamples/android-AppRestrictions

Patches are encouraged, and may be submitted by forking this project and
submitting a pull request through GitHub. Please see CONTRIBUTING.md for more details.

License
-------

Copyright 2016 The Android Open Source Project, Inc.

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
