package ar.aurora.phone;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class AppLauncher {
    static final class App {final String name,pkg;App(String n,String p) {name=n;pkg=p;}}
    static List<App> find(Context c,String query) {
        String q=CommandParser.normalize(query).replaceFirst("^(?:la|el|mi) ","");
        if(q.equals("google fotos")||q.equals("google photos")) return known(c,"Google Fotos","com.google.android.apps.photos");
        if(q.equals("galeria")||q.equals("galeria samsung")) return known(c,"Galería Samsung","com.sec.android.gallery3d");
        if(q.equals("instagram")) return known(c,"Instagram","com.instagram.android");
        List<App> all=new ArrayList<>(),exact=new ArrayList<>(),partial=new ArrayList<>();Set<String> seen=new HashSet<>();
        Intent main=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for(ResolveInfo info:c.getPackageManager().queryIntentActivities(main,0)) {
            String pkg=info.activityInfo.packageName;if(!seen.add(pkg)) continue;
            App app=new App(info.loadLabel(c.getPackageManager()).toString(),pkg);all.add(app);
            String name=CommandParser.normalize(app.name);
            if(name.equals(q)||pkg.equals(query)) exact.add(app);
            else if(!q.isEmpty()&&name.contains(q)) partial.add(app);
        }
        List<App> result=q.isEmpty()?all:!exact.isEmpty()?exact:partial;result.sort(Comparator.comparing(a -> a.name.toLowerCase(java.util.Locale.ROOT)));return result;
    }
    private static List<App> known(Context c,String label,String pkg) {
        List<App> result=new ArrayList<>();if(c.getPackageManager().getLaunchIntentForPackage(pkg)!=null) result.add(new App(label,pkg));return result;
    }
    static boolean open(Context c,App app) {
        Intent intent=c.getPackageManager().getLaunchIntentForPackage(app.pkg);if(intent==null) return false;
        try {c.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return true;}catch(RuntimeException e) {return false;}
    }
}
