package ar.aurora.phone;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Resolves a local contact, then uses WhatsApp's public click-to-chat link. Never sends. */
public final class WhatsAppActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private String name,message;
    private TextView status;
    private Button search;
    private boolean destroyed;
    private boolean inForeground;
    private List<Phone> waitingResults;

    private static final class Phone {
        final String name, raw, international;
        Phone(String n,String r,String i) { name=n; raw=r; international=i; }
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        name=getIntent().getStringExtra("contact"); message=getIntent().getStringExtra("message");
        if (name==null || name.trim().isEmpty() || message==null || message.isEmpty()) { finish(); return; }
        LinearLayout root=AppUi.column(this); setContentView(root); AppUi.insets(this,root);
        root.addView(AppUi.text(this,"Mensaje de WhatsApp",25,AppUi.TEXT));
        root.addView(AppUi.text(this,"Busco el nombre exacto en tus contactos del teléfono. Después revisás el número y el texto. El envío se confirma en WhatsApp.",15,AppUi.MUTED),AppUi.spaced(this));
        root.addView(AppUi.text(this,"Contacto: "+name+"\n\n"+message,18,AppUi.TEXT),AppUi.spaced(this));
        status=AppUi.text(this,"El acceso a contactos solo se usa para esta búsqueda. No se guarda ni se sube tu agenda.",14,AppUi.MUTED);
        root.addView(status,AppUi.spaced(this));
        search=AppUi.button(this,"Buscar contacto",true); root.addView(search,AppUi.spaced(this));
        search.setOnClickListener(v -> {
            if (checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.READ_CONTACTS},31);
            else lookup();
        });
        Button back=AppUi.button(this,"Volver",false); back.setOnClickListener(v -> finish()); root.addView(back,AppUi.spaced(this));
    }
    private static String key(String s) { return s.trim().replaceAll("\\s+"," ").toLowerCase(Locale.ROOT); }
    private void lookup() {
        search.setEnabled(false); status.setText("Buscando coincidencias exactas…");
        worker.execute(() -> {
            List<Phone> phones=new ArrayList<>(); Set<String> seen=new HashSet<>();
            String error=null;
            try {
                Uri filter=Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI,Uri.encode(name));
                try (Cursor contacts=getContentResolver().query(filter,new String[]{ContactsContract.Contacts._ID,ContactsContract.Contacts.DISPLAY_NAME_PRIMARY},null,null,null)) {
                    if (contacts==null) throw new IllegalStateException();
                    while (contacts.moveToNext() && !Thread.currentThread().isInterrupted()) {
                        String display=contacts.getString(1);
                        if (display==null || !key(name).equals(key(display))) continue;
                        String id=contacts.getString(0);
                        try (Cursor numbers=getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER,ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER},
                                ContactsContract.CommonDataKinds.Phone.CONTACT_ID+"=?",new String[]{id},null)) {
                            if (numbers==null) continue;
                            while (numbers.moveToNext()) {
                                String raw=numbers.getString(0), normalized=numbers.getString(1);
                                if (raw==null) continue;
                                String international=normalized!=null&&normalized.startsWith("+") ? normalized : raw.replaceAll("[\\s().-]","");
                                if (international.startsWith("00")) international="+"+international.substring(2);
                                if (seen.add(id+":"+international)) phones.add(new Phone(display,raw,international));
                            }
                        }
                    }
                }
            } catch (RuntimeException unavailable) { error="No pude leer los contactos. Revisá el permiso y probá otra vez."; }
            final String resultError=error;
            runOnUiThread(() -> {
                if (destroyed) return;
                search.setEnabled(true);
                if (resultError!=null) { status.setText(resultError); return; }
                if (phones.isEmpty()) { status.setText("No encontré un número para ese nombre exacto. Revisá cómo está guardado en Contactos y volvé a intentar."); return; }
                if (inForeground) choose(phones); else waitingResults=phones;
            });
        });
    }
    private void choose(List<Phone> phones) {
        status.setText("Elegí y revisá el número. No se envía nada automáticamente.");
        String[] labels=new String[phones.size()];
        for (int i=0;i<labels.length;i++) labels[i]=phones.get(i).name+" · "+phones.get(i).raw;
        new AlertDialog.Builder(this).setTitle("Elegí el destinatario")
            .setItems(labels,(dialog,index) -> confirm(phones.get(index)))
            .setNegativeButton("Cancelar",null).show();
    }
    private void confirm(Phone phone) {
        EditText number=new EditText(this); number.setSingleLine(true); number.setText(phone.international);
        number.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        LinearLayout form=AppUi.column(this); int padding=AppUi.dp(this,20); form.setPadding(padding,padding,padding,0);
        form.addView(AppUi.text(this,phone.name+"\n\n"+message+"\n\nNúmero internacional con + y código de país. Revisá que sea el correcto:",15,AppUi.TEXT)); form.addView(number);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Revisar mensaje").setView(form)
            .setNegativeButton("Cancelar",null).setPositiveButton("Abrir WhatsApp",null).create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
            String e164=number.getText().toString().replaceAll("[\\s().-]","");
            if (!e164.matches("\\+[1-9][0-9]{6,14}")) { number.setError("Usá + y código de país. No voy a adivinarlo."); return; }
            Uri uri=new Uri.Builder().scheme("https").authority("wa.me").appendPath(e164.substring(1)).appendQueryParameter("text",message).build();
            Intent intent=new Intent(Intent.ACTION_VIEW,uri).setPackage("com.whatsapp");
            try { startActivity(intent); dialog.dismiss(); status.setText("Revisá el destinatario y tocá Enviar en WhatsApp."); }
            catch (RuntimeException unavailable) { status.setText("No pude abrir WhatsApp. Instalá o actualizá la app oficial de WhatsApp y volvé a intentar."); dialog.dismiss(); }
        }));
        dialog.show();
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] grants) {
        super.onRequestPermissionsResult(code,permissions,grants);
        if (code==31 && grants.length>0 && grants[0]==PackageManager.PERMISSION_GRANTED) lookup();
        else if (code==31) status.setText("Sin acceso a contactos no puedo buscar el nombre. Podés habilitarlo en Ajustes → Aplicaciones → Aurora → Permisos.");
    }
    @Override protected void onResume() { super.onResume(); inForeground=true; if (waitingResults!=null) { List<Phone> result=waitingResults; waitingResults=null; choose(result); } }
    @Override protected void onPause() { inForeground=false; super.onPause(); }
    @Override protected void onDestroy() { destroyed=true; worker.shutdownNow(); super.onDestroy(); }
}
