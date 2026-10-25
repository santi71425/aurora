package ar.aurora.phone;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** A native conversational interface, with explicit opt-in to continuous local voice. */
public final class MainActivity extends Activity implements Conversation.Listener {
    private static final int WAKE_MIC=91,NOTIFICATIONS=92;
    private final Handler main=new Handler(Looper.getMainLooper());
    private EditText input;
    private TextView state;
    private LinearLayout messages;
    private ScrollView scroll;
    private Button wake;
    private ManualVoice voice;
    private SpeechRecognizer modelRequester;
    private boolean foreground;
    private String manualOrigin="",pendingDictation;
    private final Runnable refresh=new Runnable() {
        public void run() {
            boolean listening=WakeVoiceService.instance!=null;
            wake.setText(listening?"Apagar escucha continua":"Activar «OK Jarvis»");
            state.setText(voice!=null&&voice.listening()?"● "+voice.status():listening?"● "+WakeVoiceService.status:PhoneControlService.instance!=null?"● Control listo · "+WakeVoiceService.status:"○ Configurá el control del teléfono");
            main.postDelayed(this,1000);
        }
    };
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        LinearLayout root=AppUi.column(this);setContentView(root);AppUi.insets(this,root);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo=AppUi.text(this,"J",18,AppUi.BG);logo.setTypeface(Typeface.DEFAULT,Typeface.BOLD);logo.setGravity(Gravity.CENTER);logo.setBackground(AppUi.background(this,AppUi.TEXT,24));
        header.addView(logo,new LinearLayout.LayoutParams(dp(38),dp(38)));
        LinearLayout brand=AppUi.column(this);TextView title=AppUi.text(this,"Jarvis",23,AppUi.TEXT);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);brand.addView(title);
        brand.addView(AppUi.text(this,"Tu asistente personal",11,AppUi.MUTED));LinearLayout.LayoutParams brandParams=new LinearLayout.LayoutParams(0,-2,1);brandParams.leftMargin=dp(12);header.addView(brand,brandParams);
        Button fresh=AppUi.button(this,"+",false);fresh.setTextSize(25);fresh.setContentDescription("Nueva conversación");fresh.setOnClickListener(v -> Conversation.clear());header.addView(fresh,new LinearLayout.LayoutParams(dp(45),dp(45)));
        Button menu=AppUi.button(this,"⋮",false);menu.setTextSize(23);menu.setContentDescription("Ajustes de Jarvis");menu.setOnClickListener(v -> settings());header.addView(menu,new LinearLayout.LayoutParams(dp(45),dp(45)));root.addView(header);
        state=AppUi.text(this,"",11,AppUi.MUTED);state.setPadding(0,dp(10),0,dp(6));root.addView(state);
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);messages=AppUi.column(this);messages.setPadding(0,dp(16),0,dp(20));scroll.addView(messages);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout composer=AppUi.column(this);composer.setPadding(dp(14),dp(10),dp(8),dp(8));composer.setBackground(AppUi.background(this,AppUi.CARD,28));
        input=new EditText(this);input.setTextColor(AppUi.TEXT);input.setHintTextColor(AppUi.MUTED);input.setTextSize(16);input.setHint("Pedile algo a Jarvis");input.setMinLines(1);input.setMaxLines(4);input.setPadding(0,dp(6),dp(6),dp(8));input.setBackgroundColor(Color.TRANSPARENT);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);composer.addView(input);
        LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER_VERTICAL);
        TextView note=AppUi.text(this,"Mic ejecuta órdenes",11,AppUi.MUTED);controls.addView(note,new LinearLayout.LayoutParams(0,-2,1));
        Button mic=AppUi.button(this,"Mic",false);mic.setContentDescription("Decir y ejecutar una orden");mic.setOnClickListener(v -> {if(voice.listening()) voice.cancel();else {manualOrigin=PhoneControlService.instance==null?"":PhoneControlService.instance.currentPackage();voice.begin();}});controls.addView(mic,new LinearLayout.LayoutParams(dp(60),dp(44)));
        Button send=AppUi.button(this,"↑",true);send.setTextSize(23);send.setContentDescription("Enviar comando");send.setOnClickListener(v -> execute());LinearLayout.LayoutParams sendParams=new LinearLayout.LayoutParams(dp(44),dp(44));sendParams.leftMargin=dp(6);controls.addView(send,sendParams);composer.addView(controls);root.addView(composer);
        wake=AppUi.button(this,"Activar «OK Jarvis»",false);wake.setOnClickListener(v -> toggleWake());LinearLayout.LayoutParams wakeParams=new LinearLayout.LayoutParams(-1,dp(44));wakeParams.topMargin=dp(8);root.addView(wake,wakeParams);
        voice=new ManualVoice(this,new ManualVoice.Listener() {
            public void info(String text) {state.setText(text);if(voice!=null&&!voice.listening()) Conversation.reply(MainActivity.this,text);}
            public void result(String text) {acceptDictation(text);}
        });
        VoiceOutput.initialize(this);changed();if(saved!=null) input.setText(saved.getString("draft",""));
    }
    private int dp(int n) {return AppUi.dp(this,n);}
    @Override public void changed() {
        messages.removeAllViews();java.util.List<Conversation.Message> history=Conversation.snapshot();
        if(history.isEmpty()) {
            LinearLayout welcome=AppUi.column(this);welcome.setGravity(Gravity.CENTER_HORIZONTAL);welcome.setPadding(dp(8),dp(22),dp(8),dp(16));
            TextView hello=AppUi.text(this,"¿Qué hacemos hoy?",29,AppUi.TEXT);hello.setTypeface(Typeface.DEFAULT,Typeface.BOLD);hello.setGravity(Gravity.CENTER);welcome.addView(hello);
            TextView subtitle=AppUi.text(this,"Abrí apps, controlá tu pantalla\ny encontrá tus fotos con tu voz.",16,AppUi.MUTED);subtitle.setGravity(Gravity.CENTER);subtitle.setLineSpacing(dp(4),1);welcome.addView(subtitle,AppUi.spaced(this));messages.addView(welcome);
            suggestions("Abrir una app","Abrí WhatsApp","Mover la pantalla","Bajá");suggestions("Buscar fotos","Mostrame capturas de pantalla","Conocer a Jarvis","¿Qué podés hacer?");
            TextView tip=AppUi.text(this,"Activá OK Jarvis para dar órdenes sin volver a esta pantalla.",12,AppUi.MUTED);tip.setGravity(Gravity.CENTER);tip.setPadding(dp(15),dp(20),dp(15),0);messages.addView(tip);
        } else for(Conversation.Message message:history) {
            LinearLayout row=new LinearLayout(this);row.setGravity(message.user?Gravity.END:Gravity.TOP);row.setPadding(0,dp(8),0,dp(8));
            TextView text=AppUi.text(this,message.text,16,AppUi.TEXT);text.setTextIsSelectable(true);text.setLineSpacing(dp(3),1);
            if(message.user) {text.setPadding(dp(16),dp(12),dp(16),dp(12));text.setBackground(AppUi.background(this,AppUi.CARD,22));text.setMaxWidth((int)(getResources().getDisplayMetrics().widthPixels*0.82));row.addView(text);}
            else {
                TextView avatar=AppUi.text(this,"J",13,AppUi.BG);avatar.setGravity(Gravity.CENTER);avatar.setTypeface(Typeface.DEFAULT,Typeface.BOLD);avatar.setBackground(AppUi.background(this,AppUi.ACCENT,16));row.addView(avatar,new LinearLayout.LayoutParams(dp(27),dp(27)));
                LinearLayout.LayoutParams body=new LinearLayout.LayoutParams(0,-2,1);body.leftMargin=dp(12);row.addView(text,body);
            }messages.addView(row);
        }
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }
    private void suggestions(String first,String firstCommand,String second,String secondCommand) {
        LinearLayout row=new LinearLayout(this);Button a=AppUi.button(this,first,false),b=AppUi.button(this,second,false);
        a.setOnClickListener(v -> input.setText(firstCommand));b.setOnClickListener(v -> input.setText(secondCommand));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(58),1);p.rightMargin=dp(6);row.addView(a,p);row.addView(b,new LinearLayout.LayoutParams(0,dp(58),1));messages.addView(row,AppUi.spaced(this));
    }
    private void execute() {
        String text=input.getText().toString().trim();if(text.isEmpty()) return;voice.cancel();input.setText("");Conversation.user(text);
        PhoneControlService service=PhoneControlService.instance;String origin=service==null?"":service.currentPackage();
        PhoneActions.dispatch(this,CommandParser.parse(text),origin,false);
    }
    private void acceptDictation(String text) {
        if(!foreground) {pendingDictation=text;return;}
        input.setText("");state.setText("Orden reconocida: "+text);Conversation.user(text);
        PhoneActions.dispatch(this,CommandParser.parse(text),manualOrigin,false);
    }
    private void settings() {
        String[] labels={(VoiceOutput.enabled(this)?"✓ ":"")+"Voz de respuesta",(getSharedPreferences("jarvis",0).getBoolean("panel",false)?"✓ ":"")+"Micrófono flotante","Configurar Accesibilidad","Motor de dictado","Preparar reconocimiento local","Ayuda y límites","Borrar conversación"};
        new AlertDialog.Builder(this).setTitle("Jarvis").setItems(labels,(d,w) -> {
            if(w==0) {VoiceOutput.enabled(this,!VoiceOutput.enabled(this));if(VoiceOutput.enabled(this)) Conversation.reply(this,"Voz activada.");}
            else if(w==1) {if(PhoneControlService.instance==null) PhoneActions.requireControl(this);else PhoneControlService.instance.panel(!getSharedPreferences("jarvis",0).getBoolean("panel",false));}
            else if(w==2) PhoneActions.requireControl(this);
            else if(w==3) dictation();
            else if(w==4) model();
            else if(w==5) help();
            else Conversation.clear();
        }).setNegativeButton("Cerrar",null).show();
    }
    private void dictation() {
        String mode=getSharedPreferences("jarvis",0).getString("manualMode","auto");
        final String[] selected={mode};
        new AlertDialog.Builder(this).setTitle("Motor de dictado manual")
            .setSingleChoiceItems(new String[]{"Automático: local con recuperación","Dictado del teléfono","Solo reconocimiento local"},mode.equals("system")?1:mode.equals("local")?2:0,(d,w) -> selected[0]=new String[]{"auto","system","local"}[w])
            .setNegativeButton("Cancelar",null).setPositiveButton("Guardar",(d,w) -> {
                getSharedPreferences("jarvis",0).edit().putString("manualMode",selected[0]).apply();
                Conversation.reply(this,selected[0].equals("system")?"Mic usará el dictado del teléfono. Antes de usarlo te pediré consentimiento si todavía no lo diste. OK Jarvis sigue usando solo reconocimiento local.":"Motor de dictado guardado.");
            }).show();
    }
    private void toggleWake() {
        if(WakeVoiceService.instance!=null) {WakeVoiceService.instance.stopListening();Conversation.reply(this,"OK Jarvis apagado.");return;}
        if(PhoneControlService.instance==null) {PhoneActions.requireControl(this);return;}
        if(!WakeVoiceService.available(this)) {Conversation.reply(this,"Tu servicio de voz no ofrece reconocimiento local. Podés usar dictado manual o preparar el reconocimiento desde los ajustes de Jarvis.");return;}
        new AlertDialog.Builder(this).setTitle("Activar OK Jarvis")
            .setMessage("El micrófono quedará activo y el reconocimiento se hará en el teléfono. Decí OK Jarvis para abrir una sesión de 30 segundos. Durante la sesión podés dar varias órdenes sin repetir la frase. Cada orden reconocida renueva los 30 segundos. Si pasa ese tiempo sin una orden, vuelve a esperar OK Jarvis.\n\nConsume batería. Podés apagarlo desde la app, el control flotante o su notificación. Android y Samsung pueden interrumpir la escucha, especialmente con la pantalla bloqueada. No sustituye la autenticación del teléfono.")
            .setNegativeButton("Cancelar",null).setPositiveButton("Activar escucha",(d,w) -> permissionsForWake()).show();
    }
    private void permissionsForWake() {
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},WAKE_MIC);return;}
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED&&!getSharedPreferences("jarvis",0).getBoolean("notificationAsked",false)) {
            getSharedPreferences("jarvis",0).edit().putBoolean("notificationAsked",true).apply();requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},NOTIFICATIONS);return;
        }
        try {VoiceOutput.stop();startForegroundService(new Intent(this,WakeVoiceService.class));state.setText("Activando reconocimiento local…");}
        catch(RuntimeException error) {Conversation.reply(this,"Android no permitió iniciar la escucha. Revisá el permiso de micrófono y activala con Jarvis abierto.");}
    }
    private void model() {
        if(Build.VERSION.SDK_INT>=33&&WakeVoiceService.available(this)) {
            try {if(modelRequester==null) modelRequester=SpeechRecognizer.createOnDeviceSpeechRecognizer(this);modelRequester.triggerModelDownload(WakeVoiceService.recognitionIntent());Conversation.reply(this,"Solicité el modelo de español al servicio de Android. La disponibilidad y descarga dependen de ese servicio; después volvé a probar OK Jarvis.");}
            catch(RuntimeException e) {Conversation.reply(this,"El servicio no permitió solicitar ese modelo. Revisá el reconocimiento de voz sin conexión en los ajustes de tu teléfono.");}
        } else new AlertDialog.Builder(this).setTitle("Reconocimiento local")
            .setMessage("Necesitás un servicio de reconocimiento local compatible y el idioma español instalado. Revisá el servicio de voz del teléfono. La escucha continua no usa dictado remoto como reemplazo.")
            .setNegativeButton("Cerrar",null).setPositiveButton("Abrir ajustes de voz",(d,w) -> {try {startActivity(new Intent("android.settings.VOICE_INPUT_SETTINGS"));}catch(RuntimeException e) {Conversation.reply(this,"Buscá Reconocimiento de voz en Ajustes del teléfono.");}}).show();
    }
    private void help() {
        new AlertDialog.Builder(this).setTitle("Jarvis 0.3.2 · Tu teléfono, a tu voz")
            .setMessage("ÓRDENES\nOK Jarvis, bajá / subí / atrás / inicio / abrí WhatsApp / abrí Google Fotos / bajá el volumen / mostrame capturas de pantalla.\n\nPodés decir toca Nombre del botón o escribe: Texto. Esas acciones requieren confirmación y solo actúan en un control inequívoco o un campo de texto seleccionado. No se escriben contraseñas.\n\nDESBLOQUEO\nJarvis puede pedir a Android que muestre la autenticación. Nunca guarda ni introduce tu PIN y no salta el bloqueo. La activación por voz con pantalla apagada depende de Android, del servicio de voz y de las restricciones de batería de Samsung.\n\nVOZ\nOK Jarvis requiere reconocimiento local y un servicio de micrófono visible en Android. No graba archivos de audio, no envía audio ambiente a un reconocedor remoto y no se inicia al reiniciar el teléfono. El dictado manual puede usar el servicio del teléfono con tu consentimiento. La voz de respuesta usa el sintetizador de Android. No es un modelo conversacional general.\n\nFOTOS\nBusca metadatos locales o usa Google Fotos/Galería Samsung para contenido visual. No importa toda la nube. Los duplicados requieren selección y confirmación de Android; borrarlos puede ser permanente.\n\nINSTALACIÓN\nSi Android restringe Accesibilidad: Ajustes → Aplicaciones → Jarvis → ⋮ → Permitir ajustes restringidos, si aparece. Después activá Jarvis en Accesibilidad.\n\nEl control está limitado por los permisos y por las interfaces de las apps. Para quitarlo, apagá la escucha, desactivá Accesibilidad y desinstalá Jarvis.")
            .setPositiveButton("Cerrar",null).show();
    }
    @Override public void onRequestPermissionsResult(int code,String[] names,int[] grants) {
        super.onRequestPermissionsResult(code,names,grants);
        if(code==WAKE_MIC) {if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED) permissionsForWake();else Conversation.reply(this,"Sin permiso de micrófono no puedo activar OK Jarvis.");}
        else if(code==NOTIFICATIONS) permissionsForWake();else voice.permission(code,grants);
    }
    @Override protected void onActivityResult(int code,int result,Intent data) {super.onActivityResult(code,result,data);voice.activityResult(code,result,data);}
    @Override protected void onSaveInstanceState(Bundle state) {state.putString("draft",input.getText().toString());super.onSaveInstanceState(state);}
    @Override protected void onResume() {super.onResume();foreground=true;Conversation.listener=this;changed();main.post(refresh);}
    @Override protected void onPostResume() {super.onPostResume();if(pendingDictation!=null) {String text=pendingDictation;pendingDictation=null;acceptDictation(text);}}
    @Override protected void onPause() {foreground=false;main.removeCallbacks(refresh);voice.pause();if(Conversation.listener==this) Conversation.listener=null;super.onPause();}
    @Override protected void onDestroy() {main.removeCallbacksAndMessages(null);voice.destroy();if(modelRequester!=null) modelRequester.destroy();if(Conversation.listener==this) Conversation.listener=null;super.onDestroy();}
}
