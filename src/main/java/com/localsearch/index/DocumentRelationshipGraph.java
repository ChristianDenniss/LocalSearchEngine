package com.localsearch.index;

import com.localsearch.model.DocumentRecord;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds an undirected document graph from cheap structural signals (offline).
 * Currently: documents in the same parent directory are linked (capped for large folders).
 */
public final class DocumentRelationshipGraph
{
    /** Fully connect siblings when a folder has at most this many indexed files. */
    private static final int FULLY_CONNECT_MAX = 24;
    /** In larger folders, each file links to this many next neighbors in sorted path order (ring). */
    private static final int LARGE_FOLDER_RING_LINKS = 8;

    private DocumentRelationshipGraph()
    {
    }

    public static void attachToIndex(InvertedIndex index, List<DocumentRecord> documents)
    {
        Map<String, List<Integer>> docIdsByParent = new HashMap<>();
        for (DocumentRecord document : documents)
        {
            String parentKey = parentKey(document.getPath());
            docIdsByParent.computeIfAbsent(parentKey, ignored -> new ArrayList<>()).add(document.getId());
        }

        Map<Integer, Set<Integer>> edges = new HashMap<>();
        for (List<Integer> group : docIdsByParent.values())
        {
            if (group.size() < 2)
            {
                continue;
            }
            List<DocumentRecord> sortedDocs = new ArrayList<>();
            for (int docId : group)
            {
                DocumentRecord doc = index.getDocument(docId);
                if (doc != null)
                {
                    sortedDocs.add(doc);
                }
            }
            sortedDocs.sort(Comparator.comparing(DocumentRecord::getPath, String.CASE_INSENSITIVE_ORDER));
            List<Integer> sortedIds = sortedDocs.stream().map(DocumentRecord::getId).toList();

            if (sortedIds.size() < 2)
            {
                continue;
            }
            if (sortedIds.size() <= FULLY_CONNECT_MAX)
            {
                for (int i = 0; i < sortedIds.size(); i++)
                {
                    for (int j = i + 1; j < sortedIds.size(); j++)
                    {
                        addUndirectedEdge(edges, sortedIds.get(i), sortedIds.get(j));
                    }
                }
            }
            else
            {
                int n = sortedIds.size();
                for (int i = 0; i < n; i++)
                {
                    for (int k = 1; k <= Math.min(LARGE_FOLDER_RING_LINKS, n - 1); k++)
                    {
                        int a = sortedIds.get(i);
                        int b = sortedIds.get((i + k) % n);
                        addUndirectedEdge(edges, a, b);
                    }
                }
            }
        }

        for (Map.Entry<Integer, Set<Integer>> entry : edges.entrySet())
        {
            List<Integer> neighbors = new ArrayList<>(entry.getValue());
            Collections.sort(neighbors);
            index.setNeighborDocIds(entry.getKey(), neighbors);
        }
    }

    private static String parentKey(String pathString)
    {
        try
        {
            Path path = Path.of(pathString).normalize();
            Path parent = path.getParent();
            return parent == null ? "" : parent.toString();
        }
        catch (Exception ignored)
        {
            return "";
        }
    }

    private static void addUndirectedEdge(Map<Integer, Set<Integer>> edges, int a, int b)
    {
        if (a == b)
        {
            return;
        }
        edges.computeIfAbsent(a, ignored -> new HashSet<>()).add(b);
        edges.computeIfAbsent(b, ignored -> new HashSet<>()).add(a);
    }
}
