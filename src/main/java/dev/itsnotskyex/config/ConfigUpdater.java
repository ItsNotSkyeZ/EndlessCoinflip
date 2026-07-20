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
 *
 * List-valued settings (e.g. a "stats:" message list) can't be partially diffed line-by-line —
 * a missing entry inside an existing list looks identical to a customised list. Those are
 * instead refreshed wholesale, gated by the caller via refreshOutdatedLists (see
 * ConfigManager's internal per-file version tracking) so it only happens once per bundled change.
 */
public class ConfigUpdater {

    private static final Pattern KEY_LINE = Pattern.compile("^( *)([A-Za-z0-9_.\\-]+):(?:\\s.*)?$");

    private ConfigUpdater() {}

    public static int update(File file, Plugin plugin, String resourceName, boolean refreshOutdatedLists, Logger logger) {
        try (InputStream defaultStream = plugin.getResource(resourceName)) {
            if (defaultStream == null || !file.exists()) return 0;

            List<String> originalLines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            List<String> defaultLines;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(defaultStream, StandardCharsets.UTF_8))) {
                defaultLines = r.lines().collect(Collectors.toList());
            }

            List<String> workingLines = refreshOutdatedLists
                    ? refreshLists(originalLines, defaultLines, resourceName, logger)
                    : originalLines;

            MergeResult result = merge(workingLines, defaultLines);
            List<String> finalLines = result.lines;

            if (result.addedCount == 0 && finalLines.equals(originalLines)) return 0;

            String candidate = String.join("\n", finalLines) + "\n";
            try {
                new YamlConfiguration().loadFromString(candidate);
            } catch (Exception e) {
                logger.warning("Tried to update " + resourceName + " but the result failed to parse — leaving the file untouched (" + e.getMessage() + ").");
                return 0;
            }

            Files.write(file.toPath(), candidate.getBytes(StandardCharsets.UTF_8));
            if (result.addedCount > 0) {
                logger.info("Added " + result.addedCount + " missing setting(s) to " + resourceName + ".");
            }
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
        int lineIndex;
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
            kl.lineIndex = i;

            int headerStart = i;
            int j = i - 1;
            while (j >= 0 && isCommentOrBlank(lines.get(j))) {
                String prev = lines.get(j);
                // A comment indented deeper than this key belongs to the previous key's own
                // block (e.g. a commented-out example line under it), not this key's header.
                if (!prev.trim().isEmpty() && indentOf(prev) > kl.indent) break;
                headerStart = j;
                j--;
            }
            kl.headerStart = headerStart;

            result.add(kl);
        }
        return result;
    }

    private static class Node {
        String key;
        int headerStart;
        int lineIndex;
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
            node.lineIndex = cur.lineIndex;
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

    private static List<String> trimBlock(List<String> block) {
        List<String> copy = new ArrayList<>(block);
        while (!copy.isEmpty() && copy.get(0).trim().isEmpty()) copy.remove(0);
        while (!copy.isEmpty() && copy.get(copy.size() - 1).trim().isEmpty()) copy.remove(copy.size() - 1);
        return copy;
    }

    // A leaf node (no child keys) whose first non-comment line is a "- " list item, rather
    // than a plain scalar value on the "key:" line itself.
    private static boolean isListNode(Node n, List<String> lines) {
        if (!n.children.isEmpty()) return false;
        for (int j = n.lineIndex + 1; j < n.end && j < lines.size(); j++) {
            String line = lines.get(j);
            if (isCommentOrBlank(line)) continue;
            String trimmed = line.trim();
            return trimmed.equals("-") || trimmed.startsWith("- ");
        }
        return false;
    }

    // Overwrites list-valued settings that still look like a list default with the bundled
    // default. The caller (ConfigManager) decides whether this runs at all, based on its own
    // per-file version tracking, so this only touches lists once per bundled change.
    // Individually customised lists (that no longer look like a list, or whose key is missing
    // from the bundled defaults) are left untouched.
    private static List<String> refreshLists(List<String> existingLines, List<String> defaultLines, String resourceName, Logger logger) {
        List<KeyLine> existingSeq = scanKeyLines(existingLines);
        List<KeyLine> defaultSeq = scanKeyLines(defaultLines);
        List<Node> existingTree = buildTree(existingSeq, 0, existingSeq.size(), existingLines.size());
        List<Node> defaultTree = buildTree(defaultSeq, 0, defaultSeq.size(), defaultLines.size());

        Map<String, Node> existingByPath = new HashMap<>();
        flatten(existingTree, "", existingByPath);
        Map<String, Node> defaultByPath = new HashMap<>();
        flatten(defaultTree, "", defaultByPath);

        List<int[]> ranges = new ArrayList<>();
        List<List<String>> blocks = new ArrayList<>();

        for (Map.Entry<String, Node> e : defaultByPath.entrySet()) {
            Node defaultNode = e.getValue();
            Node existingNode = existingByPath.get(e.getKey());
            if (existingNode == null) continue; // handled by the regular missing-key merge

            if (!isListNode(defaultNode, defaultLines) || !isListNode(existingNode, existingLines)) continue;

            List<String> defaultBlock = trimBlock(defaultLines.subList(defaultNode.headerStart, defaultNode.end));
            List<String> existingBlock = trimBlock(existingLines.subList(existingNode.headerStart, existingNode.end));
            if (defaultBlock.equals(existingBlock)) continue;

            ranges.add(new int[]{existingNode.headerStart, existingNode.end});
            blocks.add(defaultBlock);
        }

        if (ranges.isEmpty()) return existingLines;

        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < ranges.size(); i++) order.add(i);
        order.sort((a, b) -> Integer.compare(ranges.get(b)[0], ranges.get(a)[0]));

        List<String> output = new ArrayList<>(existingLines);
        for (int idx : order) {
            int start = ranges.get(idx)[0];
            int end = ranges.get(idx)[1];
            output.subList(start, end).clear();
            output.addAll(start, blocks.get(idx));
        }

        logger.info("Refreshed " + ranges.size() + " list setting(s) in " + resourceName
                + " to match the updated defaults (any customisations to those specific lists were overwritten).");
        return output;
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
        for (int i = 0; i < defaultNodes.size(); i++) {
            Node dn = defaultNodes.get(i);
            String path = parentPath.isEmpty() ? dn.key : parentPath + "." + dn.key;
            Node existingNode = existingByPath.get(path);
            if (existingNode == null) {
                List<String> block = trimBlock(defaultLines.subList(dn.headerStart, dn.end));
                if (block.isEmpty()) continue;

                boolean topLevel = parentPath.isEmpty();
                int fallback = topLevel ? existingFileEnd : existingByPath.get(parentPath).end;
                int insertAt = findInsertionPoint(defaultNodes, i, parentPath, existingByPath, fallback);

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

    // Missing keys should land next to where they sit in the default template, not always
    // at the end of the file/section — find the next sibling (after this one) that's already
    // on disk and insert right before it, so ordering follows the bundled defaults.
    private static int findInsertionPoint(List<Node> siblings, int fromIndex, String parentPath,
                                           Map<String, Node> existingByPath, int fallback) {
        for (int j = fromIndex + 1; j < siblings.size(); j++) {
            String siblingPath = parentPath.isEmpty() ? siblings.get(j).key : parentPath + "." + siblings.get(j).key;
            Node existingSibling = existingByPath.get(siblingPath);
            if (existingSibling != null) return existingSibling.headerStart;
        }
        return fallback;
    }
}
