package ar.aurora.phone;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import java.util.ArrayList;

final class ManualVoice {
    static final int PERMISSION=81,REQUEST=82;
    interface Listener {void result(String text);void info(String text);}
    private final Activity activity;private final Listener listener;
    private SpeechRecognizer recognizer;private boolean listening;
    ManualVoice(Activity activity,Listener listener) {this.activity=activity;this.listener=listener;}
    boolean listening() {return listening;}
    void begin() {
        if(activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},PERMISSION);return;}
        VoiceOutput.stop();if(WakeVoiceService.instance!=null) WakeVoiceService.instance.pauseManual();
        if(WakeVoiceService.available(activity)) {
            try {
                if(recognizer==null) {
                    recognizer=SpeechRecognizer.createOnDeviceSpeechRecognizer(activity);
                    recognizer.setRecognitionListener(new RecognitionListener() {
                        public void onReadyForSpeech(Bundle b) {listener.info("Te escucho…");}
                        public void onBeginningOfSpeech() { }
                        public void onRmsChanged(float n) { }
                        public void onBufferReceived(byte[] b) { }
                        public void onEndOfSpeech() {listener.info("Procesando en el teléfono…");}
                        public void onError(int error) {listening=false;listener.info("No pude completar el dictado local ("+error+"). Revisá el modelo de español o usá texto.");resume();}
                        public void onResults(Bundle b) {listening=false;ArrayList<String> words=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(words!=null&&!words.isEmpty()) listener.result(words.get(0));resume();}
                        public void onPartialResults(Bundle b) { }
                        public void onEvent(int n,Bundle b) { }
                    });
                }
                listening=true;recognizer.startListening(WakeVoiceService.recognitionIntent());
            } catch(RuntimeException e) {listening=false;listener.info("No pude iniciar el micrófono.");resume();}
        } else if(activity.getSharedPreferences("jarvis",0).getBoolean("manualCloud",false)) external();
        else new AlertDialog.Builder(activity).setTitle("Dictado del teléfono")
            .setMessage("No hay dictado local disponible. Para dictado manual, el servicio de tu teléfono puede enviar este audio a Samsung o Google. La escucha continua OK Jarvis seguirá requiriendo reconocimiento local.")
            .setNegativeButton("Escribir",(d,w) -> resume()).setPositiveButton("Usar dictado",(d,w) -> {activity.getSharedPreferences("jarvis",0).edit().putBoolean("manualCloud",true).apply();external();}).setOnCancelListener(d -> resume()).show();
    }
    private void external() {
        try {activity.startActivityForResult(WakeVoiceService.recognitionIntent(),REQUEST);}
        catch(RuntimeException e) {listener.info("No hay un servicio de dictado disponible.");resume();}
    }
    boolean activityResult(int request,int result,Intent data) {
        if(request!=REQUEST) return false;
        if(result==Activity.RESULT_OK&&data!=null) {ArrayList<String> words=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);if(words!=null&&!words.isEmpty()) listener.result(words.get(0));}
        resume();return true;
    }
    void permission(int code,int[] grants) {if(code==PERMISSION) {if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED) begin();else listener.info("El micrófono no tiene permiso. Podés escribir.");}}
    void cancel() {listening=false;if(recognizer!=null) recognizer.cancel();resume();}
    void destroy() {cancel();if(recognizer!=null) recognizer.destroy();}
    private void resume() {if(WakeVoiceService.instance!=null&&!(activity instanceof CommandActivity)) WakeVoiceService.instance.resumeManual();}
}
