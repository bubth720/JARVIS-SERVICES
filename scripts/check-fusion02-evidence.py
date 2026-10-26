#!/usr/bin/env python3
"""Validate published FUSION-02 evidence without upgrading laboratory proof to production GO."""
import hashlib, json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def check(root=ROOT/'docs/fusion-02'):
    v=json.loads((root/'VALIDATION.json').read_text())
    assert v['verdict']=='PARTIAL', 'Unreviewed production integration cannot be READY'
    assert v['production_export_enabled'] is False
    for name,digest in v['evidence_sha256'].items():
        path=(root/name).resolve()
        assert path.is_relative_to(root.resolve()), 'Unsafe evidence path'
        assert hashlib.sha256(path.read_bytes()).hexdigest()==digest, 'Evidence digest mismatch: '+name
    for api,record in v['instrumentation'].items():
        if record['status']!='PASS':continue
        r=json.loads((root/record['record']).read_text())
        assert r['api']==int(api) and r['identity']==['1',api,'1']
        assert r['success'] and r['complete'] and r['passed']==15 and len(r['cases'])==15
        assert all(c['status_code']==0 for c in r['cases'])
        assert r['apk_sha256']==v['artifacts']['test_apk']['sha256']
        assert r['fixture_apk_sha256']==v['artifacts']['fixture_apk']['sha256']
        assert len(r['uid_metrics'])>=14 and all(m['worker_uid']!=m['fixture_provider_uid'] for m in r['uid_metrics'])
        raw=(root/record['record']).with_suffix('.log').read_text()
        assert 'OK (15 tests)' in raw and 'INSTRUMENTATION_CODE: -1' in raw
        assert raw.count('INSTRUMENTATION_STATUS_CODE: 0')==15
        assert 'INSTRUMENTATION_STATUS_CODE: -2' not in raw
    assert v['jvm']['tests']==100 and v['jvm']['failures']==0 and v['jvm']['skipped']==0
    assert v['robolectric']['tests']==40 and v['robolectric']['failures']==0 and v['robolectric']['skipped']==0
    print('FUSION-02 evidence consistent; PARTIAL, production activation blocked')
if __name__=='__main__':check()
