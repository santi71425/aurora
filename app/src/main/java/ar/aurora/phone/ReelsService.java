package ar.aurora.phone;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Bounded, explicitly started gestures. Does not save screen content. */
public final class ReelsService extends AccessibilityService {
    public static volatile ReelsService instance;
    public static volatile String status="Para gestos y búsquedas en otras apps, activá Aurora en Accesibilidad.";
    private static final String INSTAGRAM="com.instagram.android";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean running, seenViewer, seenApp, registered;
    private long deadline, grace, nextSwipe;
    private int seconds;
    private int mode, remaining, searchStage;
    private String targetPackage=INSTAGRAM, direction="up", searchQuery="";
    private WindowManager manager;
    private LinearLayout overlay;
    private TextView overlayText;
    private final BroadcastReceiver screen=new BroadcastReceiver() {
        public void onReceive(Context context,Intent intent) { if(Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) finish("Sesión detenida: pantalla apagada."); }
    };
    private final Runnable tick=new Runnable() {
        public void run() {
            if(!running) return;
            try { step(); }
            catch(RuntimeException changed) { finish("Sesión detenida: cambió la pantalla o el permiso de Accesibilidad."); }
            if(running) handler.postDelayed(this,600);
        }
    };
    @Override protected void onServiceConnected() {
        super.onServiceConnected(); instance=this; manager=(WindowManager)getSystemService(WINDOW_SERVICE);
        try {
            IntentFilter filter=new IntentFilter(Intent.ACTION_SCREEN_OFF);
            if(Build.VERSION.SDK_INT>=33) registerReceiver(screen,filter,Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(screen,filter);
            registered=true;
        } catch(RuntimeException ignored) { }
        status="Control de Reels listo. Solo se inicia cuando lo pedís.";
    }
    public void start(int interval) {
        start(interval,"up");
    }
    public void start(int interval,String requestedDirection) {
        clear();
        if(interval<3||interval>300) { status="El intervalo debe ser de 3 a 300 segundos."; return; }
        if(locked()) { status="Desbloqueá el teléfono para usar Reels."; return; }
        Intent launch=getPackageManager().getLaunchIntentForPackage(INSTAGRAM);
        if(launch==null) { status="No encontré Instagram instalado."; return; }
        mode=0; direction=requestedDirection; targetPackage=INSTAGRAM;
        seconds=interval; running=true; deadline=SystemClock.elapsedRealtime()+15*60*1000L;
        grace=SystemClock.elapsedRealtime()+10000;
        setStatus("Abrí el visor de Reels. Espero identificarlo antes de deslizar.");
        if(!showOverlay()) { finish("No pude mostrar DETENER. Revisá Accesibilidad y volvé a intentar."); return; }
        try { startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch(RuntimeException unavailable) { finish("No pude abrir Instagram."); return; }
        handler.postDelayed(tick,600);
    }
    public void swipe(String target,String requestedDirection,int count) {
        clear();
        if(count<1||count>20||!(requestedDirection.equals("up")||requestedDirection.equals("down")||requestedDirection.equals("left")||requestedDirection.equals("right"))) { status="Gesto no válido."; return; }
        mode=1;remaining=count;direction=requestedDirection;seconds=1;
        launchTask(target,"Preparando "+count+" gesto(s). DETENER cancela los siguientes.",30000);
    }
    public void search(String target,String query) {
        clear();
        if(!(target.equals("google")||target.equals("samsung"))||query==null||query.trim().isEmpty()||query.length()>2000) { status="La búsqueda o la app no son válidas."; return; }
        mode=2;searchQuery=query;searchStage=0;
        launchTask(target,"Abriendo el buscador de "+(target.equals("google")?"Google Fotos":"Galería Samsung")+"…",20000);
    }
    private void launchTask(String target,String note,long timeout) {
        targetPackage=target.equals("google")?"com.google.android.apps.photos":target.equals("samsung")?"com.sec.android.gallery3d":INSTAGRAM;
        if(locked()) { status="Desbloqueá el teléfono primero."; return; }
        Intent launch=getPackageManager().getLaunchIntentForPackage(targetPackage);
        if(launch==null) { status="No encontré esa aplicación instalada."; return; }
        running=true;deadline=SystemClock.elapsedRealtime()+timeout;grace=SystemClock.elapsedRealtime()+10000;nextSwipe=SystemClock.elapsedRealtime()+1500;
        setStatus(note);
        if(!showOverlay()) { finish("No pude mostrar DETENER. Revisá Accesibilidad.");return; }
        try { startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch(RuntimeException e) { finish("No pude abrir la aplicación.");return; }
        handler.postDelayed(tick,700);
    }
    public void stop() {
        if(Looper.myLooper()==Looper.getMainLooper()) finish("Automatización detenida.");
        else handler.post(() -> finish("Automatización detenida."));
    }
    private void step() {
        long now=SystemClock.elapsedRealtime();
        if(locked()) { finish("Sesión detenida: teléfono bloqueado."); return; }
        if(now>=deadline) { finish(mode==0?"Sesión terminada: límite de 15 minutos.":"No pude completar la acción. Usá los controles de la app o volvé a intentar."); return; }
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root==null) { if(now>grace) finish("No puedo identificar la pantalla activa."); return; }
        List<AccessibilityNodeInfo> nodes=new ArrayList<>(); nodes.add(root);
        try {
            if(!targetPackage.contentEquals(root.getPackageName()==null?"":root.getPackageName())) {
                if(seenApp||now>grace) finish("Sesión detenida: saliste de la aplicación elegida.");
                return;
            }
            seenApp=true;
            for(int i=0;i<nodes.size();i++) {
                AccessibilityNodeInfo node=nodes.get(i);
                for(int j=0;j<node.getChildCount();j++) {
                    if(nodes.size()>=800) { finish("No pude verificar el visor completo. No voy a deslizar."); return; }
                    AccessibilityNodeInfo child=node.getChild(j); if(child!=null) nodes.add(child);
                }
            }
            Rect bounds=new Rect(); root.getBoundsInScreen(bounds);
            if(mode==2) { searchStep(nodes);return; }
            if(mode==1) {
                if(now<nextSwipe) return;
                if(!gesture(bounds)) { finish("Android rechazó el gesto.");return; }
                remaining--;nextSwipe=now+1100;
                if(remaining==0) finish("Gesto completado. No hay más deslizamientos programados.");
                return;
            }
            if(!isViewer(nodes,bounds)) {
                if(seenViewer) { finish("Sesión detenida: saliste del visor o abriste otro panel."); return; }
                setStatus("En espera: abrí un reel. Si no identifico el visor, no deslizo."); return;
            }
            if(!seenViewer) { seenViewer=true; nextSwipe=now+seconds*1000L; setStatus("Reels activos: cada "+seconds+" s. Máximo 15 minutos."); }
            if(now<nextSwipe) return;
            if(!gesture(bounds)) finish("Android rechazó el gesto.");
            else nextSwipe=now+seconds*1000L;
        } finally { for(AccessibilityNodeInfo node:nodes) node.recycle(); }
    }
    private boolean gesture(Rect b) {
        if(b.width()<100||b.height()<300) return false;
        float x1=b.left+b.width()*0.45f,x2=x1,y1=b.top+b.height()*0.76f,y2=b.top+b.height()*0.30f;
        if(direction.equals("down")) { float temp=y1;y1=y2;y2=temp; }
        if(direction.equals("left")||direction.equals("right")) {
            y1=y2=b.top+b.height()*0.52f;x1=b.left+b.width()*0.76f;x2=b.left+b.width()*0.25f;
            if(direction.equals("right")) { float temp=x1;x1=x2;x2=temp; }
        }
        Path path=new Path();path.moveTo(x1,y1);path.lineTo(x2,y2);
        return dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,350)).build(),null,null);
    }
    private void searchStep(List<AccessibilityNodeInfo> nodes) {
        AccessibilityNodeInfo input=null;
        for(AccessibilityNodeInfo n:nodes) {
            if(!n.isVisibleToUser()||!n.isEditable()) continue;
            String label=(str(n.getHintText())+" "+str(n.getContentDescription())+" "+str(n.getViewIdResourceName())).toLowerCase(Locale.ROOT);
            if(label.contains("search")||label.contains("buscar")||label.contains("query")) {
                if(input!=null) { finish("Hay varios campos de búsqueda. Elegí el correcto manualmente.");return; } input=n;
            }
        }
        if(searchStage==0&&input!=null) {
            android.os.Bundle args=new android.os.Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,searchQuery);
            if(input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args)) { searchStage=1;return; }
            finish("El buscador no permitió escribir. Ingresá la consulta manualmente.");return;
        }
        if(searchStage==1) {
            if(input==null||!searchQuery.contentEquals(input.getText()==null?"":input.getText())) { finish("Cambió el campo de búsqueda. Revisá la consulta en la app.");return; }
            boolean submitted=input.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId());
            finish(submitted?"Búsqueda enviada a la app. Revisá sus resultados.":"Búsqueda escrita. Tocá Buscar o la lupa para ver los resultados.");return;
        }
        for(AccessibilityNodeInfo n:nodes) {
            if(!n.isVisibleToUser()||n.isEditable()) continue;
            String text=CommandParser.normalize(str(n.getText())),desc=CommandParser.normalize(str(n.getContentDescription()));
            if(!(text.equals("buscar")||text.equals("search")||desc.equals("buscar")||desc.equals("search")||desc.startsWith("buscar fotos")||desc.startsWith("search photos"))) continue;
            AccessibilityNodeInfo current=AccessibilityNodeInfo.obtain(n);
            for(int depth=0;current!=null&&depth<4;depth++) {
                if(current.isClickable()) { current.performAction(AccessibilityNodeInfo.ACTION_CLICK);current.recycle();return; }
                AccessibilityNodeInfo parent=current.getParent();current.recycle();current=parent;
            }
            if(current!=null) current.recycle();
        }
        setStatus("No identifiqué el buscador todavía. Podés abrir Buscar manualmente; espero hasta 20 segundos.");
    }
    private boolean isViewer(List<AccessibilityNodeInfo> nodes,Rect screen) {
        if(screen.width()<100||screen.height()<300) return false;
        boolean container=false, namedVideo=false; Set<String> actions=new HashSet<>();
        for(AccessibilityNodeInfo n:nodes) {
            if(!n.isVisibleToUser()) continue;
            String cls=str(n.getClassName()).toLowerCase(Locale.ROOT);
            String id=str(n.getViewIdResourceName()).toLowerCase(Locale.ROOT);
            String desc=str(n.getContentDescription()).trim().toLowerCase(Locale.ROOT);
            if(n.isEditable()||cls.endsWith("edittext")) return false;
            if(id.contains("comment_composer")||id.contains("bottom_sheet")||id.contains("share_sheet")||id.contains("direct_share")) return false;
            Rect b=new Rect(); n.getBoundsInScreen(b);
            if((id.contains("clips_viewer")||id.contains("reels_viewer")||id.contains("reel_viewer"))
                    &&b.height()>screen.height()*0.55f&&b.width()>screen.width()*0.60f) container=true;
            if((desc.startsWith("reel by ")||desc.startsWith("reel de ")||desc.startsWith("reel video"))
                    &&b.height()>screen.height()*0.50f&&b.width()>screen.width()*0.60f) namedVideo=true;
            if(b.centerX()>screen.left+screen.width()*0.72f&&b.top>screen.top+screen.height()*0.20f&&b.bottom<screen.top+screen.height()*0.94f) {
                if(label(desc,"like","me gusta")) actions.add("like");
                if(label(desc,"comment","comments","comentar","comentarios")) actions.add("comment");
                if(label(desc,"share","compartir")) actions.add("share");
            }
        }
        return (container||namedVideo)&&actions.size()>=2;
    }
    private static String str(CharSequence s) { return s==null?"":s.toString(); }
    private static boolean label(String value,String... labels) {
        for(String label:labels) if(value.equals(label)||value.startsWith(label+",")||value.startsWith(label+" ")) return true;
        return false;
    }
    private boolean locked() {
        KeyguardManager k=(KeyguardManager)getSystemService(KEYGUARD_SERVICE); PowerManager p=(PowerManager)getSystemService(POWER_SERVICE);
        return (k!=null&&k.isKeyguardLocked())||(p!=null&&!p.isInteractive());
    }
    private boolean showOverlay() {
        if(manager==null) return false;
        overlay=new LinearLayout(this); overlay.setGravity(Gravity.CENTER_VERTICAL); overlay.setPadding(12,8,12,8); overlay.setBackgroundColor(Color.rgb(23,35,46));
        overlayText=AppUi.text(this,status,12,Color.WHITE); overlayText.setMaxLines(3);
        overlay.addView(overlayText,new LinearLayout.LayoutParams(0,-2,1));
        Button stop=AppUi.button(this,"DETENER",true); stop.setOnClickListener(v -> stop()); overlay.addView(stop);
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(-1,-2,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.TOP; p.y=AppUi.dp(this,72); p.setTitle("Aurora: sesión de Reels");
        try { manager.addView(overlay,p); return true; }
        catch(RuntimeException e) { overlay=null; overlayText=null; return false; }
    }
    private void setStatus(String value) { status=value; if(overlayText!=null) overlayText.setText(value); }
    private void finish(String value) { clear(); status=value; }
    private void clear() {
        running=false; seenViewer=false; seenApp=false; searchQuery=""; handler.removeCallbacks(tick);
        if(overlay!=null&&manager!=null) { try { manager.removeView(overlay); } catch(RuntimeException ignored) { } }
        overlay=null; overlayText=null;
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { /* Ticker inspects UI only during an explicit session. */ }
    @Override public void onInterrupt() { finish("Android interrumpió Accesibilidad."); }
    private void release() {
        clear(); if(registered) { try { unregisterReceiver(screen); } catch(RuntimeException ignored) { } registered=false; }
        if(instance==this) instance=null; status="Control de Reels desconectado.";
    }
    @Override public boolean onUnbind(Intent intent) { release(); return super.onUnbind(intent); }
    @Override public void onDestroy() { release(); super.onDestroy(); }
}
