#!/usr/bin/env python3
"""CI-only Android OS readiness. Acknowledge at most one System UI ANR with Wait,
record it, and fail if the OS remains unstable. Never hide application test failures.
No permission grants, root, ANR disabling, app install or personal-device discovery.
"""
import argparse,json,os,re,subprocess,time,xml.etree.ElementTree as ET
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--api',type=int,required=True,choices=[24,25,26,35]);p.add_argument('--output',type=Path,required=True);p.add_argument('--capture-only',action='store_true');a=p.parse_args()
if os.environ.get('GITHUB_ACTIONS')!='true':raise SystemExit('CI-only: refusing access outside GitHub Actions')
a.output.mkdir(parents=True,exist_ok=True);record={'api':a.api,'serial':'emulator-5554','os_anr_wait_acknowledged':False,'attempts':[],'success':False,'capture_only':a.capture_only}
def adb(*args,timeout=40):
    r=subprocess.run(['adb','-s','emulator-5554',*args],capture_output=True,timeout=timeout)
    if r.returncode:raise RuntimeError(r.stderr.decode(errors='replace')[:400])
    return r.stdout
try:
    emulator_property='ro.boot.qemu' if a.api>=35 else 'ro.kernel.qemu'
    identity=adb('shell','getprop '+emulator_property+'; getprop ro.build.version.sdk; getprop sys.boot_completed').decode().strip().splitlines()
    record['emulator_property']=emulator_property;record['identity_observed']=identity
    assert identity==['1',str(a.api),'1'],'Not the requested booted CI emulator'
    record['identity']=identity
    if not a.capture_only:adb('shell','input keyevent KEYCODE_WAKEUP; input keyevent KEYCODE_MENU')
    for attempt in range(4):
        adb('shell','rm -f /data/local/tmp/fusion-readiness.xml')
        adb('shell','uiautomator dump /data/local/tmp/fusion-readiness.xml')
        raw=adb('shell','cat /data/local/tmp/fusion-readiness.xml')
        (a.output/f'ui-{attempt}.xml').write_bytes(raw)
        (a.output/f'ui-{attempt}.png').write_bytes(adb('exec-out','screencap -p'))
        tree=ET.fromstring(raw);nodes=list(tree.iter('node'));texts=[n.get('text','') for n in nodes]
        record['attempts'].append({'attempt':attempt,'texts':texts})
        if a.capture_only:record['success']=True;break
        anr=any('responding' in t.lower() for t in texts)
        if not anr:
            if not nodes:raise RuntimeError('No UI hierarchy; readiness not demonstrated')
            record['success']=True;break
        if record['os_anr_wait_acknowledged'] or not any('system ui' in t.lower() for t in texts):
            raise RuntimeError('Persistent or non-System-UI ANR: refusing to conceal failure')
        wait=next((n for n in nodes if n.get('text','').lower()=='wait'),None)
        if wait is None:raise RuntimeError('System UI ANR has no explicit Wait control')
        bounds=re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',wait.get('bounds',''))
        if bounds is None:raise RuntimeError('Invalid Wait bounds')
        x1,y1,x2,y2=map(int,bounds.groups());assert x2>x1 and y2>y1
        adb('shell',f'input tap {(x1+x2)//2} {(y1+y2)//2}')
        record['os_anr_wait_acknowledged']=True;time.sleep(2)
    if not record['success']:raise RuntimeError('OS readiness not established')
except Exception as error:record['error']=str(error)
finally:(a.output/'readiness.json').write_text(json.dumps(record,indent=2)+'\n')
if not record['success']:
    error=('Android OS readiness API'+str(a.api)+': '+str(record.get('error'))+'; observed='+str(record.get('identity_observed'))).replace('%','%25').replace('\r','%0D').replace('\n','%0A')
    print('::error::'+error)
print(json.dumps({'success':record['success'],'os_anr_wait_acknowledged':record['os_anr_wait_acknowledged'],'error':record.get('error')}))
raise SystemExit(0 if record['success'] else 1)
