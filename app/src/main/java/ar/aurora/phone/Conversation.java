package ar.aurora.phone;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;

/** Transient conversation: no disk, analytics or raw microphone recordings. */
final class Conversation {
    static final class Message {
        final boolean user;final String text;
        Message(boolean user,String text) {this.user=user;this.text=text;}
    }
    interface Listener {void changed();}
    private static final List<Message> messages=new ArrayList<>();
    static Listener listener;
    static List<Message> snapshot() {return new ArrayList<>(messages);}
    static void user(String text) {add(new Message(true,text));}
    static void reply(Context context,String text) {add(new Message(false,text));VoiceOutput.speak(context,text);}
    private static void add(Message m) {
        messages.add(m);while(messages.size()>80) messages.remove(0);if(listener!=null) listener.changed();
    }
    static void clear() {messages.clear();if(listener!=null) listener.changed();}
}
