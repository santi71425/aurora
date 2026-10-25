package ar.aurora.phone;

/** Android SpeechRecognizer error meanings; provider failures are not command-parser errors. */
final class VoiceErrors {
    static boolean reconnect(int code) {return code==3||code==4||code==5||code==8||code==10||code==11;}
    static boolean noSpeech(int code) {return code==6||code==7;}
    static String describe(int code) {
        switch(code) {
            case 1:return "El servicio de voz agotó el tiempo de conexión (1).";
            case 2:return "El servicio de voz informó un problema de red (2).";
            case 3:return "No pude acceder al audio. Otra app puede estar usando el micrófono (3).";
            case 4:return "Falló el servicio de reconocimiento de voz (4).";
            case 5:return "El reconocedor canceló la sesión de dictado (5).";
            case 6:return "No escuché palabras. Tocá Mic y probá de nuevo.";
            case 7:return "No pude distinguir lo que dijiste. Tocá Mic y probá de nuevo.";
            case 8:return "El reconocedor está ocupado (8).";
            case 9:return "Falta el permiso de micrófono (9).";
            case 10:return "El servicio pidió esperar antes de otro intento (10).";
            case 11:return "El servicio de reconocimiento local se desconectó (11).";
            case 12:return "El reconocedor local no admite el idioma solicitado (12).";
            case 13:return "El idioma solicitado todavía no está disponible sin conexión (13).";
            default:return "El servicio de dictado informó el error "+code+".";
        }
    }
}
