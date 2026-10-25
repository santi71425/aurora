package ar.aurora.phone;

import java.io.ByteArrayInputStream;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.time.LocalDate;

public final class CoreTests {
    private static int checks;
    private static void equal(Object a,Object b) { checks++; if(!a.equals(b)) throw new AssertionError("Expected "+a+"; got "+b); }
    private static void type(String s,CommandParser.Type t) { equal(t,CommandParser.parse(s).type); }
    private static void message(String s,String name,String content) {
        CommandParser.Command c=CommandParser.parse(s); equal(CommandParser.Type.WHATSAPP,c.type); equal(name,c.contact); equal(content,c.message);
    }
    private static String hash(String s) throws Exception { return ContentHash.sha256(new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8)),new AtomicBoolean()); }
    public static void main(String[] args) throws Exception {
        message("Escribile a Mom por WhatsApp: Te amo","Mom","Te amo");
        message("manda un mensaje a Mom que diga Te amo","Mom","Te amo");
        message("WhatsApp Mom: Te amo","Mom","Te amo");
        message("WhatsApp a Mamá: ¡Te amo! ❤️","Mamá","¡Te amo! ❤️");
        message("Envía un mensaje por WhatsApp a María José: Llegué bien","María José","Llegué bien");
        message("por favor, escribile a Mom por whatsapp Te amo","Mom","Te amo");
        message("Mandale a Mom: detener","Mom","detener");
        message("WhatsApp Mom: buscá fotos duplicadas","Mom","buscá fotos duplicadas");
        message("Escríbele a \"Mamá\" por WhatsApp: \"Te amo\"","Mamá","Te amo");
        message("Enviá un WhatsApp a Mom que diga No borres nada, por favor","Mom","No borres nada, por favor");
        String[] reels={"Pasá los reels cada 20 segundos","reels cada 10 segundos","Deslizá los reels automáticamente","los reels de Instagram cada 3 s","reels cada 300 segundos","reels cada 2 minutos","reels cada 5 min.","REELS."};
        int[] seconds={20,10,20,3,300,120,300,20};
        for(int i=0;i<reels.length;i++) { type(reels[i],CommandParser.Type.REELS); equal(seconds[i],CommandParser.parse(reels[i]).seconds); }
        for(String s:new String[]{"detener","Pará los reels","detén la automatización.","cancelar"}) type(s,CommandParser.Type.STOP);
        for(String s:new String[]{"buscá fotos duplicadas","Analiza mis imágenes repetidas en mi galería","Buscá duplicados","borrar fotos duplicadas"}) type(s,CommandParser.Type.DUPLICATES);
        for(String s:new String[]{""," ","reels cada 2 segundos","reels cada 301 segundos","reels cada 0 minutos","reels cada 6 minutos","reels cada -20 segundos","reels cada 999999999999999999999999 segundos","Escribile a Mom por WhatsApp:","WhatsApp Mom:","WhatsApp : Te amo","WhatsApp Mom: \"\"","Escribile a Mom por WhatsApp","abre WhatsApp","No detener los reels","Quiero que no borres mis fotos","¿Quién es Mom?"}) type(s,CommandParser.Type.UNKNOWN);
        type(null,CommandParser.Type.UNKNOWN);
        equal("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",hash(""));
        equal("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",hash("abc"));
        equal(hash("foto idéntica"),hash("foto idéntica"));
        equal(false,hash("abc").equals(hash("abd")));
        boolean aborted=false;
        try { ContentHash.sha256(new ByteArrayInputStream(new byte[]{1,2,3}),new AtomicBoolean(true)); } catch(InterruptedIOException expected) { aborted=true; }
        equal(true,aborted);
        message("¿Podrías mandarle un mensaje a Mom por WhatsApp: Te amo?","Mom","Te amo?");
        message("Necesito que le escribas a Mom: hola","Mom","hola");
        for(String s:new String[]{"desliza hacia arriba","Deslizá hacia abajo en Instagram","desplaza la pantalla hacia la derecha","Hacé scroll hacia la izquierda","puedes deslizar hacia abajo tres veces en Google Fotos","scroll arriba"}) type(s,CommandParser.Type.SCROLL);
        equal("down",CommandParser.parse("Desliza hacia abajo").direction);
        equal("left",CommandParser.parse("Hacé scroll hacia la izquierda").direction);
        equal(3,CommandParser.parse("desliza arriba tres veces").count);
        equal("google",CommandParser.parse("desliza abajo en Google Fotos").target);
        for(String s:new String[]{"desliza arriba 21 veces","desliza abajo cero veces","no deslices arriba","reels cada mil segundos","reels cada 6 minutos"}) type(s,CommandParser.Type.UNKNOWN);
        equal(10,CommandParser.parse("reels cada diez segundos").seconds);
        equal(20,CommandParser.parse("¿Podés pasar los reels cada veinte segundos?").seconds);
        equal("down",CommandParser.parse("pasá los reels hacia abajo cada 10 segundos").direction);
        for(String s:new String[]{"capturas de pantalla","Mostrame videos grandes","¿Podés mostrarme capturas de pantalla?","Encuentra fotos de perros en Google Fotos","busca fotos con mujeres en Google Fotos","Buscame fotos de Madrid en la galería Samsung","fotos de enero de 2026","Quiero que me muestres fotos de perros","Me gustaría ver videos grandes","Encuéntrame fotos de Madrid"}) type(s,CommandParser.Type.GALLERY);
        type("reels cada 1073741825 minutos",CommandParser.Type.UNKNOWN);
        equal("google",CommandParser.parse("busca fotos de perros en Google Fotos").target);
        type("elegir fotos de la nube",CommandParser.Type.PICK);
        type("abrí Google Fotos",CommandParser.Type.GALLERY);
        LocalDate today=LocalDate.of(2026,10,25);
        GalleryQuery shots=GalleryQuery.parse("Mostrame capturas de pantalla",today);
        equal(true,shots.screenshots);equal("",shots.terms);
        equal(true,shots.matches("Screenshot_1.png","Pictures/Screenshots",1000,0,false));
        equal(false,shots.matches("Foto_1.png","DCIM/Camera",1000,0,false));
        GalleryQuery videos=GalleryQuery.parse("videos grandes",today);
        equal(true,videos.videos);equal(100L*1024*1024,videos.minBytes);
        equal(false,videos.matches("video.mp4","Movies",99L*1024*1024,0,true));
        equal(true,videos.matches("video.mp4","Movies",101L*1024*1024,0,true));
        GalleryQuery size=GalleryQuery.parse("videos de más de 200 MB",today);
        equal(200L*1024*1024,size.minBytes);equal("",size.terms);
        GalleryQuery month=GalleryQuery.parse("fotos de enero de 2026",today);
        equal("",month.terms);equal(true,month.endMillis>month.startMillis);
        long jan=LocalDate.of(2026,1,15).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        long feb=LocalDate.of(2026,2,1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        equal(true,month.matches("test.jpg","Pictures",1,jan,false));equal(false,month.matches("test.jpg","Pictures",1,feb,false));
        equal("fotos de perros",GalleryQuery.parse("Buscá fotos de perros en Google Fotos",today).externalTerms());
        equal("perros",GalleryQuery.parse("Buscá fotos de perros en Google Fotos",today).terms);
        equal("fotos con mujeres",GalleryQuery.parse("busca fotos con mujeres en Google Fotos",today).externalTerms());
        System.out.println("CoreTests: "+checks+" comprobaciones correctas");
    }
}
