# Copyright (C) 2016 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

instrumentation_tests := \
    HelloWorldTests \
    crashcollector \
    LongevityLibTests \
    ManagedProvisioningTests \
    FrameworksCoreTests \
    FrameworksNetTests \
    FrameworksNotificationTests \
    ConnTestApp \
    FrameworksServicesTests \
    FrameworksUtilTests \
    MtpDocumentsProviderTests \
    DocumentsUITests \
    ShellTests \
    SystemUITests \
    TestablesTests \
    RecyclerViewTests \
    FrameworksWifiApiTests \
    FrameworksWifiTests \
    FrameworksTelephonyTests \
    ContactsProviderTests \
    ContactsProviderTests2 \
    SettingsUnitTests \
    TelecomUnitTests \
    AndroidVCardTests \
    PermissionFunctionalTests \
    BlockedNumberProviderTest \
    SettingsFunctionalTests \
    LauncherFunctionalTests \
    DownloadAppFunctionalTests \
    NotificationFunctionalTests \
    DownloadProviderTests \
    EmergencyInfoTests \
    CalendarProviderTests \
    SettingsLibTests \
    RSTest \
    PrintSpoolerOutOfProcessTests \
    CellBroadcastReceiverUnitTests \
    TelephonyProviderTests \
    CarrierConfigTests \
    TeleServiceTests \
    SettingsProviderTest \
    SettingsTests

# Android Things specific tests
ifeq ($(PRODUCT_IOT),true)

instrumentation_tests += \
    AndroidThingsTests \
    IoTLauncherTests \
    WifiSetupUnitTests

endif  # PRODUCT_IOT == true

# Storage Manager may not exist on device
ifneq ($(filter StorageManager, $(PRODUCT_PACKAGES)),)

instrumentation_tests += StorageManagerUnitTests

endif