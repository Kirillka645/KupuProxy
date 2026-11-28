#!/usr/bin/env python3
"""Stage Compose artifacts consistently on every CI runner (stdlib only)."""
import argparse
import shutil
import tarfile
import zipfile
from pathlib import Path


def only(paths, description):
    matches = sorted(p for p in paths if p.exists())
    if len(matches) != 1:
        raise ValueError(f"Expected one {description}, found {len(matches)}: {matches}")
    return matches[0]


def stage(root, output, name, fmt, ext=None):
    root, output = Path(root), Path(output)
    output.mkdir(parents=True, exist_ok=True)
    if Path(name).name != name or name in ('', '.', '..'):
        raise ValueError('Artifact name must be a basename')
    if fmt in ('zip', 'targz'):
        # createDistributable: binaries/main/app/<packageName>, not binaries/main/.
        app = only((p for p in (root / 'binaries/main/app').glob('KupuProxy*') if p.is_dir()), 'portable image')
        launcher = app / ('KupuProxy.exe' if fmt == 'zip' else 'bin/KupuProxy')
        if not launcher.is_file() or launcher.stat().st_size == 0:
            raise ValueError(f'Missing or empty launcher: {launcher}')
        target = output / (name + ('.zip' if fmt == 'zip' else '.tar.gz'))
        if fmt == 'zip':
            with zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as archive:
                for path in sorted(app.rglob('*')):
                    archive.write(path, path.relative_to(app.parent))
            with zipfile.ZipFile(target) as archive:
                if archive.testzip() is not None:
                    raise ValueError('Corrupted ZIP archive')
        else:
            with tarfile.open(target, 'w:gz') as archive:
                archive.add(app, arcname=app.name)
            with tarfile.open(target, 'r:gz') as archive:
                archive.getmember('KupuProxy/bin/KupuProxy')
    else:
        source = only(
            (root / 'jars').glob('*.jar') if fmt == 'jar' else (root / 'binaries').rglob(f'*.{ext}'),
            fmt if fmt == 'jar' else f'.{ext} installer',
        )
        if source.stat().st_size == 0:
            raise ValueError(f'Empty artifact: {source}')
        target = output / (name + '.' + ('jar' if fmt == 'jar' else ext))
        shutil.copy2(source, target)
    return target


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', default='desktop/build/compose')
    parser.add_argument('--output', default='staged')
    parser.add_argument('--name', required=True)
    parser.add_argument('--format', choices=['zip', 'targz', 'jar', 'installer'], required=True)
    parser.add_argument('--ext', choices=['msi', 'deb', 'dmg'])
    args = parser.parse_args()
    if args.format == 'installer' and not args.ext:
        parser.error('--ext is required for an installer')
    print(stage(args.root, args.output, args.name, args.format, args.ext))
