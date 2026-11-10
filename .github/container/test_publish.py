"""Run the real shell entrypoint with fake registry commands; never publish an image."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


class BranchPublishTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        scripts = self.root / '.github/container'
        scripts.mkdir(parents=True)
        self.script = scripts / 'publish-test.sh'
        shutil.copyfile(Path(__file__).with_name('publish-test.sh'), self.script)
        self.release = self.root / 'release'
        self.release.mkdir()
        (self.release / 'Remembrall-0.0.1-SNAPSHOT.jar').write_bytes(b'test-fixture-not-a-real-jar')
        bin_dir = self.root / 'bin'
        bin_dir.mkdir()
        self.log = self.root / 'commands.jsonl'
        (bin_dir / 'uname').write_text('#!/bin/sh\necho aarch64\n')
        (bin_dir / 'docker').write_text('''#!/usr/bin/env python3
import json,os,sys
with open(os.environ['TEST_LOG'],'a') as stream: stream.write(json.dumps(['docker',*sys.argv[1:]])+'\\n')
args=sys.argv[1:]
mode=os.environ.get('TEST_MODE','success')
if args[0]=='login': sys.stdin.read()
if mode==args[0]+'-fail': sys.exit(1)
if args[:2]==['image','inspect']:
    print('ghcr.io/example/remembrall-backend-test@sha256:'+'d'*64)
''')
        (scripts / 'smoke.sh').write_text('''#!/usr/bin/env python3
import json,os,sys
with open(os.environ['TEST_LOG'],'a') as stream: stream.write(json.dumps(['smoke',*sys.argv[1:]])+'\\n')
if os.environ.get('TEST_MODE')=='smoke-fail': sys.exit(1)
if os.environ.get('TEST_MODE')=='pulled-smoke-fail' and '@sha256:' in sys.argv[1]: sys.exit(1)
''')
        # smoke.sh is invoked with bash, so delegate the fixture to a Python file.
        shutil.move(scripts / 'smoke.sh', scripts / 'fake_smoke.py')
        (scripts / 'smoke.sh').write_text('#!/bin/bash\npython3 "$(dirname "$0")/fake_smoke.py" "$@"\n')
        for file in bin_dir.iterdir(): file.chmod(0o755)
        self.env = {**os.environ, 'PATH': str(bin_dir) + os.pathsep + os.environ['PATH'],
                    'GITHUB_EVENT_NAME': 'workflow_dispatch', 'GITHUB_REF': 'refs/heads/feat/ghcr-deploy',
                    'GITHUB_SHA': 'a'*40, 'GITHUB_RUN_ID': '123', 'GITHUB_RUN_ATTEMPT': '2',
                    'GITHUB_REPOSITORY_OWNER': 'Example', 'GITHUB_REPOSITORY': 'example/repo',
                    'GITHUB_ACTOR': 'tester', 'GHCR_TOKEN': 'fake-secret-never-log',
                    'GITHUB_STEP_SUMMARY': str(self.root / 'summary.md'), 'TEST_LOG': str(self.log),
                    'PROMOTE_DEPLOYMENT': 'true'}

    def run_script(self, **environment):
        self.result = subprocess.run(['bash', str(self.script)], env={**self.env, **environment},
                                     capture_output=True, text=True, timeout=20)
        self.calls = [json.loads(line) for line in self.log.read_text().splitlines()] if self.log.exists() else []
        self.assertNotIn('fake-secret-never-log', self.result.stdout + self.result.stderr)
        return self.result.returncode

    def test_success_pushes_only_test_package_then_smokes_the_pulled_digest(self):
        self.assertEqual(self.run_script(), 0, self.result.stderr)
        pushes = [call for call in self.calls if call[:2] == ['docker', 'push']]
        self.assertEqual(pushes, [['docker', 'push', 'ghcr.io/example/remembrall-backend-test:test-'+'a'*40+'-123-2']])
        self.assertFalse(any('deploy-main' in value for call in self.calls for value in call))
        receipt = json.loads((self.release / 'GHCR_TEST_RESULT.json').read_text())
        self.assertFalse(receipt['externalApiTested']); self.assertFalse(receipt['productionChannelChanged'])
        smoke = [call for call in self.calls if call[0] == 'smoke']
        self.assertEqual(len(smoke), 2); self.assertIn('@sha256:', smoke[1][1])
        external = json.loads((self.release / 'EXTERNAL_RESULT.json').read_text())
        self.assertEqual(external['status'], 'not-run')
        build = next(call for call in self.calls if call[:2] == ['docker', 'build'])
        self.assertIn('io.remembrall.external-gate=not-run', build)

    def test_main_is_rejected_before_any_registry_command(self):
        self.assertNotEqual(self.run_script(GITHUB_REF='refs/heads/main'), 0)
        self.assertEqual(self.calls, [])

    def test_push_and_tag_events_are_rejected(self):
        self.assertNotEqual(self.run_script(GITHUB_EVENT_NAME='push'), 0)
        self.assertNotEqual(self.run_script(GITHUB_REF='refs/tags/v1'), 0)
        self.assertEqual(self.calls, [])

    def test_invalid_revision_is_rejected(self):
        self.assertNotEqual(self.run_script(GITHUB_SHA='bad;command'), 0)
        self.assertEqual(self.calls, [])

    def test_build_failure_never_logs_into_registry(self):
        self.assertNotEqual(self.run_script(TEST_MODE='build-fail'), 0)
        self.assertFalse(any(call[:2] == ['docker','login'] for call in self.calls))

    def test_smoke_failure_never_publishes(self):
        self.assertNotEqual(self.run_script(TEST_MODE='smoke-fail'), 0)
        self.assertFalse(any(call[:2] in [['docker','login'],['docker','push']] for call in self.calls))

    def test_failed_push_does_not_claim_a_pass(self):
        self.assertNotEqual(self.run_script(TEST_MODE='push-fail'), 0)
        self.assertFalse((self.release / 'GHCR_TEST_RESULT.json').exists())
        self.assertFalse(any(call[:2] == ['docker','pull'] for call in self.calls))

    def test_failed_pull_does_not_claim_a_pass(self):
        self.assertNotEqual(self.run_script(TEST_MODE='pull-fail'), 0)
        self.assertFalse((self.release / 'GHCR_TEST_RESULT.json').exists())

    def test_failed_pulled_image_smoke_does_not_claim_a_pass(self):
        self.assertNotEqual(self.run_script(TEST_MODE='pulled-smoke-fail'), 0)
        self.assertFalse((self.release / 'GHCR_TEST_RESULT.json').exists())


if __name__ == '__main__': unittest.main()
