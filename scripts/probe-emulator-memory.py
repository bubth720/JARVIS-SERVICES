#!/usr/bin/env python3
"""Synthetic UI persistence proof; fixed local emulator only, never a personal device."""
import json,re,time,xml.etree.ElementTree as E
from pathlib import Path
from adb_shell.adb_device import AdbDeviceTcp
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'reports/002a'
MARKER='MEM002A'
d=AdbDeviceTcp('127.0.0.1',5581,default_transport_timeout_s=300)
record={'fixture':'synthetic_only','target':'verified_loopback_emulator','steps':[]}
def shell(command):return d.shell(command,transport_timeout_s=300,read_timeout_s=120,timeout_s=180)
def dump():
 shell('rm -f /data/local/tmp/jarvis-002a-ui.xml')
 shell('uiautomator dump /data/local/tmp/jarvis-002a-ui.xml')
 xml=shell('cat /data/local/tmp/jarvis-002a-ui.xml')
 if "System UI isn't responding" in xml:
  record['steps'].append('SystemUI ANR observed on TCG; selected normal Wait dialog')
  root=E.fromstring(xml)
  wait=next(n for n in root.iter('node') if n.get('text')=='Wait')
  tap(wait);time.sleep(2)
  shell('uiautomator dump /data/local/tmp/jarvis-002a-ui.xml')
  xml=shell('cat /data/local/tmp/jarvis-002a-ui.xml')
  if "System UI isn't responding" in xml:raise RuntimeError('SystemUI remains unresponsive')
 return xml
def center(node):
 values=list(map(int,re.findall(r'\d+',node.get('bounds',''))));assert len(values)==4
 return (values[0]+values[2])//2,(values[1]+values[3])//2
def tap(node):
 x,y=center(node);shell(f'input tap {x} {y}')
def button(label):
 for _ in range(7):
  root=E.fromstring(dump())
  for node in root.iter('node'):
   if label.casefold() in node.get('text','').casefold() and node.get('class')=='android.widget.Button':
    x,y=center(node)
    if 0<y<800:tap(node);return
  shell('input swipe 240 680 240 220 300')
 raise AssertionError('UI control unavailable: '+label)
try:
 d.connect(auth_timeout_s=3)
 assert shell('getprop ro.kernel.qemu').strip()=='1','Refusing non-emulator'
 shell('am force-stop fr.jarvis.services.diagnostic')
 shell('am start -W -n fr.jarvis.services.diagnostic/fr.jarvis.services.android.MainActivity')
 time.sleep(2)
 tree=E.fromstring(dump());edit=next(n for n in tree.iter('node') if n.get('class')=='android.widget.EditText')
 tap(edit);shell('input text '+MARKER);time.sleep(2);shell('input keyevent 111')
 button('Mémoriser une préférence')
 dialog=dump();assert MARKER in dialog,'Exact synthetic content must be displayed'
 button('Mémoriser 30 jours')
 confirmed=False
 for _ in range(8):
  tree=E.fromstring(dump())
  confirmed=any(n.get('class')=='android.widget.TextView' and MARKER in n.get('text','') and 'preference' in n.get('text','') and 'version' in n.get('text','') for n in tree.iter('node'))
  if confirmed:break
  shell('input swipe 240 680 240 220 300')
 assert confirmed,'Persistence receipt not visible before process stop' 
 before=shell('pidof fr.jarvis.services.diagnostic').strip();assert before
 record['steps'].append('Exact synthetic preference confirmed through diagnostic UI')
 shell('am force-stop fr.jarvis.services.diagnostic')
 shell('am start -W -n fr.jarvis.services.diagnostic/fr.jarvis.services.android.MainActivity')
 after=shell('pidof fr.jarvis.services.diagnostic').strip();assert after and before!=after,'Process must actually restart'
 shell('input keyevent 111');button('Lire les préférences confirmées');time.sleep(1)
 found=False
 for _ in range(7):
  tree=E.fromstring(dump())
  found=any(n.get('class')=='android.widget.TextView' and MARKER in n.get('text','') and 'preference' in n.get('text','') and 'version' in n.get('text','') for n in tree.iter('node'))
  if found:break
  shell('input swipe 240 680 240 220 300')
 assert found,'Confirmed memory result missing after process restart' 
 record.update({'success':True,'pid_before':before,'pid_after':after,'assertions':['confirmation_content_visible','distinct_process_pids','confirmed_memory_present_after_restart']})
 # No raw hierarchy is exported; it contains the synthetic value but may grow in future.
except Exception as error:record.update({'success':False,'error_type':type(error).__name__,'error':str(error)})
finally:
 d.close();(OUT/'process-restart.json').write_text(json.dumps(record,indent=2)+'\n')
print(json.dumps(record,indent=2))
raise SystemExit(0 if record['success'] else 1)
