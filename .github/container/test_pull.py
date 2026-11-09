import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('pull', Path(__file__).with_name('pull.py'))
pull = importlib.util.module_from_spec(spec)
spec.loader.exec_module(pull)
CONFIG = json.loads(Path(__file__).with_name('config.example.json').read_text())
IMAGE = CONFIG['image'] + '@sha256:'
OLD = {'image': IMAGE + 'a' * 64, 'revision': 'a' * 40, 'sequence': 1}
NEW = {'image': IMAGE + 'b' * 64, 'revision': 'b' * 40, 'sequence': 2}


class PolicyTests(unittest.TestCase):
    def info(self):
        return {'Architecture': 'arm64', 'Os': 'linux', 'RepoDigests': [NEW['image']], 'Config': {'Labels': {
            'org.opencontainers.image.source': CONFIG['source'], 'org.opencontainers.image.revision': NEW['revision'],
            'io.remembrall.external-model': CONFIG['model'], 'io.remembrall.external-gate': 'passed',
            'io.remembrall.jar-sha256': 'c' * 64, 'io.remembrall.sequence': '2'}}}

    def test_only_validated_arm64_digest_from_fixed_repository_is_accepted(self):
        self.assertEqual(pull.candidate_from_inspect(self.info(), CONFIG), NEW)
        for field, value in [('Architecture', 'amd64'), ('RepoDigests', ['evil/image@sha256:' + 'b' * 64])]:
            info = self.info(); info[field] = value
            with self.assertRaises(pull.DeployError): pull.candidate_from_inspect(info, CONFIG)

    def test_wrong_source_model_or_unvalidated_image_is_rejected(self):
        for key in ['org.opencontainers.image.source', 'org.opencontainers.image.revision',
                    'io.remembrall.external-model', 'io.remembrall.external-gate', 'io.remembrall.sequence']:
            info = self.info(); info['Config']['Labels'][key] = 'wrong'
            with self.assertRaises(pull.DeployError): pull.candidate_from_inspect(info, CONFIG)

    def test_configuration_rejects_command_injection_and_root_uid(self):
        pull.validate_config(CONFIG)
        for key, value in [('db_container', 'db; rm -rf /'), ('uid', 0), ('enabled', 'false'),
                           ('app_config', '/tmp/a\nENV=bad'), ('image', 'evil/image'),
                           ('app_config', '/tmp/../etc/shadow'), ('public_health_url', 'http://example.com/')]:
            with self.assertRaises(pull.DeployError): pull.validate_config({**CONFIG, key: value})

    def test_nginx_can_stat_the_maintenance_flag_under_service_umask(self):
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            flag = base / 'run' / 'maintenance'
            system = pull.System(CONFIG, base, maintenance=flag)
            system.run = Mock(return_value=Mock(stdout=b'503'))
            mask = os.umask(0o077)
            try:
                system.gate(True)
                self.assertEqual(flag.parent.stat().st_mode & 0o777, 0o755)
                self.assertEqual(flag.stat().st_mode & 0o777, 0o644)
                system.gate(False)
                self.assertFalse(flag.exists())
            finally:
                os.umask(mask)


class TransitionTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        self.system = Mock()
        self.system.candidate.return_value = NEW
        self.system.pending.return_value = 0
        self.system.legacy_enabled.return_value = True
        self.config = {**CONFIG, 'enabled': True}

    def deployer(self, state=None):
        if state is not None: pull.atomic_json(self.directory / 'state.json', state)
        return pull.Deployer(self.config, self.directory, self.system)

    def test_disabled_switch_does_not_contact_registry_or_server(self):
        d = self.deployer(); d.config = {**CONFIG, 'enabled': False}
        self.assertEqual(d.poll(), 'disabled')
        self.assertEqual(self.system.mock_calls, [])

    def test_pull_failure_never_stops_service(self):
        self.system.candidate.side_effect = pull.DeployError('pull failed')
        with self.assertRaises(pull.DeployError): self.deployer().poll()
        self.system.legacy.assert_not_called(); self.system.stop.assert_not_called()

    def test_success_backs_up_before_stopping_legacy_and_records_digest(self):
        d = self.deployer(); self.assertEqual(d.poll(), 'deployed')
        calls = [x[0] for x in self.system.mock_calls]
        self.assertLess(calls.index('backup'), calls.index('legacy'))
        self.system.legacy.assert_any_call('stop'); self.system.legacy.assert_any_call('disable')
        self.assertEqual(d.state['current'], NEW); self.assertEqual(d.state['phase'], 'ready')

    def test_same_digest_and_failed_digest_are_not_redeployed(self):
        self.assertEqual(self.deployer({'current': NEW}).poll(), 'unchanged')
        self.assertEqual(self.deployer({'failed': NEW['image']}).poll(), 'failed-version-skipped')
        self.system.start.assert_not_called()

    def test_older_sequence_cannot_automatically_roll_back(self):
        self.system.candidate.return_value = OLD
        with self.assertRaises(pull.DeployError): self.deployer({'current': NEW}).poll()
        self.system.prepare.assert_not_called()

    def test_backup_failure_reopens_ingress_without_stopping_service(self):
        self.system.backup.side_effect = pull.DeployError('backup failed')
        with self.assertRaises(pull.DeployError): self.deployer().poll()
        self.system.gate.assert_called_with(False)
        self.system.start.assert_not_called(); self.system.legacy.assert_not_called()

    def test_unfinished_jobs_defer_without_stopping_or_backing_up(self):
        self.system.drain.side_effect = pull.Deferred('pending')
        with self.assertRaises(pull.Deferred): self.deployer().poll()
        self.system.backup.assert_not_called(); self.system.start.assert_not_called()
        self.system.gate.assert_called_with(False)

    def test_busy_preflight_never_blocks_incoming_requests(self):
        self.system.pending.return_value = 1
        with self.assertRaises(pull.Deferred): self.deployer().poll()
        self.system.gate.assert_not_called(); self.system.prepare.assert_not_called()

    def test_missing_nginx_guard_prevents_stopping_service(self):
        self.system.gate.side_effect = lambda enabled: (_ for _ in ()).throw(pull.DeployError('no guard')) if enabled else None
        with self.assertRaises(pull.DeployError): self.deployer().poll()
        self.system.legacy.assert_not_called(); self.system.backup.assert_not_called()

    def test_first_start_failure_restores_legacy_and_enabled_state(self):
        self.system.start.side_effect = pull.DeployError('new application failed')
        d = self.deployer()
        with self.assertRaises(pull.DeployError): d.poll()
        self.system.legacy.assert_any_call('start'); self.system.legacy.assert_any_call('enable')
        self.assertEqual(d.state['failed'], NEW['image']); self.assertIsNone(d.state['current'])

    def test_public_failure_restores_previous_container_and_keeps_history(self):
        self.system.health.side_effect = [None, pull.DeployError('public failed'), None, None]
        d = self.deployer({'current': OLD, 'previous': None})
        with self.assertRaises(pull.DeployError): d.poll()
        self.system.start.assert_any_call(OLD)
        self.assertEqual(d.state['current'], OLD); self.assertIsNone(d.state['previous'])

    def test_interrupted_deployment_recovers_even_if_polling_disabled(self):
        d = self.deployer({'phase': 'deploying', 'target': NEW, 'previous': OLD, 'before': {'current': OLD}})
        d.config['enabled'] = False
        self.assertEqual(d.poll(), 'disabled')
        self.system.stop.assert_called_once_with(NEW); self.system.start.assert_called_once_with(OLD)
        self.assertEqual(d.state['current'], OLD)

    def test_interrupted_preparation_reopens_ingress_without_stopping_anything(self):
        d = self.deployer({'phase': 'preparing', 'before': {'current': OLD}, 'target': NEW})
        d.config['enabled'] = False
        d.poll(); self.system.gate.assert_called_once_with(False)
        self.system.stop.assert_not_called(); self.system.legacy.assert_not_called()

    def test_explicit_rollback_restores_previous_image_and_blocks_failed_target(self):
        d = self.deployer({'phase': 'ready', 'current': NEW, 'previous': OLD})
        self.assertEqual(d.rollback(), 'rolled-back')
        self.system.start.assert_called_once_with(OLD)
        self.assertEqual(d.state['failed'], NEW['image']); self.assertEqual(d.state['current'], OLD)

    def test_commit_write_failure_still_restores_the_previous_application(self):
        d = self.deployer({'current': OLD})
        original = pull.atomic_json
        writes = 0
        def fail_commit(path, value):
            nonlocal writes
            writes += 1
            if writes == 3: raise OSError('disk full')
            original(path, value)
        with patch.object(pull, 'atomic_json', side_effect=fail_commit):
            with self.assertRaises(OSError): d.poll()
        self.system.start.assert_any_call(OLD)
        self.assertEqual(d.state['current'], OLD)
        self.assertEqual(d.state['failed'], NEW['image'])

    def test_failed_restore_keeps_the_recovery_journal_for_next_attempt(self):
        self.system.health.side_effect = pull.DeployError('still unhealthy')
        d = self.deployer({'current': OLD})
        with self.assertRaises(pull.DeployError): d.poll()
        saved = json.loads((self.directory / 'state.json').read_text())
        self.assertEqual(saved['phase'], 'deploying')
        self.assertEqual(saved['previous'], OLD)

    def test_rollback_does_not_guess_an_unknown_older_version(self):
        d = self.deployer({'current': OLD, 'previous': None, 'legacy_previous': False})
        with self.assertRaises(pull.DeployError): d.rollback()
        self.system.stop.assert_not_called(); self.system.gate.assert_not_called()


if __name__ == '__main__': unittest.main()
