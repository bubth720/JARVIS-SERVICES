#!/usr/bin/env python3
"""Publish exact JUnit counts and safe failure annotations for synthetic Android qualification."""
import argparse,json,os,sys
from pathlib import Path
import xml.etree.ElementTree as ET
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--directory',required=True,type=Path)
p.add_argument('--expected',required=True,type=int)
p.add_argument('--output',required=True,type=Path)
a=p.parse_args();cases=[];files=[]
for f in sorted(a.directory.rglob('*.xml')):
    root=ET.parse(f).getroot()
    for c in root.iter('testcase'):
        failed=c.find('failure');error=c.find('error');skipped=c.find('skipped')
        problem=failed if failed is not None else error
        case={'class':c.get('classname'),'test':c.get('name'),'status':'FAILED' if problem is not None else 'SKIPPED' if skipped is not None else 'PASS'}
        if problem is not None:
            message=(problem.get('message') or problem.text or 'failure').strip().splitlines()[0]
            case['error']=message[:800]
            if os.environ.get('GITHUB_ACTIONS')=='true':
                safe=(str(case['class'])+'.'+str(case['test'])+': '+case['error']).replace('%','%25').replace('\r','%0D').replace('\n','%0A')
                print('::error::'+safe)
        cases.append(case)
    files.append(str(f))
result={'expected':a.expected,'tests':len(cases),'passed':sum(c['status']=='PASS' for c in cases),'failed':sum(c['status']=='FAILED' for c in cases),'skipped':sum(c['status']=='SKIPPED' for c in cases),'cases':cases,'files':files}
result['success']=result['tests']==a.expected and result['passed']==a.expected
a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(result,indent=2)+'\n')
line=f"Android XML: {result['passed']}/{result['expected']} passed; {result['failed']} failed; {result['skipped']} skipped; {result['tests']} observed.\n"
summary=os.environ.get('GITHUB_STEP_SUMMARY')
if summary:
    with open(summary,'a') as out:out.write(line)
print(line.strip());sys.exit(0 if result['success'] else 1)
