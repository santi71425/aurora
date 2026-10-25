package ar.aurora.phone;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.util.Size;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Search local metadata, delegate semantic search, or review explicitly picked cloud media. */
public final class GalleryActivity extends Activity {
    private static final int ACCESS=52,PICK=53,PAGE=20;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(),thumbs=Executors.newFixedThreadPool(2);
    private final List<Item> results=new ArrayList<>();
    private EditText query;
    private TextView summary,pageLabel;
    private LinearLayout cards;
    private Button local,previous,next;
    private volatile boolean destroyed;
    private volatile int generation,renderGeneration;
    private int page;
    private boolean busy,first=true;
    private String initialTarget;
    private static final class Item {
        final Uri uri;final String name;final long bytes,date;
        Item(Uri uri,String name,long bytes,long date) {this.uri=uri;this.name=name;this.bytes=bytes;this.date=date;}
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root=AppUi.column(this);setContentView(root);AppUi.insets(this,root);
        root.addView(AppUi.text(this,"Buscar fotos y videos",24,AppUi.TEXT));
        query=new EditText(this);query.setTextColor(AppUi.TEXT);query.setTextSize(15);query.setHintTextColor(AppUi.MUTED);
        query.setHint("Capturas, videos grandes, fotos de perros…");query.setMaxLines(2);
        query.setText(getIntent().getStringExtra("query"));root.addView(query);
        root.addView(AppUi.text(this,"Aquí: nombre, carpeta, fecha y tamaño. Para contenido (perros, lugares, personas), elegí el buscador de Google Fotos o Samsung.",12,AppUi.MUTED));
        LinearLayout options=new LinearLayout(this);
        local=AppUi.button(this,"En teléfono",true);local.setOnClickListener(v -> localSearch());options.addView(local,new LinearLayout.LayoutParams(0,-2,1));
        Button google=AppUi.button(this,"Google Fotos",false);google.setOnClickListener(v -> external("google"));options.addView(google,new LinearLayout.LayoutParams(0,-2,1));root.addView(options);
        LinearLayout more=new LinearLayout(this);
        Button samsung=AppUi.button(this,"Galería Samsung",false);samsung.setOnClickListener(v -> external("samsung"));more.addView(samsung,new LinearLayout.LayoutParams(0,-2,1));
        Button pick=AppUi.button(this,"Elegir de la nube",false);pick.setOnClickListener(v -> picker());more.addView(pick,new LinearLayout.LayoutParams(0,-2,1));root.addView(more);
        summary=AppUi.text(this,"Capturas de pantalla · videos grandes (desde 100 MB) · fotos de enero de 2026 · fotos de hoy · videos de más de 200 MB.",12,AppUi.MUTED);root.addView(summary);
        ScrollView scroll=new ScrollView(this);cards=AppUi.column(this);scroll.addView(cards);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout nav=new LinearLayout(this);
        previous=AppUi.button(this,"‹",false);previous.setOnClickListener(v -> {page--;render();});nav.addView(previous);
        pageLabel=AppUi.text(this,"",12,AppUi.MUTED);nav.addView(pageLabel,new LinearLayout.LayoutParams(0,-2,1));
        next=AppUi.button(this,"›",false);next.setOnClickListener(v -> {page++;render();});nav.addView(next);root.addView(nav);
        Button back=AppUi.button(this,"Volver",false);back.setOnClickListener(v -> finish());root.addView(back);
        initialTarget=getIntent().getStringExtra("target");
        if(state!=null) {query.setText(state.getString("query",""));first=false;}
        render();
    }
    @Override protected void onPostResume() {
        super.onPostResume();
        if(first) {
            first=false;
            if("google".equals(initialTarget)||"samsung".equals(initialTarget)) external(initialTarget);
            else if("picker".equals(initialTarget)) picker();
            else if(GalleryQuery.parse(query.getText().toString()).terms.isEmpty()) localSearch();
        }
    }
    private boolean access(boolean videos) {
        if(Build.VERSION.SDK_INT>=33) {
            if(checkSelfPermission(videos?Manifest.permission.READ_MEDIA_VIDEO:Manifest.permission.READ_MEDIA_IMAGES)==PackageManager.PERMISSION_GRANTED) return true;
            return Build.VERSION.SDK_INT>=34&&checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)==PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED;
    }
    private void localSearch() {
        GalleryQuery q=GalleryQuery.parse(query.getText().toString());
        if(!q.error.isEmpty()) {summary.setText(q.error);return;}
        if(!access(q.videos)) {
            if(Build.VERSION.SDK_INT>=34) requestPermissions(new String[]{q.videos?Manifest.permission.READ_MEDIA_VIDEO:Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED},ACCESS);
            else requestPermissions(new String[]{Build.VERSION.SDK_INT>=33?(q.videos?Manifest.permission.READ_MEDIA_VIDEO:Manifest.permission.READ_MEDIA_IMAGES):Manifest.permission.READ_EXTERNAL_STORAGE},ACCESS);
            return;
        }
        final int token=++generation;busy=true;local.setEnabled(false);summary.setText(q.description()+" · buscando…");
        worker.execute(() -> {
            List<Item> found=new ArrayList<>();String error=null;boolean truncated=false;
            String[] columns={"_id","_display_name","_size","datetaken","date_added","relative_path","volume_name"};
            Uri collection=q.videos?MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL):MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
            String order=q.sort.equals("largest")?"_size DESC":q.sort.equals("oldest")?"date_added ASC":"date_added DESC";
            try(Cursor c=getContentResolver().query(collection,columns,"is_pending=0 AND is_trashed=0",null,order)) {
                if(c==null) throw new IllegalStateException();
                while(c.moveToNext()) {
                    if(destroyed||token!=generation) return;
                    String name=c.getString(1),path=c.getString(5),volume=c.getString(6);long date=c.getLong(3)>0?c.getLong(3):c.getLong(4)*1000L;
                    if(!q.matches(name,path,c.getLong(2),date,q.videos)||volume==null) continue;
                    if(found.size()>=2000) {truncated=true;break;}
                    Uri uri=ContentUris.withAppendedId(q.videos?MediaStore.Video.Media.getContentUri(volume):MediaStore.Images.Media.getContentUri(volume),c.getLong(0));
                    found.add(new Item(uri,name==null?"Archivo":name,c.getLong(2),date));
                }
            } catch(RuntimeException unavailable) {error="No pude consultar la galería. Revisá los permisos y volvé a intentar.";}
            final String failure=error;final boolean capped=truncated;
            runOnUiThread(() -> {
                if(destroyed||token!=generation) return;
                busy=false;local.setEnabled(true);results.clear();results.addAll(found);page=0;render();
                summary.setText(failure!=null?failure:q.description()+" · "+found.size()+" resultados"+(capped?" (límite de visualización)":"")+". Solo incluye archivos locales permitidos; las palabras se buscan en nombre/carpeta, no en el contenido visual.");
            });
        });
    }
    private void external(String target) {
        String terms=GalleryQuery.parse(query.getText().toString()).externalTerms();
        String pkg=target.equals("google")?"com.google.android.apps.photos":"com.sec.android.gallery3d";
        if(terms.isEmpty()) {launch(pkg);return;}
        new AlertDialog.Builder(this).setTitle("Buscar en "+(target.equals("google")?"Google Fotos":"Galería Samsung"))
            .setMessage("Consulta: "+terms+"\n\nSe buscará en la app con tu cuenta actual. Sus resultados dependen de su indexación. Jarvis no importa ni tiene acceso directo a toda tu biblioteca en la nube. Con Accesibilidad intentará escribir y confirmar la búsqueda; si no reconoce el buscador, te avisará.")
            .setNegativeButton("Cancelar",null).setNeutralButton("Copiar y abrir",(d,w) -> {
                ((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Búsqueda de fotos",terms));
                launch(pkg);summary.setText("Pegá la consulta en Buscar dentro de la app.");
            }).setPositiveButton("Buscar con Jarvis",(d,w) -> {
                if(PhoneControlService.instance!=null) PhoneControlService.instance.search(target,terms);
                else new AlertDialog.Builder(this).setTitle("Activar control de apps")
                    .setMessage("Habilitá Jarvis en Ajustes → Accesibilidad → Aplicaciones instaladas y después volvé a tocar Google Fotos o Galería Samsung. También podés usar Copiar y abrir.")
                    .setPositiveButton("Abrir Ajustes",(a,b) -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))).setNegativeButton("Cancelar",null).show();
            }).show();
    }
    private void launch(String pkg) {
        Intent intent=getPackageManager().getLaunchIntentForPackage(pkg);
        if(intent==null) {summary.setText("No encontré esa app instalada.");return;}
        try {startActivity(intent);} catch(RuntimeException e) {summary.setText("No pude abrir la aplicación.");}
    }
    private void picker() {
        new AlertDialog.Builder(this).setTitle("Fotos de la nube")
            .setMessage("Android abrirá su selector. Si Google Fotos está disponible como proveedor, elegilo en el menú del selector. Jarvis solo podrá leer las imágenes que selecciones, no toda tu biblioteca. La disponibilidad depende de Android y Google Fotos; algunas fotos pueden necesitar descargarse.")
            .setNegativeButton("Cancelar",null).setPositiveButton("Elegir fotos",(d,w) -> {
                try {
                    Intent intent;
                    if(Build.VERSION.SDK_INT>=33) intent=new Intent(MediaStore.ACTION_PICK_IMAGES).setType("image/*").putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX,Math.min(50,MediaStore.getPickImagesMaxLimit()));
                    else intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
                    startActivityForResult(intent,PICK);
                } catch(RuntimeException e) {summary.setText("El selector no está disponible. Podés buscar directamente en Google Fotos.");}
            }).show();
    }
    private void render() {
        final int render=++renderGeneration;cards.removeAllViews();int pages=Math.max(1,(results.size()+PAGE-1)/PAGE);page=Math.max(0,Math.min(page,pages-1));
        pageLabel.setText("Página "+(page+1)+" / "+pages);previous.setEnabled(page>0);next.setEnabled((page+1)*PAGE<results.size());
        for(int i=page*PAGE;i<Math.min(results.size(),(page+1)*PAGE);i++) {
            Item item=results.get(i);LinearLayout card=AppUi.card(this);card.addView(AppUi.text(this,item.name,15,AppUi.TEXT));
            card.addView(AppUi.text(this,(item.bytes>=0?String.format(java.util.Locale.getDefault(),"%.1f MB",item.bytes/(1024.0*1024)):"Tamaño no disponible")+(item.date>0?" · "+DateFormat.getDateInstance().format(new Date(item.date)):""),12,AppUi.MUTED));
            ImageView image=new ImageView(this);image.setContentDescription(item.name);image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);card.addView(image,new LinearLayout.LayoutParams(-1,AppUi.dp(this,130)));
            card.setOnClickListener(v -> {
                try {startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(item.uri,getContentResolver().getType(item.uri)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}
                catch(RuntimeException unavailable) {summary.setText("No hay una app disponible para abrir ese archivo.");}
            });
            thumbs.execute(() -> {
                if(destroyed||render!=renderGeneration) return;
                try {Bitmap b=getContentResolver().loadThumbnail(item.uri,new Size(200,200),null);runOnUiThread(() -> {if(!destroyed&&render==renderGeneration) image.setImageBitmap(b);});}
                catch(Exception unavailable) { }
            });cards.addView(card,AppUi.spaced(this));
        }
    }
    @Override protected void onActivityResult(int code,int result,Intent data) {
        super.onActivityResult(code,result,data);
        if(code!=PICK||result!=RESULT_OK||data==null) return;
        List<Uri> picked=new ArrayList<>();
        if(data.getClipData()!=null) for(int i=0;i<Math.min(50,data.getClipData().getItemCount());i++) picked.add(data.getClipData().getItemAt(i).getUri());
        else if(data.getData()!=null) picked.add(data.getData());
        final int token=++generation;busy=true;local.setEnabled(false);summary.setText("Leyendo las imágenes elegidas…");
        worker.execute(() -> {
            List<Item> files=new ArrayList<>();
            for(Uri uri:picked) {
                if(destroyed||token!=generation) return;
                try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE},null,null,null)) {
                    files.add(c!=null&&c.moveToFirst()?new Item(uri,c.getString(0)==null?"Foto elegida":c.getString(0),c.isNull(1)?-1:c.getLong(1),0):new Item(uri,"Foto elegida",-1,0));
                } catch(RuntimeException inaccessible) { }
            }
            runOnUiThread(() -> {if(!destroyed&&token==generation) {busy=false;local.setEnabled(true);results.clear();results.addAll(files);page=0;render();summary.setText(files.size()+" imágenes elegidas. Esto no es una búsqueda completa de la nube. No se eliminó ninguna foto.");}});
        });
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] grants) {
        super.onRequestPermissionsResult(code,permissions,grants);
        if(code==ACCESS) {if(access(GalleryQuery.parse(query.getText().toString()).videos)) localSearch();else summary.setText("Sin permiso no puedo listar esos archivos. Podés usar Google Fotos o el selector.");}
    }
    @Override protected void onSaveInstanceState(Bundle state) {state.putString("query",query.getText().toString());super.onSaveInstanceState(state);}
    @Override protected void onPause() {if(busy) {generation++;busy=false;local.setEnabled(true);summary.setText("Consulta cancelada al salir. Podés volver a buscar.");}super.onPause();}
    @Override protected void onDestroy() {destroyed=true;generation++;renderGeneration++;worker.shutdownNow();thumbs.shutdownNow();super.onDestroy();}
}
