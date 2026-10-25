# Aurora 0.2 para Android

Asistente de voz y texto para Android 11+, con un intérprete local ampliado. Acepta sinónimos, fórmulas de cortesía e intervalos escritos con palabras. No es un modelo conversacional ni ejecuta órdenes arbitrarias o cadenas de acciones.

## Instalar

En Actions abrí la ejecución correcta y descargá **Aurora-APK**. Extraé el ZIP y abrí **Aurora-0.2.apk** desde Mis archivos. **Esta compilación de prueba usa una firma nueva: desinstalá Aurora 0.1 antes de instalar la 0.2.** No borra tus fotos ni contactos al desinstalar, pero tendrás que volver a habilitar los permisos.

El APK se firma y verifica en CI; no está publicado ni revisado por Google Play. Android puede pedir permiso para instalar desde Mis archivos. Si Samsung bloquea la instalación, seguí sus indicaciones y reactivá cualquier protección que cambies.

Para controlar otras apps, habilitá **Ajustes → Accesibilidad → Aplicaciones instaladas → Aurora · Control de apps**. Si Android muestra Ajuste restringido, buscá **Ajustes → Aplicaciones → Aurora → ⋮ → Permitir ajustes restringidos**, si tu versión ofrece esa opción.

## Más formas de pedirlo

- Deslizá hacia arriba / hacia abajo / hacia la izquierda / hacia la derecha.
- Desplaza la pantalla hacia la derecha.
- Hacé scroll hacia la izquierda.
- ¿Podés deslizar hacia abajo tres veces en Google Fotos?
- Quiero que pases los reels cada veinte segundos.
- Pasá los reels hacia abajo cada 10 segundos.
- ¿Podés mostrarme capturas de pantalla?
- Mostrame videos grandes.
- Buscá videos de más de 200 MB.
- Fotos de enero de 2026 / fotos de hoy / fotos de ayer.
- Encuentra fotos de perros en Google Fotos.
- Buscá fotos de Madrid en Google Fotos.
- Buscá fotos con mujeres en Google Fotos.
- Quiero que me muestres fotos de perros.
- Elegir fotos de la nube.
- Escribile a Mom por WhatsApp: Te amo.
- ¿Podrías mandarle un mensaje a Mom por WhatsApp: Te amo?
- Detener / pará los reels / frena los reels.

El dictado y los ejemplos completan el cuadro. Revisá el texto y tocá Continuar. Las acciones que corresponda muestran una vista previa o una selección del destino.

## Gestos

Los gestos explícitos describen la dirección del **dedo**, no el movimiento del contenido. Podés pedir de 1 a 20 gestos y elegir Instagram, Google Fotos o Galería Samsung. Si no indicás app, Aurora te pregunta cuál usar. Abre esa app en su pantalla actual y hace el gesto; comprobá que esté en la pantalla deseada. DETENER cancela los gestos futuros; un gesto ya iniciado puede terminar.

Reels conserva la verificación del visor. Intervalos de 3 a 300 segundos, máximo 15 minutos. Si no reconoce el visor o salís de la app, se detiene. Usa tiempo fijo, no la duración del video.

## Buscar en el teléfono

La búsqueda local es de **metadatos**, no de contenido visual. Permite:

- Capturas: nombres o carpetas que contienen screenshot o captura.
- Videos grandes: desde 100 MiB por defecto; fotos grandes: desde 10 MiB. También un tamaño explícito en MB/GB.
- Hoy, ayer, este mes, mes pasado, un año o un mes con año. Sin año, un mes se refiere al año actual. Filtra por fecha de captura; si falta, usa la fecha agregada.
- Texto en nombres/carpetas, cámara y descargas.
- Videos/fotos, orden por tamaño, agregados recientes o antiguos.

Solo incluye archivos locales a los que Android concedió acceso. Con acceso a fotos seleccionadas, los resultados también son limitados. Muestra hasta 2000 coincidencias, en páginas de 20. Las búsquedas no borran archivos.

## Lugares, perros y personas; Google Fotos

Para contenido visual, elegí **Google Fotos** o **Galería Samsung** en la pantalla de búsqueda. Aurora muestra la consulta y, con Accesibilidad, intenta abrir el buscador, escribirla y confirmarla. También podés elegir **Copiar y abrir** y pegar la consulta vos.

La búsqueda se hace **en esa app con su cuenta actual**, incluyendo las fotos en la nube que esa app tenga indexadas. Los resultados dependen del buscador, la configuración de la cuenta y los controles que la app exponga. Aurora no analiza identidades ni garantiza clasificaciones de personas. Si el buscador no es reconocible, no simula que la búsqueda funcionó: lo indica y podés completarla manualmente.

Aurora **no tiene acceso directo a toda tu biblioteca de Google Fotos**, ni importa la nube completa. No hay autenticación OAuth de Google dentro de Aurora.

**Elegir de la nube** abre el selector de Android. Si tu teléfono ofrece Google Fotos como proveedor, podés elegir allí imágenes de la nube. Aurora recibe permiso solo para las seleccionadas (hasta 50 por selección). La disponibilidad depende de Android y Google Fotos. Se pueden ver miniaturas y abrirlas; esto no equivale a una búsqueda completa o a un análisis de duplicados de toda la nube.

## Duplicados y WhatsApp

La función de duplicados sigue limitada a imágenes locales con bytes idénticos. Conserva una copia y requiere selección explícita, revalidación y confirmación de Android. El borrado puede ser permanente; no se garantiza papelera.

WhatsApp sigue buscando el nombre exacto en Contactos y pidiendo revisar el número internacional. Usa el enlace oficial para abrir un borrador: **vos tocás Enviar en WhatsApp**. No lee conversaciones ni sobrescribe mensajes por Accesibilidad.

## Privacidad

Aurora no pide internet ni guarda contactos, fotos, búsquedas o audios en una base de datos. Accesibilidad solo inspecciona controles durante acciones explícitas en las tres apps habilitadas. Copiar y abrir coloca la consulta en el portapapeles por petición del usuario. Google Fotos, Samsung y el servicio de dictado pueden usar sus propias conexiones y políticas de datos.

No escucha en segundo plano. Usa dictado local si está disponible; solicita consentimiento para usar el dictado del teléfono si no hay uno local.

## Compilar y verificar

Proyecto Java nativo sin dependencias externas, Android SDK Platform/Build Tools 35, Java 17+ y Python 3.9+. Actions compila, firma y verifica ambos APKs, y prueba la app en Android 15.

`python3 build.py --test-only` ejecuta las comprobaciones locales del intérprete, filtros y hash. `python3 build.py` genera los APKs con el SDK configurado mediante ANDROID_HOME.

El emulador prueba las pantallas y una búsqueda sobre una captura que crea y elimina para la prueba. No contiene tus cuentas ni valida la interfaz real de Google Fotos, Samsung o Instagram. Esa compatibilidad debe probarse en el teléfono.
