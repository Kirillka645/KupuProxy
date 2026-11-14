#!/usr/bin/env python3
"""Fail closed unless apksigner verifies the APK and its expected certificate."""
import argparse
import re
import subprocess


def fingerprint(value):
    result = value.replace(':', '').strip().lower()
    if not re.fullmatch(r'[0-9a-f]{64}', result):
        raise ValueError('Expected a 64-digit SHA-256 certificate fingerprint')
    return result


def verify(apksigner, apk, expected):
    expected = fingerprint(expected)
    result = subprocess.run([apksigner, 'verify', '--verbose', '--print-certs', apk],
                            check=True, capture_output=True, text=True)
    certs = re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)$', result.stdout, re.MULTILINE)
    if len(certs) != 1 or fingerprint(certs[0]) != expected:
        raise ValueError('APK signing certificate does not match the pinned release certificate')
    print('APK signature verified; certificate matches the pinned release certificate.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apksigner', required=True)
    parser.add_argument('--apk', required=True)
    parser.add_argument('--expected', required=True)
    args = parser.parse_args()
    verify(args.apksigner, args.apk, args.expected)
