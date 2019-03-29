#!/usr/bin/env python

from __future__ import print_function

import os
import shutil
import tempfile
import unittest

from reverse_path import reverse_path

class TestReversePath(unittest.TestCase):
    def setUp(self):
        self.tmpdir = tempfile.mkdtemp()
        os.chdir(self.tmpdir)

    def tearDown(self):
        shutil.rmtree(self.tmpdir)

    def test_absolute(self):
        self.assertEqual(self.tmpdir, reverse_path('/out'))

    def test_relative(self):
        os.mkdir('a')
        os.mkdir('b')

        self.assertEqual('..', reverse_path('a'))

        os.chdir('a')
        self.assertEqual('a', reverse_path('..'))
        self.assertEqual('.', reverse_path('../a'))
        self.assertEqual('../a', reverse_path('../b'))

    def test_symlink(self):
        os.mkdir('b')
        os.symlink('b', 'a')
        os.mkdir('b/d')
        os.symlink('b/d', 'c')

        self.assertEqual('..', reverse_path('a'))
        self.assertEqual('..', reverse_path('b'))
        self.assertEqual(self.tmpdir, reverse_path('c'))
        self.assertEqual('../..', reverse_path('b/d'))


if __name__ == '__main__':
    unittest.main()
