package ar.aurora.phone;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Explicit phone controls; never interprets arbitrary text as a system action. */
final class PhoneCommands {
    private static CommandParser.Command command(CommandParser.Type type,String action) {
        CommandParser.Command c=new CommandParser.Command(type,"","",0);c.action=action;return c;
    }
    static CommandParser.Command parse(String normalized,String original) {
        String n=normalized;
        if(n.matches("(?:hola|buenas|buen dia|buenos dias|buenas tardes|buenas noches|que puedes hacer|que podes hacer|ayuda|como estas)(?: jarvis)?")) return command(CommandParser.Type.HELP,n.equals("ayuda")||n.contains("hacer")?"help":"hello");
        if(n.matches("(?:baja|baja la pantalla|baja un poco|baja una pantalla|bajar|scroll abajo|desplazate hacia abajo|sube|subi|sube la pantalla|sube un poco|sube una pantalla|subir|scroll arriba)")) {
            CommandParser.Command c=command(CommandParser.Type.SCROLL,"");c.contentScroll=true;c.direction=(n.startsWith("sub")||n.equals("scroll arriba"))?"down":"up";return c;
        }
        if(n.matches("(?:atras|volver|volve|vuelve|regresa|ir atras|retrocede)")) return command(CommandParser.Type.SYSTEM,"back");
        if(n.matches("(?:inicio|home|ir al inicio|ve al inicio|pantalla de inicio|anda al inicio)")) return command(CommandParser.Type.SYSTEM,"home");
        if(n.matches("(?:apps recientes|aplicaciones recientes|muestra (?:las )?aplicaciones recientes|abre (?:las )?apps recientes)")) return command(CommandParser.Type.SYSTEM,"recents");
        if(n.matches("(?:abre|abri|mostrar|muestra|mostrame|ver) (?:las )?notificaciones")) return command(CommandParser.Type.SYSTEM,"notifications");
        if(n.matches("(?:abre|abri|muestra|mostrame) (?:los )?(?:ajustes rapidos|controles rapidos)")) return command(CommandParser.Type.SYSTEM,"quick_settings");
        if(n.matches("(?:sube|subi|aumenta) (?:el )?volumen")) return command(CommandParser.Type.SYSTEM,"volume_up");
        if(n.matches("(?:baja|reduce|disminuye) (?:el )?volumen")) return command(CommandParser.Type.SYSTEM,"volume_down");
        if(n.matches("(?:silencia|silencio|silencia el audio|silencia la musica)")) return command(CommandParser.Type.SYSTEM,"mute");
        if(n.matches("(?:captura de pantalla|toma una captura|hace una captura|haz una captura|saca una captura|captura la pantalla)")) return command(CommandParser.Type.SYSTEM,"screenshot");
        if(n.matches("(?:bloquea|bloquear|bloquea el telefono|bloquea mi telefono|apaga la pantalla)")) return command(CommandParser.Type.SYSTEM,"lock");
        if(n.matches("(?:desbloquea|desbloquear|desbloquea el telefono|desbloquea mi telefono|muestra el desbloqueo)")) return command(CommandParser.Type.UNLOCK,"");
        if(n.matches("(?:apaga el microfono|deja de escuchar|desactiva la escucha|apaga la escucha|desactiva ok jarvis)")) return command(CommandParser.Type.SYSTEM,"stop_listening");
        Matcher tap=Pattern.compile("(?iu)^(?:toca|toc[aá]|pulsa|presiona|haz clic en|hac[eé] clic en)\\s+(?:el bot[oó]n\\s+)?(.+)$").matcher(original);
        if(tap.matches()) {CommandParser.Command c=command(CommandParser.Type.TAP,"");c.query=tap.group(1).trim();return c;}
        Matcher text=Pattern.compile("(?iu)^(?:escribe|escrib[ií]|escribir)(?: aqu[ií]| el texto)?\\s*:\\s*(.+)$",Pattern.DOTALL).matcher(original);
        if(text.matches()) {CommandParser.Command c=command(CommandParser.Type.TYPE_TEXT,"");c.query=text.group(1);return c;}
        Matcher open=Pattern.compile("(?iu)^(?:abre|abr[ií]|abrir|abrime|inicia|ejecuta)\\s+(?:(?:la\\s+)?(?:app|aplicaci[oó]n)\\s+)?(.+)$").matcher(original);
        if(open.matches()) {
            CommandParser.Command c=command(CommandParser.Type.OPEN_APP,"");c.query=open.group(1).trim();
            if(c.query.equalsIgnoreCase("WhatsApp")||!c.query.isEmpty()) return c;
        }
        return null;
    }
}
