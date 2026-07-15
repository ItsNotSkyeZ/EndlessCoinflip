package dev.itsnotskyex.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.io.File;

/**
 * Diffs a YAML file on disk against the plugin's bundled default resource and appends
 * any keys missing from the disk copy (new settings introduced by an update, or ones the
 * server owner deleted), without touching existing values, comments, or ordering.
 */
public class ConfigUpdater {

    private static final Pattern KEY_LINE = Pattern.compile("^( *)([A-Za-z0-9_.\\-]+):(?:\\s.*)?$");

    private ConfigUpdater() {}

    public static int update(File file, Plugin plugin, String resourceName, Logger logger) {
        try (InputStream defaultStream = plugin.getResource(resourceName)) {
            if (defaultStream == null || !file.exists()) return 0;

            List<String> existingLines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            List<String> defaultLines;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(defaultStream, StandardCharsets.UTF_8))) {
                defaultLines = r.lines().collect(Collectors.toList());
            }

            MergeResult result = merge(existingLines, defaultLines);
            if (result.addedCount == 0) return 0;

            String candidate = String.join("\n", result.lines) + "\n";
            try {
                new YamlConfiguration().loadFromString(candidate);
            } catch (Exception e) {
                logger.warning("Tried to add " + result.addedCount + " missing setting(s) to " + resourceName
                        + " but the result failed to parse — leaving the file untouched (" + e.getMessage() + ").");
                return 0;
            }

            Files.write(file.toPath(), candidate.getBytes(StandardCharsets.UTF_8));
            logger.info("Added " + result.addedCount + " missing setting(s) to " + resourceName + ".");
            return result.addedCount;
        } catch (IOException e) {
            logger.warning("Could not check " + resourceName + " for missing settings: " + e.getMessage());
            return 0;
        }
    }

    private static boolean isCommentOrBlank(String line) {
        String t = line.trim();
        return t.isEmpty() || t.startsWith("#");
    }

    private static int indentOf(String line) {
        int i = 0;
        while (i < line.length() && line.charAt(i) == ' ') i++;
        return i;
    }

    private static class KeyLine {
        int indent;
        String key;
        int headerStart;
    }

    private static List<KeyLine> scanKeyLines(List<String> lines) {
        List<KeyLine> result = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (isCommentOrBlank(line)) continue;
            Matcher m = KEY_LINE.matcher(line);
            if (!m.matches()) continue;

            KeyLine kl = new KeyLine();
            kl.indent = indentOf(line);
            kl.key = m.group(2);

            int headerStart = i;
            int j = i - 1;
            while (j >= 0 && isCommentOrBlank(lines.get(j))) { headerStart = j; j--; }
            kl.headerStart = headerStart;

            result.add(kl);
        }
        return result;
    }

    private static class Node {
        String key;
        int headerStart;
        int end; // exclusive
        List<Node> children = new ArrayList<>();
    }

    private static List<Node> buildTree(List<KeyLine> seq, int start, int end, int totalLines) {
        List<Node> result = new ArrayList<>();
        int i = start;
        while (i < end) {
            KeyLine cur = seq.get(i);
            int childStart = i + 1;
            int childEnd = childStart;
            while (childEnd < end && seq.get(childEnd).indent > cur.indent) childEnd++;

            Node node = new Node();
            node.key = cur.key;
            node.headerStart = cur.headerStart;
            node.end = (childEnd < seq.size()) ? seq.get(childEnd).headerStart : totalLines;
            node.children = buildTree(seq, childStart, childEnd, totalLines);
            result.add(node);
            i = childEnd;
        }
        return result;
    }

    private static void flatten(List<Node> nodes, String prefix, Map<String, Node> out) {
        for (Node n : nodes) {
            String path = prefix.isEmpty() ? n.key : prefix + "." + n.key;
            out.put(path, n);
            flatten(n.children, path, out);
        }
    }

    private static int countLeaves(Node n) {
        if (n.children.isEmpty()) return 1;
        int sum = 0;
        for (Node c : n.children) sum += countLeaves(c);
        return sum;
    }

    private static class MergeResult {
        List<String> lines;
        int addedCount;
    }

    private static MergeResult merge(List<String> existingLines, List<String> defaultLines) {
        List<KeyLine> existingSeq = scanKeyLines(existingLines);
        List<KeyLine> defaultSeq = scanKeyLines(defaultLines);
        List<Node> existingTree = buildTree(existingSeq, 0, existingSeq.size(), existingLines.size());
        List<Node> defaultTree = buildTree(defaultSeq, 0, defaultSeq.size(), defaultLines.size());

        Map<String, Node> existingByPath = new HashMap<>();
        flatten(existingTree, "", existingByPath);

        Map<Integer, List<String>> insertionsByIndex = new TreeMap<>();
        int[] added = {0};
        mergeMissing(defaultTree, "", existingByPath, defaultLines, existingLines.size(), insertionsByIndex, added);

        List<String> output = new ArrayList<>(existingLines);
        List<Integer> indices = new ArrayList<>(insertionsByIndex.keySet());
        indices.sort(Collections.reverseOrder());
        for (int idx : indices) {
            output.addAll(idx, insertionsByIndex.get(idx));
        }

        MergeResult r = new MergeResult();
        r.lines = output;
        r.addedCount = added[0];
        return r;
    }

    private static void mergeMissing(List<Node> defaultNodes, String parentPath, Map<String, Node> existingByPath,
                                      List<String> defaultLines, int existingFileEnd,
                                      Map<Integer, List<String>> insertionsByIndex, int[] added) {
        for (Node dn : defaultNodes) {
            String path = parentPath.isEmpty() ? dn.key : parentPath + "." + dn.key;
            Node existingNode = existingByPath.get(path);
            if (existingNode == null) {
                List<String> block = new ArrayList<>(defaultLines.subList(dn.headerStart, dn.end));
                while (!block.isEmpty() && block.get(0).trim().isEmpty()) block.remove(0);
                while (!block.isEmpty() && block.get(block.size() - 1).trim().isEmpty()) block.remove(block.size() - 1);
                if (block.isEmpty()) continue;

                boolean topLevel = parentPath.isEmpty();
                int insertAt = topLevel ? existingFileEnd : existingByPath.get(parentPath).end;

                List<String> toInsert = new ArrayList<>();
                if (topLevel) toInsert.add("");
                toInsert.addAll(block);

                insertionsByIndex.computeIfAbsent(insertAt, k -> new ArrayList<>()).addAll(toInsert);
                added[0] += countLeaves(dn);
            } else {
                mergeMissing(dn.children, path, existingByPath, defaultLines, existingFileEnd, insertionsByIndex, added);
            }
        }
    }
}
