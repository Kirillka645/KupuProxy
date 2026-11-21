import importlib.util
import tarfile
import tempfile
import unittest
import zipfile
from pathlib import Path

spec = importlib.util.spec_from_file_location('stage', Path(__file__).parents[1] / 'stage_desktop.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class StageTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / 'compose'
        self.out = Path(self.temp.name) / 'out'
        self.app = self.root / 'binaries/main/app/KupuProxy'

    def make(self, path, data=b'artifact'):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
        return path

    def test_windows_zip_has_no_build_path(self):
        self.make(self.app / 'KupuProxy.exe')
        self.make(self.app / 'app/KupuProxy.cfg')
        self.make(self.app / 'runtime/bin/java.exe')
        result = module.stage(self.root, self.out, 'portable', 'zip')
        with zipfile.ZipFile(result) as archive:
            self.assertIn('KupuProxy/KupuProxy.exe', archive.namelist())
            self.assertTrue(all(n.startswith('KupuProxy/') for n in archive.namelist()))
            self.assertIsNone(archive.testzip())

    def test_linux_tar_preserves_executable(self):
        launcher = self.make(self.app / 'bin/KupuProxy')
        launcher.chmod(0o755)
        result = module.stage(self.root, self.out, 'portable', 'targz')
        with tarfile.open(result) as archive:
            self.assertEqual(0o755, archive.getmember('KupuProxy/bin/KupuProxy').mode)

    def test_missing_image_is_error(self):
        with self.assertRaisesRegex(ValueError, 'portable image'):
            module.stage(self.root, self.out, 'portable', 'zip')

    def test_missing_launcher_is_error(self):
        self.app.mkdir(parents=True)
        with self.assertRaisesRegex(ValueError, 'launcher'):
            module.stage(self.root, self.out, 'portable', 'zip')

    def test_ambiguous_images_are_error(self):
        self.app.mkdir(parents=True)
        self.app.with_name('KupuProxy-old').mkdir()
        with self.assertRaisesRegex(ValueError, 'found 2'):
            module.stage(self.root, self.out, 'portable', 'zip')

    def test_installer_copies_only_requested_format(self):
        self.make(self.root / 'binaries/main/deb/kupu.deb', b'deb')
        self.make(self.root / 'binaries/main/dmg/kupu.dmg', b'dmg')
        result = module.stage(self.root, self.out, 'linux', 'installer', 'deb')
        self.assertEqual(b'deb', result.read_bytes())

    def test_empty_artifact_is_error(self):
        self.make(self.root / 'binaries/main/msi/kupu.msi', b'')
        with self.assertRaisesRegex(ValueError, 'Empty'):
            module.stage(self.root, self.out, 'windows', 'installer', 'msi')

    def test_jar(self):
        self.make(self.root / 'jars/kupu.jar', b'jar')
        self.assertEqual(b'jar', module.stage(self.root, self.out, 'windows', 'jar').read_bytes())

    def test_name_cannot_escape_output(self):
        with self.assertRaisesRegex(ValueError, 'basename'):
            module.stage(self.root, self.out, '../escape', 'zip')
