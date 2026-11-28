#!/usr/bin/env python3
"""Check exact required release files and verify APK manifest/checksum consistency."""
import hashlib
import json
import os
import sys
from pathlib import Path


def verify(root, version):
    root = Path(root)
    apk_name = f'KupuProxy-v{version}.apk'
    prefix = f'KupuProxy-Desktop-{version}'
    required = [apk_name, apk_name+'.sha256', 'update_manifest.json',
                prefix+'-windows-x64.zip', prefix+'-windows-x64.msi', prefix+'-windows-x64.jar',
                prefix+'-linux-x64.deb', prefix+'-linux-x64.tar.gz', prefix+'-macos.dmg']
    missing = [name for name in required if not (root/name).is_file() or (root/name).stat().st_size == 0]
    if missing:
        raise ValueError('Missing/empty release artifacts: '+', '.join(missing))
    apk = root / apk_name
    sha = hashlib.sha256(apk.read_bytes()).hexdigest()
    manifest = json.loads((root/'update_manifest.json').read_text())
    tag = 'v'+version
    url = f'https://github.com/Kirillka645/KupuProxy/releases/download/{tag}/{apk_name}'
    expected = dict(version=version, tag_name=tag, apk_name=apk_name, apk_size=apk.stat().st_size,
                    sha256=sha, apk_url=url, sha256_url=url+'.sha256')
    for key, value in expected.items():
        if manifest.get(key) != value:
            raise ValueError(f'Manifest {key} does not match the built APK/tag')
    if (root/(apk_name+'.sha256')).read_text().strip() != f'{sha}  {apk_name}':
        raise ValueError('APK checksum file does not match the built APK')
    print('All nine required release files and APK manifest verified.')


if __name__ == '__main__':
    verify(sys.argv[1], os.environ['VERSION'])
