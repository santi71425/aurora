package ar.aurora.phone;

/** Identifies one user request; canceled or replaced recognizers cannot deliver stale results. */
final class SpeechAttempt {
    private int generation,retries;
    private boolean active;
    int begin() {active=true;retries=0;return ++generation;}
    boolean current(int token) {return active&&token==generation;}
    boolean active() {return active;}
    int retry(int token,int error) {
        if(!current(token)||retries>=1||!VoiceErrors.reconnect(error)) return -1;
        retries++;return ++generation;
    }
    boolean finish(int token) {if(!current(token)) return false;active=false;return true;}
    void cancel() {active=false;generation++;}
}
