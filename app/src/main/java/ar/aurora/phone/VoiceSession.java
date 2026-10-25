package ar.aurora.phone;

/** A rolling 30-second command window, driven by monotonic time rather than wall clock. */
public final class VoiceSession {
    public static final long WINDOW_MS=30000L;
    private long until;
    public boolean active(long now) {return now<until;}
    public int remainingSeconds(long now) {return active(now)?(int)((until-now+999)/1000):0;}
    public String accept(String text,long now) {
        WakePhrase wake=WakePhrase.parse(text);
        if(wake.detected) {until=now+WINDOW_MS;return wake.command;}
        if(!active(now)||text==null||text.trim().isEmpty()) return null;
        if(CommandParser.parse(text).type==CommandParser.Type.UNKNOWN) return null;
        until=now+WINDOW_MS;
        return text.trim();
    }
    public void close() {until=0;}
}
