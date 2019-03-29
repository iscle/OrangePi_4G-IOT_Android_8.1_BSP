#!/usr/bin/python

# Copyright (C) 2009 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Module for generating CTS test descriptions and test plans."""

import glob
import os
import re
import shutil
import string
import subprocess
import sys
import xml.dom.minidom as dom
from cts import tools
from multiprocessing import Pool

def GetSubDirectories(root):
  """Return all directories under the given root directory."""
  return [x for x in os.listdir(root) if os.path.isdir(os.path.join(root, x))]

def ReadFileLines(filePath):
  """Reads a file and returns its contents as a line list."""
  f = open(filePath, 'r');
  lines = [line.strip() for line in f.readlines()]
  f.close()
  return lines

def ReadDeqpTestList(testRoot, file):
  """Reads a file, converts test names from deqp to CTS format, and returns
  its contents as a line list.
  """
  REPO_ROOT = os.path.join(testRoot, "../../..")
  f = open(os.path.join(REPO_ROOT, "external/deqp/android/cts", file), 'r');
  lines = [string.join(line.strip().rsplit('.',1),'#') for line in f.readlines()]
  f.close()
  return lines

def GetMakeFileVars(makefile_path):
  """Extracts variable definitions from the given make file.

  Args:
    makefile_path: Path to the make file.

  Returns:
    A dictionary mapping variable names to their assigned value.
  """
  result = {}
  pattern = re.compile(r'^\s*([^:#=\s]+)\s*:=\s*(.*?[^\\])$', re.MULTILINE + re.DOTALL)
  stream = open(makefile_path, 'r')
  content = stream.read()
  for match in pattern.finditer(content):
    result[match.group(1)] = match.group(2)
  stream.close()
  return result


class CtsBuilder(object):
  """Main class for generating test descriptions and test plans."""

  def __init__(self, argv):
    """Initialize the CtsBuilder from command line arguments."""
    if len(argv) != 6:
      print 'Usage: %s <testRoot> <ctsOutputDir> <tempDir> <androidRootDir> <docletPath>' % argv[0]
      print ''
      print 'testRoot:       Directory under which to search for CTS tests.'
      print 'ctsOutputDir:   Directory in which the CTS repository should be created.'
      print 'tempDir:        Directory to use for storing temporary files.'
      print 'androidRootDir: Root directory of the Android source tree.'
      print 'docletPath:     Class path where the DescriptionGenerator doclet can be found.'
      sys.exit(1)
    self.test_root = sys.argv[1]
    self.out_dir = sys.argv[2]
    self.temp_dir = sys.argv[3]
    self.android_root = sys.argv[4]
    self.doclet_path = sys.argv[5]

    self.test_repository = os.path.join(self.out_dir, 'repository/testcases')
    self.plan_repository = os.path.join(self.out_dir, 'repository/plans')
    self.definedplans_repository = os.path.join(self.android_root, 'cts/tests/plans')

  def GenerateTestDescriptions(self):
    """Generate test descriptions for all packages."""
    pool = Pool(processes=2)

    # generate test descriptions for android tests
    results = []
    pool.close()
    pool.join()
    return sum(map(lambda result: result.get(), results))

  def __WritePlan(self, plan, plan_name):
    print 'Generating test plan %s' % plan_name
    plan.Write(os.path.join(self.plan_repository, plan_name + '.xml'))

  def GenerateTestPlans(self):
    """Generate default test plans."""
    # TODO: Instead of hard-coding the plans here, use a configuration file,
    # such as test_defs.xml
    packages = []
    descriptions = sorted(glob.glob(os.path.join(self.test_repository, '*.xml')))
    for description in descriptions:
      doc = tools.XmlFile(description)
      packages.append(doc.GetAttr('TestPackage', 'appPackageName'))
    # sort the list to give the same sequence based on name
    packages.sort()

    plan = tools.TestPlan(packages)
    plan.Exclude('android\.car')
    plan.Exclude('android\.performance.*')
    self.__WritePlan(plan, 'CTS')
    self.__WritePlan(plan, 'CTS-TF')

    plan = tools.TestPlan(packages)
    plan.Exclude('android\.car')
    plan.Exclude('android\.performance.*')
    plan.Exclude('android\.media\.cts\.StreamingMediaPlayerTest.*')
    # Test plan to not include media streaming tests
    self.__WritePlan(plan, 'CTS-No-Media-Stream')

    plan = tools.TestPlan(packages)
    plan.Exclude('android\.car')
    plan.Exclude('android\.performance.*')
    self.__WritePlan(plan, 'SDK')

    plan.Exclude(r'android\.signature')
    plan.Exclude(r'android\.core.*')
    self.__WritePlan(plan, 'Android')

    plan = tools.TestPlan(packages)
    plan.Include(r'android\.core\.tests.*')
    plan.Exclude(r'android\.core\.tests\.libcore\.package\.harmony*')
    self.__WritePlan(plan, 'Java')

    # TODO: remove this once the tests are fixed and merged into Java plan above.
    plan = tools.TestPlan(packages)
    plan.Include(r'android\.core\.tests\.libcore\.package\.harmony*')
    self.__WritePlan(plan, 'Harmony')

    plan = tools.TestPlan(packages)
    plan.Include(r'android\.core\.vm-tests-tf')
    self.__WritePlan(plan, 'VM-TF')

    plan = tools.TestPlan(packages)
    plan.Include(r'android\.tests\.appsecurity')
    self.__WritePlan(plan, 'AppSecurity')

    # hard-coded white list for PDK plan
    plan.Exclude('.*')
    plan.Include('android\.aadb')
    plan.Include('android\.bluetooth')
    plan.Include('android\.graphics.*')
    plan.Include('android\.hardware')
    plan.Include('android\.media')
    plan.Exclude('android\.mediastress')
    plan.Include('android\.net')
    plan.Include('android\.opengl.*')
    plan.Include('android\.renderscript')
    plan.Include('android\.telephony')
    plan.Include('android\.nativemedia.*')
    plan.Include('com\.android\.cts\..*')#TODO(stuartscott): Should PDK have all these?
    plan.Exclude('android\.car')
    self.__WritePlan(plan, 'PDK')

    temporarily_known_failure_tests = BuildCtsTemporarilyKnownFailureList();
    flaky_tests = BuildCtsFlakyTestList()
    releasekey_tests = BuildListForReleaseBuildTest()

    # CTS Stable plan
    plan = tools.TestPlan(packages)
    plan.Exclude('android\.car')
    plan.Exclude(r'android\.browser')
    for package, test_list in flaky_tests.iteritems():
      plan.ExcludeTests(package, test_list)
    for package, test_list in releasekey_tests.iteritems():
      plan.ExcludeTests(package, test_list)
    self.__WritePlan(plan, 'CTS-stable')

    # CTS Flaky plan - list of tests known to be flaky in lab environment
    plan = tools.TestPlan(packages)
    plan.Exclude('.*')
    plan.Include(r'android\.browser')
    for package, test_list in flaky_tests.iteritems():
      plan.Include(package+'$')
      plan.IncludeTests(package, test_list)
    self.__WritePlan(plan, 'CTS-flaky')

    small_tests = BuildAospSmallSizeTestList()
    medium_tests = BuildAospMediumSizeTestList()
    new_test_packages = BuildCtsVettedNewPackagesList()

    # CTS - sub plan for public, small size tests
    plan = tools.TestPlan(packages)
    plan.Exclude('.*')
    for package, test_list in small_tests.iteritems():
      plan.Include(package+'$')
    plan.Exclude(r'android\.browser')
    for package, test_list in flaky_tests.iteritems():
      plan.ExcludeTests(package, test_list)
    for package, test_list in releasekey_tests.iteritems():
      plan.ExcludeTests(package, test_list)
    self.__WritePlan(plan, 'CTS-kitkat-small')
    self.__WritePlan(plan, 'CTS-public-small')

    # CTS - sub plan for public, medium size tests
    plan = tools.TestPlan(packages)
    plan.Exclude('.*')
    for package, test_list in medium_tests.iteritems():
      plan.Include(package+'$')
    plan.Exclude(r'android\.browser')
    for package, test_list in flaky_tests.iteritems():
      plan.ExcludeTests(package, test_list)
    for package, test_list in releasekey_tests.iteritems():
      plan.ExcludeTests(package, test_list)
    self.__WritePlan(plan, 'CTS-kitkat-medium')
    self.__WritePlan(plan, 'CTS-public-medium')

    # CTS - sub plan for hardware tests which is public, large
    plan = tools.TestPlan(packages)
    plan.Exclude('.*')
    plan.Include(r'android\.hardware$')
    plan.Exclude(r'android\.browser')
    for package, test_list in flaky_tests.iteritems():
      plan.ExcludeTests(package, test_list)
    for package, test_list in releasekey_tests.iteritems():
      plan.ExcludeTests(package, test_list)
    self.__WritePlan(plan, 'CTS-hardware')

    # CTS - sub plan for camera tests which is public, large
    plan = tools.TestPlan(packages)
    plan.Exclude('.*')
    plan.Include(r'android\.camera$')
    misc_camera_tests = BuildCtsMiscCameraList()
    for package, test_list in misc_camera_tests.iteritems():
      plan.Include(package+'$')
      plan.IncludeTests(package, test_list)
    for package, test_list in flaky_tests.iteritems():
      plan.ExcludeTests(package, test_list)
    for package, test_list in releasekey_tests.iteritems():
      plan.ExcludeTests(package, test_list)
    self.__WritePlan(plan, 'CTS-camera')

    # CTS - sub plan for media tests which is public, large
    plan = tools.TestPlan(packages)
    plan.Exclude('.*')
    plan.Include(r'android\.media$')
    plan.Include(r'android\.view$')
    plan.Exclude(r'android\.browser')
    for package, test_list in flaky_tests.iteritems():
      plan.ExcludeTests(package, test_list)
    for package, test_list in releasekey_tests.iteritems():
      plan.ExcludeTests(package, test_list)
    self.__WritePlan(plan, 'CTS-media')

    # CTS - sub plan for mediastress tests which is public, large
    plan = tools.TestPlan(packages)
    plan.Exclude('.*')
    plan.Include(r'android\.mediastress$')
    plan.Exclude(r'android\.browser')
    for package, test_list in flaky_tests.iteritems():
      plan.ExcludeTests(package, test_list)
    for package, test_list in releasekey_tests.iteritems():
      plan.ExcludeTests(package, test_list)
    self.__WritePlan(plan, 'CTS-mediastress')

    # CTS - sub plan for new tests that is vetted for L launch
    plan = tools.TestPlan(packages)
    plan.Exclude('.*')
    for package, test_list in new_test_packages.iteritems():
      plan.Include(package+'$')
    plan.Exclude(r'android\.browser')
    for package, test_list in flaky_tests.iteritems():
      plan.ExcludeTests(package, test_list)
    for package, test_list in releasekey_tests.iteritems():
      plan.ExcludeTests(package, test_list)
    self.__WritePlan(plan, 'CTS-l-tests')

    # CTS - sub plan for tests in drawelement packages
    plan = tools.TestPlan(packages)
    plan.Exclude('.*')
    plan.Include(r'com\.drawelements\.')
    plan.IncludeTests('com.drawelements.deqp.egl', ReadDeqpTestList(self.test_root, 'mnc/egl-master.txt'))
    plan.IncludeTests('com.drawelements.deqp.gles2', ReadDeqpTestList(self.test_root, 'mnc/gles2-master.txt'))
    plan.IncludeTests('com.drawelements.deqp.gles3', ReadDeqpTestList(self.test_root, 'mnc/gles3-master.txt'))
    plan.IncludeTests('com.drawelements.deqp.gles31', ReadDeqpTestList(self.test_root, 'mnc/gles31-master.txt'))
    self.__WritePlan(plan, 'CTS-DEQP')

    plan = tools.TestPlan(packages)
    plan.Exclude('.*')
    plan.Include(r'com\.drawelements\.')
    plan.ExcludeTests('com.drawelements.deqp.egl', ReadDeqpTestList(self.test_root, 'mnc/egl-master.txt'))
    plan.ExcludeTests('com.drawelements.deqp.gles2', ReadDeqpTestList(self.test_root, 'mnc/gles2-master.txt'))
    plan.ExcludeTests('com.drawelements.deqp.gles3', ReadDeqpTestList(self.test_root, 'mnc/gles3-master.txt'))
    plan.ExcludeTests('com.drawelements.deqp.gles31', ReadDeqpTestList(self.test_root, 'mnc/gles31-master.txt'))
    self.__WritePlan(plan, 'CTS-DEQP-for-next-rel')

    # CTS - sub plan for new test packages added for staging
    plan = tools.TestPlan(packages)
    for package, test_list in small_tests.iteritems():
      plan.Exclude(package+'$')
    for package, test_list in medium_tests.iteritems():
      plan.Exclude(package+'$')
    for package, tests_list in new_test_packages.iteritems():
      plan.Exclude(package+'$')
    plan.Exclude(r'com\.drawelements\.')
    plan.Exclude(r'android\.hardware$')
    plan.Exclude(r'android\.media$')
    plan.Exclude(r'android\.view$')
    plan.Exclude(r'android\.mediastress$')
    plan.Exclude(r'android\.browser')
    plan.Exclude('android\.car')
    for package, test_list in flaky_tests.iteritems():
      plan.ExcludeTests(package, test_list)
    for package, test_list in releasekey_tests.iteritems():
      plan.ExcludeTests(package, test_list)
    self.__WritePlan(plan, 'CTS-m-tests')


    # CTS - sub plan for new test packages added for staging
    plan = tools.TestPlan(packages)
    plan.Exclude('.*')
    for package, test_list in temporarily_known_failure_tests.iteritems():
      plan.Include(package+'$')
      plan.IncludeTests(package, test_list)
    self.__WritePlan(plan, 'CTS-staging')

    plan = tools.TestPlan(packages)
    plan.Exclude('.*')
    self.__WritePlan(plan, 'CTS-webview')

    # CTS - sub plan for Security
    plan = tools.TestPlan(packages)
    plan.Exclude('.*')
    plan.Include(r'android\.security$')
    plan.Include('android\.host\.jdwpsecurity$')
    plan.Include('android\.host\.abioverride$')
    self.__WritePlan(plan, 'Security')

def BuildAospMediumSizeTestList():
  """ Construct a defaultdic that lists package names of medium tests
      already published to aosp. """
  return {
      'android.app' : [],
      'android.core.tests.libcore.package.libcore' : [],
      'android.core.tests.libcore.package.org' : [],
      'android.core.vm-tests-tf' : [],
      'android.dpi' : [],
      'android.host.security' : [],
      'android.net' : [],
      'android.os' : [],
      'android.permission2' : [],
      'android.security' : [],
      'android.telephony' : [],
      'android.webkit' : [],
      'android.widget' : [],
      'android.browser' : []}

def BuildAospSmallSizeTestList():
  """ Construct a default dict that lists packages names of small tests
      already published to aosp. """
  return {
      'android.aadb' : [],
      'android.acceleration' : [],
      'android.accessibility' : [],
      'android.accessibilityservice' : [],
      'android.accounts' : [],
      'android.admin' : [],
      'android.animation' : [],
      'android.appsecurity' : [],
      'android.bionic' : [],
      'android.bluetooth' : [],
      'android.calendarcommon' : [],
      'android.content' : [],
      'android.core.tests.libcore.package.com' : [],
      'android.core.tests.libcore.package.conscrypt' : [],
      'android.core.tests.libcore.package.dalvik' : [],
      'android.core.tests.libcore.package.sun' : [],
      'android.core.tests.libcore.package.tests' : [],
      'android.database' : [],
      'android.dram' : [],
      'android.dreams' : [],
      'android.drm' : [],
      'android.effect' : [],
      'android.filesystem' : [],
      'android.gesture' : [],
      'android.graphics' : [],
      'android.graphics2' : [],
      'android.jni' : [],
      'android.keystore' : [],
      'android.location' : [],
      'android.nativemedia.sl' : [],
      'android.nativemedia.xa' : [],
      'android.ndef' : [],
      'android.opengl' : [],
      'android.openglperf' : [],
      'android.permission' : [],
      'android.preference' : [],
      'android.preference2' : [],
      'android.provider' : [],
      'android.renderscript' : [],
      'android.rscpp' : [],
      'android.rsg' : [],
      'android.sax' : [],
      'android.server' : [],
      'android.signature' : [],
      'android.simplecpu' : [],
      'android.simpleperf' : [],
      'android.speech' : [],
      'android.text' : [],
      'android.textureview' : [],
      'android.theme' : [],
      'android.usb' : [],
      'android.util' : [],
      'android.video' : [],
      'com.android.cts.jank' : [],
      'com.android.cts.jank2' : [],
      'com.android.cts.opengl' : [],
      'com.android.cts.ui' : [],
      'com.android.cts.uihost' : [],
      'zzz.android.monkey' : []}

def BuildCtsVettedNewPackagesList():
  """ Construct a defaultdict that maps package names that is vetted for L. """
  return {
      'android.JobScheduler' : [],
      'android.core.tests.libcore.package.harmony_annotation' : [],
      'android.core.tests.libcore.package.harmony_beans' : [],
      'android.core.tests.libcore.package.harmony_java_io' : [],
      'android.core.tests.libcore.package.harmony_java_lang' : [],
      'android.core.tests.libcore.package.harmony_java_math' : [],
      'android.core.tests.libcore.package.harmony_java_net' : [],
      'android.core.tests.libcore.package.harmony_java_nio' : [],
      'android.core.tests.libcore.package.harmony_java_util' : [],
      'android.core.tests.libcore.package.harmony_java_text' : [],
      'android.core.tests.libcore.package.harmony_javax_security' : [],
      'android.core.tests.libcore.package.harmony_logging' : [],
      'android.core.tests.libcore.package.harmony_prefs' : [],
      'android.core.tests.libcore.package.harmony_sql' : [],
      'android.core.tests.libcore.package.jsr166' : [],
      'android.core.tests.libcore.package.okhttp' : [],
      'android.display' : [],
      'android.host.theme' : [],
      'android.jdwp' : [],
      'android.location2' : [],
      'android.print' : [],
      'android.renderscriptlegacy' : [],
      'android.signature' : [],
      'android.tv' : [],
      'android.uiautomation' : [],
      'android.uirendering' : []}

def BuildListForReleaseBuildTest():
  """ Construct a defaultdict that maps package name to a list of tests
      that are expected to pass only when running against a user/release-key build. """
  return {
      'android.app' : [
          'android.app.cts.ActivityManagerTest#testIsRunningInTestHarness',],
      'android.dpi' : [
          'android.dpi.cts.DefaultManifestAttributesSdkTest#testPackageHasExpectedSdkVersion',],
      'android.host.security' : [
          'android.cts.security.SELinuxHostTest#testAllEnforcing',
          'android.cts.security.SELinuxHostTest#testSuDomain',],
      'android.os' : [
          'android.os.cts.BuildVersionTest#testReleaseVersion',
          'android.os.cts.BuildTest#testIsSecureUserBuild',],
      'android.security' : [
          'android.security.cts.BannedFilesTest#testNoSu',
          'android.security.cts.BannedFilesTest#testNoSuInPath',
          'android.security.cts.PackageSignatureTest#testPackageSignatures',
          'android.security.cts.SELinuxDomainTest#testSuDomain',],
      '' : []}

def BuildCtsFlakyTestList():
  """ Construct a defaultdict that maps package name to a list of tests
      that flaky during dev cycle and cause other subsequent tests to fail. """
  return {
      'android.camera' : [
          'android.hardware.cts.CameraTest#testVideoSnapshot',
          'android.hardware.cts.CameraGLTest#testCameraToSurfaceTextureMetadata',
          'android.hardware.cts.CameraGLTest#testSetPreviewTextureBothCallbacks',
          'android.hardware.cts.CameraGLTest#testSetPreviewTexturePreviewCallback',],
      'android.media' : [
          'android.media.cts.DecoderTest#testCodecResetsH264WithSurface',
          'android.media.cts.StreamingMediaPlayerTest#testHLS',],
      'android.net' : [
          'android.net.cts.ConnectivityManagerTest#testStartUsingNetworkFeature_enableHipri',
          'android.net.cts.DnsTest#testDnsWorks',
          'android.net.cts.SSLCertificateSocketFactoryTest#testCreateSocket',
          'android.net.cts.SSLCertificateSocketFactoryTest#test_createSocket_bind',
          'android.net.cts.SSLCertificateSocketFactoryTest#test_createSocket_simple',
          'android.net.cts.SSLCertificateSocketFactoryTest#test_createSocket_wrapping',
          'android.net.cts.TrafficStatsTest#testTrafficStatsForLocalhost',
          'android.net.wifi.cts.NsdManagerTest#testAndroidTestCaseSetupProperly',],
      'android.security' : [
          'android.security.cts.ListeningPortsTest#testNoRemotelyAccessibleListeningUdp6Ports',
          'android.security.cts.ListeningPortsTest#testNoRemotelyAccessibleListeningUdpPorts',],
      'android.webkit' : [
          'android.webkit.cts.WebViewClientTest#testOnUnhandledKeyEvent',],
      'com.android.cts.filesystemperf' : [
          'com.android.cts.filesystemperf.RandomRWTest#testRandomRead',
          'com.android.cts.filesystemperf.RandomRWTest#testRandomUpdate',],
      '' : []}

def BuildCtsTemporarilyKnownFailureList():
  """ Construct a defaultdict that maps package name to a list of tests
      that are known failures during dev cycle but expected to be fixed before launch """
  return {
      'android.alarmclock' : [
          'android.alarmclock.cts.DismissAlarmTest#testAll',
          'android.alarmclock.cts.SetAlarmTest#testAll',
          'android.alarmclock.cts.SnoozeAlarmTest#testAll',
      ],
      'android.dumpsys' : [
          'android.dumpsys.cts.DumpsysHostTest#testBatterystatsOutput',
          'android.dumpsys.cts.DumpsysHostTest#testGfxinfoFramestats',
      ],
      'android.telecom' : [
          'android.telecom.cts.ExtendedInCallServiceTest#testAddNewOutgoingCallAndThenDisconnect',
          'android.telecom.cts.RemoteConferenceTest#testRemoteConferenceCallbacks_ConferenceableConnections',
      ],
      'android.transition' : [
          'android.transition.cts.ChangeScrollTest#testChangeScroll',
      ],
      'android.voicesettings' : [
          'android.voicesettings.cts.ZenModeTest#testAll',
      ],
      'android.systemui.cts' : [
          'android.systemui.cts.LightStatusBarTests#testLightStatusBarIcons',
      ],
      'com.android.cts.app.os' : [
          'com.android.cts.app.os.OsHostTests#testNonExportedActivities',
      ],
      'com.android.cts.devicepolicy' : [
          'com.android.cts.devicepolicy.MixedDeviceOwnerTest#testPackageInstallUserRestrictions',
          'com.android.cts.devicepolicy.MixedProfileOwnerTest#testPackageInstallUserRestrictions',
      ],
      '' : []}

def BuildCtsMiscCameraList():
  """ Construct a defaultdict that maps package name to a list of tests
      that are relevant to camera but does not reside in camera test package """
  return {
      'android.app' : [
          'android.app.cts.SystemFeaturesTest#testCameraFeatures',
      ],
      'android.permission' : [
          'android.permission.cts.CameraPermissionTest',
          'android.permission.cts.Camera2PermissionTest',
      ],
      '' : []}

def LogGenerateDescription(name):
  print 'Generating test description for package %s' % name

if __name__ == '__main__':
  builder = CtsBuilder(sys.argv)
  result = builder.GenerateTestDescriptions()
  if result != 0:
    sys.exit(result)
  builder.GenerateTestPlans()
