#!/usr/bin/python2.7

# Copyright (C) 2016 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

#
# Generate a CTS test XML file from a text file containing every single class#method per line
#
# For example, given an input file:
#
#          foo.txt:
#                com.android.ClassName#methodNameA
#                com.android.ClassName#methodNameB
#
# Will generate the output file:
#
#          TestPackage.xml:
#                <TestPackage>
#                  <TestSuite name="com">
#                    <TestSuite name="android">
#                      <TestCase name="ClassName">
#                        <Test name="methodNameA" />
#                        <Test name="methodNameB" />
#                      </TestCase>
#                    </TestSuite>
#                  </TestSuite>
#                </TestPackage>
#

import argparse
import sys

INDENTATION_INCREASE=2

class BaseNode(object):
    def __init__(self, name=None):
        self._children = []
        self._name = name
        self._properties = []

    def _get_children(self):
        return self._children

    def _set_children(self, value):
        self._children = value

    children = property(_get_children, _set_children, doc="Get/set list of children BaseNode")

    def append_child(self, child):
        self._children.append(child)

    def has_children(self):
        return not not self._children

    def _get_name(self):
        return self._name

    def _set_name(self, value):
        self._name = value

    name = property(_get_name, _set_name, doc="Get/set the name property of the current XML node")

    def _get_type_name(self):
        return type(self).__name__

    type_name = property(_get_type_name, doc="Get the name of the current XML node")

    def _set_properties(self, value):
        self._properties = value

    def _get_properties(self):
        return self._properties

    properties = property(_get_properties, _set_properties, doc="Get/set additional XML properties such as appPackageName (as a dict)")

    def write_xml(self, out, indent=0):
        out.write(' ' * indent)
        out.write('<' + self.type_name)

        if self.name is not None:
            out.write(' name="')
            out.write(self.name)
            out.write('"')

        if self.properties:
            for key, value in self.properties.iteritems():
                out.write(' ' + key + '="' + value + '"')

        if not self.has_children():
            out.write(' />')
            out.write('\n')
            return

        out.write('>\n')

        #TODO: print all the properties

        for child in self.children:
            child.write_xml(out, indent + INDENTATION_INCREASE)

        out.write(' ' * indent)
        out.write('</' + self.type_name + '>')
        out.write('\n')

class _SuiteContainer(BaseNode):
    def get_or_create_suite(self, package_list):
        debug_print("get_or_create_suite, package_list = " + str(package_list))
        debug_print("name = " + self.name)
        # If we are empty, then we just reached the TestSuite which we actually wanted. Return.
        if not package_list:
            return self

        current_package = package_list[0]
        rest_of_packages = package_list[1:]

        # If a suite already exists for the requested package, then have it look/create recursively.
        for child in self.children:
            if child.name == current_package:
                return child.get_or_create_suite(rest_of_packages)

        # No suite exists yet, create it recursively
        new_suite = TestSuite(name=current_package)
        self.append_child(new_suite)
        return new_suite.get_or_create_suite(rest_of_packages)

class TestPackage(_SuiteContainer):
    def add_class_and_method(self, fq_class_name, method):
        debug_print("add_class_and_method, fq_class_name=" + fq_class_name + ", method=" + method)
        package_list = fq_class_name.split(".")[:-1] # a.b.c -> ['a', 'b']
        just_class_name = fq_class_name.split(".")[-1] # a.b.c -> 'c'

        test_suite = self.get_or_create_suite(package_list)

        if test_suite == self:
            raise Exception("The suite cannot be the package")

        return test_suite.add_class_and_method(just_class_name, method)

class TestSuite(_SuiteContainer):
    def add_class_and_method(self, just_class_name, method_name):
        test_case = self.get_or_create_test_case(just_class_name)
        return test_case.add_method(method_name)

    def get_or_create_test_case(self, just_class_name):
        for child in self.children:
            if child.name == just_class_name:
                return child

        new_test_case = TestCase(name=just_class_name)
        self.append_child(new_test_case)
        return new_test_case

class TestCase(BaseNode):
    def add_method(self, method_name):
        tst = Test(name=method_name)
        self.append_child(tst)
        return tst

class Test(BaseNode):
    def __init__(self, name):
        super(Test, self).__init__(name)
        self._children = None

def debug_print(x):
    #print x
    pass

def build_xml_test_package(input, name, xml_properties):
    root = TestPackage(name=name)

    for line in input:
        class_and_method_name = line.split('#')
        fq_class_name = class_and_method_name[0].strip()
        method_name = class_and_method_name[1].strip()

        root.add_class_and_method(fq_class_name, method_name)

    root.properties = xml_properties
    return root

def write_xml(out, test_package):
    out.write('<?xml version="1.0" encoding="UTF-8"?>\n')
    test_package.write_xml(out)

def main():
    parser = argparse.ArgumentParser(description='Process a test methods list file to generate CTS test xml.')

    # Named required
    parser.add_argument('--cts-name', help="name (e.g. CtsJdwp)", required=True)
    parser.add_argument('--app-package-name', help="appPackageName (e.g. android.jdwp)", required=True)
    parser.add_argument('--jar-path', help="jarPath (e.g. CtsJdwp.jar)", required=True)

    # Named optionals
    parser.add_argument('--test-type', help="testType (default testNGDeviceTest)",
                        default="testNGDeviceTest")
    parser.add_argument('--runtime-args', help="runtimeArgs (e.g. -XXlib:libart.so)")
    parser.add_argument('--version', help="version (default 1.0)", default="1.0")

    # Positional optionals
    parser.add_argument('input-filename', nargs='?',
                               help='name of the cts test file (stdin by default)')
    parser.add_argument('output-filename', nargs='?',
                               help='name of the cts output file (stdout by default)')

    # Map named arguments into the xml <TestPackage> property key name
    argv_to_xml = {
            'app_package_name' : 'appPackageName',
            'jar_path' : 'jarPath',
            'test_type' : 'testType',
            'runtime_args' : 'runtimeArgs',
            'version' : 'version'
    }

    args = parser.parse_args()
    argv = vars(args) # convert Namespace to Dict

    xml_properties = {}
    for key, value in argv_to_xml.iteritems():
        if argv.get(key):
            xml_properties[value] = argv[key]

    debug_print(argv['input-filename'])
    debug_print(argv['output-filename'])

    name_in = argv['input-filename']
    name_out = argv['output-filename']

    file_in = name_in and open(name_in, "r") or sys.stdin
    file_out = name_out and open(name_out, "w+") or sys.stdout

    # read all the input
    test_package = build_xml_test_package(file_in, args.cts_name, xml_properties)
    # write all the output
    write_xml(file_out, test_package)

if __name__ == "__main__":
    main()
