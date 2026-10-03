import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
import zipfile

SCRIPT = Path(__file__).resolve().parents[1] / 'release.py'
spec = importlib.util.spec_from_file_location('bridge_release', SCRIPT)
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)
REPOSITORY = SCRIPT.parent.parent


class VersionTest(unittest.TestCase):
    def test_stable_and_prerelease_semver(self):
        for version in ('1.0.0', '0.1.0', '1.2.3+build.01'):
            with self.subTest(version=version):
                self.assertFalse(release.validate_version(version))
        for version in ('0.1.0-poc.2', '1.0.0-rc.1', '1.2.3-alpha+build.1'):
            with self.subTest(version=version):
                self.assertTrue(release.validate_version(version))

    def test_unsafe_and_invalid_input_is_rejected(self):
        for version in ('', 'v1.0.0', '01.0.0', '1.0', '1.0.0-01', '1.0.0-rc..1',
                        '1.0.0\nfoo=bar', '1.0.0;echo boom', '$(id)', '../1.0.0',
                        '1.0.0+bad/metadata', '1.0.0-' + 'a' * 130):
            with self.subTest(version=version):
                with self.assertRaises(ValueError):
                    release.validate_version(version)


class ReleaseRepositoryTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix='feato-release-test-')
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        shutil.copy(REPOSITORY / 'bridge.properties', self.root)
        shutil.copytree(REPOSITORY / 'datapack', self.root / 'datapack')
        self.git('init', '-b', 'main')
        self.git('config', 'user.name', 'Release tests')
        self.git('config', 'user.email', 'tests@example.invalid')
        self.git('add', '.')
        self.git('commit', '-m', 'fixture')
        self.base = self.git('rev-parse', 'HEAD')
        self.initial = release.properties((self.root / 'bridge.properties').read_text())

    def git(self, *args):
        return release.git(self.root, *args)

    def prepare_tag(self, version):
        result = release.prepare(self.root, version)
        self.git('checkout', '--detach')
        self.git('add', '.')
        self.git('commit', '-m', 'release metadata')
        self.git('tag', '-a', result['tag'], '-m', result['tag'])
        self.git('checkout', '--detach', self.base)
        return result

    def fixture_artifacts(self, version):
        jar = self.root / f'plugin/build/libs/feato-gun-valhalla-bridge-plugin-{version}.jar'
        pack = self.root / f'plugin/build/distributions/feato-gun-valhalla-bridge-datapack-{version}.zip'
        jar.parent.mkdir(parents=True, exist_ok=True)
        pack.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(jar, 'w') as archive:
            archive.write(self.root / 'bridge.properties', 'bridge.properties')
            archive.writestr('plugin.yml', f"name: FEATOGunValhallaBridge\nversion: '{version}'\n")
        with zipfile.ZipFile(pack, 'w') as archive:
            for path in (self.root / 'datapack').rglob('*'):
                if path.is_file():
                    archive.write(path, str(path.relative_to(self.root / 'datapack')))
        return jar, pack

    def test_prepare_synchronizes_metadata_and_preserves_protocol_targets(self):
        result = release.prepare(self.root, '0.1.0-poc.2')
        updated = release.properties((self.root / 'bridge.properties').read_text())
        self.assertEqual('0.1.0-poc.2', updated['bridge.version'])
        self.assertEqual(str(int(self.initial['bridge.release']) + 1), updated['bridge.release'])
        for key in self.initial.keys() - {'bridge.version', 'bridge.release'}:
            self.assertEqual(self.initial[key], updated[key])
        self.assertEqual('true', result['prerelease'])
        self.assertIn(f"scoreboard players set #release fgv_bridge {result['release_id']}",
                      (self.root / release.MARKER).read_text())
        self.assertIn('0.1.0-poc.2', json.loads((self.root / 'datapack/pack.mcmeta').read_text())['pack']['description'])

    def test_release_ids_advance_across_tags_while_main_stays_at_base(self):
        first = self.prepare_tag('0.1.0-poc.2')
        second = self.prepare_tag('0.1.0-poc.3')
        self.assertEqual(int(first['release_id']) + 1, int(second['release_id']))
        self.assertEqual(self.base, self.git('rev-parse', 'main'))
        metadata = release.properties(self.git('show', 'v0.1.0-poc.3:bridge.properties'))
        self.assertEqual('0.1.0-poc.3', metadata['bridge.version'])
        self.assertEqual(second['release_id'], metadata['bridge.release'])

    def test_duplicate_tag_is_rejected_without_modifying_sources(self):
        self.prepare_tag('0.1.0-poc.2')
        with self.assertRaisesRegex(ValueError, 'already exists'):
            release.prepare(self.root, '0.1.0-poc.2')
        self.assertEqual('', self.git('status', '--porcelain'))

    def test_inconsistent_old_tag_metadata_stops_allocation(self):
        self.git('tag', 'v9.9.9')
        with self.assertRaisesRegex(ValueError, 'Version mismatch'):
            release.prepare(self.root, '0.1.0-poc.2')
        self.assertEqual('', self.git('status', '--porcelain'))

    def test_dirty_tree_and_marker_mismatch_stop_before_writes(self):
        path = self.root / release.MARKER
        path.write_text(path.read_text().replace('#release fgv_bridge', '#wrong fgv_bridge'))
        with self.assertRaisesRegex(ValueError, 'clean tracked'):
            release.prepare(self.root, '0.1.0-poc.2')
        self.git('add', '.')
        self.git('commit', '-m', 'broken marker')
        with self.assertRaisesRegex(ValueError, 'Source release marker'):
            release.prepare(self.root, '0.1.0-poc.2')
        self.assertEqual(self.initial, release.properties((self.root / 'bridge.properties').read_text()))

    def test_missing_release_metadata_in_tag_stops_allocation(self):
        self.git('rm', 'bridge.properties')
        self.git('commit', '-m', 'no metadata')
        self.git('tag', 'v9.9.9')
        self.git('checkout', '--detach', self.base)
        with self.assertRaises(subprocess.CalledProcessError):
            release.prepare(self.root, '0.1.0-poc.2')

    def test_scoreboard_id_overflow_stops_before_writes(self):
        path = self.root / 'bridge.properties'
        path.write_text(path.read_text().replace(f"bridge.release={self.initial['bridge.release']}",
                                               f'bridge.release={release.MAX_RELEASE_ID}'))
        self.git('add', '.')
        self.git('commit', '-m', 'maximum release ID')
        with self.assertRaisesRegex(ValueError, 'exhausted'):
            release.prepare(self.root, '0.1.0-poc.2')

    def test_artifact_verification_and_checksums(self):
        release.prepare(self.root, '0.1.0-poc.2')
        self.fixture_artifacts('0.1.0-poc.2')
        result = release.verify(self.root, '0.1.0-poc.2')
        lines = (self.root / result['checksums']).read_text().splitlines()
        self.assertEqual(2, len(lines))
        self.assertTrue(all(len(line.split()[0]) == 64 for line in lines))

    def test_stale_plugin_or_datapack_cannot_be_published(self):
        release.prepare(self.root, '0.1.0-poc.2')
        jar, pack = self.fixture_artifacts('0.1.0-poc.2')
        with zipfile.ZipFile(jar, 'w') as archive:
            archive.writestr('bridge.properties', 'bridge.version=0.0.0\n')
        with self.assertRaisesRegex(ValueError, 'JAR compatibility'):
            release.verify(self.root, '0.1.0-poc.2')
        self.fixture_artifacts('0.1.0-poc.2')
        with zipfile.ZipFile(pack, 'w') as archive:
            archive.writestr(str(release.MARKER.relative_to('datapack')), 'old marker')
        with self.assertRaisesRegex(ValueError, 'Datapack release marker'):
            release.verify(self.root, '0.1.0-poc.2')


if __name__ == '__main__':
    unittest.main()
