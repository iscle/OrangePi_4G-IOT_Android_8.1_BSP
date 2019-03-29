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
"""Tests for gen_stub_libs.py."""
import cStringIO
import textwrap
import unittest

import gen_stub_libs as gsl


# pylint: disable=missing-docstring


class DecodeApiLevelTest(unittest.TestCase):
    def test_decode_api_level(self):
        self.assertEqual(9, gsl.decode_api_level('9', {}))
        self.assertEqual(9000, gsl.decode_api_level('O', {'O': 9000}))

        with self.assertRaises(KeyError):
            gsl.decode_api_level('O', {})


class TagsTest(unittest.TestCase):
    def test_get_tags_no_tags(self):
        self.assertEqual([], gsl.get_tags(''))
        self.assertEqual([], gsl.get_tags('foo bar baz'))

    def test_get_tags(self):
        self.assertEqual(['foo', 'bar'], gsl.get_tags('# foo bar'))
        self.assertEqual(['bar', 'baz'], gsl.get_tags('foo # bar baz'))

    def test_split_tag(self):
        self.assertTupleEqual(('foo', 'bar'), gsl.split_tag('foo=bar'))
        self.assertTupleEqual(('foo', 'bar=baz'), gsl.split_tag('foo=bar=baz'))
        with self.assertRaises(ValueError):
            gsl.split_tag('foo')

    def test_get_tag_value(self):
        self.assertEqual('bar', gsl.get_tag_value('foo=bar'))
        self.assertEqual('bar=baz', gsl.get_tag_value('foo=bar=baz'))
        with self.assertRaises(ValueError):
            gsl.get_tag_value('foo')

    def test_is_api_level_tag(self):
        self.assertTrue(gsl.is_api_level_tag('introduced=24'))
        self.assertTrue(gsl.is_api_level_tag('introduced-arm=24'))
        self.assertTrue(gsl.is_api_level_tag('versioned=24'))

        # Shouldn't try to process things that aren't a key/value tag.
        self.assertFalse(gsl.is_api_level_tag('arm'))
        self.assertFalse(gsl.is_api_level_tag('introduced'))
        self.assertFalse(gsl.is_api_level_tag('versioned'))

        # We don't support arch specific `versioned` tags.
        self.assertFalse(gsl.is_api_level_tag('versioned-arm=24'))

    def test_decode_api_level_tags(self):
        api_map = {
            'O': 9000,
            'P': 9001,
        }

        tags = [
            'introduced=9',
            'introduced-arm=14',
            'versioned=16',
            'arm',
            'introduced=O',
            'introduced=P',
        ]
        expected_tags = [
            'introduced=9',
            'introduced-arm=14',
            'versioned=16',
            'arm',
            'introduced=9000',
            'introduced=9001',
        ]
        self.assertListEqual(
            expected_tags, gsl.decode_api_level_tags(tags, api_map))

        with self.assertRaises(gsl.ParseError):
            gsl.decode_api_level_tags(['introduced=O'], {})


class PrivateVersionTest(unittest.TestCase):
    def test_version_is_private(self):
        self.assertFalse(gsl.version_is_private('foo'))
        self.assertFalse(gsl.version_is_private('PRIVATE'))
        self.assertFalse(gsl.version_is_private('PLATFORM'))
        self.assertFalse(gsl.version_is_private('foo_private'))
        self.assertFalse(gsl.version_is_private('foo_platform'))
        self.assertFalse(gsl.version_is_private('foo_PRIVATE_'))
        self.assertFalse(gsl.version_is_private('foo_PLATFORM_'))

        self.assertTrue(gsl.version_is_private('foo_PRIVATE'))
        self.assertTrue(gsl.version_is_private('foo_PLATFORM'))


class SymbolPresenceTest(unittest.TestCase):
    def test_symbol_in_arch(self):
        self.assertTrue(gsl.symbol_in_arch([], 'arm'))
        self.assertTrue(gsl.symbol_in_arch(['arm'], 'arm'))

        self.assertFalse(gsl.symbol_in_arch(['x86'], 'arm'))

    def test_symbol_in_api(self):
        self.assertTrue(gsl.symbol_in_api([], 'arm', 9))
        self.assertTrue(gsl.symbol_in_api(['introduced=9'], 'arm', 9))
        self.assertTrue(gsl.symbol_in_api(['introduced=9'], 'arm', 14))
        self.assertTrue(gsl.symbol_in_api(['introduced-arm=9'], 'arm', 14))
        self.assertTrue(gsl.symbol_in_api(['introduced-arm=9'], 'arm', 14))
        self.assertTrue(gsl.symbol_in_api(['introduced-x86=14'], 'arm', 9))
        self.assertTrue(gsl.symbol_in_api(
            ['introduced-arm=9', 'introduced-x86=21'], 'arm', 14))
        self.assertTrue(gsl.symbol_in_api(
            ['introduced=9', 'introduced-x86=21'], 'arm', 14))
        self.assertTrue(gsl.symbol_in_api(
            ['introduced=21', 'introduced-arm=9'], 'arm', 14))
        self.assertTrue(gsl.symbol_in_api(
            ['future'], 'arm', gsl.FUTURE_API_LEVEL))

        self.assertFalse(gsl.symbol_in_api(['introduced=14'], 'arm', 9))
        self.assertFalse(gsl.symbol_in_api(['introduced-arm=14'], 'arm', 9))
        self.assertFalse(gsl.symbol_in_api(['future'], 'arm', 9))
        self.assertFalse(gsl.symbol_in_api(
            ['introduced=9', 'future'], 'arm', 14))
        self.assertFalse(gsl.symbol_in_api(
            ['introduced-arm=9', 'future'], 'arm', 14))
        self.assertFalse(gsl.symbol_in_api(
            ['introduced-arm=21', 'introduced-x86=9'], 'arm', 14))
        self.assertFalse(gsl.symbol_in_api(
            ['introduced=9', 'introduced-arm=21'], 'arm', 14))
        self.assertFalse(gsl.symbol_in_api(
            ['introduced=21', 'introduced-x86=9'], 'arm', 14))

        # Interesting edge case: this symbol should be omitted from the
        # library, but this call should still return true because none of the
        # tags indiciate that it's not present in this API level.
        self.assertTrue(gsl.symbol_in_api(['x86'], 'arm', 9))

    def test_verioned_in_api(self):
        self.assertTrue(gsl.symbol_versioned_in_api([], 9))
        self.assertTrue(gsl.symbol_versioned_in_api(['versioned=9'], 9))
        self.assertTrue(gsl.symbol_versioned_in_api(['versioned=9'], 14))

        self.assertFalse(gsl.symbol_versioned_in_api(['versioned=14'], 9))


class OmitVersionTest(unittest.TestCase):
    def test_omit_private(self):
        self.assertFalse(gsl.should_omit_version('foo', [], 'arm', 9, False))

        self.assertTrue(gsl.should_omit_version(
            'foo_PRIVATE', [], 'arm', 9, False))
        self.assertTrue(gsl.should_omit_version(
            'foo_PLATFORM', [], 'arm', 9, False))

        self.assertTrue(gsl.should_omit_version(
            'foo', ['platform-only'], 'arm', 9, False))

    def test_omit_vndk(self):
        self.assertTrue(gsl.should_omit_version(
            'foo', ['vndk'], 'arm', 9, False))

        self.assertFalse(gsl.should_omit_version('foo', [], 'arm', 9, True))
        self.assertFalse(gsl.should_omit_version(
            'foo', ['vndk'], 'arm', 9, True))

    def test_omit_arch(self):
        self.assertFalse(gsl.should_omit_version('foo', [], 'arm', 9, False))
        self.assertFalse(gsl.should_omit_version(
            'foo', ['arm'], 'arm', 9, False))

        self.assertTrue(gsl.should_omit_version(
            'foo', ['x86'], 'arm', 9, False))

    def test_omit_api(self):
        self.assertFalse(gsl.should_omit_version('foo', [], 'arm', 9, False))
        self.assertFalse(
            gsl.should_omit_version('foo', ['introduced=9'], 'arm', 9, False))

        self.assertTrue(
            gsl.should_omit_version('foo', ['introduced=14'], 'arm', 9, False))


class SymbolFileParseTest(unittest.TestCase):
    def test_next_line(self):
        input_file = cStringIO.StringIO(textwrap.dedent("""\
            foo

            bar
            # baz
            qux
        """))
        parser = gsl.SymbolFileParser(input_file, {})
        self.assertIsNone(parser.current_line)

        self.assertEqual('foo', parser.next_line().strip())
        self.assertEqual('foo', parser.current_line.strip())

        self.assertEqual('bar', parser.next_line().strip())
        self.assertEqual('bar', parser.current_line.strip())

        self.assertEqual('qux', parser.next_line().strip())
        self.assertEqual('qux', parser.current_line.strip())

        self.assertEqual('', parser.next_line())
        self.assertEqual('', parser.current_line)

    def test_parse_version(self):
        input_file = cStringIO.StringIO(textwrap.dedent("""\
            VERSION_1 { # foo bar
                baz;
                qux; # woodly doodly
            };

            VERSION_2 {
            } VERSION_1; # asdf
        """))
        parser = gsl.SymbolFileParser(input_file, {})

        parser.next_line()
        version = parser.parse_version()
        self.assertEqual('VERSION_1', version.name)
        self.assertIsNone(version.base)
        self.assertEqual(['foo', 'bar'], version.tags)

        expected_symbols = [
            gsl.Symbol('baz', []),
            gsl.Symbol('qux', ['woodly', 'doodly']),
        ]
        self.assertEqual(expected_symbols, version.symbols)

        parser.next_line()
        version = parser.parse_version()
        self.assertEqual('VERSION_2', version.name)
        self.assertEqual('VERSION_1', version.base)
        self.assertEqual([], version.tags)

    def test_parse_version_eof(self):
        input_file = cStringIO.StringIO(textwrap.dedent("""\
            VERSION_1 {
        """))
        parser = gsl.SymbolFileParser(input_file, {})
        parser.next_line()
        with self.assertRaises(gsl.ParseError):
            parser.parse_version()

    def test_unknown_scope_label(self):
        input_file = cStringIO.StringIO(textwrap.dedent("""\
            VERSION_1 {
                foo:
            }
        """))
        parser = gsl.SymbolFileParser(input_file, {})
        parser.next_line()
        with self.assertRaises(gsl.ParseError):
            parser.parse_version()

    def test_parse_symbol(self):
        input_file = cStringIO.StringIO(textwrap.dedent("""\
            foo;
            bar; # baz qux
        """))
        parser = gsl.SymbolFileParser(input_file, {})

        parser.next_line()
        symbol = parser.parse_symbol()
        self.assertEqual('foo', symbol.name)
        self.assertEqual([], symbol.tags)

        parser.next_line()
        symbol = parser.parse_symbol()
        self.assertEqual('bar', symbol.name)
        self.assertEqual(['baz', 'qux'], symbol.tags)

    def test_wildcard_symbol_global(self):
        input_file = cStringIO.StringIO(textwrap.dedent("""\
            VERSION_1 {
                *;
            };
        """))
        parser = gsl.SymbolFileParser(input_file, {})
        parser.next_line()
        with self.assertRaises(gsl.ParseError):
            parser.parse_version()

    def test_wildcard_symbol_local(self):
        input_file = cStringIO.StringIO(textwrap.dedent("""\
            VERSION_1 {
                local:
                    *;
            };
        """))
        parser = gsl.SymbolFileParser(input_file, {})
        parser.next_line()
        version = parser.parse_version()
        self.assertEqual([], version.symbols)

    def test_missing_semicolon(self):
        input_file = cStringIO.StringIO(textwrap.dedent("""\
            VERSION_1 {
                foo
            };
        """))
        parser = gsl.SymbolFileParser(input_file, {})
        parser.next_line()
        with self.assertRaises(gsl.ParseError):
            parser.parse_version()

    def test_parse_fails_invalid_input(self):
        with self.assertRaises(gsl.ParseError):
            input_file = cStringIO.StringIO('foo')
            parser = gsl.SymbolFileParser(input_file, {})
            parser.parse()

    def test_parse(self):
        input_file = cStringIO.StringIO(textwrap.dedent("""\
            VERSION_1 {
                local:
                    hidden1;
                global:
                    foo;
                    bar; # baz
            };

            VERSION_2 { # wasd
                # Implicit global scope.
                    woodly;
                    doodly; # asdf
                local:
                    qwerty;
            } VERSION_1;
        """))
        parser = gsl.SymbolFileParser(input_file, {})
        versions = parser.parse()

        expected = [
            gsl.Version('VERSION_1', None, [], [
                gsl.Symbol('foo', []),
                gsl.Symbol('bar', ['baz']),
            ]),
            gsl.Version('VERSION_2', 'VERSION_1', ['wasd'], [
                gsl.Symbol('woodly', []),
                gsl.Symbol('doodly', ['asdf']),
            ]),
        ]

        self.assertEqual(expected, versions)


class GeneratorTest(unittest.TestCase):
    def test_omit_version(self):
        # Thorough testing of the cases involved here is handled by
        # OmitVersionTest, PrivateVersionTest, and SymbolPresenceTest.
        src_file = cStringIO.StringIO()
        version_file = cStringIO.StringIO()
        generator = gsl.Generator(src_file, version_file, 'arm', 9, False)

        version = gsl.Version('VERSION_PRIVATE', None, [], [
            gsl.Symbol('foo', []),
        ])
        generator.write_version(version)
        self.assertEqual('', src_file.getvalue())
        self.assertEqual('', version_file.getvalue())

        version = gsl.Version('VERSION', None, ['x86'], [
            gsl.Symbol('foo', []),
        ])
        generator.write_version(version)
        self.assertEqual('', src_file.getvalue())
        self.assertEqual('', version_file.getvalue())

        version = gsl.Version('VERSION', None, ['introduced=14'], [
            gsl.Symbol('foo', []),
        ])
        generator.write_version(version)
        self.assertEqual('', src_file.getvalue())
        self.assertEqual('', version_file.getvalue())

    def test_omit_symbol(self):
        # Thorough testing of the cases involved here is handled by
        # SymbolPresenceTest.
        src_file = cStringIO.StringIO()
        version_file = cStringIO.StringIO()
        generator = gsl.Generator(src_file, version_file, 'arm', 9, False)

        version = gsl.Version('VERSION_1', None, [], [
            gsl.Symbol('foo', ['x86']),
        ])
        generator.write_version(version)
        self.assertEqual('', src_file.getvalue())
        self.assertEqual('', version_file.getvalue())

        version = gsl.Version('VERSION_1', None, [], [
            gsl.Symbol('foo', ['introduced=14']),
        ])
        generator.write_version(version)
        self.assertEqual('', src_file.getvalue())
        self.assertEqual('', version_file.getvalue())

        version = gsl.Version('VERSION_1', None, [], [
            gsl.Symbol('foo', ['vndk']),
        ])
        generator.write_version(version)
        self.assertEqual('', src_file.getvalue())
        self.assertEqual('', version_file.getvalue())

    def test_write(self):
        src_file = cStringIO.StringIO()
        version_file = cStringIO.StringIO()
        generator = gsl.Generator(src_file, version_file, 'arm', 9, False)

        versions = [
            gsl.Version('VERSION_1', None, [], [
                gsl.Symbol('foo', []),
                gsl.Symbol('bar', ['var']),
            ]),
            gsl.Version('VERSION_2', 'VERSION_1', [], [
                gsl.Symbol('baz', []),
            ]),
            gsl.Version('VERSION_3', 'VERSION_1', [], [
                gsl.Symbol('qux', ['versioned=14']),
            ]),
        ]

        generator.write(versions)
        expected_src = textwrap.dedent("""\
            void foo() {}
            int bar = 0;
            void baz() {}
            void qux() {}
        """)
        self.assertEqual(expected_src, src_file.getvalue())

        expected_version = textwrap.dedent("""\
            VERSION_1 {
                global:
                    foo;
                    bar;
            };
            VERSION_2 {
                global:
                    baz;
            } VERSION_1;
        """)
        self.assertEqual(expected_version, version_file.getvalue())


class IntegrationTest(unittest.TestCase):
    def test_integration(self):
        api_map = {
            'O': 9000,
            'P': 9001,
        }

        input_file = cStringIO.StringIO(textwrap.dedent("""\
            VERSION_1 {
                global:
                    foo; # var
                    bar; # x86
                    fizz; # introduced=O
                    buzz; # introduced=P
                local:
                    *;
            };

            VERSION_2 { # arm
                baz; # introduced=9
                qux; # versioned=14
            } VERSION_1;

            VERSION_3 { # introduced=14
                woodly;
                doodly; # var
            } VERSION_2;

            VERSION_4 { # versioned=9
                wibble;
                wizzes; # vndk
            } VERSION_2;

            VERSION_5 { # versioned=14
                wobble;
            } VERSION_4;
        """))
        parser = gsl.SymbolFileParser(input_file, api_map)
        versions = parser.parse()

        src_file = cStringIO.StringIO()
        version_file = cStringIO.StringIO()
        generator = gsl.Generator(src_file, version_file, 'arm', 9, False)
        generator.write(versions)

        expected_src = textwrap.dedent("""\
            int foo = 0;
            void baz() {}
            void qux() {}
            void wibble() {}
            void wobble() {}
        """)
        self.assertEqual(expected_src, src_file.getvalue())

        expected_version = textwrap.dedent("""\
            VERSION_1 {
                global:
                    foo;
            };
            VERSION_2 {
                global:
                    baz;
            } VERSION_1;
            VERSION_4 {
                global:
                    wibble;
            } VERSION_2;
        """)
        self.assertEqual(expected_version, version_file.getvalue())

    def test_integration_future_api(self):
        api_map = {
            'O': 9000,
            'P': 9001,
            'Q': 9002,
        }

        input_file = cStringIO.StringIO(textwrap.dedent("""\
            VERSION_1 {
                global:
                    foo; # introduced=O
                    bar; # introduced=P
                    baz; # introduced=Q
                local:
                    *;
            };
        """))
        parser = gsl.SymbolFileParser(input_file, api_map)
        versions = parser.parse()

        src_file = cStringIO.StringIO()
        version_file = cStringIO.StringIO()
        generator = gsl.Generator(src_file, version_file, 'arm', 9001, False)
        generator.write(versions)

        expected_src = textwrap.dedent("""\
            void foo() {}
            void bar() {}
        """)
        self.assertEqual(expected_src, src_file.getvalue())

        expected_version = textwrap.dedent("""\
            VERSION_1 {
                global:
                    foo;
                    bar;
            };
        """)
        self.assertEqual(expected_version, version_file.getvalue())


def main():
    suite = unittest.TestLoader().loadTestsFromName(__name__)
    unittest.TextTestRunner(verbosity=3).run(suite)


if __name__ == '__main__':
    main()
