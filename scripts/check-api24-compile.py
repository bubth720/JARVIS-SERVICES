#!/usr/bin/env python3
"""Compile the real portable + Android sources against Google's API24 boot classes.

No JDK17 boot APIs, no dependency-verification bypass, no Android installation.
core-lambda-stubs.jar is a compiler bootstrap stub, never packaged in an artifact.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--sdk', type=Path, required=True)
    p.add_argument('--dependency-cache', type=Path, required=True)
    p.add_argument('--javac', default='javac')
    p.add_argument('--output', type=Path, required=True)
    args = p.parse_args()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    metadata = ET.parse(ROOT / 'gradle/verification-metadata.xml')
    ns = {'v': 'https://schema.gradle.org/dependency-verification'}
    def dependency(group, name, version, extension):
        candidates = list((args.dependency_cache / group / name / version).glob('*/*.' + extension))
        if len(candidates) != 1:
            raise ValueError('Expected one locked artifact: ' + name)
        artifact = candidates[0]
        digest = hashlib.sha256(artifact.read_bytes()).hexdigest()
        component = metadata.find(f'.//v:component[@group="{group}"][@name="{name}"][@version="{version}"]', ns)
        trusted = set() if component is None else {e.get('value') for a in component.findall('v:artifact', ns) if a.get('name') == artifact.name for e in a.findall('v:sha256', ns)}
        if digest not in trusted:
            raise ValueError('Artifact is not SHA256-locked: ' + name)
        return artifact
    gson = dependency('com.google.code.gson', 'gson', '2.11.0', 'jar')
    pdf = dependency('com.tom-roush', 'pdfbox-android', '2.0.27.0', 'aar')
    with zipfile.ZipFile(pdf) as z:
        pdf_jar = output / 'pdfbox-compiler-classpath.jar'
        pdf_jar.write_bytes(z.read('classes.jar'))
    android = args.sdk / 'platforms/android-24/android.jar'
    lambdas = args.sdk / 'build-tools/35.0.0/core-lambda-stubs.jar'
    for f in (android, lambdas):
        if not f.is_file():
            raise ValueError('Install official SDK prerequisite: ' + str(f))
    sources = sorted((ROOT / 'services-portable-api/src/main/java').rglob('*.java')) + sorted((ROOT / 'services-android-adapter/src/main/java').rglob('*.java'))
    classes = output / 'classes'
    command = [args.javac, '-source', '8', '-target', '8', '-encoding', 'UTF-8', '-bootclasspath', str(android) + ':' + str(lambdas), '-classpath', str(gson) + ':' + str(pdf_jar), '-d', str(classes)] + [str(s) for s in sources]
    run = subprocess.run(command, cwd=output, text=True, capture_output=True)
    (output / 'compile.log').write_text(run.stdout + run.stderr)
    result = {'api': 24, 'type': 'compile_only_no_runtime_or_device_claim', 'source_files': len(sources), 'exit_code': run.returncode, 'sha256': {str(f): hashlib.sha256(f.read_bytes()).hexdigest() for f in (android, lambdas, gson, pdf)}}
    (output / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result))
    if run.returncode:
        print(run.stdout + run.stderr)
    return run.returncode


if __name__ == '__main__':
    raise SystemExit(main())
