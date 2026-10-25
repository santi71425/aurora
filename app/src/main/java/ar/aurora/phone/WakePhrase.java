package ar.aurora.phone;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A wake phrase must lead the recognized segment; quoted/embedded mentions never execute. */
public final class WakePhrase {
    public final boolean detected;
    public final String command;
    private WakePhrase(boolean detected,String command) {this.detected=detected;this.command=command;}
    private static final Pattern WAKE=Pattern.compile("^\\s*(?:ok|okay|okey|oye|hey)[\\s,]+jarvis\\b[\\s,.:;!?¡¿-]*(.*)$",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE|Pattern.DOTALL);
    public static WakePhrase parse(String text) {
        if(text==null) return new WakePhrase(false,"");
        Matcher match=WAKE.matcher(text);
        return match.matches()?new WakePhrase(true,match.group(1).trim()):new WakePhrase(false,"");
    }
}
