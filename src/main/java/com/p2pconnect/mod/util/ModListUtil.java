package com.p2pconnect.mod.util;

import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Extracts the list of installed Forge mods in "modid@version" format and
 * finds the differences (missing / version mismatch) between two lists.
 */
public class ModListUtil {

    public static List<String> getInstalledMods() {
        List<String> result = new ArrayList<>();
        for (var info : ModList.get().getMods()) {
            // Exclude core entries like forge/mcp/minecraft from the comparison
            if (info.getModId().equals("forge") || info.getModId().equals("minecraft")) continue;
            result.add(info.getModId() + "@" + info.getVersion().toString());
        }
        return result;
    }

    public static class DiffResult {
        public final List<String> missingOnMySide = new ArrayList<>();   // they have it, I don't
        public final List<String> extraOnMySide = new ArrayList<>();     // I have it, they don't
        public final List<String> versionMismatch = new ArrayList<>();   // both have it, but versions differ

        public boolean isCompatible() {
            return missingOnMySide.isEmpty() && versionMismatch.isEmpty();
        }
    }

    public static DiffResult compare(List<String> myMods, List<String> theirMods) {
        Map<String, String> mine = toMap(myMods);
        Map<String, String> theirs = toMap(theirMods);

        DiffResult diff = new DiffResult();

        for (var e : theirs.entrySet()) {
            if (!mine.containsKey(e.getKey())) {
                diff.missingOnMySide.add(e.getKey() + "@" + e.getValue());
            } else if (!mine.get(e.getKey()).equals(e.getValue())) {
                diff.versionMismatch.add(e.getKey() + " (mine: " + mine.get(e.getKey()) + ", theirs: " + e.getValue() + ")");
            }
        }
        for (var e : mine.entrySet()) {
            if (!theirs.containsKey(e.getKey())) {
                diff.extraOnMySide.add(e.getKey() + "@" + e.getValue());
            }
        }
        return diff;
    }

    private static Map<String, String> toMap(List<String> mods) {
        Map<String, String> map = new HashMap<>();
        for (String s : mods) {
            int idx = s.lastIndexOf('@');
            if (idx > 0) map.put(s.substring(0, idx), s.substring(idx + 1));
        }
        return map;
    }
}
