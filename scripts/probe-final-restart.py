#!/usr/bin/env python3
"""Synthetic restart qualification of the fixed isolated emulator, never a personal device."""
import base64, hashlib, io, json, re, shlex, sqlite3, tempfile, time, zipfile
from datetime import datetime, timezone
from pathlib import Path
import xml.etree.ElementTree as E
from adb_shell.adb_device import AdbDeviceTcp

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'reports/services-final-01/android-restart.json'
PACKAGE = 'fr.jarvis.services.diagnostic'
ACTIVITY = PACKAGE + '/fr.jarvis.services.android.WorkspaceActivity'
MARKER = 'FIN01'
device = AdbDeviceTcp('127.0.0.1', 5581, default_transport_timeout_s=300)
record = {'fixture': 'synthetic FIN01 only', 'target': 'verified isolated emulator', 'started_at_utc': datetime.now(timezone.utc).isoformat(), 'observations': []}

def shell(command):
    return device.shell(command, transport_timeout_s=300, read_timeout_s=120, timeout_s=180)

def dump():
    root = None
    for attempt in range(4):
        shell('rm -f /data/local/tmp/jarvis-final-restart-ui.xml')
        shell('uiautomator dump /data/local/tmp/jarvis-final-restart-ui.xml')
        try:
            root = E.fromstring(shell('cat /data/local/tmp/jarvis-final-restart-ui.xml'))
            break
        except E.ParseError:
            record['observations'].append('Fresh UI hierarchy unavailable; waited before retry')
            time.sleep(.5)
    if root is None:
        raise RuntimeError('No fresh UI hierarchy; refusing stale observation')
    data = E.tostring(root, encoding='unicode')
    if "System UI isn't responding" in data:
        waits = [n for n in root.iter('node') if n.get('text', '').lower() == 'wait']
        if not waits:
            raise RuntimeError('SystemUI ANR requires attention')
        tap(waits[0]); time.sleep(2)
        record['observations'].append('SystemUI ANR: normal Wait button selected; no protection disabled')
        return dump()
    return root

def bounds(node):
    values = [int(v) for v in re.findall(r'\d+', node.get('bounds', ''))]
    return values if len(values) == 4 else [0, 0, 0, 0]

def visible(node):
    a, b, c, d = bounds(node)
    return c > a and d > b and 0 <= (b + d) // 2 < 800

def tap(node):
    a, b, c, d = bounds(node)
    shell('input tap %d %d' % ((a + c) // 2, (b + d) // 2))
    time.sleep(.4)

def swipe(root, forward):
    containers = [n for n in root.iter('node') if n.get('class') == 'android.widget.ScrollView' and visible(n)]
    if not containers:
        raise RuntimeError('No visible workspace scroll viewport')
    left, top, right, bottom = bounds(containers[0])
    x = (left + right) // 2
    high, low = top + 35, bottom - 35
    if low <= high:
        raise RuntimeError('Invalid visible scroll viewport')
    start, end = (low, high) if forward else (high, low)
    shell('input swipe %d %d %d %d 300' % (x, start, x, end))

def hide_keyboard():
    state = shell('dumpsys input_method')
    if 'mInputShown=true' in state or 'mIsInputViewShown=true' in state:
        shell('input keyevent 4'); time.sleep(.4)
        record['observations'].append('On-screen keyboard dismissed with normal Back action before inspection')

def click(label, resource=None):
    for attempt in range(18):
        root = dump()
        for node in root.iter('node'):
            if visible(node) and ((resource and node.get('resource-id') == resource) or (not resource and node.get('text', '').casefold() == label.casefold())):
                tap(node); return
        swipe(root, attempt % 16 < 8)
    raise RuntimeError('UI control unavailable: ' + label)

def wait_ready():
    for _ in range(20):
        root = dump()
        if any('Atelier local prêt' in n.get('text', '') for n in root.iter('node')):
            return root
        time.sleep(.4)
    raise RuntimeError('Workspace not ready')

def state(job_id=None):
    if shell('pidof fr.jarvis.services.diagnostic').strip():
        raise RuntimeError('Refusing non-transactional copy of a live SQLite database')
    encoded = shell('run-as fr.jarvis.services.diagnostic cat files/workspace/journal.sqlite | base64')
    data = base64.b64decode(''.join(encoded.split()), validate=True)
    with tempfile.TemporaryDirectory(prefix='jarvis-final-db-') as tmp:
        path = Path(tmp) / 'snapshot.sqlite'; path.write_bytes(data)
        for suffix in ['-journal', '-wal', '-shm']:
            encoded_sidecar = shell('run-as fr.jarvis.services.diagnostic cat files/workspace/journal.sqlite' + suffix + ' 2>/dev/null | base64')
            if encoded_sidecar.strip():
                Path(str(path) + suffix).write_bytes(base64.b64decode(''.join(encoded_sidecar.split()), validate=True))
        with sqlite3.connect(path) as db:
            assert db.execute('PRAGMA integrity_check').fetchone()[0] == 'ok'
            rows = [json.loads(r[0]) for r in db.execute("SELECT body FROM entries WHERE namespace='final_jobs'")]
            matching = [r for r in rows if r.get('text') == MARKER and (job_id is None or r['id'] == job_id)]
            if not matching:
                raise RuntimeError('Synthetic job missing')
            job = max(matching, key=lambda r: r['created_at'])
            workflow = json.loads(db.execute("SELECT body FROM entries WHERE namespace='workflows' AND id=?", (job['id'],)).fetchone()[0])
            journal = [json.loads(r[0]) for r in db.execute("SELECT body FROM entries WHERE namespace='workflow_journal'")]
            verification_runs = sum(r.get('workflow') == job['id'] and r.get('step') == 'verify' and r.get('state') == 'RUNNING' for r in journal)
            return job, workflow, verification_runs

try:
    device.connect(auth_timeout_s=3)
    identity = shell('getprop ro.kernel.qemu; getprop ro.build.version.sdk; getprop sys.boot_completed').strip().splitlines()
    if identity != ['1', '26', '1']:
        raise RuntimeError('Refusing: expected booted isolated API26 emulator')
    package_path = shell('pm path fr.jarvis.services.diagnostic').strip()
    if not package_path.startswith('package:/data/app/'):
        raise RuntimeError('Unexpected isolated application path')
    apk_path = package_path[len('package:'):]
    if not re.fullmatch(r'/data/app/[A-Za-z0-9_./=+-]+/base\.apk', apk_path):
        raise RuntimeError('Invalid application path')
    device_digest = shell('sha256sum ' + shlex.quote(apk_path)).split()[0]
    expected_digest = hashlib.sha256((ROOT / 'android/build/outputs/apk/debug/android-debug.apk').read_bytes()).hexdigest()
    if device_digest != expected_digest:
        raise RuntimeError('Installed APK does not match qualification build')
    record['device_app_sha256'] = device_digest
    shell('am force-stop fr.jarvis.services.diagnostic')
    shell('am start -n ' + ACTIVITY)
    wait_ready(); hide_keyboard()
    click('PDF / consultation sourcée → DOCX')
    click('Créer un DOCX à partir du texte saisi')
    for _ in range(12):
        root = dump()
        target = next((n for n in root.iter('node') if visible(n) and n.get('class') == 'android.widget.EditText'), None)
        if target is not None:
            tap(target); break
        swipe(root, False)
    else:
        raise RuntimeError('Synthetic input unavailable')
    time.sleep(1); shell('input text FIN01'); time.sleep(1)
    if not any(n.get('text') == MARKER and n.get('class') == 'android.widget.EditText' for n in dump().iter('node')):
        raise RuntimeError('Exact synthetic input not accepted; refusing confirmation')
    shell('input keyevent 4')
    click('Préparer et prévisualiser'); wait_ready()
    click('Étape suivante / reprendre'); wait_ready()
    pid_before = shell('pidof fr.jarvis.services.diagnostic').strip()
    shell('am force-stop fr.jarvis.services.diagnostic')
    assert not shell('pidof fr.jarvis.services.diagnostic').strip()
    job, before, runs = state()
    assert before['steps']['verify'] == 'SUCCEEDED' and before['steps']['publish'] == 'PENDING' and runs == 1
    record['before_restart'] = {'pid': pid_before, 'job_id': job['id'], 'workflow': before['status'], 'steps': before['steps'], 'sequence': before['sequence'], 'verification_runs': runs, 'snapshot': 'stopped database plus any sidecars; integrity_check ok'}
    shell('am start -n ' + ACTIVITY); wait_ready(); hide_keyboard()
    pid_after = shell('pidof fr.jarvis.services.diagnostic').strip()
    view = None
    for attempt in range(16):
        root = dump()
        for node in root.iter('node'):
            text = node.get('text', '')
            if text.startswith('{'):
                try:
                    candidate = json.loads(text)
                    if candidate.get('preview') == MARKER and candidate.get('workflow', {}).get('id') == job['id']:
                        view = candidate
                        break
                except ValueError:
                    pass
        if view is not None:
            break
        swipe(root, True)
    if view is None:
        raise RuntimeError('Resumed native UI did not expose the expected persisted job')
    after = view['workflow']
    assert pid_after and pid_after != pid_before and after['steps'] == before['steps'] and after['sequence'] == before['sequence'] and runs == 1
    record['after_restart'] = {'pid': pid_after, 'job_id': job['id'], 'steps': after['steps'], 'sequence': after['sequence'], 'verification_runs': runs}
    for _ in range(2): swipe(dump(), False)
    click('Étape suivante / reprendre')
    dialog = dump()
    texts = '\n'.join(n.get('text', '') for n in dialog.iter('node'))
    if MARKER not in texts or 'Créer une nouvelle sortie privée' not in texts:
        raise RuntimeError('Exact synthetic preview unavailable; refusing confirmation')
    click('', resource='android:id/button1'); wait_ready()
    shell('am force-stop fr.jarvis.services.diagnostic')
    job, final, runs = state(job['id'])
    assert final['status'] == 'SUCCEEDED' and runs == 1
    name = job['output']
    assert re.fullmatch(r'result-[0-9a-f-]{36}\.docx', name)
    data = base64.b64decode(''.join(shell('run-as fr.jarvis.services.diagnostic cat files/workspace/files/' + name + ' | base64').split()), validate=True)
    assert hashlib.sha256(data).hexdigest() == job['encoded_digest']
    with zipfile.ZipFile(io.BytesIO(data)) as docx:
        xml = docx.read('word/document.xml'); assert b'<!DOCTYPE' not in xml
        assert ''.join(E.fromstring(xml).itertext()) == MARKER
    record['final'] = {'workflow': final['status'], 'verification_runs': runs, 'output_bytes': len(data), 'output_sha256': hashlib.sha256(data).hexdigest(), 'docx_text_readback_verified': True}
    record['success'] = True
except Exception as error:
    record['success'] = False; record['error_type'] = type(error).__name__; record['error'] = str(error)
finally:
    record['finished_at_utc'] = datetime.now(timezone.utc).isoformat()
    OUT.parent.mkdir(parents=True, exist_ok=True); OUT.write_text(json.dumps(record, indent=2, ensure_ascii=False) + '\n')
    try: device.close()
    except Exception: pass
print(json.dumps(record, indent=2, ensure_ascii=False))
raise SystemExit(0 if record['success'] else 1)
