#!/usr/bin/env python3
"""Record built APK digests, never document or credential contents."""
import argparse, hashlib, json, subprocess
from pathlib import Path
parser=argparse.ArgumentParser()
parser.add_argument('--output',type=Path,required=True)
args=parser.parse_args()
root=Path(__file__).resolve().parents[1]
files=sorted((root/'android/build/outputs/apk').rglob('*.apk'))
if len(files)!=2:raise SystemExit('Expected diagnostic and AndroidTest APKs')
manifest={'source_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip(),'tracked_tree_dirty':subprocess.run(['git','diff','--quiet','HEAD'],cwd=root).returncode!=0,'build_type':'debug','artifacts':[{'path':str(f.relative_to(root)),'bytes':f.stat().st_size,'sha256':hashlib.sha256(f.read_bytes()).hexdigest()} for f in files]}
args.output.parent.mkdir(parents=True,exist_ok=True)
args.output.write_text(json.dumps(manifest,indent=2)+'\n')
