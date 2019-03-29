#!/bin/sh

# Outputs the provided certificate (PEM or DER) in a format used by CTS tests.
# The format is PEM block, followed by the textual representation of the
# certificate, followed by the SHA-1 fingerprint.

# OpenSSL binary built from this Android source
OPENSSL="$ANDROID_HOST_OUT/bin/openssl"
if [ "$ANDROID_HOST_OUT" == "" ]; then
  echo "Android build environment not set up"
  echo
  echo "Run the following from the root of the Android source tree:"
  echo "  . build/envsetup.sh && lunch"
  exit 1
fi
if [ ! -f "$OPENSSL" ]; then
  echo "openssl binary not found"
  echo
  echo "Run 'mmm external/openssl' or 'make openssl' from the root of the" \
      "Android source tree to build it."
  exit 1
fi

# Input file containing the certificate in PEM or DER format
in_file="$1"

# Output file. If not specified, the file will be named <hash>.0 where "hash"
# is the certificate's subject hash produced by:
#   openssl x509 -in cert_file -subject_hash_old -noout
out_file="$2"

# Detect whether the input file is PEM or DER.
# It must use old_hash(MD5) function.
in_form="pem"
subject_hash=$("$OPENSSL" x509 -in "$in_file" -inform $in_form -subject_hash_old \
    -noout 2>/dev/null)
if [ "$?" != "0" ]; then
  in_form="der"
  subject_hash=$("$OPENSSL" x509 -in "$in_file" -inform $in_form -subject_hash_old \
      -noout)
  if [ "$?" != "0" ]; then
    echo "Certificate file format is neither PEM nor DER"
    exit 1
  fi
fi

# Name the output file <hash>.0 if the name is not specified explicitly.
if [ "$out_file" == "" ]; then
  out_file="$subject_hash.0"
  echo "Auto-generated output file name: $out_file"
fi

# Output the certificate in the target format
"$OPENSSL" x509 -in "$in_file" -inform $in_form -outform pem > "$out_file" && \
"$OPENSSL" x509 -in "$in_file" -inform $in_form -noout -text -fingerprint \
    >> "$out_file"
