// builders/StreamlitMain.java
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

// Minimal POJOs matching upstream expectations
class Obs { int x,y,z,rotation; boolean sideOnly; }

public class StreamlitMain {

    // --- tiny helpers to pull numbers/strings from a known-shaped JSON ---
    private static int grabInt(String s, String key, int def) {
        Matcher m = Pattern.compile("\""+Pattern.quote(key)+"\"\\s*:\\s*(-?\\d+)").matcher(s);
        return m.find() ? Integer.parseInt(m.group(1)) : def;
    }
    private static String grabStr(String s, String key, String def) {
        Matcher m = Pattern.compile("\""+Pattern.quote(key)+"\"\\s*:\\s*\"([^\"]+)\"").matcher(s);
        return m.find() ? m.group(1) : def;
    }
    private static List<Obs> grabObservations(String s) {
        List<Obs> out = new ArrayList<>();
        // naive: find objects inside "observations":[ {...}, {...} ]
        Matcher arr = Pattern.compile("\"observations\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL).matcher(s);
        if (!arr.find()) return out;
        String body = arr.group(1);
        Matcher obj = Pattern.compile("\\{(.*?)\\}", Pattern.DOTALL).matcher(body);
        while (obj.find()) {
            String o = obj.group(1);
            Obs ob = new Obs();
            ob.x = grabInt(o, "x", 0);
            ob.y = grabInt(o, "y", 0);
            ob.z = grabInt(o, "z", 0);
            ob.rotation = grabInt(o, "rotation", 0);
            Matcher b = Pattern.compile("\"sideOnly\"\\s*:\\s*(true|false)").matcher(o);
            ob.sideOnly = b.find() && b.group(1).equals("true");
            out.add(ob);
        }
        return out;
    }

    // turn candidates into a tiny JSON without deps
    private static String toJson(List<long[]> cands, String mode) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"mode\":\"").append(mode).append("\",\"candidates\":[");
        for (int i=0;i<cands.size();i++){
            long[] c = cands.get(i);
            sb.append("[").append(c[0]).append(",").append(c[1]).append(",").append(c[2]).append("]");
            if (i<cands.size()-1) sb.append(",");
        }
        sb.append("]}");
        return sb.toString();
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: java -jar TextureRotations.jar <input.json> <output.json>");
            System.exit(2);
        }
        String json = Files.readString(Paths.get(args[0]));

        // 1) observations
        java.util.List<RotationInfo> formation = new java.util.ArrayList<>();
        for (Obs o : grabObservations(json)) {
            formation.add(new RotationInfo(o.x, o.y, o.z, o.rotation, o.sideOnly));
        }

        // 2) bounds, threads, mode
        int xMin = grabInt(json, "xMin", -4096);
        int xMax = grabInt(json, "xMax",  4096);
        int yMin = grabInt(json, "yMin", 0);
        int yMax = grabInt(json, "yMax", 255);
        int zMin = grabInt(json, "zMin", -4096);
        int zMax = grabInt(json, "zMax",  4096);
        int threads = grabInt(json, "threads", 8);
        String mode = grabStr(json, "mode", "Vanilla21_1Textures");

        // 3) drive upstream finder (adjust methods if upstream differs)
        TextureFinder finder = new TextureFinder(formation);
        finder.setBounds(xMin, xMax, yMin, yMax, zMin, zMax);
        finder.setThreads(threads);
        finder.setMode(mode);

        List<long[]> results = finder.findCandidates();

        Files.writeString(Paths.get(args[1]), toJson(results, mode));
        System.out.println("OK");
    }
}
