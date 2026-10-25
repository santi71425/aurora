package ar.aurora.phone;

import java.util.Locale;
import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CommandParser {
    public enum Type { WHATSAPP, REELS, STOP, DUPLICATES, SCROLL, GALLERY, PICK, UNKNOWN }
    public static final class Command {
        public final Type type;
        public final String contact, message;
        public final int seconds;
        public String direction="up", target="", query="";
        public int count=1;
        Command(Type type, String contact, String message, int seconds) {
            this.type=type; this.contact=contact; this.message=message; this.seconds=seconds;
        }
    }
    private static Pattern p(String s) { return Pattern.compile(s,Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL); }
    private static final String APP = "whats\\s*app";
    private static final String ACTION = "(?:escribile|escr[ií]bele|escrib[ií]|escribe|escribir(?:le)?|escribas|mand[aá](?:le)?|m[aá]nda(?:le)?|mandar(?:le)?|mandes|env[ií]a(?:le)?|envi[aá](?:le)?|enviar(?:le)?|env[ií]es|decile|dile)";
    private static final String TO = "(?:le\\s+)?"+ACTION+"(?:\\s+(?:un\\s+)?(?:mensaje|"+APP+"))?\\s+a\\s+";
    private static final String SEP = "(?:\\s*[:,]\\s*|\\s+que\\s+diga\\s+|\\s+diciendo\\s+)";
    private static final Pattern[] MESSAGES = {
        p("^"+TO+"(.+?)\\s+(?:por|en|v[ií]a)\\s+"+APP+"(?:\\s*[:,]\\s*|\\s+(?:que\\s+diga\\s+|diciendo\\s+)?)(.+)$"),
        p("^"+ACTION+"\\s+(?:un\\s+)?mensaje\\s+(?:por|en)\\s+"+APP+"\\s+a\\s+(.+?)"+SEP+"(.+)$"),
        p("^"+APP+"\\s+(?:a\\s+)?(.+?)"+SEP+"(.+)$"),
        p("^"+TO+"(.+?)"+SEP+"(.+)$")
    };
    private static final Pattern STOP = p("^(?:detener|det[eé]n|deten[eé]|par[aá]|parar|stop|cancelar|cancel[aá])(?:\\s+(?:(?:los|la|el)\\s+)?(?:reels|automatizaci[oó]n|deslizamiento))?$");
    private static final Pattern REELS = p("^(?:(?:pas[aá]|pasar|desliz[aá]|deslizar|inici[aá]|iniciar|activ[aá]|activar)\\s+)?(?:los\\s+)?reels(?:\\s+(?:de|en)\\s+instagram)?(?:\\s+autom[aá]ticamente)?(?:\\s+cada\\s+(\\d+)\\s*(segundos?|segs?\\.?|s|minutos?|min\\.?))?$");
    private static final Pattern PHOTOS = p("^(?:(?:busc[aá]|buscar|analiz[aá]|analiza|analizar|revis[aá]|revisar|borr[aá]|borrar|elimin[aá]|eliminar)\\s+)?(?:(?:las|mis)\\s+)?(?:fotos|im[aá]genes)\\s+(?:duplicadas|repetidas)(?:\\s+(?:en|de)\\s+(?:(?:mi|la)\\s+)?galer[ií]a)?$");
    public static Command parse(String input) {
        if (input==null) return unknown();
        String s=stripPoliteness(input);
        for (Pattern pattern : MESSAGES) {
            Matcher m=pattern.matcher(s);
            if (m.matches()) {
                String name=unquote(m.group(1).trim()), message=unquote(m.group(2).trim());
                return name.isEmpty()||message.isEmpty() ? unknown() : new Command(Type.WHATSAPP,name,message,20);
            }
        }
        s=s.replaceFirst("[.!?]+$","").trim();
        String n=normalize(s).replaceFirst("\\s+por favor$","").replaceAll("\\bmostrarme\\b","mostrame").replaceAll("\\bbuscarme\\b","buscame");
        n=n.replaceFirst("^(?:me )?(?:mostras|muestras|muestres|mostres) ","mostrame ").replaceFirst("^encuentrame ","encuentra ");
        if(n.matches("^(?:no|nunca|no quiero)\\b.*")) return unknown();
        if (STOP.matcher(s).matches()) return new Command(Type.STOP,"","",20);
        if(n.matches("(?:dej[aá] de (?:pasar|deslizar)(?: los reels)?|para todo|deten todo|detener todo|frena(?: los reels)?)")) return new Command(Type.STOP,"","",20);
        if (PHOTOS.matcher(s).matches() || p("^busc[aá] (?:los )?duplicados$").matcher(s).matches()) return new Command(Type.DUPLICATES,"","",20);
        if(n.matches("(?:busca|buscame|encuentra|encontra|mostrame|muestrame|revisa|revisar|analiza|analizar|elimina|eliminar|borra|borrar) (?:las |mis )?(?:fotos|imagenes|copias) (?:duplicadas|repetidas)(?: del telefono| en mi galeria)?")) return new Command(Type.DUPLICATES,"","",20);
        Command extended=extended(n,s);
        if(extended!=null) return extended;
        Matcher m=REELS.matcher(s);
        if (m.matches()) {
            long intervalValue=20;
            if (m.group(1)!=null) {
                try {
                    intervalValue=Long.parseLong(m.group(1));
                    if (intervalValue>300) return unknown();
                    if (m.group(2).toLowerCase(Locale.ROOT).startsWith("min")) intervalValue*=60;
                } catch (NumberFormatException bad) { return unknown(); }
            }
            if (intervalValue>=3 && intervalValue<=300) return new Command(Type.REELS,"","",(int)intervalValue);
        }
        return unknown();
    }
    public static String normalize(String s) { return Normalizer.normalize(s,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).trim().replaceAll("\\s+"," "); }
    public static String stripPoliteness(String input) {
        String s=input.trim().replaceFirst("^[¿¡]+","");
        for(int i=0;i<3;i++) s=s.replaceFirst("(?iu)^(?:por\\s+favor|pod[eé]s|puedes|podr[ií]as|quiero\\s+que|necesito\\s+que|me\\s+gustar[ií]a\\s+que|me\\s+gustar[ií]a|quisiera|necesito|quiero)\\s*,?\\s+","");
        return s;
    }
    private static int number(String s) {
        String[] words={"cero","uno","dos","tres","cuatro","cinco","seis","siete","ocho","nueve","diez","once","doce","trece","catorce","quince","dieciseis","diecisiete","dieciocho","diecinueve","veinte"};
        if(s.equals("un")||s.equals("una")) return 1;
        for(int i=0;i<words.length;i++) if(s.equals(words[i])) return i;
        if(s.equals("treinta")) return 30; if(s.equals("sesenta")) return 60;
        try { return Integer.parseInt(s); } catch(NumberFormatException e) { return -1; }
    }
    private static String target(String n) {
        if(n.contains("google fotos")||n.contains("google photos")) return "google";
        if(n.contains("galeria")||n.contains("samsung")) return "samsung";
        if(n.contains("instagram")) return "instagram";
        return "";
    }
    private static Command extended(String n,String original) {
        if(n.matches("(?:selecciona|seleccionar|elige|elegi|elegir|importa|importar|abre el selector de) (?:las |mis )?(?:fotos|imagenes)(?: de google| de google fotos| de la nube)?")) {
            return new Command(Type.PICK,"","",20);
        }
        if(n.matches("(?:abre|abri|abrir) (?:google fotos|google photos|la galeria|galeria samsung)")) {
            Command c=new Command(Type.GALLERY,"","",20); c.target=target(n); return c;
        }
        Matcher swipe=p("^(?:(?:desliza|deslizar|deslizate|desplaza|desplazate|mueve|move)(?: la pantalla)?|(?:haz|hace) (?:scroll|un deslizamiento)|scroll)(?: hacia| para)?(?: la)? (arriba|abajo|izquierda|derecha)(?: (\\w+) veces)?(?: en (?:la )?(instagram|google fotos|google photos|galeria|galeria samsung))?$").matcher(n);
        if(swipe.matches()) {
            int count=swipe.group(2)==null?1:number(swipe.group(2));
            if(count<1||count>20) return unknown();
            Command c=new Command(Type.SCROLL,"","",20); c.count=count;c.target=target(n);
            c.direction=swipe.group(1).equals("arriba")?"up":swipe.group(1).equals("abajo")?"down":swipe.group(1).equals("izquierda")?"left":"right";
            return c;
        }
        if(n.contains("reels")) {
            String rest=n; int seconds=20;
            Matcher interval=p("\\bcada ([\\w]+) (segundos?|s|minutos?|min\\.?)").matcher(rest);
            if(interval.find()) {
                seconds=number(interval.group(1)); if(seconds<0||seconds>300) return unknown();
                if(interval.group(2).startsWith("min")) seconds*=60;
                if(seconds<3||seconds>300) return unknown();
                rest=interval.replaceFirst("").trim();
            } else if(rest.contains("cada")) return unknown();
            String direction=rest.contains("hacia abajo")?"down":"up";
            rest=rest.replaceAll("\\b(?:hacia|para) (?:arriba|abajo)\\b","").replaceAll("\\b(?:en|de) instagram\\b","").replaceAll("\\b(?:automaticamente|automaticos|automatico|solos)\\b","").trim().replaceAll("\\s+"," ");
            if(rest.matches("(?:(?:pasa|pasar|desliza|deslizar|avanza|avanzar|inicia|iniciar|activa|activar|reproduce|reproducir|pon|pone|poneme) )?(?:los )?reels")) {
                Command c=new Command(Type.REELS,"","",seconds); c.direction=direction; return c;
            }
            return null;
        }
        if(n.matches("(?:(?:busca|buscame|buscar|encuentra|encontra|encontrar|mostrame|muestrame|muestra|mostrar|ver|quiero ver|filtra|filtrar) )?(?:las |mis |los )?(?:fotos|imagenes|videos|capturas)(?: .*)?")) {
            Command c=new Command(Type.GALLERY,"","",20); c.query=original; c.target=target(n); return c;
        }
        return null;
    }
    private static String unquote(String s) {
        if (s.length()>1 && ((s.startsWith("\"")&&s.endsWith("\""))||(s.startsWith("“")&&s.endsWith("”")))) return s.substring(1,s.length()-1).trim();
        return s;
    }
    private static Command unknown() { return new Command(Type.UNKNOWN,"","",20); }
    private CommandParser() { }
}
