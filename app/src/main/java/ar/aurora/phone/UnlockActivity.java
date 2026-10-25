package ar.aurora.phone;

import android.app.Activity;
import android.app.KeyguardManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;

/** Delegates authentication to Android; never handles, stores or enters a PIN. */
public final class UnlockActivity extends Activity {
    private boolean first=true;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);setShowWhenLocked(true);setTurnScreenOn(true);
        LinearLayout root=AppUi.column(this);setContentView(root);AppUi.insets(this,root);
        root.addView(AppUi.text(this,"Desbloqueo de Android",26,AppUi.TEXT));
        root.addView(AppUi.text(this,"Confirmá con tu PIN, patrón, huella o el método que Android ofrezca. Jarvis no puede saltarse la protección ni introduce tu clave.",16,AppUi.MUTED),AppUi.spaced(this));
        Button unlock=AppUi.button(this,"Continuar con Android",true);unlock.setOnClickListener(v -> request());root.addView(unlock,AppUi.spaced(this));
        Button back=AppUi.button(this,"Volver",false);back.setOnClickListener(v -> finish());root.addView(back,AppUi.spaced(this));
    }
    @Override protected void onPostResume() {super.onPostResume();if(first) {first=false;request();}}
    private void request() {
        KeyguardManager manager=(KeyguardManager)getSystemService(KEYGUARD_SERVICE);
        if(manager==null||!manager.isKeyguardLocked()) {Conversation.reply(this,"El teléfono ya está desbloqueado.");return;}
        Conversation.reply(this,"Confirmá tu identidad en la pantalla de Android.");
        manager.requestDismissKeyguard(this,new KeyguardManager.KeyguardDismissCallback() {
            @Override public void onDismissSucceeded() {Conversation.reply(UnlockActivity.this,"Android confirmó el desbloqueo.");finish();}
            @Override public void onDismissCancelled() {Conversation.reply(UnlockActivity.this,"Desbloqueo cancelado. El teléfono sigue protegido.");}
            @Override public void onDismissError() {Conversation.reply(UnlockActivity.this,"Android no permitió mostrar el desbloqueo desde aquí. Usá el botón de encendido y tu autenticación habitual.");}
        });
    }
}
