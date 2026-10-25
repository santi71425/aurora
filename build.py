#!/usr/bin/env python3
"""Compile Android without Gradle dependencies, using the official installed SDK."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

ROOT=Path(__file__).resolve().parent
BUILD=ROOT/'build'
DIST=ROOT/'dist'
NS='{http://schemas.android.com/apk/res/android}'

def run(*args):
    print('Ejecutando:',Path(str(args[0])).name,flush=True)
    subprocess.run([str(arg) for arg in args],check=True)

def javac(*args):
    if shutil.which('javac'): run('javac',*args)
    else: run('java','com.sun.tools.javac.Main',*args)

def tests():
    classes=BUILD/'unit'; classes.mkdir(parents=True,exist_ok=True)
    sources=[ROOT/'app/src/main/java/ar/aurora/phone/CommandParser.java',ROOT/'app/src/main/java/ar/aurora/phone/PhoneCommands.java',ROOT/'app/src/main/java/ar/aurora/phone/WakePhrase.java',ROOT/'app/src/main/java/ar/aurora/phone/GalleryQuery.java',ROOT/'app/src/main/java/ar/aurora/phone/ContentHash.java',ROOT/'tests/CoreTests.java']
    javac('-encoding','UTF-8','-d',classes,*sources)
    run('java','-cp',classes,'ar.aurora.phone.CoreTests')

def main():
    parser=argparse.ArgumentParser(); parser.add_argument('--test-only',action='store_true'); args=parser.parse_args()
    BUILD.mkdir(exist_ok=True); tests()
    if args.test_only: return
    sdk=Path(os.environ.get('AURORA_ANDROID_SDK') or os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or '/nonexistent')
    tools=sdk/'build-tools/35.0.0'; android=sdk/'platforms/android-35/android.jar'
    if not android.exists() or not (tools/'aapt2').exists(): raise RuntimeError('Instalá Android SDK Platform 35 y Build Tools 35.0.0; configurá ANDROID_HOME.')
    DIST.mkdir(exist_ok=True)
    for folder in ['classes','generated','dex','test-classes','test-dex']:
        path=BUILD/folder
        if path.exists(): shutil.rmtree(path)
        path.mkdir()
    ET.register_namespace('android',NS[1:-1])
    manifest=ET.parse(ROOT/'app/src/main/AndroidManifest.xml'); manifest.getroot().set('package','ar.aurora.phone')
    manifest.getroot().set(NS+'versionCode','3'); manifest.getroot().set(NS+'versionName','0.3.0')
    manifest.write(BUILD/'AndroidManifest.xml',encoding='utf-8',xml_declaration=True)
    run(tools/'aapt2','compile','--dir',ROOT/'app/src/main/res','-o',BUILD/'resources.zip')
    run(tools/'aapt2','link','-I',android,'--manifest',BUILD/'AndroidManifest.xml','--java',BUILD/'generated','--min-sdk-version','30','--target-sdk-version','35','-o',BUILD/'unsigned.apk',BUILD/'resources.zip')
    sources=sorted((ROOT/'app/src/main/java').rglob('*.java'))+sorted((BUILD/'generated').rglob('*.java'))
    # Keep the JDK core definitions for lambda linkage; android.jar omits the
    # compiler-only LambdaMetafactory method. D8 desugars this Java 8 bytecode.
    javac('-encoding','UTF-8','-source','8','-target','8','-classpath',android,'-d',BUILD/'classes',*sources)
    def dex(classes,jar,output,classpath=None):
        with zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as archive:
            for p in classes.rglob('*.class'): archive.write(p,p.relative_to(classes).as_posix())
        options=['--classpath',classpath] if classpath else []
        run(tools/'d8','--min-api','30','--lib',android,*options,'--output',output,jar)
    dex(BUILD/'classes',BUILD/'classes.jar',BUILD/'dex')
    test_manifest=ET.Element('manifest',{'package':'ar.aurora.phone.tests'})
    ET.SubElement(test_manifest,'uses-sdk',{NS+'minSdkVersion':'30',NS+'targetSdkVersion':'35'})
    ET.SubElement(test_manifest,'application',{NS+'label':'Jarvis Tests'})
    ET.SubElement(test_manifest,'instrumentation',{NS+'name':'ar.aurora.phone.tests.SmokeRunner',NS+'targetPackage':'ar.aurora.phone',NS+'label':'Jarvis smoke tests'})
    ET.ElementTree(test_manifest).write(BUILD/'TestManifest.xml',encoding='utf-8',xml_declaration=True)
    run(tools/'aapt2','link','-I',android,'--manifest',BUILD/'TestManifest.xml','-o',BUILD/'test-unsigned.apk')
    javac('-encoding','UTF-8','-source','8','-target','8','-classpath',android,'-d',BUILD/'test-classes',ROOT/'tests/SmokeRunner.java')
    dex(BUILD/'test-classes',BUILD/'tests.jar',BUILD/'test-dex')
    # A local test signing key: no credentials or private keys are uploaded as artifacts.
    signing=ROOT/'.signing'; signing.mkdir(mode=0o700,exist_ok=True); keystore=signing/'aurora.jks'
    if not keystore.exists():
        run('keytool','-genkeypair','-keystore',keystore,'-alias','aurora','-storepass','android','-keypass','android',
            '-keyalg','RSA','-keysize','2048','-validity','3650','-dname','CN=Jarvis Test','-noprompt')
        keystore.chmod(0o600)
    for unsigned,dexdir,name in [('unsigned.apk','dex','Jarvis-0.3.apk'),('test-unsigned.apk','test-dex','Jarvis-tests.apk')]:
        with zipfile.ZipFile(BUILD/unsigned,'a',zipfile.ZIP_DEFLATED) as archive:
            for p in (BUILD/dexdir).glob('*.dex'): archive.write(p,p.name)
        aligned=BUILD/('aligned-'+name)
        run(tools/'zipalign','-f','4',BUILD/unsigned,aligned)
        run(tools/'apksigner','sign','--ks',keystore,'--ks-key-alias','aurora','--ks-pass','pass:android','--out',DIST/name,aligned)
        run(tools/'apksigner','verify','--verbose',DIST/name); run(tools/'zipalign','-c','4',DIST/name)
    run(tools/'aapt2','dump','badging',DIST/'Jarvis-0.3.apk')
    sha=hashlib.sha256((DIST/'Jarvis-0.3.apk').read_bytes()).hexdigest()
    (DIST/'Jarvis-0.3.apk.sha256').write_text(sha+'  Jarvis-0.3.apk\n')
    (DIST/'build-info.json').write_text(json.dumps({'version':'0.3.0','minSdk':30,'targetSdk':35,'sha256':sha,'signing':'temporary-test-key','physicalDeviceTested':False},indent=2)+'\n')
    print('APK generado y firma verificada:',DIST/'Jarvis-0.3.apk')

if __name__=='__main__':
    try: main()
    except (OSError,RuntimeError,subprocess.CalledProcessError) as error:
        print('Falló la compilación:',error,file=sys.stderr); sys.exit(1)
