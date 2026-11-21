#!/usr/bin/env python3
"""Launch the packaged application without UI and verify its actual version."""
import argparse
import platform
import subprocess
from pathlib import Path
from stage_desktop import only

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--version', required=True)
    parser.add_argument('--root', default='desktop/build/compose/binaries/main/app')
    args = parser.parse_args()
    app = only(Path(args.root).glob('KupuProxy*'), 'app image')
    if platform.system() == 'Windows':
        launcher = app / 'KupuProxy.exe'
    elif platform.system() == 'Darwin':
        launcher = app / 'Contents/MacOS/KupuProxy'
    else:
        launcher = app / 'bin/KupuProxy'
    result = subprocess.run([str(launcher.resolve()), '--version'], check=True, text=True, capture_output=True, timeout=30)
    expected = f'KupuProxy {args.version}'
    if result.stdout.strip() != expected:
        raise SystemExit(f'Unexpected version output: {result.stdout!r}; expected {expected!r}; stderr={result.stderr!r}')
    print(expected + ' — packaged launcher OK')
