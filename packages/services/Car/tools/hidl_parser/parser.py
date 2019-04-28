#!/usr/bin/env python3
#
# Copyright (C) 2017 The Android Open Source Project
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

# A parser for enum types defined in HIDL.
# This script can parse HIDL files and generate a parse tree.
# To use, import and call parse("path/to/file.hal")
# It will return a Python dictionary with three keys:
#  - header: an instance of Header
#  - enums: a dictionary of EnumDecl objects by name
#  - structs: a dictionary of StructDecl objects by name

# It requires 'ply' (Python Lex/Yacc).

from __future__ import print_function

import ply

tokens = ('package', 'import', 'enum', 'struct',
    'COLON', 'IDENTIFIER', 'COMMENT', 'NUMBER', 'HEX', 'OR', 'EQUALS',
    'LPAREN', 'RPAREN', 'LBRACE', 'RBRACE', 'DOT', 'SEMICOLON', 'VERSION',
    'COMMA', 'SHIFT', 'LESSTHAN', 'GREATERTHAN')

t_COLON = r':'
t_NUMBER = r'[0-9]+'
t_HEX = r'0x[0-9A-Fa-f]+'
t_OR = r'\|'
t_EQUALS = r'='
t_LPAREN = r'\('
t_RPAREN = r'\)'
t_SHIFT = r'<<'
t_LESSTHAN = r'<'
t_GREATERTHAN = r'>'

def t_COMMENT(t):
    r'(/\*(.|\n)*?\*/)|(//.*)'
    pass

t_LBRACE = r'{'
t_RBRACE = r'}'
t_DOT = r'\.'
t_SEMICOLON = r';'
t_VERSION = r'@[0-9].[0-9]'
t_COMMA = r','
t_ignore = ' \n\t'

def t_IDENTIFIER(t):
    r'[a-zA-Z_][a-zA-Z_0-9]*'
    if t.value == 'package':
        t.type = 'package'
    elif t.value == 'import':
        t.type = 'import'
    elif t.value == 'enum':
        t.type = 'enum'
    elif t.value == 'struct':
        t.type = 'struct'
    return t

def t_error(t):
    t.type = t.value[0]
    t.value = t.value[0]
    t.lexer.skip(1)
    return t

import ply.lex as lex
lexer = lex.lex()

class Typename(object):
    pass

class SimpleTypename(Typename):
    def __init__(self, name):
        self.name = name

    def __str__(self):
        return self.name

class GenericTypename(Typename):
    def __init__(self, name, arg):
        self.name = name
        self.arg = arg

    def __str__(self):
        return '%s<%s>' % (self.name, self.arg)

class EnumHeader(object):
    def __init__(self, name, base):
        self.name = name
        self.base = base

    def __str__(self):
        return '%s%s' % (self.name, ' %s' % self.base if self.base else '')

class StructHeader(object):
    def __init__(self, name):
        self.name = name

    def __str__(self):
        return 'struct %s' % self.name

class EnumDecl(object):
    def __init__(self, header, cases):
        self.header = header
        self.cases = cases
        self.fillInValues()

    def fillInValues(self):
        # if no cases, we're done
        if len(self.cases) < 1: return
        # then, if case 0 has no value, set it to 0
        if self.cases[0].value is None:
            self.cases[0].value = EnumValueConstant("0")
        # then for all other cases...
        for i in range(1,len(self.cases)):
            # ...if there's no value
            if self.cases[i].value is None:
                # set to previous case + 1
                self.cases[i].value = EnumValueSuccessor(
                    EnumValueLocalRef(self.cases[i-1].name))

    def __str__(self):
        return '%s {\n%s\n}' % (self.header,
            '\n'.join(str(x) for x in self.cases))

    def __repr__(self):
        return self.__str__()

class StructDecl(object):
    def __init__(self, header, items):
        self.header = header
        self.items = items

    def __str__(self):
        return '%s {\n%s\n}' % (self.header,
            '\n'.join(str(x) for x in self.items))

    def __repr__(self):
        return self.__str__()

class StructElement(object):
    pass

class StructElementIVar(StructElement):
    def __init__(self, typename, name):
        self.typename = typename
        self.name = name

    def __str__(self):
        return '%s %s' % (self.typename, self.name)

class StructElementStruct(StructElement):
    def __init__(self, struct):
        self.name = struct.header.name
        self.struct = struct

    def __str__(self):
        return self.struct.__str__()

class EnumCase(object):
    def __init__(self, name, value):
        self.name = name
        self.value = value

    def __str__(self):
        return '%s = %s' % (self.name, self.value)

class PackageID(object):
    def __init__(self, name, version):
        self.name = name
        self.version = version

    def __str__(self):
        return '%s%s' % (self.name, self.version)

class Package(object):
    def __init__(self, package):
        self.package = package

    def __str__(self):
        return 'package %s' % self.package

class Import(object):
    def __init__(self, package):
        self.package = package

    def __str__(self):
        return 'import %s' % self.package

class Header(object):
    def __init__(self, package, imports):
        self.package = package
        self.imports = imports

    def __str__(self):
        return str(self.package) + "\n" + \
            '\n'.join(str(x) for x in self.imports)

class EnumValue(object):
    def resolve(self, enum, document):
        pass

class EnumValueConstant(EnumValue):
    def __init__(self, value):
        self.value = value

    def __str__(self):
        return self.value

    def resolve(self, enum, document):
        if self.value.startswith("0x"):
            return int(self.value, 16)
        else:
            return int(self.value, 10)

class EnumValueSuccessor(EnumValue):
    def __init__(self, value):
        self.value = value

    def __str__(self):
        return '%s + 1' % self.value

    def resolve(self, enum, document):
        return self.value.resolve(enum, document) + 1

class EnumValueLocalRef(EnumValue):
    def __init__(self, ref):
        self.ref = ref

    def __str__(self):
        return self.ref

    def resolve(self, enum, document):
        for case in enum.cases:
            if case.name == self.ref: return case.value.resolve(enum, document)

class EnumValueLShift(EnumValue):
    def __init__(self, base, offset):
        self.base = base
        self.offset = offset

    def __str__(self):
        return '%s << %s' % (self.base, self.offset)

    def resolve(self, enum, document):
        base = self.base.resolve(enum, document)
        offset = self.offset.resolve(enum, document)
        return base << offset

class EnumValueOr(EnumValue):
    def __init__(self, param1, param2):
        self.param1 = param1
        self.param2 = param2

    def __str__(self):
        return '%s | %s' % (self.param1, self.param2)

    def resolve(self, enum, document):
        param1 = self.param1.resolve(enum, document)
        param2 = self.param2.resolve(enum, document)
        return param1 | param2

class EnumValueExternRef(EnumValue):
    def __init__(self, where, ref):
        self.where = where
        self.ref = ref

    def __str__(self):
        return '%s:%s' % (self.where, self.ref)

    def resolve(self, enum, document):
        enum = document['enums'][self.where]
        return EnumValueLocalRef(self.ref).resolve(enum, document)

# Error rule for syntax errors
def p_error(p):
    print("Syntax error in input: %s" % p)
    try:
        while True:
            print(p.lexer.next().value, end=' ')
    except:
        pass

def p_document(t):
    'document : header type_decls'
    enums = {}
    structs = {}
    for enum in t[2]:
        if not isinstance(enum, EnumDecl): continue
        enums[enum.header.name] = enum
    for struct in t[2]:
        if not isinstance(struct, StructDecl): continue
        structs[struct.header.name] = struct
    t[0] = {'header' : t[1], 'enums' : enums, 'structs' : structs}

def p_type_decls_1(t):
    'type_decls : type_decl'
    t[0] = [t[1]]
def p_type_decls_2(t):
    'type_decls : type_decls type_decl'
    t[0] = t[1] + [t[2]]

def p_type_decl_e(t):
    'type_decl : enum_decl'
    t[0] = t[1]
def p_type_decl_s(t):
    'type_decl : struct_decl'
    t[0] = t[1]

def p_enum_cases_1(t):
    'enum_cases : enum_case'
    t[0] = [t[1]]
def p_enum_cases_2(t):
    'enum_cases : enum_cases COMMA enum_case'
    t[0] = t[1] + [t[3]]

def p_struct_elements_1(t):
    'struct_elements : struct_element'
    t[0] = [t[1]]
def p_struct_elements_2(t):
    'struct_elements : struct_elements struct_element'
    t[0] = t[1] + [t[2]]

def p_enum_base_1(t):
    'enum_base : VERSION COLON COLON IDENTIFIER'
    t[0] = '%s::%s' % (t[1], t[4])
def p_enum_base_2(t):
    'enum_base : IDENTIFIER'
    t[0] = t[1]

def p_struct_header(t):
    'struct_header : struct IDENTIFIER'
    t[0] = StructHeader(t[2])

def p_enum_header_1(t):
    'enum_header : enum IDENTIFIER'
    t[0] = EnumHeader(t[2], None)
def p_enum_header_2(t):
    'enum_header : enum IDENTIFIER COLON enum_base'
    t[0] = EnumHeader(t[2], t[4])

def p_struct_decl(t):
    'struct_decl : struct_header LBRACE struct_elements RBRACE SEMICOLON'
    t[0] = StructDecl(t[1], t[3])

def p_enum_decl_1(t):
    'enum_decl : enum_header LBRACE enum_cases RBRACE SEMICOLON'
    t[0] = EnumDecl(t[1], t[3])
def p_enum_decl_2(t):
    'enum_decl : enum_header LBRACE enum_cases COMMA RBRACE SEMICOLON'
    t[0] = EnumDecl(t[1], t[3])

def p_enum_value_1(t):
    '''enum_value : NUMBER
                  | HEX'''
    t[0] = EnumValueConstant(t[1])
def p_enum_value_2(t):
    'enum_value : enum_value SHIFT NUMBER'
    t[0] = EnumValueLShift(t[1], EnumValueConstant(t[3]))
def p_enum_value_3(t):
    'enum_value : enum_value OR enum_value'
    t[0] = EnumValueOr(t[1], t[3])
def p_enum_value_4(t):
    'enum_value : LPAREN enum_value RPAREN'
    t[0] = t[2]
def p_enum_value_5(t):
    'enum_value : IDENTIFIER COLON IDENTIFIER'
    t[0] = EnumValueExternRef(t[1],t[3])
def p_enum_value_6(t):
    'enum_value : IDENTIFIER'
    t[0] = EnumValueLocalRef(t[1])

def p_typename_v(t):
    'typename : IDENTIFIER'
    t[0] = SimpleTypename(t[1])
def p_typename_g(t):
    'typename : IDENTIFIER LESSTHAN IDENTIFIER GREATERTHAN'
    t[0] = GenericTypename(t[1], t[3])

def p_struct_element_ivar(t):
    'struct_element : typename IDENTIFIER SEMICOLON'
    t[0] = StructElementIVar(t[1], t[2])

def p_struct_element_struct(t):
    'struct_element : struct_decl'
    t[0] = StructElementStruct(t[1])

def p_enum_case_v(t):
    'enum_case : IDENTIFIER EQUALS enum_value'
    t[0] = EnumCase(t[1], t[3])
def p_enum_case_b(t):
    'enum_case : IDENTIFIER'
    t[0] = EnumCase(t[1], None)

def p_header_1(t):
    'header : package_decl'
    t[0] = Header(t[1], [])

def p_header_2(t):
    'header : package_decl import_decls'
    t[0] = Header(t[1], t[2])

def p_import_decls_1(t):
    'import_decls : import_decl'
    t[0] = [t[1]]

def p_import_decls_2(t):
    'import_decls : import_decls import_decl'
    t[0] = t[1] + [t[2]]

def p_package_decl(t):
    'package_decl : package package_ID SEMICOLON'
    t[0] = Package(t[2])

def p_import_decl(t):
    'import_decl : import package_ID SEMICOLON'
    t[0] = Import(t[2])

def p_package_ID(t):
    'package_ID : dotted_identifier VERSION'
    t[0] = PackageID(t[1], t[2])

def p_dotted_identifier_1(t):
    'dotted_identifier : IDENTIFIER'
    t[0] = t[1]
def p_dotted_identifier_2(t):
    'dotted_identifier : dotted_identifier DOT IDENTIFIER'
    t[0] = t[1] + '.' + t[3]

class SilentLogger(object):
    def warning(*args):
        pass

import ply.yacc as yacc
parser = yacc.yacc(debug=False, write_tables=False, errorlog=SilentLogger())
import sys

def parse(filename):
    return parser.parse(open(filename, 'r').read())
