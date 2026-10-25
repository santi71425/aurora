package ar.aurora.phone;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import java.util.ArrayList;

final class ManualVoice {
    static final int PERMISSION=81,REQUEST=82;
    interface Listener {void result(String text);void info(String text);}
    private final Activity activity;private final Listener listener;
    private final SpeechAttempt attempt=new SpeechAttempt();
    private final Handler main=new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    private AlertDialog fallback;
    private boolean destroyed,externalPending;
    private String status="";
    ManualVoice(Activity activity,Listener listener) {this.activity=activity;this.listener=listener;}
    boolean listening() {return attempt.active();}
    String status() {return status;}
    private void info(String text) {status=text;listener.info(text);}
    void begin() {
        if(destroyed||activity.isFinishing()||activity.isDestroyed()||externalPending) return;
        cancelLocal();VoiceOutput.stop();if(WakeVoiceService.instance!=null) WakeVoiceService.instance.pauseManual();
        String mode=activity.getSharedPreferences("jarvis",0).getString("manualMode","auto");
        if(mode.equals("system")) {offerSystem(null);return;}
        if(WakeVoiceService.available(activity)&&activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {resume();activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},PERMISSION);return;}
        if(WakeVoiceService.available(activity)) {
            int token=attempt.begin();info("Preparando el dictado local…");
            main.postDelayed(() -> local(token),350);
        } else if(mode.equals("local")) {info("Este teléfono no ofrece reconocimiento local. Podés elegir Dictado del teléfono en los ajustes de Jarvis.");resume();}
        else offerSystem("El reconocimiento local no está disponible en este servicio.");
    }
    private void local(final int token) {
        if(destroyed||!attempt.current(token)) return;
        releaseRecognizer();
        try {
            recognizer=SpeechRecognizer.createOnDeviceSpeechRecognizer(activity);
            recognizer.setRecognitionListener(new RecognitionListener() {
                public void onReadyForSpeech(Bundle b) {if(attempt.current(token)) info("Te escucho…");}
                public void onBeginningOfSpeech() { }
                public void onRmsChanged(float n) { }
                public void onBufferReceived(byte[] b) { }
                public void onEndOfSpeech() {if(attempt.current(token)) info("Procesando en el teléfono…");}
                public void onError(int error) {failed(token,error);}
                public void onResults(Bundle b) {
                    if(!attempt.finish(token)||destroyed) return;
                    releaseRecognizer();ArrayList<String> words=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if(words!=null&&!words.isEmpty()) listener.result(words.get(0));else info("No recibí texto del reconocedor.");resume();
                }
                public void onPartialResults(Bundle b) { }
                public void onEvent(int n,Bundle b) { }
            });
            recognizer.startListening(WakeVoiceService.recognitionIntent());
        } catch(RuntimeException unavailable) {failed(token,11);}
    }
    private void failed(int token,int error) {
        if(destroyed||!attempt.current(token)) return;
        int retry=attempt.retry(token,error);
        if(retry!=-1) {
            releaseRecognizer();info("El servicio de voz se interrumpió. Reconectando una vez…");
            main.postDelayed(() -> local(retry),error==8?1000:700);return;
        }
        if(!attempt.finish(token)) return;releaseRecognizer();
        String message=VoiceErrors.describe(error);
        if(VoiceErrors.noSpeech(error)||error==9) {info(message);resume();return;}
        String mode=activity.getSharedPreferences("jarvis",0).getString("manualMode","auto");
        if(mode.equals("local")) {info(message+" Podés cambiar a Dictado del teléfono desde los ajustes de Jarvis.");resume();}
        else offerSystem(message);
    }
    private void offerSystem(String reason) {
        if(destroyed||activity.isFinishing()||activity.isDestroyed()) {resume();return;}
        if(activity.getSharedPreferences("jarvis",0).getBoolean("manualCloud",false)) {if(reason!=null) info(reason+" Voy a usar el dictado del teléfono.");external();return;}
        fallback=new AlertDialog.Builder(activity).setTitle("Usar el dictado del teléfono")
            .setMessage((reason==null?"":reason+"\n\n")+"Podés dictar con el servicio de voz configurado en tu teléfono. Ese servicio puede procesar el audio de esta sesión en Samsung o Google y necesitar internet. No se usa para escuchar audio ambiente ni reemplaza la activación local OK Jarvis.\n\nSi aceptás, lo recordaré para el dictado manual; podés cambiarlo en ⋮ → Motor de dictado.")
            .setNegativeButton("Escribir",(d,w) -> resume()).setPositiveButton("Usar dictado del teléfono",(d,w) -> {
                activity.getSharedPreferences("jarvis",0).edit().putBoolean("manualCloud",true).putString("manualMode","system").apply();external();
            }).setOnCancelListener(d -> resume()).create();fallback.show();
    }
    private void external() {
        if(destroyed) return;
        VoiceOutput.stop();if(WakeVoiceService.instance!=null) WakeVoiceService.instance.pauseManual();
        releaseRecognizer();externalPending=true;
        Intent intent=WakeVoiceService.recognitionIntent();intent.removeExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE);intent.putExtra(RecognizerIntent.EXTRA_PROMPT,"Decile a Jarvis qué querés hacer");
        try {activity.startActivityForResult(intent,REQUEST);}
        catch(RuntimeException unavailable) {externalPending=false;info("No hay una app de dictado disponible. Configurá el servicio de voz del teléfono o usá el micrófono del teclado.");resume();}
    }
    boolean activityResult(int request,int result,Intent data) {
        if(request!=REQUEST) return false;
        if(!externalPending||destroyed) return true;externalPending=false;
        if(result==Activity.RESULT_OK&&data!=null) {ArrayList<String> words=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);if(words!=null&&!words.isEmpty()) listener.result(words.get(0));else info("El dictado no devolvió texto.");}
        else info("Dictado cancelado. Podés volver a intentarlo.");
        resume();return true;
    }
    void permission(int code,int[] grants) {if(code==PERMISSION) {if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED) begin();else info("El micrófono no tiene permiso. Podés escribir.");}}
    void pause() {if(!externalPending) cancel();}
    void cancel() {externalPending=false;cancelLocal();resume();}
    private void cancelLocal() {attempt.cancel();main.removeCallbacksAndMessages(null);releaseRecognizer();}
    private void releaseRecognizer() {SpeechRecognizer old=recognizer;recognizer=null;if(old!=null) {try {old.cancel();}catch(RuntimeException ignored) { }try {old.destroy();}catch(RuntimeException ignored) { }}}
    void destroy() {destroyed=true;cancel();if(fallback!=null) fallback.dismiss();}
    private void resume() {if(WakeVoiceService.instance!=null&&!(activity instanceof CommandActivity)) WakeVoiceService.instance.resumeManual();}
}
