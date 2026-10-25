# Jarvis 0.3 para Android

Interfaz nativa de conversación con tema oscuro, respuestas por voz y órdenes de control del teléfono. Reemplaza Aurora. Se eliminó la automatización de Instagram/Reels: no hay intervalos ni deslizamientos automáticos por duración.

## Instalar y activar

1. En Actions descargá el artefacto **Jarvis-APK**, extraé el ZIP y abrí **Jarvis-0.3.apk**. **Desinstalá Aurora/Jarvis anterior primero**, porque esta compilación de prueba tiene una firma nueva. No se eliminan tus fotos o contactos al desinstalar; sí se reinician los permisos de la app.
2. En Jarvis abrí **⋮ → Configurar Accesibilidad** y activá **Jarvis · Control del teléfono**. Ahora el permiso cubre controles en distintas apps, no solo tres apps concretas. Si Android muestra Ajuste restringido, revisá Ajustes → Aplicaciones → Jarvis → ⋮ → Permitir ajustes restringidos, si tu versión ofrece esa opción.
3. Para que hable, dejá activada **⋮ → Voz de respuesta**. Usa el sintetizador y las voces de Android; no clona voces de personajes o personas. Prefiere una voz local en español. Si faltan voces o un sintetizador compatible, seguirá mostrando texto.
4. Para manos libres, tocá **Activar «OK Jarvis»**, leé la explicación y habilitá micrófono y, si querés ver el control en la cortina, notificaciones. **Se activa desde la app visible**, no automáticamente al instalar o reiniciar.
5. Decí **«OK Jarvis, bajá»**. También podés decir solo **«OK Jarvis»** y luego una orden dentro de 10 segundos.

El APK es un prototipo firmado en CI, no una app revisada por Google Play. El nombre del repositorio y el paquete interno se mantienen para continuar el proyecto; el nombre e interfaz visibles son Jarvis.

## Escucha continua y límites reales

La escucha requiere **reconocimiento local compatible de Android (API 31+) y un modelo de español disponible**. Se solicita mediante `createOnDeviceSpeechRecognizer`; no se reemplaza silenciosamente por dictado remoto. Si no está disponible, Jarvis lo indica. **⋮ → Preparar reconocimiento local** solicita el modelo al servicio de Android cuando ese servicio admite la operación; su disponibilidad y descarga no están garantizadas por la app.

El servicio muestra una notificación de **micrófono activo** y el indicador de privacidad de Android. Consume batería: usa sesiones sucesivas del reconocedor de Android, no un detector de palabras de bajo consumo integrado en el hardware. Android, el proveedor de voz y las restricciones de batería de Samsung pueden interrumpirlo, particularmente con pantalla apagada o bloqueada. No se garantiza una activación permanente como la de un asistente integrado del sistema.

Solo acepta la frase de activación al inicio del segmento reconocido. No ejecuta menciones de «OK Jarvis» dentro de otras frases. La detección no identifica biométricamente quién habla. Las menciones «OK», «okay», «okey», «oye» o «hey Jarvis» se aceptan. Solo se usan resultados finales; los resultados parciales no ejecutan órdenes. Si se activa sin orden, hay una ventana de 10 segundos para la siguiente frase.

Pausa la escucha mientras sintetiza una respuesta o se abre el dictado/revisión manual, para no ejecutar sus propias respuestas. Podés apagarla desde el botón inferior, la notificación, el control flotante o diciendo **«OK Jarvis, dejá de escuchar»**. Se apaga al quitar la tarea de la app; no tiene receptor de arranque automático.

El dictado manual del botón Mic usa reconocimiento local si lo hay. Si no, ofrece usar el servicio de dictado del teléfono **con consentimiento separado**: ese servicio puede enviar el audio de esa sesión a Samsung o Google. Eso no habilita escucha continua remota.

## Órdenes del teléfono

- **Bajá / subí / bajá un poco**: desplaza el contenido de la pantalla actual. «Bajá» mueve el dedo hacia arriba para avanzar en la página; «subí» hace lo contrario.
- **Desliza hacia arriba / abajo / izquierda / derecha**, también 2–20 veces: describen la dirección del dedo. Actúa en la pantalla actual o en una app explícita.
- **Abrí WhatsApp / abrí Chrome / abrí Google Fotos / abrí Ajustes**: busca apps instaladas por nombre. Si hay varias coincidencias, permite elegir. No se solicitan permisos de listado global de paquetes; se consultan las actividades de inicio visibles.
- **Atrás / inicio / apps recientes / abre notificaciones / abre ajustes rápidos**: usa las acciones de navegación que Android permita.
- **Subí el volumen / bajá el volumen / silencia**: ajusta el volumen multimedia, no cambia silenciosamente políticas de No molestar o permisos del sistema.
- **Captura de pantalla**: pide la captura mediante Android. Algunas apps y pantallas protegidas pueden impedirla.
- **Bloqueá mi teléfono**: requiere confirmar antes de solicitar el bloqueo.
- **Desbloqueá mi teléfono**: muestra una actividad que pide a Android su desbloqueo. **Nunca guarda, introduce o adivina PIN/patrón, ni evita huella, reconocimiento facial o autenticación.** Si el bloqueo es seguro, la persona debe autenticarse. La invocación con pantalla apagada depende de que Android mantenga disponible la escucha.
- **Toca el botón Buscar**: pide confirmar y toca solo un control visible con nombre exacto y sin ambigüedad. Usá OK Jarvis o el micrófono flotante con la app deseada abierta, para que la revisión aparezca sobre esa pantalla.
- **Escribe: Hola**: pide confirmar y agrega texto al campo seleccionado; no reemplaza otro borrador ni pulsa Enviar. No escribe en campos de contraseña. Igual que los toques, se usa desde OK Jarvis o la revisión flotante.
- **Detener**: cancela gestos y búsquedas pendientes; no apaga por sí solo el modo de activación. **Dejá de escuchar** apaga ese modo.
- **Hola / ¿qué podés hacer?**: respuestas habladas de saludo y ayuda. No es un modelo conversacional generativo.

El control no es total: respeta el bloqueo, campos de contraseña, pantallas protegidas, permisos y controles expuestos por cada app. No incluye root, evasión de protecciones, instalaciones silenciosas ni cambios de permisos sin participación del usuario.

## Micrófono flotante y chat

En **⋮ → Micrófono flotante** podés mostrar una barra pequeña con Hablar y Apagar. El dictado se hace en una ventana propia visible y temporal; no necesita permisos de superposición general. Su acceso depende del servicio de Accesibilidad. El botón Apagar detiene la escucha y oculta la barra.

En el chat, Mic completa el cuadro: revisá el dictado y tocá ↑. El botón + inicia una conversación vacía. Los mensajes se mantienen en memoria y se limitan a los últimos 80; no hay base de datos ni sincronización de conversaciones. Un borrador puede conservarse por el mecanismo de estado de actividad de Android.

## Fotos y WhatsApp conservados

- **Mostrame capturas de pantalla**, **videos grandes**, **fotos de enero de 2026**: filtros locales de tipo, tamaño, fecha, nombre y carpeta. Videos grandes significa desde 100 MiB. Las palabras se buscan en metadatos, no en el contenido visual.
- **Buscá fotos de perros / Madrid / con mujeres en Google Fotos**: delega la consulta al buscador de Google Fotos o de Galería Samsung. Sus resultados dependen de su indexación y su interfaz. Si no reconoce el buscador, lo indica y podés completar la acción manualmente o usar Copiar y abrir.
- **Elegir fotos de la nube**: selector de Android, con Google Fotos cuando el proveedor esté disponible. Jarvis recibe permiso solo sobre las imágenes elegidas, no toda la biblioteca. No importa ni analiza automáticamente toda la nube.
- **Buscá fotos duplicadas**: conserva la detección local exacta, selección explícita, revalidación y confirmación de borrado de Android. El borrado puede ser permanente.
- **Escribile a Mom por WhatsApp: Te amo**: conserva el buscador de Contactos y la revisión del número. Prepara el mensaje mediante el enlace oficial; vos lo enviás desde WhatsApp.

## Privacidad y validación

La app no solicita internet ni guarda archivos de audio, pantallas o conversaciones en una base de datos. El reconocedor local recibe audio ambiente mientras está activo, pero solo las órdenes activadas se agregan a la conversación. No se guardan transcripciones de las frases ignoradas. El sintetizador de Android y los servicios de las otras apps se rigen por su configuración y políticas; se prefiere síntesis local en español.

Accesibilidad recibe cambios de ventana, usa metadatos de paquete para recordar la app actual cuando los controles de voz están activos, y solo consulta los controles de pantalla para una acción explícita. Si encuentra una pantalla bloqueada o un campo de contraseña, cancela la manipulación. Los toques/texto requieren revisión; los mensajes y borrados conservan sus confirmaciones.

Las pruebas locales cubren comandos, dirección de desplazamiento, frase de activación, rechazo de menciones no activadas, retirada de Reels, filtros de galería y hash. El emulador verifica la interfaz, el compositor visible, controles de conversación, inicio sin micrófono continuo, apagado, revisiones y búsqueda de una captura creada para el ensayo. **La escucha real en español, la voz, el bloqueo seguro y las interfaces reales de otras apps requieren probarse en el Samsung.**

Para compilar: Android SDK Platform/Build Tools 35, Java 17+, Python 3.9+ y `python3 build.py`. Solo pruebas sin SDK: `python3 build.py --test-only`. Las claves de prueba son temporales por ejecución; cada recompilación puede exigir desinstalar la anterior.
