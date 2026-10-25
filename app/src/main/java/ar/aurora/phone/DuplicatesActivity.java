package ar.aurora.phone;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.provider.MediaStore;
import android.util.Size;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Exact local duplicates; only the explicitly checked copies can enter Android's delete dialog. */
public final class DuplicatesActivity extends Activity {
    private static final int PERMISSION=40, DELETE=41, PAGE_SIZE=20, MAX_DELETE=100;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ExecutorService thumbnails=Executors.newFixedThreadPool(2);
    private final List<Pair> pairs=new ArrayList<>();
    private final Set<Uri> selected=new HashSet<>();
    private AtomicBoolean cancelled=new AtomicBoolean();
    private CancellationSignal querySignal;
    private volatile boolean destroyed;
    private boolean busy, foreground, waitingDelete;
    private int generation, page;
    private volatile int renderGeneration;
    private TextView summary, progress, pageLabel;
    private Button access, scan, cancel, delete, previous, next;
    private LinearLayout cards;
    private AlertDialog review;
    private String scope;
    private static final class Photo {
        final Uri uri; final String name; final long bytes,added,modified,revision;
        Photo(Uri u,String n,long b,long a,long m,long r) { uri=u;name=n;bytes=b;added=a;modified=m;revision=r; }
        boolean same(Photo other) { return other!=null && uri.equals(other.uri) && bytes==other.bytes && modified==other.modified && revision==other.revision; }
    }
    private static final class Pair {
        final Photo keeper,copy; final String hash;
        Pair(Photo k,Photo c,String h) { keeper=k;copy=c;hash=h; }
    }
    private static final String[] COLUMNS={MediaStore.Images.Media._ID,MediaStore.Images.Media.DISPLAY_NAME,MediaStore.Images.Media.SIZE,
        MediaStore.Images.Media.DATE_ADDED,MediaStore.Images.Media.DATE_MODIFIED,MediaStore.Images.Media.GENERATION_MODIFIED,
        MediaStore.Images.Media.VOLUME_NAME,MediaStore.Images.Media.IS_PENDING,MediaStore.Images.Media.IS_TRASHED};
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout root=AppUi.column(this); setContentView(root); AppUi.insets(this,root);
        root.addView(AppUi.text(this,"Fotos duplicadas",25,AppUi.TEXT));
        root.addView(AppUi.text(this,"Compara archivos idénticos. Conserva la copia más antigua y nunca selecciona fotos para borrar por su cuenta. Las fotos parecidas no se incluyen.",14,AppUi.MUTED),AppUi.spaced(this));
        summary=AppUi.text(this,"",14,AppUi.TEXT); root.addView(summary,AppUi.spaced(this));
        access=AppUi.button(this,"Elegir acceso a fotos",false); access.setOnClickListener(v -> askAccess()); root.addView(access);
        LinearLayout actions=new LinearLayout(this);
        scan=AppUi.button(this,"Analizar",true); scan.setOnClickListener(v -> { if(hasAccess()) scan(); else askAccess(); }); actions.addView(scan,new LinearLayout.LayoutParams(0,-2,1));
        cancel=AppUi.button(this,"Cancelar análisis",false); cancel.setOnClickListener(v -> cancelOperation("Análisis cancelado. Podés volver a iniciarlo.")); actions.addView(cancel,new LinearLayout.LayoutParams(0,-2,1)); root.addView(actions);
        progress=AppUi.text(this,"El análisis se procesa en el teléfono; no sube fotos.",12,AppUi.MUTED); root.addView(progress);
        ScrollView scroll=new ScrollView(this); cards=AppUi.column(this); scroll.addView(cards); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout nav=new LinearLayout(this);
        previous=AppUi.button(this,"‹",false); previous.setOnClickListener(v -> { page--; render(); }); nav.addView(previous);
        pageLabel=AppUi.text(this,"",13,AppUi.MUTED); nav.addView(pageLabel,new LinearLayout.LayoutParams(0,-2,1));
        next=AppUi.button(this,"›",false); next.setOnClickListener(v -> { page++; render(); }); nav.addView(next); root.addView(nav);
        delete=AppUi.button(this,"Revisar selección (0)",true); delete.setOnClickListener(v -> review()); root.addView(delete);
        Button back=AppUi.button(this,"Volver",false); back.setOnClickListener(v -> finish()); root.addView(back);
        scope=permissionScope(); updateScope(); render(); controls();
        if(saved!=null && saved.getBoolean("waitingDelete",false)) { waitingDelete=true; progress.setText("Volvé a analizar cuando cierre el diálogo de Android."); controls(); }
    }
    private String permissionScope() {
        if(Build.VERSION.SDK_INT>=33) {
            if(checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES)==PackageManager.PERMISSION_GRANTED) return "all";
            if(Build.VERSION.SDK_INT>=34 && checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)==PackageManager.PERMISSION_GRANTED) return "selected";
            return "none";
        }
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED ? "all":"none";
    }
    private boolean hasAccess() { return !permissionScope().equals("none"); }
    private void updateScope() {
        String s=permissionScope();
        summary.setText(s.equals("all") ? "Acceso: fotos visibles en la galería local." : s.equals("selected") ? "Acceso limitado: solo se analizan las fotos que elegiste." : "Sin acceso a fotos. Tocá Elegir acceso a fotos.");
    }
    private void askAccess() {
        if(Build.VERSION.SDK_INT>=34) requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED},PERMISSION);
        else if(Build.VERSION.SDK_INT>=33) requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES},PERMISSION);
        else requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},PERMISSION);
    }
    private void controls() {
        boolean locked=busy||waitingDelete||review!=null;
        scan.setEnabled(!locked); access.setEnabled(!locked); cancel.setEnabled(busy&&!waitingDelete);
        delete.setEnabled(!locked&&!selected.isEmpty()); delete.setText("Revisar selección ("+selected.size()+")");
        previous.setEnabled(!locked&&page>0); next.setEnabled(!locked&&(page+1)*PAGE_SIZE<pairs.size());
        for(int i=0;i<cards.getChildCount();i++) setEnabled(cards.getChildAt(i),!locked);
    }
    private void setEnabled(android.view.View view,boolean enabled) {
        view.setEnabled(enabled);
        if(view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group=(android.view.ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++) setEnabled(group.getChildAt(i),enabled);
        }
    }
    private void cancelOperation(String note) {
        cancelled.set(true); if(querySignal!=null) querySignal.cancel(); generation++;
        busy=false; progress.setText(note); controls();
    }
    private void scan() {
        if(busy||waitingDelete) return;
        cancelled.set(true); cancelled=new AtomicBoolean(); final AtomicBoolean stop=cancelled;
        querySignal=new CancellationSignal(); final CancellationSignal signal=querySignal;
        final int token=++generation; busy=true; page=0; pairs.clear(); selected.clear(); render(); controls();
        progress.setText("Leyendo fotos y agrupando por tamaño…");
        worker.execute(() -> {
            List<Pair> result=new ArrayList<>(); int skipped=0; String error=null;
            try {
                Map<Long,List<Photo>> bySize=new HashMap<>();
                try(Cursor cursor=getContentResolver().query(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),COLUMNS,
                        MediaStore.Images.Media.IS_PENDING+"=0 AND "+MediaStore.Images.Media.IS_TRASHED+"=0",null,null,signal)) {
                    if(cursor==null) throw new IOException("Galería no disponible");
                    while(cursor.moveToNext()) {
                        if(stop.get()) return;
                        Photo p=read(cursor);
                        if(p!=null && p.bytes>0) bySize.computeIfAbsent(p.bytes,k -> new ArrayList<>()).add(p);
                    }
                }
                int processed=0;
                for(List<Photo> candidates:bySize.values()) {
                    if(stop.get()) return;
                    if(candidates.size()<2) continue;
                    Collections.sort(candidates,Comparator.comparingLong((Photo p) -> p.added).thenComparing(p -> p.uri.toString()));
                    Map<String,Photo> originals=new HashMap<>();
                    for(Photo p:candidates) {
                        if(stop.get()) return;
                        try {
                            if(!p.same(fresh(p.uri,signal))) { skipped++; continue; }
                            String hash=hash(p,stop);
                            if(!p.same(fresh(p.uri,signal))) { skipped++; continue; }
                            Photo keeper=originals.get(hash);
                            if(keeper==null) originals.put(hash,p); else result.add(new Pair(keeper,p,hash));
                        } catch(IOException|RuntimeException unavailable) { if(stop.get()) return; skipped++; }
                        processed++;
                        if(processed%20==0) { final int count=processed; runOnUiThread(() -> { if(!destroyed&&token==generation&&!stop.get()) progress.setText("Comparando contenido: "+count+" archivos…"); }); }
                    }
                }
            } catch(IOException|RuntimeException unavailable) { error="No pude completar el análisis. Revisá los permisos y volvé a intentar."; }
            final int missing=skipped; final String failure=error;
            runOnUiThread(() -> {
                if(destroyed||token!=generation||stop.get()) return;
                busy=false; pairs.addAll(result);
                progress.setText(failure!=null ? failure : pairs.size()+" copias idénticas adicionales. "+(missing>0 ? missing+" archivos no pudieron verificarse.":"Nada está seleccionado para borrar."));
                render(); controls();
            });
        });
    }
    private Photo read(Cursor cursor) {
        if(cursor.getInt(7)!=0||cursor.getInt(8)!=0) return null;
        String volume=cursor.getString(6),name=cursor.getString(1);
        if(volume==null) return null;
        Uri uri=ContentUris.withAppendedId(MediaStore.Images.Media.getContentUri(volume),cursor.getLong(0));
        return new Photo(uri,name==null?"Imagen":name,cursor.getLong(2),cursor.getLong(3),cursor.getLong(4),cursor.getLong(5));
    }
    private Photo fresh(Uri uri,CancellationSignal signal) {
        try(Cursor c=getContentResolver().query(uri,COLUMNS,null,null,null,signal)) { return c!=null&&c.moveToFirst() ? read(c):null; }
    }
    private String hash(Photo p,AtomicBoolean stop) throws IOException {
        try(InputStream stream=getContentResolver().openInputStream(p.uri)) { return ContentHash.sha256(stream,stop); }
    }
    private void render() {
        final int render=++renderGeneration; cards.removeAllViews();
        int totalPages=Math.max(1,(pairs.size()+PAGE_SIZE-1)/PAGE_SIZE); page=Math.max(0,Math.min(page,totalPages-1));
        pageLabel.setText("Página "+(page+1)+" / "+totalPages);
        for(int index=page*PAGE_SIZE; index<Math.min(pairs.size(),(page+1)*PAGE_SIZE);index++) {
            Pair pair=pairs.get(index); LinearLayout card=AppUi.card(this);
            CheckBox choice=new CheckBox(this); choice.setTextColor(AppUi.TEXT); choice.setText("Borrar copia: "+pair.copy.name); choice.setChecked(selected.contains(pair.copy.uri));
            choice.setOnCheckedChangeListener((v,checked) -> {
                if(checked && selected.size()>=MAX_DELETE) { choice.setChecked(false); progress.setText("Elegí como máximo 100 copias por confirmación."); return; }
                if(checked) selected.add(pair.copy.uri); else selected.remove(pair.copy.uri); controls();
            }); card.addView(choice);
            String date=DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(pair.keeper.added*1000L));
            card.addView(AppUi.text(this,"Se conserva: "+pair.keeper.name+"\nAgregada: "+date+"\n"+pair.copy.bytes+" bytes · contenido idéntico",12,AppUi.MUTED));
            ImageView thumbnail=new ImageView(this); thumbnail.setContentDescription("Miniatura de "+pair.copy.name); thumbnail.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            card.addView(thumbnail,new LinearLayout.LayoutParams(-1,AppUi.dp(this,120)));
            thumbnails.execute(() -> {
                if(destroyed||render!=renderGeneration) return;
                try {
                    Bitmap bitmap=getContentResolver().loadThumbnail(pair.copy.uri,new Size(160,160),null);
                    runOnUiThread(() -> { if(!destroyed&&render==renderGeneration) thumbnail.setImageBitmap(bitmap); });
                } catch(IOException|RuntimeException unavailable) { }
            });
            cards.addView(card,AppUi.spaced(this));
        }
        if(pairs.isEmpty()) cards.addView(AppUi.text(this,"Tocá Analizar para revisar tus fotos. Solo se muestran duplicados exactos.",14,AppUi.MUTED));
        controls();
    }
    private void review() {
        if(busy||waitingDelete||selected.isEmpty()) return;
        review=new AlertDialog.Builder(this).setTitle("Revisar "+selected.size()+" copias")
            .setMessage("Se conservará la copia indicada en cada grupo. Primero vuelvo a comprobar los archivos y luego Android te pedirá confirmar.\n\nEl borrado puede ser permanente; no se garantiza una papelera.")
            .setNegativeButton("Cancelar",null).setPositiveButton("Verificar y continuar",(d,w) -> verify()).create();
        review.setOnDismissListener(d -> { review=null; controls(); }); review.show(); controls();
    }
    private void verify() {
        cancelled.set(true); cancelled=new AtomicBoolean(); final AtomicBoolean stop=cancelled;
        querySignal=new CancellationSignal(); final CancellationSignal signal=querySignal;
        final int token=++generation; busy=true; controls(); progress.setText("Revalidando las copias y sus originales…");
        List<Pair> choices=new ArrayList<>(); for(Pair p:pairs) if(selected.contains(p.copy.uri)) choices.add(p);
        final Set<Uri> chosen=new HashSet<>(selected);
        worker.execute(() -> {
            ArrayList<Uri> safe=new ArrayList<>();
            for(Pair p:choices) {
                if(stop.get()) return;
                try {
                    if(p.keeper.uri.equals(p.copy.uri)||chosen.contains(p.keeper.uri)) continue;
                    if(!p.keeper.same(fresh(p.keeper.uri,signal))||!p.copy.same(fresh(p.copy.uri,signal))) continue;
                    if(!p.hash.equals(hash(p.keeper,stop))||!p.hash.equals(hash(p.copy,stop))) continue;
                    if(!p.keeper.same(fresh(p.keeper.uri,signal))||!p.copy.same(fresh(p.copy.uri,signal))) continue;
                    safe.add(p.copy.uri);
                } catch(IOException|RuntimeException unavailable) { if(stop.get()) return; }
            }
            runOnUiThread(() -> {
                if(destroyed||!foreground||stop.get()||token!=generation) return;
                busy=false;
                if(safe.size()!=choices.size()) { progress.setText("Alguna foto cambió o no está disponible. No solicité el borrado. Volvé a analizar antes de elegir otra vez."); controls(); return; }
                if(safe.isEmpty()) { controls(); return; }
                try {
                    PendingIntent request=MediaStore.createDeleteRequest(getContentResolver(),safe);
                    waitingDelete=true; controls();
                    startIntentSenderForResult(request.getIntentSender(),DELETE,null,0,0,0);
                } catch(Exception unavailable) { waitingDelete=false; progress.setText("Android no permitió abrir la confirmación. No se solicitó otro borrado."); controls(); }
            });
        });
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] grants) {
        super.onRequestPermissionsResult(code,permissions,grants);
        if(code==PERMISSION) { scope=permissionScope(); updateScope(); if(!hasAccess()) progress.setText("Podés habilitar el permiso desde Ajustes → Aplicaciones → Aurora → Permisos."); }
    }
    @Override protected void onActivityResult(int code,int result,Intent data) {
        super.onActivityResult(code,result,data);
        if(code==DELETE) {
            waitingDelete=false; selected.clear(); pairs.clear(); render();
            progress.setText(result==RESULT_OK ? "Android confirmó la solicitud. Tocá Analizar para actualizar la lista." : "Confirmación cancelada. Tocá Analizar para actualizar la lista."); controls();
        }
    }
    @Override protected void onResume() {
        super.onResume(); foreground=true;
        if(scope!=null&&!scope.equals(permissionScope())) { cancelled.set(true); generation++; busy=false; pairs.clear(); selected.clear(); scope=permissionScope(); updateScope(); render(); }
    }
    @Override protected void onPause() {
        foreground=false;
        if(review!=null) review.dismiss();
        if(busy&&!waitingDelete) cancelOperation("Análisis detenido al salir de esta pantalla. Tocá Analizar para continuar.");
        super.onPause();
    }
    @Override protected void onSaveInstanceState(Bundle state) { state.putBoolean("waitingDelete",waitingDelete); super.onSaveInstanceState(state); }
    @Override protected void onDestroy() {
        destroyed=true; cancelled.set(true); if(querySignal!=null) querySignal.cancel(); generation++; renderGeneration++; worker.shutdownNow(); thumbnails.shutdownNow(); super.onDestroy();
    }
}
