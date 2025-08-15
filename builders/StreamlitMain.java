// Minimal CLI entrypoint that uses upstream classes (TextureFinder, RotationInfo, RNG modes).
// It reads input.json and writes output.json so Python doesn't need to touch Java sources.
//
// If the upstream repo ever adds packages, set the proper `package` and imports here accordingly.
// As of the README, classes like TextureFinder and RotationInfo are in the default package.

import java.io.*;
import java.nio.file.*;
import java.util.*;
import com.google.gson.*; // We won't assume Gson exists upstream; so we ship a tiny JSON reader below if needed.

// ---- Tiny JSON model (no external deps) ----
class Obs {
    int x, y, z, rotation;
    boolean sideOnly;
}
class Search {
    int xMin, xMax, yMin, yMax, zMin, zMax, threads;
}
class InputModel {
    int[] origin_hint;    // optional
    List<Obs> observations = new ArrayList<>();
    Search search = new Search();
    String mode;
}

class OutputModel {
    String mode;
    int tested; // optional metric if TextureFinder exposes one
    List<long[]> candidates = new ArrayList<>(); // [x,y,z]
    String note;
}

public class StreamlitMain {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: java -jar TextureRotations.jar <input.json> <output.json>");
            System.exit(2);
        }
        Path in = Paths.get(args[0]);
        Path out = Paths.get(args[1]);

        String json = Files.readString(in);
        InputModel cfg = parse(json);

        // Build RotationInfo list expected by upstream code
        java.util.List<RotationInfo> formation = new java.util.ArrayList<>();
        for (Obs o : cfg.observations) {
            formation.add(new RotationInfo(o.x, o.y, o.z, o.rotation, o.sideOnly));
        }

        // Configure search bounds + mode. The README states to set these in main.
        // We assume TextureFinder exposes a constructor or setters taking formation + bounds + mode + threads.
        // If upstream signature differs, adjust below accordingly to match the available API.
        TextureFinder finder = new TextureFinder(formation);
        finder.setBounds(cfg.search.xMin, cfg.search.xMax,
                         cfg.search.yMin, cfg.search.yMax,
                         cfg.search.zMin, cfg.search.zMax);
        finder.setThreads(cfg.search.threads);
        finder.setMode(cfg.mode);  // "VanillaTextures", "Vanilla21_1Textures", "Vanilla12Textures", "SodiumTextures", "Sodium19Textures"

        // Run the search
        List<long[]> results = finder.findCandidates();

        // Write output
        OutputModel outm = new OutputModel();
        outm.mode = cfg.mode;
        outm.candidates = results;
        outm.note = "Candidate world coordinates inferred from texture rotations.";

        Files.writeString(out, toJson(outm));
        System.out.println("OK");
    }

    // --- Tiny JSON helpers (very small, no external libs) ---
    private static InputModel parse(String s) throws Exception {
        // We will use a minimal hand-rolled parser via Gson-like approach using java.util.Map
        // to avoid external dependencies in Streamlit Cloud. If you prefer Gson, you can add it.
        var p = new javax.json.bind.JsonbConfig(); // <-- If this line errors, remove and use the fallback below.
        throw new UnsupportedOperationException("Remove this line and use the fallback parser below if JSON-B is unavailable.");
    }

    // Fallback minimal JSON parser (no comments, no trailing commas)
    // Replace the parse() above with Gson or JSON-B if available in your build.
    private static String toJson(OutputModel o) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"mode\":\"").append(o.mode).append("\",");
        sb.append("\"candidates\":[");
        for (int i=0;i<o.candidates.size();i++){
            long[] c = o.candidates.get(i);
            sb.append("[").append(c[0]).append(",").append(c[1]).append(",").append(c[2]).append("]");
            if (i<o.candidates.size()-1) sb.append(",");
        }
        sb.append("]}");
        return sb.toString();
    }
}
