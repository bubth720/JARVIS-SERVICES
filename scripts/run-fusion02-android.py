#!/usr/bin/env python3
"""Run only the FUSION-02 test APK on one booted, isolated loopback emulator.
No discovery, adb server, personal device, auth override or production activation.
Requires adb-shell==0.4.4, exactly selected API and qemu identity; keep TLS for tooling.
"""
import argparse, hashlib, json, re, sys, time, tempfile
from pathlib import Path
from adb_shell.adb_device import AdbDeviceTcp
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--apk',required=True,type=Path)
p.add_argument('--api',required=True,type=int,choices=[24,25,26,35])
p.add_argument('--class-name')
p.add_argument('--expected',type=int,default=14)
p.add_argument('--output',required=True,type=Path)
a=p.parse_args(); a.output.parent.mkdir(parents=True,exist_ok=True)
payload=a.apk.read_bytes()
snapshot=tempfile.NamedTemporaryFile(prefix='fusion-apk-',suffix='.apk',dir='/tmp',delete=False)
snapshot.write(payload);snapshot.close()
d=AdbDeviceTcp('127.0.0.1',5581,default_transport_timeout_s=300)
r={'api':a.api,'host':'127.0.0.1','port':5581,'apk_sha256':hashlib.sha256(payload).hexdigest(),'expected':a.expected,'cases':[]};start=time.monotonic()
try:
 for attempt in range(180):
  try:
   d.connect(auth_timeout_s=3)
   identity=d.shell('getprop ro.kernel.qemu; getprop ro.build.version.sdk; getprop sys.boot_completed',timeout_s=60,read_timeout_s=60).strip().splitlines()
   if len(identity)>=2 and (identity[0]!='1' or identity[1]!=str(a.api)):raise RuntimeError('Identity mismatch')
   if identity==['1',str(a.api),'1']:break
  except (OSError,ConnectionError):pass
  time.sleep(2)
 else:raise RuntimeError('Emulator boot deadline')
 r['identity']=identity;print('Verified isolated API'+str(a.api),flush=True)
 r['fingerprint']=d.shell('getprop ro.build.fingerprint',timeout_s=60,read_timeout_s=60).strip()
 d.shell('settings put global airplane_mode_on 1; am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true',timeout_s=60,read_timeout_s=60)
 r['airplane_mode']=d.shell('settings get global airplane_mode_on',timeout_s=60,read_timeout_s=60).strip()
 print('Installing laboratory APK',flush=True)
 remote='/data/local/tmp/fusion02-fixture.apk';d.push(snapshot.name,remote,transport_timeout_s=300,read_timeout_s=300)
 result=d.shell('pm install -r -t '+remote,timeout_s=300,read_timeout_s=300)
 if 'Success' not in result:raise RuntimeError(result)
 d.shell('rm '+remote,timeout_s=60,read_timeout_s=60)
 d.shell('pm clear fr.jarvis.services.android.adapter.test',timeout_s=60,read_timeout_s=60)
 if a.class_name and not re.fullmatch(r'[A-Za-z0-9_.]+(?:#[A-Za-z0-9_]+)?',a.class_name):raise ValueError('Invalid class')
 command='am instrument -w -r '+('-e class '+a.class_name+' ' if a.class_name else '')+'fr.jarvis.services.android.adapter.test/androidx.test.runner.AndroidJUnitRunner';r['command']=command
 print('Running instrumentation',flush=True)
 with a.output.open('w') as f:
  for chunk in d.streaming_shell(command,read_timeout_s=300):f.write(chunk);f.flush()
 log=a.output.read_text();status={}
 for line in log.splitlines():
  m=re.match(r'INSTRUMENTATION_STATUS: ([^=]+)=(.*)',line)
  if m:status[m[1]]=m[2]
  m=re.match(r'INSTRUMENTATION_STATUS_CODE: (-?\d+)',line)
  if m:
   code=int(m[1])
   if 'test' in status and code!=1:r['cases'].append({**status,'status_code':code})
   status={}
 r['complete']='INSTRUMENTATION_CODE: -1' in log and len(r['cases'])==a.expected
 r['passed']=sum(c['status_code']==0 for c in r['cases']);r['success']=r['complete'] and r['passed']==a.expected
except Exception as e:r['success']=False;r['error']=str(e);r['error_type']=type(e).__name__
finally:
 try:d.close()
 except Exception:pass
 Path(snapshot.name).unlink(missing_ok=True)
 r['elapsed_host_seconds']=round(time.monotonic()-start,3);a.output.with_suffix('.json').write_text(json.dumps(r,indent=2)+'\n')
print(json.dumps({k:r.get(k) for k in ['api','passed','complete','success','error']}));sys.exit(0 if r['success'] else 1)
