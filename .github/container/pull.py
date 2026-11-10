"""Root-owned pull deployer. Registry metadata is data, never shell or Compose code."""
import argparse
import fcntl
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import sys
import time

INSTALL = Path('/usr/local/lib/remembrall-container')
CONFIG = Path('/etc/remembrall-container/config.json')
STATE = Path('/var/lib/remembrall-container')
MAINTENANCE = Path('/run/remembrall-container/maintenance')
SHA = re.compile(r'[0-9a-f]{40}')
DIGEST = re.compile(r'sha256:[0-9a-f]{64}')
NAME = re.compile(r'[a-zA-Z0-9_-]+')


class DeployError(Exception):
    pass


class Deferred(DeployError):
    pass


def atomic_json(path, value):
    temporary = path.with_suffix('.next')
    with open(temporary, 'w', encoding='utf-8') as stream:
        os.chmod(temporary, 0o600)
        json.dump(value, stream, ensure_ascii=False)
        stream.flush()
        os.fsync(stream.fileno())
    os.replace(temporary, path)
    descriptor = os.open(path.parent, os.O_DIRECTORY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def validate_config(config):
    if type(config.get('enabled')) is not bool:
        raise DeployError('enabled must be a JSON boolean')
    if not re.fullmatch(r'ghcr\.io/[a-z0-9_-]+/[a-z0-9_-]+', config.get('image', '')):
        raise DeployError('Invalid fixed image repository')
    if not re.fullmatch(r'https://github\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', config.get('source', '')):
        raise DeployError('Invalid repository source')
    if not re.fullmatch(r'[A-Za-z0-9._/-]{1,100}', config.get('model', '')):
        raise DeployError('Invalid model')
    if not re.fullmatch(r'https://[A-Za-z0-9.-]+/actuator/health', config.get('public_health_url', '')):
        raise DeployError('Invalid public health URL')
    for key in ('db_container', 'db_name', 'db_user', 'db_network', 'legacy_unit'):
        if not NAME.fullmatch(config.get(key, '')):
            raise DeployError('Invalid local service identifier')
    for key in ('uid', 'gid'):
        if type(config.get(key)) is not int or not 0 < config[key] < 65535:
            raise DeployError('Container must run as a non-root UID/GID')
    if type(config.get('host_port', 8080)) is not int or not 1024 <= config.get('host_port', 8080) <= 65535:
        raise DeployError('Invalid localhost application port')
    for key in ('project', 'container_name'):
        if not re.fullmatch(r'[a-z0-9][a-z0-9_-]{1,60}', config.get(key, 'remembrall-backend')):
            raise DeployError('Invalid Compose project or container name')
    for key in ('app_config', 'fcm_config'):
        if not re.fullmatch(r'/[A-Za-z0-9_./-]+', config.get(key, '')) or '..' in config[key].split('/'):
            raise DeployError('Invalid local secret file path')
    return config


def candidate_from_inspect(info, config):
    labels = info.get('Config', {}).get('Labels') or {}
    revision = labels.get('org.opencontainers.image.revision', '')
    sequence = labels.get('io.remembrall.sequence', '')
    if (info.get('Architecture') != 'arm64' or info.get('Os') != 'linux'
            or labels.get('org.opencontainers.image.source') != config['source']
            or not SHA.fullmatch(revision)
            or labels.get('io.remembrall.external-gate') != 'passed'
            or labels.get('io.remembrall.test-only') == 'true'
            or labels.get('io.remembrall.external-model') != config['model']
            or not re.fullmatch(r'[0-9a-f]{64}', labels.get('io.remembrall.jar-sha256', ''))
            or not re.fullmatch(r'[1-9][0-9]*', sequence)):
        raise DeployError('Image architecture, source, revision or validated model does not match')
    prefix = config['image'] + '@'
    digests = [d for d in info.get('RepoDigests', [])
               if d.startswith(prefix) and DIGEST.fullmatch(d[len(prefix):])]
    if not digests:
        raise DeployError('Image is missing an immutable repository digest')
    return {'image': digests[0], 'revision': revision, 'sequence': int(sequence)}


class System:
    def __init__(self, config, directory, maintenance=MAINTENANCE, install=INSTALL):
        self.config, self.directory, self.maintenance, self.install = config, directory, maintenance, install
        self.env_file = directory / 'runtime.env'

    def run(self, args, timeout=60, stdout=None, check=True):
        try:
            result = subprocess.run(args, stdout=stdout or subprocess.PIPE, stderr=subprocess.PIPE,
                                    timeout=timeout, check=False)
        except (OSError, subprocess.TimeoutExpired):
            raise DeployError('Command unavailable or timed out: ' + args[0]) from None
        if check and result.returncode:
            # Never forward command output: it may contain application credentials.
            raise DeployError('Command failed: ' + args[0] + ' ' + args[1])
        return result

    def candidate(self):
        channel = self.config['image'] + ':deploy-main'
        result = self.run(['docker', '--config', '/root/.docker', 'pull', channel], timeout=180, check=False)
        if result.returncode:
            error = (result.stderr or b'').lower()
            if b'unauthorized' in error or b'denied' in error:
                raise DeployError('GHCR access denied; check root login, read:packages, SSO and token expiry. Running service retained.')
            raise DeployError('GHCR image pull failed; running service retained')
        info = json.loads(self.run(['docker', 'image', 'inspect', channel]).stdout)[0]
        return candidate_from_inspect(info, self.config)

    def compose(self, image, *args):
        prefix = self.config['image'] + '@'
        if not image.startswith(prefix) or not DIGEST.fullmatch(image[len(prefix):]):
            raise DeployError('Only the configured repository and an immutable digest may run')
        env = {'BACKEND_IMAGE': image, 'APP_UID': self.config['uid'], 'APP_GID': self.config['gid'],
               'APP_CONFIG': self.config['app_config'], 'FCM_CONFIG': self.config['fcm_config'],
               'DB_CONTAINER': self.config['db_container'], 'DB_NAME': self.config['db_name'],
               'DB_NETWORK': self.config['db_network'], 'RECALL_MODEL': self.config['model'],
               'HOST_PORT': self.config.get('host_port', 8080),
               'APP_CONTAINER': self.config.get('container_name', 'remembrall-backend'),
               'DEPLOY_PROJECT': self.config.get('project', 'remembrall-backend')}
        self.env_file.write_text(''.join(f'{k}={v}\n' for k, v in env.items()), encoding='utf-8')
        os.chmod(self.env_file, 0o600)
        return self.run(['docker', 'compose', '--env-file', str(self.env_file), '-p', self.config.get('project', 'remembrall-backend'),
                         '-f', str(self.install / 'compose.yaml'), *args], timeout=180)

    def prepare(self, candidate):
        for key in ('app_config', 'fcm_config'):
            item = Path(self.config[key])
            if not item.is_file() or item.stat().st_uid != self.config['uid'] or not item.stat().st_mode & 0o400:
                raise DeployError('Secret files must be readable by the configured application UID')
        self.compose(candidate['image'], 'config', '--quiet')
        network = self.config['db_network']
        if self.run(['docker', 'network', 'inspect', network], check=False).returncode:
            self.run(['docker', 'network', 'create', network])
        info = json.loads(self.run(['docker', 'inspect', self.config['db_container']]).stdout)[0]
        if network not in info['NetworkSettings']['Networks']:
            self.run(['docker', 'network', 'connect', network, self.config['db_container']])

    def gate(self, enabled):
        self.maintenance.parent.mkdir(mode=0o755, parents=True, exist_ok=True)
        os.chmod(self.maintenance.parent, 0o755)
        if enabled:
            self.maintenance.touch(mode=0o644)
            os.chmod(self.maintenance, 0o644)
            # Refuse to stop the application unless Nginx actually blocks new traffic.
            response = self.run(['curl', '-sS', '--max-time', '10', '-o', '/dev/null', '-w', '%{http_code}',
                                 self.config['public_health_url']]).stdout.decode().strip()
            if response != '503':
                raise DeployError('Install and verify the Nginx maintenance include before activation')
        else:
            self.maintenance.unlink(missing_ok=True)

    def pending(self):
        sql = ("SELECT (SELECT count(*) FROM content WHERE analysis_status='ANALYZING') + "
               "(SELECT count(*) FROM recall_execution WHERE status IN ('PENDING','RUNNING'));")
        count = self.run(['docker', 'exec', self.config['db_container'], 'psql', '-X', '-qAt',
                          '-v', 'ON_ERROR_STOP=1', '-U', self.config['db_user'], '-d', self.config['db_name'],
                          '-c', sql]).stdout.decode().strip()
        if not count.isdigit():
            raise DeployError('Could not read pending job count')
        return int(count)

    def drain(self):
        quiet = 0
        for _ in range(45):
            quiet = quiet + 1 if self.pending() == 0 else 0
            if quiet >= 3:
                return
            time.sleep(2)
        raise Deferred('Pending analysis/recall work remains; previous service retained')

    def backup(self, revision):
        folder = self.directory / 'backups'
        folder.mkdir(mode=0o700, exist_ok=True)
        with open(folder / f'{time.time_ns()}-{revision}.dump', 'xb') as stream:
            os.chmod(stream.name, 0o600)
            self.run(['docker', 'exec', self.config['db_container'], 'pg_dump', '-U', self.config['db_user'],
                      '-d', self.config['db_name'], '-Fc'], stdout=stream, timeout=120)
            if stream.tell() == 0:
                raise DeployError('Database backup is empty')

    def legacy(self, operation):
        self.run(['systemctl', operation, self.config['legacy_unit']], timeout=180)

    def legacy_enabled(self):
        return self.run(['systemctl', 'is-enabled', self.config['legacy_unit']], check=False).returncode == 0

    def start(self, candidate):
        self.compose(candidate['image'], 'up', '-d', '--no-build', '--pull', 'never', 'backend')

    def stop(self, candidate):
        self.compose(candidate['image'], 'stop', 'backend')

    def health(self, public=False):
        port = self.config.get('host_port', 8080)
        url = self.config['public_health_url'] if public else f'http://127.0.0.1:{port}/actuator/health'
        for _ in range(60 if not public else 5):
            result = self.run(['curl', '-fsS', '--max-time', '3', url], check=False)
            try:
                if result.returncode == 0 and json.loads(result.stdout).get('status') == 'UP':
                    if public:
                        return
                    code = self.run(['curl', '-sS', '--max-time', '5', '-o', '/dev/null', '-w', '%{http_code}',
                                     f'http://127.0.0.1:{port}/api/me/consents']).stdout.decode().strip()
                    if code == '401':
                        return
            except (ValueError, AttributeError):
                pass
            time.sleep(2)
        raise DeployError('Public health failed' if public else 'Backend health/auth check failed')


class Deployer:
    def __init__(self, config, directory, system):
        self.config, self.directory, self.system = config, directory, system
        self.state_path = directory / 'state.json'
        self.state = json.loads(self.state_path.read_text()) if self.state_path.exists() else {}

    def save(self):
        atomic_json(self.state_path, self.state)

    def recover(self):
        if self.state.get('phase') == 'preparing':
            self.system.gate(False)
            self.state = self.state.get('before', {})
            self.save()
            return
        previous, target = self.state.get('previous'), self.state.get('target')
        if self.state.get('phase') != 'deploying' or not target:
            return
        self.system.stop(target)
        if previous:
            self.system.start(previous)
        else:
            self.system.legacy('start')
            if self.state.get('legacy_enabled'):
                self.system.legacy('enable')
        self.system.health()
        self.system.gate(False)
        self.system.health(public=True)
        self.state = {**self.state.get('before', {}), 'phase': 'failed', 'current': previous, 'failed': target['image']}
        self.save()

    def poll(self, manual=False, retry=False):
        self.recover()  # Recover an interrupted deployment even when the polling switch was turned off.
        if not manual and not self.config['enabled']:
            return 'disabled'
        target = self.system.candidate()
        current = self.state.get('current')
        if current and target['image'] == current['image']:
            return 'unchanged'
        if not retry and target['image'] == self.state.get('failed'):
            return 'failed-version-skipped'
        if current and target['sequence'] <= current['sequence']:
            raise DeployError('Refusing to automatically deploy an older release sequence')
        if self.system.pending() != 0:
            raise Deferred('Application is busy; deployment deferred without blocking requests')
        self.system.prepare(target)
        self._transition(target)
        return 'deployed'

    def _transition(self, target):
        current = self.state.get('current')
        before = dict(self.state)
        self.state = {'phase': 'preparing', 'before': before, 'previous': current, 'target': target,
                      'legacy_enabled': self.system.legacy_enabled()}
        self.save()
        try:
            self.system.gate(True)
            self.system.drain()
            self.system.backup(target['revision'])
        except BaseException:
            self.system.gate(False)
            self.state = before
            self.save()
            raise
        self.state.update(phase='deploying')
        self.save()  # Durable journal BEFORE stopping a running service.
        try:
            if current:
                self.system.stop(current)
            else:
                self.system.legacy('stop')
            self.system.start(target)
            self.system.health()
            self.system.gate(False)
            self.system.health(public=True)
            self.system.legacy('disable')
            ready = {'phase': 'ready', 'current': target, 'previous': current, 'failed': None,
                     'legacy_enabled': self.state['legacy_enabled'], 'legacy_previous': current is None}
            atomic_json(self.state_path, ready)
            self.state = ready
        except BaseException:
            # Re-close ingress while restoring the known previous application.
            try:
                self.system.gate(True)
            except DeployError:
                pass
            self.recover()
            raise

    def rollback(self):
        self.recover()
        current, previous = self.state.get('current'), self.state.get('previous')
        if not current:
            raise DeployError('No container release to roll back')
        if not previous and not self.state.get('legacy_previous'):
            raise DeployError('No known previous application remains for another rollback')
        if self.system.pending() != 0:
            raise Deferred('Application is busy; rollback deferred without blocking requests')
        try:
            self.system.gate(True)
            self.system.drain()
            before = {'current': previous, 'previous': None, 'legacy_enabled': self.state.get('legacy_enabled', False)}
            self.state.update(phase='deploying', target=current, before=before)
            self.save()
            self.recover()
        finally:
            if self.state.get('phase') != 'deploying':
                self.system.gate(False)
        return 'rolled-back'


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=['poll', 'deploy', 'rollback', 'status'])
    parser.add_argument('--retry', action='store_true')
    args = parser.parse_args()
    if os.geteuid() != 0 or CONFIG.is_symlink() or CONFIG.stat().st_uid != 0 or CONFIG.stat().st_mode & 0o077:
        raise DeployError('Use the root-owned configuration with mode 600')
    config = validate_config(json.loads(CONFIG.read_text()))
    STATE.mkdir(mode=0o700, parents=True, exist_ok=True)
    with open(STATE / 'deploy.lock', 'a') as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            return
        deployer = Deployer(config, STATE, System(config, STATE))
        if args.action == 'status':
            print(json.dumps({'enabled': config['enabled'], **deployer.state}))
            return
        def interrupted(signum, frame):
            raise DeployError('Deployment interrupted; recovering previous service')
        signal.signal(signal.SIGTERM, interrupted)
        signal.signal(signal.SIGINT, interrupted)
        outcome = deployer.rollback() if args.action == 'rollback' else deployer.poll(args.action == 'deploy', args.retry)
        if outcome not in ('disabled', 'unchanged', 'failed-version-skipped'):
            print(json.dumps({'result': outcome, 'current': deployer.state.get('current')}))


if __name__ == '__main__':
    try:
        main()
    except Deferred as error:
        print(json.dumps({'result': 'deferred', 'reason': str(error)}))
    except DeployError as error:
        print(json.dumps({'result': 'failed', 'reason': str(error)}), file=sys.stderr)
        sys.exit(1)
    except (ValueError, KeyError, OSError):
        # Do not emit subprocess output, credentials or the full configuration.
        print(json.dumps({'result': 'failed', 'reason': 'Deployment check failed; inspect local state and service health'}), file=sys.stderr)
        sys.exit(1)
