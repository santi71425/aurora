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
    private int failures;
    private long awaitingCommand;
    private final Runnable restart=() -> listen();
    public static boolean available(android.content.Context c) {return Build.VERSION.SDK_INT>=31&&SpeechRecognizer.isOnDeviceRecognitionAvailable(c);}
    static Intent recognitionIntent() {
        return new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE,"es-ES").putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3).putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false).putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true);
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
            recognizer=SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
            recognizer.setRecognitionListener(new RecognitionListener() {
                public void onReadyForSpeech(Bundle b) {update(awaitingCommand>SystemClock.elapsedRealtime()?"Te escucho…":"Esperando OK Jarvis · reconocimiento local");}
                public void onBeginningOfSpeech() { }
                public void onRmsChanged(float rms) { }
                public void onBufferReceived(byte[] audio) { /* Never copy or store audio. */ }
                public void onEndOfSpeech() { }
                public void onError(int error) {
                    if(!running||!listening) return;listening=false;
                    if(error==SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS||error==SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED||error==SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE) {
                        update("Falta permiso o modelo local de español. Abrí Jarvis para prepararlo.");shutdown();return;
                    }
                    if(error!=SpeechRecognizer.ERROR_NO_MATCH&&error!=SpeechRecognizer.ERROR_SPEECH_TIMEOUT) failures++;
                    if(failures>=8) {update("El servicio de voz local falló varias veces. Reactivá OK Jarvis desde la app.");shutdown();return;}
                    schedule(error==SpeechRecognizer.ERROR_NO_MATCH||error==SpeechRecognizer.ERROR_SPEECH_TIMEOUT?600:1800);
                }
                public void onResults(Bundle b) {
                    if(!running||!listening) return;listening=false;failures=0;
                    ArrayList<String> texts=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    String command=null;
                    if(texts!=null) {
                        for(String text:texts) {WakePhrase phrase=WakePhrase.parse(text);if(phrase.detected) {command=phrase.command;break;}}
                        if(command==null&&awaitingCommand>SystemClock.elapsedRealtime()&&!texts.isEmpty()) command=texts.get(0);
                    }
                    if(command!=null) {
                        if(command.isEmpty()) {awaitingCommand=SystemClock.elapsedRealtime()+10000;Conversation.reply(WakeVoiceService.this,"Te escucho.");}
                        else {awaitingCommand=0;PhoneControlService control=PhoneControlService.instance;if(control!=null) control.spoken(command);else Conversation.reply(WakeVoiceService.this,"Activá el control de Jarvis en Accesibilidad.");}
                    }
                    schedule(500);
                }
                public void onPartialResults(Bundle b) { }
                public void onEvent(int type,Bundle b) { }
            });
            schedule(400);
        } catch(RuntimeException failed) {update("No pude iniciar el reconocimiento local. Revisá el servicio de voz de Android.");shutdown();}
        return START_NOT_STICKY;
    }
    private void listen() {
        if(!running||ttsPaused||manualPaused||listening||recognizer==null) return;
        try {listening=true;recognizer.startListening(recognitionIntent());}
        catch(RuntimeException failed) {listening=false;failures++;if(failures>=8) shutdown();else schedule(1800);}
    }
    private void schedule(long delay) {main.removeCallbacks(restart);if(running&&!ttsPaused&&!manualPaused) main.postDelayed(restart,delay);}
    public void pauseForSpeech() {ttsPaused=true;pause();}
    public void resumeAfterSpeech() {ttsPaused=false;schedule(700);}
    public void pauseManual() {manualPaused=true;pause();}
    public void resumeManual() {manualPaused=false;schedule(700);}
    private void pause() {main.removeCallbacks(restart);listening=false;if(recognizer!=null) recognizer.cancel();}
    public void stopListening() {status="OK Jarvis apagado";shutdown();}
    private void shutdown() {running=false;pause();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
    private void update(String value) {
        status=value;NotificationManager m=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(running&&m!=null) m.notify(ID,notification());
    }
    private Notification notification() {
        PendingIntent open=PendingIntent.getActivity(this,1,new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(this,2,new Intent(this,WakeVoiceService.class).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this,"jarvis_mic").setSmallIcon(R.drawable.ic_mic).setContentTitle("Jarvis · micrófono activo").setContentText(status).setContentIntent(open)
            .setOngoing(true).setCategory(Notification.CATEGORY_SERVICE).addAction(new Notification.Action.Builder(null,"Apagar",stop).build()).build();
    }
    @Override public void onTaskRemoved(Intent intent) {stopListening();}
    @Override public void onDestroy() {running=false;main.removeCallbacksAndMessages(null);if(recognizer!=null) {recognizer.cancel();recognizer.destroy();}if(instance==this) instance=null;super.onDestroy();}
    @Override public IBinder onBind(Intent intent) {return null;}
}
