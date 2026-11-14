import importlib.util
import hashlib
import json
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


def load(name):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).parents[1] / (name+'.py'))
    module = importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    return module

apk = load('verify_apk')
release = load('verify_release')


class SigningTests(unittest.TestCase):
    def test_fingerprint_accepts_colons_and_uppercase(self):
        self.assertEqual('ab'*32, apk.fingerprint(':'.join(['AB']*32)))

    def test_missing_or_bad_fingerprint_rejected(self):
        for value in ['', 'debug', 'g'*64, 'a'*63]:
            with self.subTest(value=value), self.assertRaises(ValueError):
                apk.fingerprint(value)

    @patch.object(apk.subprocess, 'run')
    def test_matching_single_signer_is_accepted(self, run):
        run.return_value.stdout = 'Signer #1 certificate SHA-256 digest: '+'a'*64+'\n'
        apk.verify('apksigner', 'release.apk', 'a'*64)
        self.assertTrue(run.call_args.kwargs['check'])

    @patch.object(apk.subprocess, 'run')
    def test_wrong_or_multiple_signers_are_rejected(self, run):
        for output in ['', 'Signer #1 certificate SHA-256 digest: '+'b'*64+'\n',
                       'Signer #1 certificate SHA-256 digest: '+'a'*64+'\nSigner #2 certificate SHA-256 digest: '+'b'*64+'\n']:
            run.return_value.stdout = output
            with self.assertRaises(ValueError):
                apk.verify('apksigner', 'release.apk', 'a'*64)

    @patch.object(apk.subprocess, 'run')
    def test_invalid_apk_signature_cannot_pass(self, run):
        run.side_effect = subprocess.CalledProcessError(1, ['apksigner'])
        with self.assertRaises(subprocess.CalledProcessError):
            apk.verify('apksigner', 'release.apk', 'a'*64)


class ReleaseTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.version = '1.4.0.4'
        self.apk = 'KupuProxy-v'+self.version+'.apk'
        prefix = 'KupuProxy-Desktop-'+self.version
        for suffix in ['windows-x64.zip','windows-x64.msi','windows-x64.jar','linux-x64.deb','linux-x64.tar.gz','macos.dmg']:
            (self.root/(prefix+'-'+suffix)).write_bytes(b'artifact')
        data = b'signed apk fixture'
        (self.root/self.apk).write_bytes(data)
        sha = hashlib.sha256(data).hexdigest()
        (self.root/(self.apk+'.sha256')).write_text(sha+'  '+self.apk+'\n')
        url = f'https://github.com/Kirillka645/KupuProxy/releases/download/v{self.version}/{self.apk}'
        self.manifest = dict(version=self.version,tag_name='v'+self.version,apk_name=self.apk,
                             apk_size=len(data),sha256=sha,apk_url=url,sha256_url=url+'.sha256')
        self.write_manifest()

    def write_manifest(self):
        (self.root/'update_manifest.json').write_text(json.dumps(self.manifest))

    def test_complete_release(self):
        release.verify(self.root, self.version)

    def test_missing_msi_blocks_release(self):
        next(self.root.glob('*.msi')).unlink()
        with self.assertRaisesRegex(ValueError, 'msi'):
            release.verify(self.root, self.version)

    def test_incorrect_manifest_blocks_release(self):
        for field, value in [('sha256','0'*64),('apk_size',0),('version','1.3'),('apk_url','https://example.org/a.apk')]:
            with self.subTest(field=field):
                old = self.manifest[field];self.manifest[field]=value;self.write_manifest()
                with self.assertRaisesRegex(ValueError, field): release.verify(self.root, self.version)
                self.manifest[field]=old

    def test_corrupted_checksum_blocks_release(self):
        (self.root/(self.apk+'.sha256')).write_text('0'*64+'  '+self.apk)
        with self.assertRaisesRegex(ValueError,'checksum'): release.verify(self.root, self.version)

    def test_empty_artifact_blocks_release(self):
        next(self.root.glob('*.dmg')).write_bytes(b'')
        with self.assertRaisesRegex(ValueError,'dmg'): release.verify(self.root, self.version)
