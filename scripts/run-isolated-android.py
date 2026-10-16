#!/usr/bin/env python3
"""Cloud-only direct ADB protocol client for one isolated local emulator.

No discovery, no personal-device target, no auth disabling, no HOME override.
Install adb-shell==0.4.4 in a disposable tool directory (see qualification guide).
"""
import argparse, hashlib, json, re, sys, time
from pathlib import Path
from adb_shell.adb_device import AdbDeviceTcp

parser=argparse.ArgumentParser()
parser.add_argument('--app',required=True,type=Path)
parser.add_argument('--tests',required=True,type=Path)
parser.add_argument('--output',required=True,type=Path)
parser.add_argument('--expected',required=True,type=int)
parser.add_argument('--class-name')
parser.add_argument('--port',type=int,default=5581)
args=parser.parse_args()
if args.class_name and not re.fullmatch(r'[A-Za-z0-9_.]+(?:#[A-Za-z0-9_]+)?',args.class_name):parser.error('Invalid test class')
args.output.parent.mkdir(parents=True,exist_ok=True)
device=AdbDeviceTcp('127.0.0.1',args.port,default_transport_timeout_s=300)
started=time.monotonic();record={'host':'127.0.0.1','port':args.port,'expected_tests':args.expected,'apk':[]}
try:
 device.connect(auth_timeout_s=3)
 identity=device.shell('getprop ro.kernel.qemu; getprop ro.build.version.sdk; getprop sys.boot_completed',timeout_s=30).strip().splitlines()
 if len(identity)!=3 or identity[0]!='1' or int(identity[1])<26 or identity[2]!='1':raise RuntimeError('Refusing install: not a booted API26+ emulator')
 record['emulator_identity']={'qemu':True,'api':int(identity[1]),'boot_completed':True}
 for artifact,name in [(args.app,'app'),(args.tests,'tests')]:
  record['apk'].append({'kind':name,'sha256':hashlib.sha256(artifact.read_bytes()).hexdigest(),'bytes':artifact.stat().st_size})
  remote='/data/local/tmp/jarvis-002a-'+name+'.apk'
  print('Installing '+name+' on verified isolated emulator',flush=True)
  record['phase']='push_'+name
  device.push(str(artifact),remote,transport_timeout_s=300,read_timeout_s=300)
  record['phase']='install_'+name
  result=device.shell('pm install -r -t '+remote,transport_timeout_s=300,read_timeout_s=300,timeout_s=600)
  if 'Success' not in result:raise RuntimeError('Emulator installation failed: '+result)
  device.shell('rm '+remote)
 command='am instrument -w -r '
 if args.class_name:command+='-e class '+args.class_name+' '
 command+='fr.jarvis.services.diagnostic.test/androidx.test.runner.AndroidJUnitRunner'
 record['command']=command
 record['phase']='instrumentation'
 print('Running instrumentation',flush=True)
 with args.output.open('w') as log:
  for chunk in device.streaming_shell(command,read_timeout_s=300):log.write(chunk);log.flush()
 text=args.output.read_text();status={};cases=[];metrics=[]
 for line in text.splitlines():
  match=re.match(r'INSTRUMENTATION_STATUS: ([^=]+)=(.*)',line)
  if match:status[match[1]]=match[2]
  match=re.match(r'INSTRUMENTATION_STATUS_CODE: (-?\d+)',line)
  if match:
   code=int(match[1])
   if 'metric_scenario' in status:metrics.append(dict(status))
   elif 'test' in status and code!=1:cases.append({**status,'status_code':code})
   status={}
 record['cases']=cases;record['metrics']=metrics
 record['passed']=sum(c['status_code']==0 for c in cases)
 record['failed']=sum(c['status_code']<0 and c['status_code'] not in [-3,-4] for c in cases)
 record['skipped']=sum(c['status_code'] in [-3,-4] for c in cases)
 record['complete']='INSTRUMENTATION_CODE: -1' in text and len(cases)==args.expected
 record['success']=record['complete'] and record['passed']==args.expected and record['failed']==0 and record['skipped']==0
except Exception as error:
 record['success']=False;record['error_type']=type(error).__name__;record['error']=str(error)
finally:
 record['elapsed_host_seconds']=round(time.monotonic()-started,3)
 try:device.close()
 except Exception:pass
 args.output.with_suffix('.json').write_text(json.dumps(record,indent=2)+'\n')
print(json.dumps({k:record.get(k) for k in ['success','passed','failed','skipped','complete','error']},indent=2),flush=True)
sys.exit(0 if record['success'] else 1)
