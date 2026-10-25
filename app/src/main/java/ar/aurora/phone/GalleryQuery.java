package ar.aurora.phone;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Local metadata filters. Semantic content is delegated to the chosen gallery app. */
public final class GalleryQuery {
    public final String original;
    public String terms="", folder="", sort="newest", error="";
    public boolean videos, screenshots;
    public long minBytes, startMillis, endMillis;
    private GalleryQuery(String original) { this.original=original; }
    public static GalleryQuery parse(String text) { return parse(text,LocalDate.now()); }
    public static GalleryQuery parse(String text,LocalDate today) {
        String clean=CommandParser.stripPoliteness(text==null?"":text).replaceFirst("(?iu)^(?:me )?(?:mostr[aá]s|muestras|muestres|mostres|mostrarme|buscarme|encu[eé]ntrame) ","mostrame ").replaceFirst("[!?]+$","");
        GalleryQuery q=new GalleryQuery(clean);
        String n=CommandParser.normalize(q.original);
        q.videos=n.matches(".*\\bvideos\\b.*"); q.screenshots=n.contains("capturas")||n.contains("screenshots");
        if(n.contains("grandes")||n.contains("pesados")) { q.minBytes=q.videos?100L*1024*1024:10L*1024*1024;q.sort="largest"; }
        Matcher size=Pattern.compile("(?:mas de|mayores (?:a|de)|superiores a) (\\d+(?:[.,]\\d+)?) (mb|gb)").matcher(n);
        if(size.find()) {
            try { double value=Double.parseDouble(size.group(1).replace(',','.')); if(value<=0||value>1000000) q.error="El tamaño debe ser positivo y razonable.";else q.minBytes=(long)(value*(size.group(2).equals("gb")?1024L*1024*1024:1024L*1024)); }
            catch(NumberFormatException e) { q.error="No entendí el tamaño."; }
            n=size.replaceFirst("");
        }
        if(n.contains("mas antiguas")||n.contains("mas antiguos")) q.sort="oldest";
        LocalDate start=null,end=null;
        if(n.contains("ayer")) { start=today.minusDays(1);end=today; }
        else if(n.matches(".*\\bhoy\\b.*")) { start=today;end=today.plusDays(1); }
        else if(n.contains("mes pasado")) { start=today.withDayOfMonth(1).minusMonths(1);end=start.plusMonths(1); }
        else if(n.contains("este mes")) { start=today.withDayOfMonth(1);end=start.plusMonths(1); }
        String[] months={"enero","febrero","marzo","abril","mayo","junio","julio","agosto","septiembre","octubre","noviembre","diciembre"};
        Matcher month=Pattern.compile("\\b("+String.join("|",months)+")(?: (?:de |del )?(20\\d{2}))?\\b").matcher(n);
        if(month.find()) {
            int index=0;while(!months[index].equals(month.group(1))) index++;
            int year=month.group(2)==null?today.getYear():Integer.parseInt(month.group(2));
            start=LocalDate.of(year,index+1,1);end=start.plusMonths(1);n=month.replaceFirst("");
        } else if(start==null) {
            Matcher year=Pattern.compile("\\b(20\\d{2})\\b").matcher(n);
            if(year.find()) { start=LocalDate.of(Integer.parseInt(year.group(1)),1,1);end=start.plusYears(1);n=year.replaceFirst(""); }
        }
        if(start!=null) { q.startMillis=start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();q.endMillis=end.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(); }
        if(n.contains("descargas")||n.contains("downloads")) q.folder="download";
        else if(n.contains("camara")||n.contains("camera")) q.folder="dcim/camera";
        n=n.replaceAll("\\b(?:en|de) (?:mi |la |el )?(?:google fotos|google photos|galeria samsung|galeria|telefono|movil)\\b","");
        n=n.replaceFirst("^(?:busca|buscame|buscar|encuentra|encontra|encontrar|mostrame|muestrame|muestra|mostrar|ver|quiero ver|filtra|filtrar)\\s+","");
        n=n.replaceAll("\\b(?:fotos|imagenes|videos|capturas|pantalla|screenshots|grandes|pesados|pesadas|antiguas|antiguos|recientes|reciente|nuevas|nuevos|hoy|ayer|este mes|mes pasado|camara|camera|descargas|downloads|google fotos|google photos)\\b","");
        n=n.replaceAll("\\b(?:de|del|en|el|la|las|los|mis|mi|mas|con|un|una)\\b","").replaceAll("[.,!?\"“”]","").trim().replaceAll("\\s+"," ");
        q.terms=n;return q;
    }
    public String externalTerms() {
        String s=original.replaceFirst("(?iu)^(?:busc[aá](?:me)?|buscar|encuentra|encontr[aá]|encontrar|mostr[aá]me|mu[eé]strame|muestra|mostrar|ver|quiero ver|filtra|filtrar)\\s+","");
        s=s.replaceAll("(?iu)\\s+(?:en|de)\\s+(?:mi\\s+|la\\s+)?(?:google\\s+fotos|google\\s+photos|galer[ií]a\\s+samsung|galer[ií]a|tel[eé]fono)\\s*$","");
        return s.trim();
    }
    public boolean matches(String name,String path,long bytes,long date,boolean isVideo) {
        if(videos!=isVideo||bytes<minBytes) return false;
        if(startMillis>0&&(date<startMillis||date>=endMillis)) return false;
        String combined=CommandParser.normalize((name==null?"":name)+" "+(path==null?"":path));
        if(screenshots&&!combined.contains("screenshot")&&!combined.contains("captura")) return false;
        if(!folder.isEmpty()&&!combined.contains(folder)) return false;
        for(String term:terms.split(" ")) if(!term.isEmpty()&&!combined.contains(term)) return false;
        return true;
    }
    public String description() {
        return (videos?"Videos":"Fotos")+(screenshots?" · capturas":"")+(minBytes>0?" · desde "+(minBytes/(1024*1024))+" MB":"")+(startMillis>0?" · con filtro de fecha":"")+(!terms.isEmpty()?" · nombre/carpeta: "+terms:"")+(!folder.isEmpty()?" · carpeta: "+folder:"");
    }
}
