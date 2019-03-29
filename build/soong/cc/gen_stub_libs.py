#!/usr/bin/env python
#
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
#
"""Generates source for stub shared libraries for the NDK."""
import argparse
import json
import logging
import os
import re


ALL_ARCHITECTURES = (
    'arm',
    'arm64',
    'mips',
    'mips64',
    'x86',
    'x86_64',
)


# Arbitrary magic number. We use the same one in api-level.h for this purpose.
FUTURE_API_LEVEL = 10000


def logger():
    """Return the main logger for this module."""
    return logging.getLogger(__name__)


def get_tags(line):
    """Returns a list of all tags on this line."""
    _, _, all_tags = line.strip().partition('#')
    return [e for e in re.split(r'\s+', all_tags) if e.strip()]


def is_api_level_tag(tag):
    """Returns true if this tag has an API level that may need decoding."""
    if tag.startswith('introduced='):
        return True
    if tag.startswith('introduced-'):
        return True
    if tag.startswith('versioned='):
        return True
    return False


def decode_api_level_tags(tags, api_map):
    """Decodes API level code names in a list of tags.

    Raises:
        ParseError: An unknown version name was found in a tag.
    """
    for idx, tag in enumerate(tags):
        if not is_api_level_tag(tag):
            continue
        name, value = split_tag(tag)

        try:
            decoded = str(decode_api_level(value, api_map))
            tags[idx] = '='.join([name, decoded])
        except KeyError:
            raise ParseError('Unknown version name in tag: {}'.format(tag))
    return tags


def split_tag(tag):
    """Returns a key/value tuple of the tag.

    Raises:
        ValueError: Tag is not a key/value type tag.

    Returns: Tuple of (key, value) of the tag. Both components are strings.
    """
    if '=' not in tag:
        raise ValueError('Not a key/value tag: ' + tag)
    key, _, value = tag.partition('=')
    return key, value


def get_tag_value(tag):
    """Returns the value of a key/value tag.

    Raises:
        ValueError: Tag is not a key/value type tag.

    Returns: Value part of tag as a string.
    """
    return split_tag(tag)[1]


def version_is_private(version):
    """Returns True if the version name should be treated as private."""
    return version.endswith('_PRIVATE') or version.endswith('_PLATFORM')


def should_omit_version(name, tags, arch, api, vndk):
    """Returns True if the version section should be ommitted.

    We want to omit any sections that do not have any symbols we'll have in the
    stub library. Sections that contain entirely future symbols or only symbols
    for certain architectures.
    """
    if version_is_private(name):
        return True
    if 'platform-only' in tags:
        return True
    if 'vndk' in tags and not vndk:
        return True
    if not symbol_in_arch(tags, arch):
        return True
    if not symbol_in_api(tags, arch, api):
        return True
    return False


def symbol_in_arch(tags, arch):
    """Returns true if the symbol is present for the given architecture."""
    has_arch_tags = False
    for tag in tags:
        if tag == arch:
            return True
        if tag in ALL_ARCHITECTURES:
            has_arch_tags = True

    # If there were no arch tags, the symbol is available for all
    # architectures. If there were any arch tags, the symbol is only available
    # for the tagged architectures.
    return not has_arch_tags


def symbol_in_api(tags, arch, api):
    """Returns true if the symbol is present for the given API level."""
    introduced_tag = None
    arch_specific = False
    for tag in tags:
        # If there is an arch-specific tag, it should override the common one.
        if tag.startswith('introduced=') and not arch_specific:
            introduced_tag = tag
        elif tag.startswith('introduced-' + arch + '='):
            introduced_tag = tag
            arch_specific = True
        elif tag == 'future':
            return api == FUTURE_API_LEVEL

    if introduced_tag is None:
        # We found no "introduced" tags, so the symbol has always been
        # available.
        return True

    return api >= int(get_tag_value(introduced_tag))


def symbol_versioned_in_api(tags, api):
    """Returns true if the symbol should be versioned for the given API.

    This models the `versioned=API` tag. This should be a very uncommonly
    needed tag, and is really only needed to fix versioning mistakes that are
    already out in the wild.

    For example, some of libc's __aeabi_* functions were originally placed in
    the private version, but that was incorrect. They are now in LIBC_N, but
    when building against any version prior to N we need the symbol to be
    unversioned (otherwise it won't resolve on M where it is private).
    """
    for tag in tags:
        if tag.startswith('versioned='):
            return api >= int(get_tag_value(tag))
    # If there is no "versioned" tag, the tag has been versioned for as long as
    # it was introduced.
    return True


class ParseError(RuntimeError):
    """An error that occurred while parsing a symbol file."""
    pass


class Version(object):
    """A version block of a symbol file."""
    def __init__(self, name, base, tags, symbols):
        self.name = name
        self.base = base
        self.tags = tags
        self.symbols = symbols

    def __eq__(self, other):
        if self.name != other.name:
            return False
        if self.base != other.base:
            return False
        if self.tags != other.tags:
            return False
        if self.symbols != other.symbols:
            return False
        return True


class Symbol(object):
    """A symbol definition from a symbol file."""
    def __init__(self, name, tags):
        self.name = name
        self.tags = tags

    def __eq__(self, other):
        return self.name == other.name and set(self.tags) == set(other.tags)


class SymbolFileParser(object):
    """Parses NDK symbol files."""
    def __init__(self, input_file, api_map):
        self.input_file = input_file
        self.api_map = api_map
        self.current_line = None

    def parse(self):
        """Parses the symbol file and returns a list of Version objects."""
        versions = []
        while self.next_line() != '':
            if '{' in self.current_line:
                versions.append(self.parse_version())
            else:
                raise ParseError(
                    'Unexpected contents at top level: ' + self.current_line)
        return versions

    def parse_version(self):
        """Parses a single version section and returns a Version object."""
        name = self.current_line.split('{')[0].strip()
        tags = get_tags(self.current_line)
        tags = decode_api_level_tags(tags, self.api_map)
        symbols = []
        global_scope = True
        while self.next_line() != '':
            if '}' in self.current_line:
                # Line is something like '} BASE; # tags'. Both base and tags
                # are optional here.
                base = self.current_line.partition('}')[2]
                base = base.partition('#')[0].strip()
                if not base.endswith(';'):
                    raise ParseError(
                        'Unterminated version block (expected ;).')
                base = base.rstrip(';').rstrip()
                if base == '':
                    base = None
                return Version(name, base, tags, symbols)
            elif ':' in self.current_line:
                visibility = self.current_line.split(':')[0].strip()
                if visibility == 'local':
                    global_scope = False
                elif visibility == 'global':
                    global_scope = True
                else:
                    raise ParseError('Unknown visiblity label: ' + visibility)
            elif global_scope:
                symbols.append(self.parse_symbol())
            else:
                # We're in a hidden scope. Ignore everything.
                pass
        raise ParseError('Unexpected EOF in version block.')

    def parse_symbol(self):
        """Parses a single symbol line and returns a Symbol object."""
        if ';' not in self.current_line:
            raise ParseError(
                'Expected ; to terminate symbol: ' + self.current_line)
        if '*' in self.current_line:
            raise ParseError(
                'Wildcard global symbols are not permitted.')
        # Line is now in the format "<symbol-name>; # tags"
        name, _, _ = self.current_line.strip().partition(';')
        tags = get_tags(self.current_line)
        tags = decode_api_level_tags(tags, self.api_map)
        return Symbol(name, tags)

    def next_line(self):
        """Returns the next non-empty non-comment line.

        A return value of '' indicates EOF.
        """
        line = self.input_file.readline()
        while line.strip() == '' or line.strip().startswith('#'):
            line = self.input_file.readline()

            # We want to skip empty lines, but '' indicates EOF.
            if line == '':
                break
        self.current_line = line
        return self.current_line


class Generator(object):
    """Output generator that writes stub source files and version scripts."""
    def __init__(self, src_file, version_script, arch, api, vndk):
        self.src_file = src_file
        self.version_script = version_script
        self.arch = arch
        self.api = api
        self.vndk = vndk

    def write(self, versions):
        """Writes all symbol data to the output files."""
        for version in versions:
            self.write_version(version)

    def write_version(self, version):
        """Writes a single version block's data to the output files."""
        name = version.name
        tags = version.tags
        if should_omit_version(name, tags, self.arch, self.api, self.vndk):
            return

        section_versioned = symbol_versioned_in_api(tags, self.api)
        version_empty = True
        pruned_symbols = []
        for symbol in version.symbols:
            if not self.vndk and 'vndk' in symbol.tags:
                continue
            if not symbol_in_arch(symbol.tags, self.arch):
                continue
            if not symbol_in_api(symbol.tags, self.arch, self.api):
                continue

            if symbol_versioned_in_api(symbol.tags, self.api):
                version_empty = False
            pruned_symbols.append(symbol)

        if len(pruned_symbols) > 0:
            if not version_empty and section_versioned:
                self.version_script.write(version.name + ' {\n')
                self.version_script.write('    global:\n')
            for symbol in pruned_symbols:
                emit_version = symbol_versioned_in_api(symbol.tags, self.api)
                if section_versioned and emit_version:
                    self.version_script.write('        ' + symbol.name + ';\n')

                if 'var' in symbol.tags:
                    self.src_file.write('int {} = 0;\n'.format(symbol.name))
                else:
                    self.src_file.write('void {}() {{}}\n'.format(symbol.name))

            if not version_empty and section_versioned:
                base = '' if version.base is None else ' ' + version.base
                self.version_script.write('}' + base + ';\n')


def decode_api_level(api, api_map):
    """Decodes the API level argument into the API level number.

    For the average case, this just decodes the integer value from the string,
    but for unreleased APIs we need to translate from the API codename (like
    "O") to the future API level for that codename.
    """
    try:
        return int(api)
    except ValueError:
        pass

    if api == "current":
        return FUTURE_API_LEVEL

    return api_map[api]


def parse_args():
    """Parses and returns command line arguments."""
    parser = argparse.ArgumentParser()

    parser.add_argument('-v', '--verbose', action='count', default=0)

    parser.add_argument(
        '--api', required=True, help='API level being targeted.')
    parser.add_argument(
        '--arch', choices=ALL_ARCHITECTURES, required=True,
        help='Architecture being targeted.')
    parser.add_argument(
        '--vndk', action='store_true', help='Use the VNDK variant.')

    parser.add_argument(
        '--api-map', type=os.path.realpath, required=True,
        help='Path to the API level map JSON file.')

    parser.add_argument(
        'symbol_file', type=os.path.realpath, help='Path to symbol file.')
    parser.add_argument(
        'stub_src', type=os.path.realpath,
        help='Path to output stub source file.')
    parser.add_argument(
        'version_script', type=os.path.realpath,
        help='Path to output version script.')

    return parser.parse_args()


def main():
    """Program entry point."""
    args = parse_args()

    with open(args.api_map) as map_file:
        api_map = json.load(map_file)
    api = decode_api_level(args.api, api_map)

    verbose_map = (logging.WARNING, logging.INFO, logging.DEBUG)
    verbosity = args.verbose
    if verbosity > 2:
        verbosity = 2
    logging.basicConfig(level=verbose_map[verbosity])

    with open(args.symbol_file) as symbol_file:
        versions = SymbolFileParser(symbol_file, api_map).parse()

    with open(args.stub_src, 'w') as src_file:
        with open(args.version_script, 'w') as version_file:
            generator = Generator(src_file, version_file, args.arch, api,
                                  args.vndk)
            generator.write(versions)


if __name__ == '__main__':
    main()
