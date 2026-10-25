#!/usr/bin/env python3
"""Install both APKs, run device-side UI checks and save logs/screenshot."""
from pathlib import Path
import subprocess

DIST=Path('dist')
def adb(*args,check=True):
    return subprocess.run(['adb',*args],text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,check=check).stdout

adb('wait-for-device')
adb('install','-r',str(DIST/'Jarvis-0.3.apk'))
adb('install','-r',str(DIST/'Jarvis-tests.apk'))
adb('shell','pm','grant','ar.aurora.phone','android.permission.READ_MEDIA_IMAGES')
adb('shell','pm','grant','ar.aurora.phone','android.permission.READ_MEDIA_VIDEO')
adb('logcat','-c')
output=adb('shell','am','instrument','-w','-r','ar.aurora.phone.tests/ar.aurora.phone.tests.SmokeRunner',check=False)
(DIST/'emulator-tests.txt').write_text(output)
print(output,flush=True)
crashlog=adb('logcat','-d','-t','1000',check=False)
(DIST/'emulator-logcat.txt').write_text(crashlog)
if 'JARVIS_SMOKE_OK' not in output or 'JARVIS_SMOKE_FAILED' in output:
    print('\n'.join(line for line in crashlog.splitlines() if any(word in line for word in ['AndroidRuntime','FATAL EXCEPTION','ar.aurora.phone','Caused by','AssertionError'])),flush=True)
    raise SystemExit('Fallaron las pruebas de interfaz. Revisá emulator-tests.txt y emulator-logcat.txt.')
adb('shell','am','start','-W','-n','ar.aurora.phone/.MainActivity')
adb('shell','screencap','-p','/sdcard/aurora.png')
adb('pull','/sdcard/aurora.png',str(DIST/'emulator-pantalla.png'))
print('Interfaz Jarvis, conversación, apagado de escucha y búsqueda local correctos. No prueba cuentas reales ni el Samsung físico.')
