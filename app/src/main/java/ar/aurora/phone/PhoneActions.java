package ar.aurora.phone;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import java.util.List;

final class PhoneActions {
    static void dispatch(Activity a,CommandParser.Command c,String origin,boolean transientWindow) {
        PhoneControlService service=PhoneControlService.instance;
        switch(c.type) {
            case HELP:
                reply(a,c.action.equals("hello")?"Hola, soy Jarvis. ¿Qué querés hacer?":"Puedo abrir apps, bajar o subir la pantalla, volver, ir al inicio, cambiar el volumen, buscar fotos y preparar mensajes. Activá OK Jarvis para dar órdenes desde otras apps.");done(a,transientWindow);break;
            case STOP:
                if(service!=null) service.stop();reply(a,"Automatización detenida.");done(a,transientWindow);break;
            case OPEN_APP:
                String normalized=CommandParser.normalize(c.query);
                if(normalized.equals("ajustes")||normalized.equals("configuracion")) {a.startActivity(new Intent(Settings.ACTION_SETTINGS));reply(a,"Abrí Ajustes.");done(a,transientWindow);break;}
                List<AppLauncher.App> apps=AppLauncher.find(a,c.query);
                if(apps.isEmpty()) {reply(a,"No encontré una app instalada con ese nombre.");done(a,transientWindow);break;}
                if(apps.size()==1) {open(a,apps.get(0),transientWindow);break;}
                String[] labels=new String[apps.size()];for(int i=0;i<labels.length;i++) labels[i]=apps.get(i).name+" · "+apps.get(i).pkg;
                new AlertDialog.Builder(a).setTitle("Elegí la app").setItems(labels,(d,w) -> open(a,apps.get(w),transientWindow)).setNegativeButton("Cancelar",(d,w) -> done(a,transientWindow)).setOnCancelListener(d -> done(a,transientWindow)).show();break;
            case UNLOCK:
                a.startActivity(new Intent(a,UnlockActivity.class));done(a,transientWindow);break;
            case WHATSAPP:
                a.startActivity(new Intent(a,WhatsAppActivity.class).putExtra("contact",c.contact).putExtra("message",c.message));reply(a,"Revisá el contacto y el mensaje antes de enviarlo.");done(a,transientWindow);break;
            case GALLERY:
                a.startActivity(new Intent(a,GalleryActivity.class).putExtra("query",c.query).putExtra("target",c.target));reply(a,"Abrí la búsqueda de fotos y videos.");done(a,transientWindow);break;
            case PICK:
                a.startActivity(new Intent(a,GalleryActivity.class).putExtra("query","").putExtra("target","picker"));done(a,transientWindow);break;
            case DUPLICATES:
                a.startActivity(new Intent(a,DuplicatesActivity.class));reply(a,"Voy a revisar duplicados. No borro nada sin tu selección y confirmación.");done(a,transientWindow);break;
            case SCROLL:
                if(service==null) {requireControl(a);break;}
                if(!c.target.isEmpty()) {
                    String name=c.target.equals("google")?"Google Fotos":c.target.equals("samsung")?"Galería Samsung":c.target;
                    List<AppLauncher.App> targets=AppLauncher.find(a,name);
                    if(targets.size()!=1) {reply(a,"No identifiqué una app única para ese gesto.");done(a,transientWindow);break;}
                    AppLauncher.App app=targets.get(0);AppLauncher.open(a,app);done(a,transientWindow);new Handler(Looper.getMainLooper()).postDelayed(() -> service.swipeCurrent(app.pkg,c.direction,c.count),900);break;
                }
                if(origin==null||origin.isEmpty()) {reply(a,"Usá OK Jarvis o el micrófono flotante desde la pantalla que querés desplazar.");done(a,transientWindow);break;}
                restore(a,origin,transientWindow,() -> service.swipeCurrent(origin,c.direction,c.count));break;
            case SYSTEM:
                if(c.action.equals("stop_listening")) {if(WakeVoiceService.instance!=null) WakeVoiceService.instance.stopListening();reply(a,"OK Jarvis apagado.");done(a,transientWindow);break;}
                if(service==null) {requireControl(a);break;}
                if(c.action.equals("lock")) new AlertDialog.Builder(a).setTitle("Bloquear el teléfono")
                    .setMessage("Android bloqueará la pantalla. Para volver a entrar necesitarás tu autenticación habitual.")
                    .setNegativeButton("Cancelar",(d,w) -> done(a,transientWindow)).setPositiveButton("Bloquear",(d,w) -> after(a,transientWindow,() -> service.system("lock"))).setOnCancelListener(d -> done(a,transientWindow)).show();
                else after(a,transientWindow,() -> service.system(c.action));break;
            case TAP: case TYPE_TEXT:
                if(service==null) {requireControl(a);break;}
                if(!transientWindow) {reply(a,"Para tocar o escribir en otra app, dejá esa pantalla abierta y pedímelo con OK Jarvis o el micrófono flotante. Te mostraré una confirmación.");break;}
                new AlertDialog.Builder(a).setTitle(c.type==CommandParser.Type.TAP?"Confirmar toque":"Confirmar texto")
                    .setMessage((c.type==CommandParser.Type.TAP?"Tocar el control con nombre exacto:\n":"Agregar al campo de texto seleccionado:\n")+c.query+"\n\nRevisá la pantalla de la app detrás de este diálogo. No se manipulan contraseñas.")
                    .setNegativeButton("Cancelar",(d,w) -> done(a,true)).setPositiveButton("Confirmar",(d,w) -> after(a,true,() -> {if(c.type==CommandParser.Type.TAP) service.tap(origin,c.query);else service.type(origin,c.query);})).setOnCancelListener(d -> done(a,true)).show();break;
            default:
                reply(a,"No reconocí esa orden. Probá: bajá, abrí WhatsApp, atrás, mostrame capturas o ayuda.");done(a,transientWindow);
        }
    }
    private static void open(Activity a,AppLauncher.App app,boolean close) {reply(a,AppLauncher.open(a,app)?"Abrí "+app.name+".":"No pude abrir esa app.");done(a,close);}
    private static void restore(Activity a,String pkg,boolean close,Runnable task) {
        if(!close&&!pkg.equals(a.getPackageName())) {
            Intent intent=a.getPackageManager().getLaunchIntentForPackage(pkg);
            if(intent==null) {reply(a,"No pude volver a esa pantalla. Usá OK Jarvis con la app abierta.");return;}
            try {a.startActivity(intent);}catch(RuntimeException e) {reply(a,"Android no permitió volver a esa app.");return;}
        }
        after(a,close,task);
    }
    private static void after(Activity a,boolean close,Runnable action) {done(a,close);new Handler(Looper.getMainLooper()).postDelayed(action,500);}
    private static void done(Activity a,boolean close) {if(close) a.finish();}
    static void reply(Activity a,String text) {Conversation.reply(a,text);}
    static void requireControl(Activity a) {
        new AlertDialog.Builder(a).setTitle("Control del teléfono")
            .setMessage("Habilitá Jarvis en Ajustes → Accesibilidad → Aplicaciones instaladas. Podrá identificar controles y realizar gestos en las apps cuando se lo pidas. No guarda pantallas ni manipula campos de contraseña.")
            .setNegativeButton("Cancelar",null).setPositiveButton("Abrir Ajustes",(d,w) -> a.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))).show();
    }
}
