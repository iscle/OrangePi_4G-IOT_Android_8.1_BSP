#!/usr/bin/env python
#
# Copyright (C) 2016 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the 'License');
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an 'AS IS' BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
import random

from OpenSSL import crypto
from Crypto.PublicKey import RSA

class Certificate(object):
    cert = None
    key = None
    def __init__(self, cert, key):
        self.cert = cert
        self.key = key

    def cert_pem(self):
        return crypto.dump_certificate(crypto.FILETYPE_PEM, self.cert)

    def key_pem(self):
        return crypto.dump_privatekey(crypto.FILETYPE_PEM, self.key)

    def save_to_file(self, path):
        with open(path, "w") as f:
            f.write(self.cert_pem())
            f.write(self.key_pem())

    def save_cert_to_file(self, path):
        with open(path, "w") as f:
            f.write(self.cert_pem())

    @staticmethod
    def from_file(path):
        with open(path) as f:
            data = f.read()
            cert = crypto.load_certificate(crypto.FILETYPE_PEM, data)
            key = crypto.load_privatekey(crypto.FILETYPE_PEM, data)
            return Certificate(cert, key)

    @staticmethod
    def create(cn, issuer=None, key=None, keysize=2048, digest="sha256",
            notBefore="20150101000000+0000", notAfter="20300101000000+0000",
            additional_extensions=None):
        if key is None:
            key = crypto.PKey()
            key.generate_key(crypto.TYPE_RSA, keysize)

        cert = crypto.X509()
        cert.set_pubkey(key)
        cert.set_version(2)
        cert.set_serial_number(random.randint(0, 2**20))

        cert.set_notBefore(notBefore)
        cert.set_notAfter(notAfter)
        cert.get_subject().CN = cn
        cert.set_issuer(cert.get_subject() if issuer is None else issuer.cert.get_subject())
        # Add the CA=True basic constraint
        basicContraints = crypto.X509Extension("basicConstraints", True, "CA:TRUE")
        cert.add_extensions([basicContraints])
        if additional_extensions is not None:
            cert.add_extensions(additional_extensions)

        signing_key = key if issuer is None else issuer.key
        cert.sign(signing_key, digest)

        return Certificate(cert, key)

if __name__ == "__main__":
    # Generate test certificates.
    a = Certificate.create("Root A")
    a_sha1 = Certificate.create("Root A", key=a.key, digest="sha1")
    b = Certificate.create("Root B")
    a_to_b = Certificate.create("Root A", b, a.key)
    b_to_a = Certificate.create("Root B", a, b.key)
    leaf1 = Certificate.create("Leaf", a)
    intermediate_a = Certificate.create("intermediate", a)
    intermediate_b = Certificate.create("intermediate", b, intermediate_a.key)
    leaf2 = Certificate.create("Leaf 2", intermediate_a)

    # Save test certificates.
    a.save_cert_to_file("a.pem")
    a_sha1.save_cert_to_file("a_sha1.pem")
    b.save_cert_to_file("b.pem")
    a_to_b.save_cert_to_file("a_to_b.pem")
    b_to_a.save_cert_to_file("b_to_a.pem")
    leaf1.save_cert_to_file("leaf1.pem")
    leaf2.save_cert_to_file("leaf2.pem")
    intermediate_a.save_cert_to_file("intermediate_a.pem")
    intermediate_b.save_cert_to_file("intermediate_b.pem")
