package ar.aurora.phone.tests;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.net.Uri;
import android.content.ContentValues;
import android.provider.MediaStore;
import android.graphics.Bitmap;

/** UI smoke tests on a clean emulator; no WhatsApp/Instagram accounts or real photo deletion. */
public final class SmokeRunner extends Instrumentation {
    private Uri fixture;
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    private static View find(View v,Class<?> type,String text) {
        if(type.isInstance(v) && (text==null || (v instanceof TextView && ((TextView)v).getText().toString().contains(text)))) return v;
        if(v instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)v;
            for(int i=0;i<group.getChildCount();i++) { View found=find(group.getChildAt(i),type,text); if(found!=null) return found; }
        }
        return null;
    }
    private void command(Activity main,String text) {
        runOnMainSync(() -> {
            View decor=main.getWindow().getDecorView();
            EditText input=(EditText)find(decor,EditText.class,null);
            Button go=(Button)find(decor,Button.class,"↑");
            if(input==null||go==null) throw new AssertionError("Falta entrada de comandos");
            input.setText(text); go.performClick();
        });
        waitForIdleSync();
    }
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            Activity main=startActivitySync(new Intent().setClassName("ar.aurora.phone","ar.aurora.phone.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            runOnMainSync(() -> { if(find(main.getWindow().getDecorView(),TextView.class,"Jarvis")==null) throw new AssertionError("No abrió Jarvis"); });
            runOnMainSync(() -> {
                Button send=(Button)find(main.getWindow().getDecorView(),Button.class,"↑");
                Button wake=(Button)find(main.getWindow().getDecorView(),Button.class,"OK Jarvis");
                if(send==null||wake==null) throw new AssertionError("Faltan controles de la conversación o de activación");
                android.graphics.Rect visible=new android.graphics.Rect();
                if(!send.getGlobalVisibleRect(visible)||visible.height()<32||visible.width()<32) throw new AssertionError("El compositor está recortado");
            });
            Class<?> wakeService=getTargetContext().getClassLoader().loadClass("ar.aurora.phone.WakeVoiceService");
            if(wakeService.getField("instance").get(null)!=null) throw new AssertionError("El micrófono continuo se inició sin activación explícita");
            command(main,"detener");
            runOnMainSync(() -> { if(find(main.getWindow().getDecorView(),TextView.class,"Automatización detenida")==null) throw new AssertionError("No funciona Detener"); });
            command(main,"hola");
            runOnMainSync(() -> {if(find(main.getWindow().getDecorView(),TextView.class,"Hola, soy Jarvis")==null) throw new AssertionError("Falta respuesta de conversación");});
            command(main,"deja de escuchar");
            runOnMainSync(() -> {if(find(main.getWindow().getDecorView(),TextView.class,"OK Jarvis apagado")==null) throw new AssertionError("No funciona apagar escucha");});
            ActivityMonitor whatsapp=addMonitor("ar.aurora.phone.WhatsAppActivity",null,false);
            command(main,"Escribile a Mom por WhatsApp: Te amo");
            Activity message=waitForMonitorWithTimeout(whatsapp,5000); removeMonitor(whatsapp);
            if(message==null) throw new AssertionError("No abrió revisión WhatsApp");
            runOnMainSync(() -> { if(find(message.getWindow().getDecorView(),TextView.class,"Te amo")==null) throw new AssertionError("Mensaje incorrecto"); message.finish(); });
            waitForIdleSync();
            ActivityMonitor photos=addMonitor("ar.aurora.phone.DuplicatesActivity",null,false);
            command(main,"Buscá fotos duplicadas");
            Activity gallery=waitForMonitorWithTimeout(photos,5000); removeMonitor(photos);
            if(gallery==null) throw new AssertionError("No abrió revisión de fotos");
            runOnMainSync(() -> {
                Button button=(Button)find(gallery.getWindow().getDecorView(),Button.class,"Revisar selección (0)");
                if(button==null||button.isEnabled()) throw new AssertionError("El borrado debe estar deshabilitado sin selección");
                gallery.finish();
            });
            waitForIdleSync();
            ActivityMonitor searches=addMonitor("ar.aurora.phone.GalleryActivity",null,false);
            command(main,"Mostrame fotos de perros");
            Activity search=waitForMonitorWithTimeout(searches,5000);removeMonitor(searches);
            if(search==null) throw new AssertionError("No abrió búsqueda flexible");
            runOnMainSync(() -> {if(find(search.getWindow().getDecorView(),Button.class,"Google Fotos")==null) throw new AssertionError("Falta búsqueda en Google Fotos");search.finish();});
            waitForIdleSync();
            ContentValues values=new ContentValues();values.put(MediaStore.Images.Media.DISPLAY_NAME,"AuroraSmokeScreenshot.png");
            values.put(MediaStore.Images.Media.MIME_TYPE,"image/png");values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/Screenshots");values.put(MediaStore.Images.Media.IS_PENDING,1);
            fixture=getTargetContext().getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
            if(fixture==null) throw new AssertionError("No pude preparar la captura de prueba");
            try(java.io.OutputStream out=getTargetContext().getContentResolver().openOutputStream(fixture)) {
                Bitmap bitmap=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888);bitmap.eraseColor(android.graphics.Color.MAGENTA);
                bitmap.compress(Bitmap.CompressFormat.PNG,100,out);bitmap.recycle();
            }
            values.clear();values.put(MediaStore.Images.Media.IS_PENDING,0);getTargetContext().getContentResolver().update(fixture,values,null,null);
            ActivityMonitor captures=addMonitor("ar.aurora.phone.GalleryActivity",null,false);
            command(main,"¿Podés mostrarme capturas de pantalla?");
            Activity capture=waitForMonitorWithTimeout(captures,5000);removeMonitor(captures);
            if(capture==null) throw new AssertionError("No abrió filtro de capturas");
            final boolean[] matched={false};
            for(int i=0;i<40&&!matched[0];i++) {
                waitForIdleSync();runOnMainSync(() -> matched[0]=find(capture.getWindow().getDecorView(),TextView.class,"AuroraSmokeScreenshot.png")!=null);
                if(!matched[0]) Thread.sleep(250);
            }
            if(!matched[0]) throw new AssertionError("El filtro no encontró la captura de prueba");
            getTargetContext().getContentResolver().delete(fixture,null,null);fixture=null;
            runOnMainSync(() -> {capture.finish();});waitForIdleSync();
            runOnMainSync(() -> {
                Button fresh=(Button)find(main.getWindow().getDecorView(),Button.class,"+");
                if(fresh==null) throw new AssertionError("Falta nueva conversación");fresh.performClick();
                if(find(main.getWindow().getDecorView(),TextView.class,"¿Qué hacemos hoy?")==null) throw new AssertionError("No limpió la conversación");
                main.finish();
            });
            result.putString("stream","JARVIS_SMOKE_OK: interfaz Jarvis y compositor visible, conversación, apagar escucha, detener, mensaje, duplicados, búsqueda flexible y captura local encontrada\n");
            finish(Activity.RESULT_OK,result);
        } catch(Throwable error) {
            if(fixture!=null) {try {getTargetContext().getContentResolver().delete(fixture,null,null);}catch(RuntimeException ignored) {}}
            result.putString("stream","JARVIS_SMOKE_FAILED: "+error.toString()+"\n");
            finish(Activity.RESULT_CANCELED,result);
        }
    }
}
