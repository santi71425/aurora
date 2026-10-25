package ar.aurora.phone;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import java.util.Locale;

/** System speech synthesis, preferring a local Spanish voice and never cloning a voice. */
final class VoiceOutput {
    private static TextToSpeech engine;
    private static boolean ready;
    private static String pending,current="";
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    static boolean enabled(Context c) {return c.getSharedPreferences("jarvis",0).getBoolean("voice",true);}
    static void enabled(Context c,boolean value) {
        c.getSharedPreferences("jarvis",0).edit().putBoolean("voice",value).apply();
        if(!value) stop();
    }
    static void initialize(Context context) {
        if(engine!=null) return;
        Context app=context.getApplicationContext();
        engine=new TextToSpeech(app,status -> {
            if(status!=TextToSpeech.SUCCESS) {ready=false;pending=null;return;}
            int language=engine.setLanguage(new Locale("es","ES"));
            ready=language!=TextToSpeech.LANG_MISSING_DATA&&language!=TextToSpeech.LANG_NOT_SUPPORTED;
            if(!ready) {pending=null;return;}
            if(engine.getVoices()!=null) for(Voice voice:engine.getVoices()) {
                if(voice.getLocale().getLanguage().equals("es")&&!voice.isNetworkConnectionRequired()) {engine.setVoice(voice);break;}
            }
            engine.setSpeechRate(1.0f);
            engine.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                public void onStart(String id) { }
                public void onDone(String id) {done(id);}
                public void onError(String id) {done(id);}
                @Override public void onStop(String id,boolean interrupted) {done(id);}
                private void done(String id) {MAIN.post(() -> {if(id.equals(current)) {current="";if(WakeVoiceService.instance!=null) WakeVoiceService.instance.resumeAfterSpeech();}});}
            });
            if(pending!=null) {String text=pending;pending=null;speak(app,text);}
        });
    }
    static void speak(Context c,String text) {
        if(!enabled(c)||text==null||text.isEmpty()) return;
        initialize(c);
        if(!ready) {pending=text;return;}
        if(WakeVoiceService.instance!=null) WakeVoiceService.instance.pauseForSpeech();
        current="jarvis-"+android.os.SystemClock.uptimeMillis();
        int result=engine.speak(text,TextToSpeech.QUEUE_FLUSH,null,current);
        if(result==TextToSpeech.ERROR) {current="";if(WakeVoiceService.instance!=null) WakeVoiceService.instance.resumeAfterSpeech();}
    }
    static void stop() {
        pending=null;current="";if(engine!=null) engine.stop();
        if(WakeVoiceService.instance!=null) WakeVoiceService.instance.resumeAfterSpeech();
    }
    static boolean speaking() {return !current.isEmpty();}
}
