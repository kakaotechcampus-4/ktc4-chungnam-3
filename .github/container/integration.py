"""Explicit manual Linux/root fixture. Never reads production application secrets.

Uses a pulled test-only ARM64 image, dummy keys, fresh DB and localhost random port.
Exercises the real Compose transition/recovery; does NOT validate release eligibility,
production Nginx, paid APIs, FCM delivery or the legacy systemd handover.
"""
import argparse
import json
import os
from pathlib import Path
import secrets
import shutil
import socket
import subprocess
import tempfile
import time
import urllib.request
import uuid

import pull


def command(*args):
    result = subprocess.run(args, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=180)
    if result.returncode:
        raise RuntimeError('Fixture command failed: ' + ' '.join(args[:2]))
    return result.stdout


class FixtureSystem(pull.System):
    fail_next_start = False

    def legacy_enabled(self):
        return False

    def legacy(self, operation):
        # No call to systemctl is allowed in this fixture.
        assert operation == 'disable', operation

    def gate(self, enabled):
        # Local fixture flag only. Production Nginx protection is unit-tested separately.
        if enabled:
            self.maintenance.touch()
        else:
            self.maintenance.unlink(missing_ok=True)

    def start(self, candidate):
        if self.fail_next_start:
            self.fail_next_start = False
            # Invalid entrypoint only for this fixture's replacement container.
            original = self.install
            self.install = self.directory / 'broken'
            try:
                super().start(candidate)
            finally:
                self.install = original
        else:
            super().start(candidate)

    def health(self, public=False):
        info = json.loads(command('docker', 'inspect', self.config['container_name']))[0]
        if info['Config']['Entrypoint'] == ['/bin/false']:
            raise pull.DeployError('Deliberate fixture startup failure')
        # Public TLS/Nginx is deliberately outside this fixture.
        super().health(public=False)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--image', required=True)
    args = parser.parse_args()
    if os.geteuid() != 0:
        raise RuntimeError('Run this isolated fixture as root on Linux')
    repository = 'ghcr.io/kakaotechcampus-4/remembrall-backend-test'
    if not args.image.startswith(repository + '@sha256:') or not pull.DIGEST.fullmatch(args.image.split('@')[-1]):
        raise RuntimeError('Only an immutable test-package digest is allowed')
    info = json.loads(command('docker', 'image', 'inspect', args.image))[0]
    assert info['Architecture'] == 'arm64'
    assert info['Config']['Labels']['io.remembrall.test-only'] == 'true'
    name = 'ghcr-fixture-' + secrets.token_hex(4)
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        port = sock.getsockname()[1]
    assert port not in (8080, 5432)
    root = Path(tempfile.mkdtemp(prefix=name + '-'))
    os.chmod(root, 0o755)
    system = None
    target = {'image': args.image, 'revision': info['Config']['Labels']['org.opencontainers.image.revision'], 'sequence': 1}
    try:
        install = root / 'install'
        state = root / 'state'
        broken = state / 'broken'
        install.mkdir(); state.mkdir(); broken.mkdir()
        source = Path(__file__).parent
        compose = (source / 'compose.yaml').read_text().replace('--recall.agent.mode=llm', '--recall.agent.mode=stub')
        compose = compose.replace('--notification.fcm-credentials-path=/run/secrets/firebase_json', '--notification.fcm-credentials-path=')
        (install / 'compose.yaml').write_text(compose)
        (broken / 'compose.yaml').write_text(compose.replace('    command:\n', '    entrypoint: ["/bin/false"]\n    command:\n'))
        password = secrets.token_hex(24)
        properties = (source.parent / 'ci/application-ci.properties').read_text()
        properties += '\nspring.datasource.username=fixture\nspring.datasource.password=' + password + '\n'
        for filename, content in [('app.properties', properties), ('firebase.json', '{}')]:
            path = root / filename
            path.write_text(content)
            os.chown(path, 1001, 1001); os.chmod(path, 0o600)
        command('docker', 'run', '-d', '--name', name + '-db',
                '--label', 'io.remembrall.fixture=' + name,
                '-e', 'POSTGRES_USER=fixture', '-e', 'POSTGRES_DB=fixture',
                '-e', 'POSTGRES_PASSWORD=' + password, 'pgvector/pgvector:pg16')
        for _ in range(45):
            ready = subprocess.run(['docker', 'exec', name + '-db', 'pg_isready', '-U', 'fixture'],
                                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            if ready.returncode == 0: break
            time.sleep(1)
        else: raise RuntimeError('Fixture database did not become ready')
        config = {**json.loads((source / 'config.example.json').read_text()),
                  'image': repository, 'app_config': str(root / 'app.properties'), 'fcm_config': str(root / 'firebase.json'),
                  'db_container': name + '-db', 'db_name': 'fixture', 'db_user': 'fixture', 'db_network': name,
                  'host_port': port, 'project': name, 'container_name': name + '-app'}
        pull.validate_config(config)
        system = FixtureSystem(config, state, maintenance=root / 'maintenance', install=install)
        system.prepare(target); system.start(target); system.health()
        base = f'http://127.0.0.1:{port}'

        def api(path, body=None, token=None, method=None):
            headers = {'Content-Type': 'application/json'}
            if token: headers['Authorization'] = 'Bearer ' + token
            request = urllib.request.Request(base + path, data=json.dumps(body).encode() if body is not None else None,
                                             headers=headers, method=method)
            with urllib.request.urlopen(request, timeout=15) as response:
                return json.load(response)

        token = api('/api/auth/guest', {'installationId': str(uuid.uuid4())})['sessionToken']
        api('/api/me/consents/LOCATION_BASED_SERVICE', {'agreed': True, 'termsVersion': 'v1'}, token, 'PUT')
        # Compare persisted GET values; PostgreSQL rounds nanoseconds to microseconds.
        baseline = next(row for row in api('/api/me/consents', token=token)
                        if row['consentType'] == 'LOCATION_BASED_SERVICE')
        assert baseline['agreed'] is True and baseline['agreedAt'] is not None

        def verify_saved_consent():
            rows = api('/api/me/consents', token=token)
            saved = next(row for row in rows if row['consentType'] == 'LOCATION_BASED_SERVICE')
            assert saved == baseline

        def pid():
            return json.loads(command('docker', 'inspect', name + '-app'))[0]['State']['Pid']

        deployer = pull.Deployer(config, state, system)
        deployer.state = {'phase': 'ready', 'current': target, 'previous': None}
        deployer.save()
        before = pid()
        # Same immutable bytes, new fixture metadata: a lifecycle check, not two releases.
        second = {**target, 'sequence': 2}
        deployer._transition(second)
        assert pid() != before and deployer.state['phase'] == 'ready'
        verify_saved_consent()
        assert not system.maintenance.exists()
        system.fail_next_start = True
        try:
            deployer._transition({**target, 'sequence': 3})
        except pull.DeployError:
            pass
        else:
            raise AssertionError('Expected deliberate new-container failure')
        assert deployer.state['phase'] == 'failed' and deployer.state['current'] == second
        system.health(); verify_saved_consent()
        assert not system.maintenance.exists()
        backups = list((state / 'backups').glob('*.dump'))
        assert len(backups) == 2 and all(file.stat().st_size > 0 for file in backups)
        print(json.dumps({'result': 'passed', 'image': args.image, 'port': port,
                          'checks': ['startup-UP-401', 'compose-restart', 'session-consent-persist',
                                     'startup-failure-recovery', 'database-backup'],
                          'limits': ['same-image lifecycle', 'local maintenance flag', 'dummy API keys', 'no production handover']}))
    finally:
        if system:
            try: system.compose(args.image, 'down', '--remove-orphans', '--volumes')
            except pull.DeployError: pass
        subprocess.run(['docker', 'rm', '-fv', name + '-app', name + '-db'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        subprocess.run(['docker', 'network', 'rm', name], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        assert root.parent == Path(tempfile.gettempdir()) and root.name.startswith(name + '-') and not root.is_symlink()
        shutil.rmtree(root)


if __name__ == '__main__':
    main()
