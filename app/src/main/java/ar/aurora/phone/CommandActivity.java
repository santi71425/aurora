package ar.aurora.phone;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small foreground voice/review window, keeping the target app visible underneath. */
public final class CommandActivity extends Activity {
    private ManualVoice voice;
    private boolean first=true;
    private String origin;
    private TextView state;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);getWindow().setGravity(Gravity.BOTTOM);getWindow().setLayout(-1,-2);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams p=getWindow().getAttributes();p.dimAmount=0.18f;getWindow().setAttributes(p);
        origin=getIntent().getStringExtra("origin");if(WakeVoiceService.instance!=null) WakeVoiceService.instance.pauseManual();
        LinearLayout root=AppUi.column(this);int pad=AppUi.dp(this,22);root.setPadding(pad,pad,pad,pad);root.setBackground(AppUi.background(this,AppUi.CARD,24));setContentView(root);
        root.addView(AppUi.text(this,"Jarvis",24,AppUi.TEXT));state=AppUi.text(this,"Decime qué querés hacer",15,AppUi.MUTED);root.addView(state,AppUi.spaced(this));
        voice=new ManualVoice(this,new ManualVoice.Listener() {
            public void info(String text) {state.setText(text);}
            public void result(String text) {Conversation.user(text);state.setText(text);PhoneActions.dispatch(CommandActivity.this,CommandParser.parse(text),origin,true);}
        });
        Button again=AppUi.button(this,"Hablar",true);again.setOnClickListener(v -> voice.begin());root.addView(again,AppUi.spaced(this));
        Button cancel=AppUi.button(this,"Cancelar",false);cancel.setOnClickListener(v -> finish());root.addView(cancel,AppUi.spaced(this));
    }
    @Override protected void onPostResume() {
        super.onPostResume();if(!first) return;first=false;
        String command=getIntent().getStringExtra("command");
        if(command!=null) {state.setText(command);PhoneActions.dispatch(this,CommandParser.parse(command),origin,true);}
        else if(getIntent().getBooleanExtra("listen",false)) voice.begin();
    }
    @Override protected void onActivityResult(int code,int result,Intent data) {super.onActivityResult(code,result,data);voice.activityResult(code,result,data);}
    @Override public void onRequestPermissionsResult(int code,String[] names,int[] grants) {super.onRequestPermissionsResult(code,names,grants);voice.permission(code,grants);}
    @Override protected void onPause() {voice.pause();super.onPause();}
    @Override protected void onDestroy() {voice.destroy();if(WakeVoiceService.instance!=null) WakeVoiceService.instance.resumeManual();super.onDestroy();}
}
