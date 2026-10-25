package ar.aurora.phone;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import java.util.ArrayList;

/** User-started microphone foreground service. Ambient audio stays with the on-device recognizer. */
public final class WakeVoiceService extends Service {
    public static volatile WakeVoiceService instance;
    public static volatile String status="OK Jarvis apagado";
    private static final int ID=41;
    private final Handler main=new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    private boolean running,listening,ttsPaused,manualPaused;
    private int failures,connection;
    private boolean reconnecting;
    private final VoiceSession session=new VoiceSession();
    private final Runnable restart=() -> listen();
    private final Runnable sessionStatus=new Runnable() {
        public void run() {
            if(!running) return;
            int seconds=session.remainingSeconds(SystemClock.elapsedRealtime());
            update(reconnecting?"Reconectando el servicio local de voz…":seconds>0?"Sesión activa · "+seconds+" s · hablá sin OK Jarvis":"Esperando OK Jarvis · reconocimiento local");
            main.postDelayed(this,1000);
        }
    };
    public static boolean available(android.content.Context c) {return Build.VERSION.SDK_INT>=31&&SpeechRecognizer.isOnDeviceRecognitionAvailable(c);}
    static Intent recognitionIntent() {
        return new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE,java.util.Locale.getDefault().getLanguage().equals("es")?java.util.Locale.getDefault().toLanguageTag():"es-ES").putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3).putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false).putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent!=null&&"STOP".equals(intent.getAction())) {stopListening();return START_NOT_STICKY;}
        if(running) return START_NOT_STICKY;
        if(!available(this)||checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {status="Falta reconocimiento local o permiso de micrófono.";stopSelf();return START_NOT_STICKY;}
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        manager.createNotificationChannel(new NotificationChannel("jarvis_mic","Escucha de Jarvis",NotificationManager.IMPORTANCE_LOW));
        instance=this;running=true;status="Esperando OK Jarvis · reconocimiento local";
        try {startForeground(ID,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);}
        catch(RuntimeException denied) {running=false;instance=null;status="Android no permitió iniciar el micrófono. Activá OK Jarvis desde la app visible.";stopSelf();return START_NOT_STICKY;}
        try {
            connectRecognizer();
            schedule(400);
            main.post(sessionStatus);
        } catch(RuntimeException failed) {update("No pude iniciar el reconocimiento local. Revisá el servicio de voz de Android.");shutdown();}
        return START_NOT_STICKY;
    }
    private void connectRecognizer() {
        releaseRecognizer();final int token=connection;
        recognizer=SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
            recognizer.setRecognitionListener(new RecognitionListener() {
                public void onReadyForSpeech(Bundle b) {if(token==connection) reconnecting=false;}
                public void onBeginningOfSpeech() { }
                public void onRmsChanged(float rms) { }
                public void onBufferReceived(byte[] audio) { /* Never copy or store audio. */ }
                public void onEndOfSpeech() { }
                public void onError(int error) {
                    if(token!=connection||!running||!listening) return;listening=false;
                    if(error==SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS||error==SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED||error==SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE) {
                        update(VoiceErrors.describe(error));shutdown();return;
                    }
                    if(VoiceErrors.noSpeech(error)) failures=0;else failures++;
                    if(failures>=3) {update(VoiceErrors.describe(error)+" No pude recuperar la conexión. Probá Mic con el dictado del teléfono.");shutdown();return;}
                    if(!VoiceErrors.noSpeech(error)) {releaseRecognizer();reconnecting=true;}
                    schedule(error==SpeechRecognizer.ERROR_NO_MATCH||error==SpeechRecognizer.ERROR_SPEECH_TIMEOUT?600:1800);
                }
                public void onResults(Bundle b) {
                    if(token!=connection||!running||!listening) return;listening=false;failures=0;
                    ArrayList<String> texts=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    String command=null;
                    if(texts!=null) {
                        long now=SystemClock.elapsedRealtime();
                        for(String text:texts) if(WakePhrase.parse(text).detected) {command=session.accept(text,now);break;}
                        if(command==null&&!texts.isEmpty()) command=session.accept(texts.get(0),now);
                    }
                    if(command!=null) {
                        if(command.isEmpty()) Conversation.reply(WakeVoiceService.this,"Te escucho. Podés dar órdenes sin repetir mi nombre durante treinta segundos.");
                        else {PhoneControlService control=PhoneControlService.instance;if(control!=null) control.spoken(command);else Conversation.reply(WakeVoiceService.this,"Activá el control de Jarvis en Accesibilidad.");}
                    }
                    schedule(500);
                }
                public void onPartialResults(Bundle b) { }
                public void onEvent(int type,Bundle b) { }
            });
    }
    private void releaseRecognizer() {
        connection++;SpeechRecognizer old=recognizer;recognizer=null;
        if(old!=null) {try {old.cancel();}catch(RuntimeException ignored) { }try {old.destroy();}catch(RuntimeException ignored) { }}
    }
    private void listen() {
        if(!running||ttsPaused||manualPaused||listening) return;
        try {if(recognizer==null) {reconnecting=true;connectRecognizer();}listening=true;recognizer.startListening(recognitionIntent());}
        catch(RuntimeException failed) {listening=false;releaseRecognizer();failures++;if(failures>=3) {update("No pude conectar el reconocedor local. Probá Mic con el dictado del teléfono.");shutdown();}else schedule(1800);}
    }
    private void schedule(long delay) {main.removeCallbacks(restart);if(running&&!ttsPaused&&!manualPaused) main.postDelayed(restart,delay);}
    public void pauseForSpeech() {ttsPaused=true;pause();}
    public void resumeAfterSpeech() {ttsPaused=false;schedule(700);}
    public void pauseManual() {manualPaused=true;pause();}
    public void resumeManual() {manualPaused=false;schedule(700);}
    private void pause() {main.removeCallbacks(restart);listening=false;reconnecting=false;releaseRecognizer();}
    public void stopListening() {status="OK Jarvis apagado";shutdown();}
    private void shutdown() {running=false;session.close();main.removeCallbacks(sessionStatus);pause();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
    private void update(String value) {
        if(value.equals(status)) return;
        status=value;NotificationManager m=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(running&&m!=null) m.notify(ID,notification());
    }
    private Notification notification() {
        PendingIntent open=PendingIntent.getActivity(this,1,new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(this,2,new Intent(this,WakeVoiceService.class).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this,"jarvis_mic").setSmallIcon(R.drawable.ic_mic).setContentTitle("Jarvis · micrófono activo").setContentText(status).setContentIntent(open)
            .setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_SERVICE).addAction(new Notification.Action.Builder(null,"Apagar",stop).build()).build();
    }
    @Override public void onTaskRemoved(Intent intent) {stopListening();}
    @Override public void onDestroy() {running=false;main.removeCallbacksAndMessages(null);releaseRecognizer();if(instance==this) instance=null;super.onDestroy();}
    @Override public IBinder onBind(Intent intent) {return null;}
}
