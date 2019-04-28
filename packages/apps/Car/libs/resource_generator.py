#!/usr/bin/python3

##
# A good background read on how Android handles alternative resources is here:
# https://developer.android.com/guide/topics/resources/providing-resources.html
#
# This uses lxml so you may need to install it manually if your distribution
# does not ordinarily ship with it. On Ubuntu, you can run:
#
# sudo apt-get install python-lxml
#
# Example invocation:
# ./resource_generator.py --csv specs/keylines.csv --resdir car-stream-ui-lib/res --type dimens
##

import argparse
import csv
import datetime
import os
import pprint

import lxml.etree as et

DBG = False

class ResourceGenerator:
    def __init__(self):
        self.COLORS = "colors"
        self.DIMENS = "dimens"

        self.TAG_DIMEN = "dimen"

        self.resource_handlers = {
            self.COLORS : self.HandleColors,
            self.DIMENS : self.HandleDimens,
        }

        self.ENCODING = "utf-8"
        self.XML_HEADER = '<?xml version="1.0" encoding="%s"?>' % self.ENCODING
        # The indentation looks off but it needs to be otherwise the indentation will end up in the
        # string itself, which we don't want. So much for pythons indentation policy.
        self.AOSP_HEADER = """
<!-- Copyright (C) %d The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->
""" % datetime.datetime.now().year
        self.EMPTY_XML = "<resources/>"


    def HandleColors(self, reader, resource_dir):
        raise Exception("Not yet implemented")


    ##
    # Validate the header row of the csv. Getting this wrong would mean that the resources wouldn't
    # actually work, so find any mistakes ahead of time.
    ##
    def ValidateHeader(self, header):
        # TODO: Validate the header values based on the ordering of modifiers in table 2.
        pass


    ##
    # Given a list of resource modifers, create the appropriate directories and xml files for
    # them to be populated in.
    # Returns a tuple of maps of the form  ({ modifier : xml file } , { modifier : xml object })
    ##
    def CreateOrOpenResourceFiles(self, resource_dir, resource_type, modifiers):
        filenames = { }
        xmltrees = { }
        dir_prefix = "values"
        qualifier_separator = "-"
        file_extension = ".xml"
        for modifier in modifiers:
            # We're using the keyword none to specify that there are no modifiers and so the
            # values specified here goes into the default file.
            directory = resource_dir + os.path.sep + dir_prefix
            if modifier != "none":
                directory = directory + qualifier_separator + modifier

            if not os.path.exists(directory):
                if DBG:
                    print("Creating directory %s" % directory)
                os.mkdir(directory)

            filename = directory + os.path.sep + resource_type + file_extension
            if not os.path.exists(filename):
                if DBG:
                    print("Creating file %s" % filename)
                with open(filename, "w") as xmlfile:
                    xmlfile.write(self.XML_HEADER)
                    xmlfile.write(self.AOSP_HEADER)
                    xmlfile.write(self.EMPTY_XML)

            filenames[modifier] = filename
            if DBG:
                print("Parsing %s" % (filename))
            parser = et.XMLParser(remove_blank_text=True)
            xmltrees[modifier] = et.parse(filename, parser)
        return filenames, xmltrees


    ##
    # Updates a resource value in the xmltree if it exists, adds it in if not.
    ##
    def AddOrUpdateValue(self, xmltree, tag, resource_name, resource_value):
        root = xmltree.getroot()
        found = False
        resource_node = None
        attr_name = "name"
        # Look for the value that we want.
        for elem in root:
            if elem.tag == tag and elem.attrib[attr_name] == resource_name:
                resource_node = elem
                found = True
                break
        # If it doesn't exist yet, create one.
        if not found:
            resource_node = et.SubElement(root, tag)
            resource_node.attrib[attr_name] = resource_name
        # Update the value.
        resource_node.text = resource_value


    ##
    # lxml formats xml with 2 space indentation. Android convention says 4 spaces. Multiply any
    # leading spaces by 2 and re-generate the string.
    ##
    def FixupIndentation(self, xml_string):
        reformatted_xml = ""
        for line in xml_string.splitlines():
            stripped = line.lstrip()
            # Special case for multiline comments. These usually are hand aligned with something
            # so we don't want to reformat those.
            if not stripped.startswith("<"):
                leading_spaces = 0
            else:
                leading_spaces = len(line) - len(stripped)
            reformatted_xml += " " * leading_spaces + line + os.linesep
        return reformatted_xml


    ##
    # Read all the lines that appear before the <resources.*> tag so that they can be replicated
    # while writing out the file again. We can't simply re-generate the aosp header because it's
    # apparently not a good thing to change the date on a copyright notice to something more
    # recent.
    # Returns a string of the lines that appear before the resources tag.
    ##
    def ReadStartingLines(self, filename):
        with open(filename) as f:
            starting_lines = ""
            for line in f.readlines():
                # Yes, this will fail if you start a line inside a comment with <resources>.
                # It's more work than it's worth to handle that case.
                if line.lstrip().startswith("<resources"):
                    break;
                starting_lines += line
        return starting_lines

    ##
    # Take a map of resources and a directory and update the xml files within it with the new
    # values. Will create any directories and files as necessary.
    ##
    def ModifyXml(self, resources, resource_type, resource_dir, tag):
        # Create a deduplicated list of the resource modifiers that we will need.
        modifiers = set()
        for resource_values in resources.values():
            for modifier in resource_values.keys():
                modifiers.add(modifier)
        if DBG:
            pp = pprint.PrettyPrinter()
            pp.pprint(modifiers)
            pp.pprint(resources)

        # Update each of the trees with their respective values.
        filenames, xmltrees = self.CreateOrOpenResourceFiles(resource_dir, resource_type, modifiers)
        for resource_name, resource_values in resources.items():
            if DBG:
                print(resource_name)
                print(resource_values)
            for modifier, value in resource_values.items():
                xmltree = xmltrees[modifier]
                self.AddOrUpdateValue(xmltree, tag, resource_name, value)

        # Finally write out all the trees.
        for modifier, xmltree in xmltrees.items():
            if DBG:
                print("Writing out %s" % filenames[modifier])
            # ElementTree.write() doesn't allow us to place the aosp header at the top
            # of the file so bounce it through a string.
            starting_lines = self.ReadStartingLines(filenames[modifier])
            with open(filenames[modifier], "wt", encoding=self.ENCODING) as xmlfile:
                xml = et.tostring(xmltree.getroot(), pretty_print=True).decode("utf-8")
                formatted_xml = self.FixupIndentation(xml)
                if DBG:
                    print(formatted_xml)
                xmlfile.write(starting_lines)
                xmlfile.write(formatted_xml)


    ##
    # Read in a csv file that contains dimensions and update the resources, creating any necessary
    # files and directories along the way.
    ##
    def HandleDimens(self, reader, resource_dir):
        read_header = False
        header = []
        resources = { }
        # Create nested maps of the form { resource_name : { modifier : value } }
        for row in reader:
            # Skip any empty lines.
            if len(row) == 0:
                continue

            trimmed = [cell.strip() for cell in row]
            # Skip any comment lines.
            if trimmed[0].startswith("#"):
                continue

            # Store away the header row. We'll need it later to create and/or modify the xml files.
            if not read_header:
                self.ValidateHeader(trimmed)  # Will raise if it fails.
                header = trimmed
                read_header = True
                continue

            if (len(trimmed) != len(header)):
                raise ValueError("Missing commas in csv file!")

            var_name = trimmed[0]
            var_values = { }
            for idx in range(1, len(trimmed)):
                cell = trimmed[idx]
                # Only deal with cells that actually have content in them.
                if len(cell) > 0:
                    var_values[header[idx]] = cell

            if len(var_values.keys()) > 0:
                resources[var_name] = var_values

        self.ModifyXml(resources, self.DIMENS, resource_dir, self.TAG_DIMEN)


    ##
    # Validate the command line arguments that we have been passed. Will raise an exception if
    # there are any invalid arguments.
    ##
    def ValidateArgs(self, csv, resource_dir, resource_type):
        if not os.path.isfile(csv):
            raise ValueError("%s is not a valid path" % csv)
        if not os.path.isdir(resource_dir):
            raise ValueError("%s is not a valid resource directory" % resource_dir)
        if not resource_type in self.resource_handlers:
            raise ValueError("%s is not a supported resource type" % resource_type)


    ##
    # The logical entry point of this application.
    ##
    def Main(self, csv_file, resource_dir, resource_type):
        self.ValidateArgs(csv_file, resource_dir, resource_type)  # Will raise if it fails.
        with open(csv_file, 'r') as handle:
            reader = csv.reader(handle)  # Defaults to the excel dialect of csv.
            self.resource_handlers[resource_type](reader, resource_dir)
        print("Done!")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description='Convert a CSV into android resources')
    parser.add_argument('--csv', action='store', dest='csv')
    parser.add_argument('--resdir', action='store', dest='resdir')
    parser.add_argument('--type', action='store', dest='type')
    args = parser.parse_args()
    app = ResourceGenerator()
    app.Main(args.csv, args.resdir, args.type)
