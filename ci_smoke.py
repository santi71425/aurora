#!/usr/bin/env python3
"""Install both APKs, run device-side UI checks and save logs/screenshot."""
from pathlib import Path
import subprocess

DIST=Path('dist')
def adb(*args,check=True):
    return subprocess.run(['adb',*args],text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,check=check).stdout

adb('wait-for-device')
adb('install','-r',str(DIST/'Aurora-0.2.apk'))
adb('install','-r',str(DIST/'Aurora-tests.apk'))
adb('shell','pm','grant','ar.aurora.phone','android.permission.READ_MEDIA_IMAGES')
adb('shell','pm','grant','ar.aurora.phone','android.permission.READ_MEDIA_VIDEO')
adb('logcat','-c')
output=adb('shell','am','instrument','-w','-r','ar.aurora.phone.tests/ar.aurora.phone.tests.SmokeRunner',check=False)
(DIST/'emulator-tests.txt').write_text(output)
print(output,flush=True)
(DIST/'emulator-logcat.txt').write_text(adb('logcat','-d','-t','1000',check=False))
if 'AURORA_SMOKE_OK' not in output or 'AURORA_SMOKE_FAILED' in output:
    raise SystemExit('Fallaron las pruebas de interfaz. Revisá emulator-tests.txt y emulator-logcat.txt.')
adb('shell','am','start','-W','-n','ar.aurora.phone/.MainActivity')
adb('shell','screencap','-p','/sdcard/aurora.png')
adb('pull','/sdcard/aurora.png',str(DIST/'emulator-pantalla.png'))
print('Instalación y seis comprobaciones de interfaz correctas, incluida una captura creada para la prueba. No prueba cuentas reales ni el Samsung físico.')
