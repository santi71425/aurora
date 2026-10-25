# Jarvis para Android

Asistente personal nativo con interfaz de conversación, respuestas por voz y activación «OK Jarvis» mediante reconocimiento local de Android. Evolución del prototipo Aurora; no incluye automatización de Reels.

Ver [instalación, comandos y límites](GUIA.md).

## Funciones

- Chat oscuro, dictado manual y síntesis de voz del sistema.
- Escucha continua opcional, con servicio de micrófono y control para apagarla.
- Abrir apps, desplazar la pantalla, navegación, volumen y captura mediante acciones permitidas por Android.
- Toques y escritura con revisión; búsqueda de fotos y borradores de WhatsApp.
- Desbloqueo delegado a Android: requiere la autenticación habitual, nunca evita PIN o biometría.

El reconocimiento local de español y la ejecución con pantalla apagada dependen del servicio de voz y del teléfono. No equivale a un asistente integrado con detector de activación de bajo consumo y no es un modelo conversacional general.

## Compilar

Java 17+, Python 3.9+, Android SDK Platform 35 y Build Tools 35.0.0:

```sh
python3 build.py
```

Solo comprobaciones de lógica sin SDK: `python3 build.py --test-only`.

El workflow de Actions genera `Jarvis-0.3.2.apk` y ejecuta comprobaciones de interfaz en Android 15. Las claves de prueba son temporales: puede ser necesario desinstalar la compilación anterior. No se suben claves privadas como artefactos.

Las pruebas automatizadas no validan micrófono real, español instalado, voz, bloqueo seguro o interfaces reales de apps de terceros en el Samsung del usuario.

La activación OK Jarvis abre una sesión de 30 segundos, renovada por cada orden válida, para dar varias órdenes sin repetir la frase. Al vencer vuelve a esperar la activación.
