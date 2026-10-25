package ar.aurora.phone;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
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
import java.util.Set;

/** Only explicit actions are executed; no automatic scrolling or passive screen transcription. */
public final class PhoneControlService extends AccessibilityService {
    public static volatile PhoneControlService instance;
    public static volatile String status="Activá el control de Jarvis en Accesibilidad.";
    private final Handler main=new Handler(Looper.getMainLooper());
    private WindowManager windows;
    private LinearLayout panel;
    private TextView panelStatus;
    private boolean registered,running;
    private String lastExternal="",target="",argument="",direction="up";
    private int mode,remaining,stage;
    private long deadline,nextAction;
    private static final int SCROLL=1,SEARCH=2,TAP=3,TYPE=4;
    private final BroadcastReceiver screen=new BroadcastReceiver() {
        @Override public void onReceive(Context context,Intent intent) {
            if(Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {cancelTask();hidePanel();}
            else if(Intent.ACTION_USER_PRESENT.equals(intent.getAction())&&getSharedPreferences("jarvis",0).getBoolean("panel",false)) showPanel();
        }
    };
    private final Runnable tick=new Runnable() {
        public void run() {
            if(!running) return;
            try {step();}catch(RuntimeException changed) {complete("La pantalla cambió. No pude completar la acción.");}
            if(running) main.postDelayed(this,500);
        }
    };
    @Override protected void onServiceConnected() {
        instance=this;windows=(WindowManager)getSystemService(WINDOW_SERVICE);
        IntentFilter filter=new IntentFilter(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_USER_PRESENT);
        try {if(Build.VERSION.SDK_INT>=33) registerReceiver(screen,filter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(screen,filter);registered=true;}catch(RuntimeException ignored) { }
        setStatus("Control del teléfono listo.");
        if(getSharedPreferences("jarvis",0).getBoolean("panel",false)) showPanel();
    }
    public boolean locked() {KeyguardManager k=(KeyguardManager)getSystemService(KEYGUARD_SERVICE);return k!=null&&k.isKeyguardLocked();}
    public String currentPackage() {
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root!=null) {String pkg=str(root.getPackageName());root.recycle();if(!pkg.isEmpty()&&!pkg.equals(getPackageName())) return pkg;}
        return lastExternal;
    }
    public void panel(boolean enabled) {
        getSharedPreferences("jarvis",0).edit().putBoolean("panel",enabled).apply();if(enabled) showPanel();else hidePanel();
    }
    public void stop() {cancelTask();setStatus("Automatización detenida.");}
    private void setStatus(String value) {status=value;if(panelStatus!=null) panelStatus.setText(value);}
    private void cancelTask() {running=false;main.removeCallbacks(tick);argument="";}
    private void complete(String value) {cancelTask();setStatus(value);Conversation.reply(this,value);}
    private void begin(String pkg,int operation,String text,String move,int count,boolean launch) {
        cancelTask();
        if(instance!=this) {complete("El control de Jarvis está desconectado.");return;}
        if(locked()) {complete("Primero confirmá el desbloqueo con Android.");return;}
        if(pkg==null||pkg.isEmpty()) {complete("No identifiqué la app. Probá OK Jarvis desde la pantalla que querés controlar.");return;}
        target=pkg;mode=operation;argument=text;direction=move;remaining=count;stage=0;
        deadline=SystemClock.elapsedRealtime()+20000;nextAction=SystemClock.elapsedRealtime()+(launch?1300:350);running=true;
        if(launch) {
            Intent intent=getPackageManager().getLaunchIntentForPackage(pkg);
            if(intent==null) {complete("No encontré esa aplicación instalada.");return;}
            try {startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}catch(RuntimeException e) {complete("Android no permitió abrir esa app.");return;}
        }
        setStatus(operation==SEARCH?"Preparando la búsqueda…":"Preparando la acción…");main.postDelayed(tick,500);
    }
    public void swipeCurrent(String pkg,String move,int count) {
        if(count<1||count>20||!(move.equals("up")||move.equals("down")||move.equals("left")||move.equals("right"))) {complete("Gesto no válido.");return;}
        begin(pkg,SCROLL,"",move,count,false);
    }
    public void tap(String pkg,String label) {begin(pkg,TAP,label,"",1,false);}
    public void type(String pkg,String text) {begin(pkg,TYPE,text,"",1,false);}
    public void search(String app,String query) {
        if(query==null||query.isEmpty()||query.length()>2000||!(app.equals("google")||app.equals("samsung"))) {complete("La búsqueda no es válida.");return;}
        begin(app.equals("google")?"com.google.android.apps.photos":"com.sec.android.gallery3d",SEARCH,query,"",1,true);
    }
    private void step() {
        long now=SystemClock.elapsedRealtime();
        if(locked()) {complete("El teléfono se bloqueó. La acción se canceló.");return;}
        if(now>=deadline) {complete("No identifiqué los controles a tiempo. Podés completar la acción manualmente.");return;}
        if(now<nextAction) return;
        AccessibilityNodeInfo root=getRootInActiveWindow();if(root==null) return;
        List<AccessibilityNodeInfo> nodes=new ArrayList<>();nodes.add(root);
        try {
            if(!target.equals(str(root.getPackageName()))) {complete("La app activa cambió. No ejecuté la acción en otra pantalla.");return;}
            if(mode==SCROLL) {
                Rect bounds=new Rect();root.getBoundsInScreen(bounds);
                if(!gesture(bounds)) {complete("Android rechazó el gesto.");return;}
                remaining--;nextAction=now+850;if(remaining<=0) complete("Gesto completado.");return;
            }
            for(int i=0;i<nodes.size();i++) {
                AccessibilityNodeInfo node=nodes.get(i);
                for(int j=0;j<node.getChildCount();j++) {
                    if(nodes.size()>=1000) {complete("No pude verificar la pantalla completa. Elegí el control manualmente.");return;}
                    AccessibilityNodeInfo child=node.getChild(j);if(child!=null) nodes.add(child);
                }
            }
            if(mode==SEARCH) {searchStep(nodes);return;}
            if(mode==TAP) {tapStep(nodes);return;}
            if(mode==TYPE) {typeStep(root);return;}
        } finally {for(AccessibilityNodeInfo node:nodes) node.recycle();}
    }
    private boolean gesture(Rect b) {
        if(b.width()<100||b.height()<200) return false;
        float x1=b.left+b.width()*0.45f,x2=x1,y1=b.top+b.height()*0.76f,y2=b.top+b.height()*0.30f;
        if(direction.equals("down")) {float t=y1;y1=y2;y2=t;}
        if(direction.equals("left")||direction.equals("right")) {y1=y2=b.top+b.height()*0.5f;x1=b.left+b.width()*0.76f;x2=b.left+b.width()*0.25f;if(direction.equals("right")) {float t=x1;x1=x2;x2=t;}}
        Path path=new Path();path.moveTo(x1,y1);path.lineTo(x2,y2);
        return dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,350)).build(),null,null);
    }
    private void tapStep(List<AccessibilityNodeInfo> nodes) {
        String wanted=CommandParser.normalize(argument);List<AccessibilityNodeInfo> matches=new ArrayList<>();Set<String> locations=new HashSet<>();
        for(AccessibilityNodeInfo n:nodes) {
            if(!n.isVisibleToUser()||!(wanted.equals(CommandParser.normalize(str(n.getText())))||wanted.equals(CommandParser.normalize(str(n.getContentDescription()))))) continue;
            AccessibilityNodeInfo clickable=clickable(n);
            if(clickable!=null) {Rect b=new Rect();clickable.getBoundsInScreen(b);if(locations.add(b.toShortString())) matches.add(clickable);else clickable.recycle();}
        }
        try {
            if(matches.size()!=1) {complete(matches.isEmpty()?"No encontré un botón con ese nombre exacto.":"Hay varios controles con ese nombre. Elegí el correcto manualmente.");return;}
            boolean done=matches.get(0).refresh()&&matches.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK);
            complete(done?"Toqué el control que confirmaste.":"Android no permitió tocar ese control.");
        } finally {for(AccessibilityNodeInfo n:matches) n.recycle();}
    }
    private void typeStep(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo input=root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        try {
            if(input==null||!input.isVisibleToUser()||!input.isEditable()||input.isPassword()) {complete("Primero seleccioná un campo de texto que no sea una contraseña.");return;}
            String old=input.isShowingHintText()?"":str(input.getText());
            if(old.length()+argument.length()>10000) {complete("El texto es demasiado largo para esta acción.");return;}
            Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,old+argument);
            complete(input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args)?"Agregué el texto en el campo seleccionado. No lo envié.":"No pude escribir en ese campo.");
        } finally {if(input!=null) input.recycle();}
    }
    private void searchStep(List<AccessibilityNodeInfo> nodes) {
        AccessibilityNodeInfo input=null;
        for(AccessibilityNodeInfo n:nodes) {
            String label=(str(n.getHintText())+" "+str(n.getContentDescription())+" "+str(n.getViewIdResourceName())).toLowerCase(java.util.Locale.ROOT);
            if(n.isVisibleToUser()&&n.isEditable()&&(label.contains("buscar")||label.contains("search")||label.contains("query"))) {
                if(input!=null) {complete("Hay varios buscadores. Elegí el campo manualmente.");return;}input=n;
            }
        }
        if(stage==0&&input!=null) {
            Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,argument);
            if(input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args)) {stage=1;return;}complete("El buscador no permite escribir la consulta.");return;
        }
        if(stage==1) {
            if(input==null||!argument.contentEquals(input.getText()==null?"":input.getText())) {complete("Cambió el buscador. Revisá la consulta en la app.");return;}
            complete(input.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId())?"Búsqueda enviada. Revisá los resultados de la app.":"Búsqueda escrita. Tocá la lupa para confirmarla.");return;
        }
        for(AccessibilityNodeInfo n:nodes) {
            String label=CommandParser.normalize(str(n.getText())),desc=CommandParser.normalize(str(n.getContentDescription()));
            if(!n.isVisibleToUser()||!(label.equals("buscar")||label.equals("search")||desc.equals("buscar")||desc.equals("search")||desc.startsWith("buscar fotos")||desc.startsWith("search photos"))) continue;
            AccessibilityNodeInfo button=clickable(n);if(button!=null) {button.performAction(AccessibilityNodeInfo.ACTION_CLICK);button.recycle();return;}
        }
        setStatus("Abrí Buscar en la app. Espero identificar el campo.");
    }
    private AccessibilityNodeInfo clickable(AccessibilityNodeInfo n) {
        AccessibilityNodeInfo current=AccessibilityNodeInfo.obtain(n);
        for(int depth=0;current!=null&&depth<5;depth++) {
            if(current.isVisibleToUser()&&current.isClickable()) return current;
            AccessibilityNodeInfo parent=current.getParent();current.recycle();current=parent;
        }
        if(current!=null) current.recycle();return null;
    }
    public void system(String action) {
        if(action.equals("stop_listening")) {if(WakeVoiceService.instance!=null) WakeVoiceService.instance.stopListening();else Conversation.reply(this,"La escucha ya está apagada.");return;}
        AudioManager audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        if(action.equals("volume_up")||action.equals("volume_down")||action.equals("mute")) {
            if(audio==null) return;
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,action.equals("volume_up")?AudioManager.ADJUST_RAISE:action.equals("volume_down")?AudioManager.ADJUST_LOWER:AudioManager.ADJUST_MUTE,AudioManager.FLAG_SHOW_UI);
            Conversation.reply(this,action.equals("mute")?"Audio multimedia silenciado.":"Volumen multimedia ajustado.");return;
        }
        if(locked()) {Conversation.reply(this,"Confirmá primero el desbloqueo en Android.");return;}
        int code=action.equals("back")?GLOBAL_ACTION_BACK:action.equals("home")?GLOBAL_ACTION_HOME:action.equals("recents")?GLOBAL_ACTION_RECENTS:action.equals("notifications")?GLOBAL_ACTION_NOTIFICATIONS:action.equals("quick_settings")?GLOBAL_ACTION_QUICK_SETTINGS:action.equals("screenshot")?GLOBAL_ACTION_TAKE_SCREENSHOT:action.equals("lock")?GLOBAL_ACTION_LOCK_SCREEN:0;
        boolean success=code!=0&&performGlobalAction(code);Conversation.reply(this,success?"Listo.":"Android no permitió esa acción.");
    }
    public void spoken(String text) {
        cancelTask();
        CommandParser.Command c=CommandParser.parse(text);Conversation.user(text);
        if(c.type==CommandParser.Type.STOP) {stop();Conversation.reply(this,"Automatización detenida.");return;}
        if(c.type==CommandParser.Type.HELP) {Conversation.reply(this,c.action.equals("hello")?"Hola, soy Jarvis. ¿Qué querés hacer?":"Puedo abrir apps, bajar o subir la pantalla, volver, ir al inicio, ajustar el volumen y ayudarte con tus fotos. Para tocar controles o escribir texto te pediré confirmación.");return;}
        if(c.type==CommandParser.Type.UNLOCK) {startActivity(new Intent(this,UnlockActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return;}
        if(c.type==CommandParser.Type.SYSTEM&&!c.action.equals("lock")) {system(c.action);return;}
        if(locked()) {Conversation.reply(this,"El teléfono está protegido. Pedime desbloquear y confirmá con Android.");return;}
        String origin=currentPackage();
        if(c.type==CommandParser.Type.SCROLL&&c.target.isEmpty()) {swipeCurrent(origin,c.direction,c.count);return;}
        if(c.type==CommandParser.Type.OPEN_APP) {
            String name=CommandParser.normalize(c.query);
            if(name.equals("ajustes")||name.equals("configuracion")) {startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));Conversation.reply(this,"Abrí Ajustes.");return;}
            List<AppLauncher.App> apps=AppLauncher.find(this,c.query);
            if(apps.size()==1) {Conversation.reply(this,AppLauncher.open(this,apps.get(0))?"Abrí "+apps.get(0).name+".":"No pude abrir esa app.");return;}
        }
        if(c.type==CommandParser.Type.UNKNOWN) {Conversation.reply(this,"No reconocí esa orden. Podés decir bajá, abrí una app, atrás o buscá fotos.");return;}
        startActivity(new Intent(this,CommandActivity.class).putExtra("command",text).putExtra("origin",origin).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
    private void showPanel() {
        if(panel!=null||windows==null||locked()) return;
        panel=new LinearLayout(this);panel.setGravity(Gravity.CENTER_VERTICAL);panel.setPadding(10,5,10,5);panel.setBackground(AppUi.background(this,AppUi.CARD,24));
        panelStatus=AppUi.text(this,"Jarvis",12,AppUi.TEXT);panelStatus.setMaxLines(2);panel.addView(panelStatus,new LinearLayout.LayoutParams(0,-2,1));
        Button mic=AppUi.button(this,"Hablar",true);mic.setOnClickListener(v -> {
            String origin=currentPackage();
            if(WakeVoiceService.instance!=null) WakeVoiceService.instance.pauseManual();
            startActivity(new Intent(this,CommandActivity.class).putExtra("listen",true).putExtra("origin",origin).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        });panel.addView(mic);
        Button stop=AppUi.button(this,"Apagar",false);stop.setOnClickListener(v -> {stop();panel(false);if(WakeVoiceService.instance!=null) WakeVoiceService.instance.stopListening();});panel.addView(stop);
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(AppUi.dp(this,310),-2,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.TOP|Gravity.END;p.y=AppUi.dp(this,72);p.setTitle("Jarvis: controles de voz");
        try {windows.addView(panel,p);}catch(RuntimeException e) {panel=null;panelStatus=null;}
    }
    private void hidePanel() {if(panel!=null&&windows!=null) {try {windows.removeView(panel);}catch(RuntimeException ignored) {}}panel=null;panelStatus=null;}
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if(event!=null&&event.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED&&(panel!=null||WakeVoiceService.instance!=null)) {
            String pkg=str(event.getPackageName());if(!pkg.isEmpty()&&!pkg.equals(getPackageName())&&!pkg.equals("com.android.systemui")&&!pkg.contains("inputmethod")&&!pkg.contains("keyboard")) lastExternal=pkg;
        }
    }
    private static String str(CharSequence s) {return s==null?"":s.toString();}
    @Override public void onInterrupt() {stop();}
    private void release() {cancelTask();hidePanel();if(registered) {try {unregisterReceiver(screen);}catch(RuntimeException ignored) {}}registered=false;if(instance==this) instance=null;if(WakeVoiceService.instance!=null) WakeVoiceService.instance.stopListening();status="Control de Jarvis desconectado.";}
    @Override public boolean onUnbind(Intent i) {release();return super.onUnbind(i);}
    @Override public void onDestroy() {release();super.onDestroy();}
}
