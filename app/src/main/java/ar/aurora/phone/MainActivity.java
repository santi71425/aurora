package ar.aurora.phone;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;

/** Push-to-talk command interface. No background microphone or remote language model. */
public final class MainActivity extends Activity {
    private static final int MIC=10, VOICE=11;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private EditText input;
    private TextView feedback, serviceStatus;
    private Button mic;
    private SpeechRecognizer recognizer;
    private boolean listening;
    private final Runnable update=new Runnable() {
        public void run() { serviceStatus.setText(ReelsService.status); handler.postDelayed(this,1000); }
    };
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true);
        LinearLayout root=AppUi.column(this); scroll.addView(root); setContentView(scroll); AppUi.insets(this,root);
        TextView eyebrow=AppUi.text(this,"TU TELÉFONO, A TU PEDIDO",11,AppUi.ACCENT); eyebrow.setLetterSpacing(0.12f); root.addView(eyebrow);
        root.addView(AppUi.text(this,"Aurora",42,AppUi.TEXT),AppUi.spaced(this));
        root.addView(AppUi.text(this,"Escribí o hablá. Vos elegís cuándo actuar.",16,AppUi.MUTED));
        LinearLayout card=AppUi.card(this); card.addView(AppUi.text(this,"¿Qué querés hacer?",18,AppUi.TEXT));
        input=new EditText(this); input.setTextColor(AppUi.TEXT); input.setHintTextColor(AppUi.MUTED);
        input.setHint("Escribile a Mom por WhatsApp: Te amo"); input.setMinLines(3); input.setMaxLines(6);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        card.addView(input);
        LinearLayout buttons=new LinearLayout(this);
        mic=AppUi.button(this,"Hablar",false); mic.setOnClickListener(v -> { if (listening) stopVoice(); else voice(); });
        Button go=AppUi.button(this,"Continuar →",true); go.setOnClickListener(v -> execute());
        buttons.addView(mic,new LinearLayout.LayoutParams(0,-2,1)); buttons.addView(go,new LinearLayout.LayoutParams(0,-2,1));
        card.addView(buttons); root.addView(card,AppUi.spaced(this));
        feedback=AppUi.text(this,"El dictado completa el cuadro. Revisá el texto y tocá Continuar.",14,AppUi.MUTED); root.addView(feedback,AppUi.spaced(this));
        addExample(root,"WhatsApp","Escribile a Mom por WhatsApp: Te amo","Buscá el contacto, revisá y enviá desde WhatsApp.");
        addExample(root,"Reels","Pasá los reels cada 20 segundos","Sesión de hasta 15 minutos, con botón DETENER.");
        addExample(root,"Galería","Mostrame capturas de pantalla","También: videos grandes, fotos de enero, nombres y carpetas.");
        addExample(root,"Google Fotos","Buscá fotos de perros en Google Fotos","Búsquedas por contenido en la app con tu cuenta actual.");
        addExample(root,"Gestos","Deslizá hacia abajo en Instagram","Arriba, abajo, izquierda o derecha. Hasta 20 gestos pedidos.");
        addExample(root,"Duplicados","Buscá fotos duplicadas","Copias exactas; revisás antes de borrar.");
        serviceStatus=AppUi.text(this,ReelsService.status,12,AppUi.MUTED); root.addView(serviceStatus,AppUi.spaced(this));
        Button stop=AppUi.button(this,"Detener automatización",false); stop.setOnClickListener(v -> stopAutomation()); root.addView(stop,AppUi.spaced(this));
        Button settings=AppUi.button(this,"Configurar control de apps",false); settings.setOnClickListener(v -> accessibility()); root.addView(settings,AppUi.spaced(this));
        Button help=AppUi.button(this,"Privacidad, permisos y ayuda",false); help.setOnClickListener(v -> help()); root.addView(help,AppUi.spaced(this));
        root.addView(AppUi.text(this,"VERSIÓN 0.2 · MÁS FORMAS DE PEDIRLO\nSin escucha permanente ni cuenta de Aurora.",11,AppUi.MUTED),AppUi.spaced(this));
        if (saved!=null) input.setText(saved.getString("input",""));
    }
    private void addExample(LinearLayout root,String title,String command,String detail) {
        LinearLayout card=AppUi.card(this); card.addView(AppUi.text(this,title,17,AppUi.TEXT));
        card.addView(AppUi.text(this,command,14,AppUi.ACCENT),AppUi.spaced(this));
        card.addView(AppUi.text(this,detail,12,AppUi.MUTED),AppUi.spaced(this));
        card.setContentDescription(command+". Tocar para completar el comando.");
        card.setOnClickListener(v -> { input.setText(command); input.setSelection(input.length()); feedback.setText("Revisá el comando y tocá Continuar."); });
        root.addView(card,AppUi.spaced(this));
    }
    private void execute() {
        stopVoice(); CommandParser.Command c=CommandParser.parse(input.getText().toString());
        switch(c.type) {
            case STOP: stopAutomation(); break;
            case WHATSAPP:
                stopAutomation(); startActivity(new Intent(this,WhatsAppActivity.class).putExtra("contact",c.contact).putExtra("message",c.message)); break;
            case DUPLICATES: stopAutomation(); startActivity(new Intent(this,DuplicatesActivity.class)); break;
            case GALLERY:
                stopAutomation();startActivity(new Intent(this,GalleryActivity.class).putExtra("query",c.query).putExtra("target",c.target));break;
            case PICK:
                stopAutomation();startActivity(new Intent(this,GalleryActivity.class).putExtra("query","").putExtra("target","picker"));break;
            case SCROLL:
                if(ReelsService.instance==null) {accessibility();return;}
                if(c.target.isEmpty()) new AlertDialog.Builder(this).setTitle("¿Dónde hago el gesto?")
                    .setItems(new String[]{"Instagram","Google Fotos","Galería Samsung"},(d,w) -> swipe(c,new String[]{"instagram","google","samsung"}[w])).setNegativeButton("Cancelar",null).show();
                else swipe(c,c.target);
                break;
            case REELS:
                if (ReelsService.instance==null) { accessibility(); return; }
                new AlertDialog.Builder(this).setTitle("Pasar Reels automáticamente")
                    .setMessage("Cada "+c.seconds+" segundos, hacia "+(c.direction.equals("down")?"abajo":"arriba")+", hasta 15 minutos. Instagram se abrirá; entrá manualmente a un reel. Aurora solo desliza cuando identifica el visor. Tocá DETENER, salí de Instagram o bloqueá el teléfono para terminar.")
                    .setNegativeButton("Cancelar",null).setPositiveButton("Iniciar",(d,w) -> {
                        ReelsService s=ReelsService.instance; if(s!=null) { s.start(c.seconds,c.direction); feedback.setText(ReelsService.status); } else accessibility();
                    }).show(); break;
            default:
                new AlertDialog.Builder(this).setTitle("No reconocí ese comando")
                    .setMessage("Podés decir, por ejemplo:\n• ¿Podés mostrarme capturas de pantalla?\n• Mostrame videos grandes\n• Buscá fotos de enero de 2026\n• Encuentra fotos de perros en Google Fotos\n• Desliza hacia abajo en Instagram\n• Hacé scroll hacia la izquierda\n• Pasá los reels cada veinte segundos\n• Detener\n\nEl intérprete admite sinónimos y fórmulas de cortesía para estas tareas. No es un modelo conversacional ni ejecuta órdenes arbitrarias.")
                    .setPositiveButton("Entendido",null).show();
        }
    }
    private void swipe(CommandParser.Command c,String target) {
        String direction=c.direction.equals("down")?"abajo":c.direction.equals("left")?"la izquierda":c.direction.equals("right")?"la derecha":"arriba";
        new AlertDialog.Builder(this).setTitle("Deslizar hacia "+direction)
            .setMessage("Moveré el dedo hacia "+direction+", "+c.count+" vez/veces, dentro de la app elegida. La app se abrirá en su pantalla actual. Revisá que sea la pantalla que querés mover; DETENER cancela los gestos siguientes.")
            .setNegativeButton("Cancelar",null).setPositiveButton("Hacer gesto",(d,w) -> {if(ReelsService.instance!=null) ReelsService.instance.swipe(target,c.direction,c.count);else accessibility();}).show();
    }
    private void stopAutomation() { if(ReelsService.instance!=null) ReelsService.instance.stop(); feedback.setText("Automatización detenida."); }
    private void accessibility() {
        new AlertDialog.Builder(this).setTitle("Control de apps")
            .setMessage("Accesibilidad permite identificar controles, hacer gestos y escribir búsquedas en Instagram, Google Fotos y Galería Samsung. Solo actúa cuando lo pedís; no guarda contenido.\n\nEn Ajustes → Accesibilidad → Aplicaciones instaladas, activá Aurora · Control de apps. Podés desactivarlo cuando quieras. Si Android muestra ‘Ajuste restringido’, revisá la ayuda de instalación.")
            .setNegativeButton("Ahora no",null).setPositiveButton("Abrir Ajustes",(d,w) -> {
                try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); } catch(ActivityNotFoundException e) { feedback.setText("Abrí Ajustes → Accesibilidad manualmente."); }
            }).show();
    }
    private Intent voiceIntent() {
        return new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE,"es-AR").putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true).putExtra(RecognizerIntent.EXTRA_PROMPT,"Decile a Aurora qué querés hacer");
    }
    private void voice() {
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) { requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MIC); return; }
        if(Build.VERSION.SDK_INT>=31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            try {
                if(recognizer==null) {
                    recognizer=SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
                    recognizer.setRecognitionListener(new RecognitionListener() {
                        public void onReadyForSpeech(Bundle b) { feedback.setText("Te escucho…"); }
                        public void onBeginningOfSpeech() { }
                        public void onRmsChanged(float rms) { }
                        public void onBufferReceived(byte[] data) { }
                        public void onEndOfSpeech() { feedback.setText("Procesando dictado local…"); }
                        public void onError(int code) { listening=false; mic.setText("Hablar"); feedback.setText("No pude completar el dictado local ("+code+"). Revisá el idioma español del servicio de voz o usá el teclado."); }
                        public void onResults(Bundle b) { listening=false; mic.setText("Hablar"); accept(b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)); }
                        public void onPartialResults(Bundle b) { }
                        public void onEvent(int code,Bundle b) { }
                    });
                }
                listening=true; mic.setText("Cancelar escucha"); recognizer.startListening(voiceIntent());
            } catch(RuntimeException unavailable) { stopVoice(); feedback.setText("No pude abrir el micrófono. Usá el teclado."); }
        } else if(getPreferences(MODE_PRIVATE).getBoolean("systemVoice",false)) systemVoice();
        else new AlertDialog.Builder(this).setTitle("Dictado del teléfono")
            .setMessage("No hay dictado local disponible. El servicio de voz de tu teléfono puede enviar el audio a Samsung/Google y necesitar internet. Aurora solo recibe el texto. También podés escribir.")
            .setNegativeButton("Escribir",null).setPositiveButton("Usar dictado",(d,w) -> { getPreferences(MODE_PRIVATE).edit().putBoolean("systemVoice",true).apply(); systemVoice(); }).show();
    }
    private void systemVoice() {
        try { startActivityForResult(voiceIntent(),VOICE); }
        catch(ActivityNotFoundException e) { feedback.setText("No hay un servicio de dictado disponible. Usá el teclado o configurá el reconocimiento de voz del teléfono."); }
    }
    private void accept(ArrayList<String> result) {
        if(result!=null && !result.isEmpty()) { input.setText(result.get(0)); input.setSelection(input.length()); feedback.setText("Revisá el texto y tocá Continuar."); }
    }
    private void stopVoice() { if(recognizer!=null&&listening) recognizer.cancel(); listening=false; if(mic!=null) mic.setText("Hablar"); }
    private void help() {
        new AlertDialog.Builder(this).setTitle("Aurora 0.2")
            .setMessage("NOVEDADES\nPodés pedir gestos hacia arriba, abajo, izquierda o derecha. La búsqueda En teléfono filtra por nombre, carpeta, fecha y tamaño; videos grandes significa desde 100 MB. Para lugares, perros o personas, elegí Google Fotos o Galería Samsung: sus buscadores procesan la consulta y muestran los resultados. Elegir de la nube abre el selector de Android, con Google Fotos si está disponible como proveedor. Aurora no importa toda la nube.\n\nINSTALAR\nAbrí el APK en Mis archivos y seguí los permisos de instalación. Si Samsung impide instalarlo, revisá Bloqueador automático y reactivá esa protección después.\n\nACCESIBILIDAD RESTRINGIDA\nSi aparece esa restricción: Ajustes → Aplicaciones → Aurora → ⋮ → Permitir ajustes restringidos, si tu versión muestra la opción. Después activá Aurora en Accesibilidad.\n\nWHATSAPP\nBusca el nombre exacto en Contactos; necesita permiso de agenda. Elegís y revisás el número internacional. WhatsApp se abre con el texto; vos tocás Enviar. No accede a conversaciones.\n\nREELS\nNecesita Accesibilidad. Abrí el visor manualmente tras iniciar una sesión. Pasa por tiempo, no al finalizar cada video. Si no reconoce el visor, no desliza. Botón DETENER y límite de 15 minutos.\n\nFOTOS\nCompara archivos idénticos y conserva una copia de cada grupo. Seleccionás las otras y Android pide confirmar el borrado. Puede ser permanente; no se garantiza papelera. Solo revisa las fotos permitidas.\n\nPRIVACIDAD\nAurora no solicita internet ni guarda contactos, fotos o audio. El servicio de dictado del sistema puede usar internet con su propio permiso. Sin modelo generativo ni escucha de fondo.\n\nDesactivá Aurora en Accesibilidad y desinstalala para quitarla.")
            .setPositiveButton("Cerrar",null).show();
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] grants) {
        super.onRequestPermissionsResult(code,permissions,grants);
        if(code==MIC && grants.length>0 && grants[0]==PackageManager.PERMISSION_GRANTED) voice();
        else if(code==MIC) feedback.setText("Podés seguir usando el teclado sin permiso de micrófono.");
    }
    @Override protected void onActivityResult(int code,int result,Intent data) { super.onActivityResult(code,result,data); if(code==VOICE&&result==RESULT_OK&&data!=null) accept(data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)); }
    @Override protected void onSaveInstanceState(Bundle state) { state.putString("input",input.getText().toString()); super.onSaveInstanceState(state); }
    @Override protected void onResume() { super.onResume(); handler.post(update); }
    @Override protected void onPause() { handler.removeCallbacks(update); stopVoice(); super.onPause(); }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); if(recognizer!=null) recognizer.destroy(); super.onDestroy(); }
}
