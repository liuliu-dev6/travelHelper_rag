package com.example.travelhelper_server.service;

import com.example.travelhelper_server.entity.KnowledgeParentChunk;
import com.example.travelhelper_server.repository.KnowledgeParentChunkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 命中小块后扩展为父上下文；查询或数据库异常时安全退回原小块。 */
@Service
@RequiredArgsConstructor
public class ParentChunkExpansionService {
    private final KnowledgeParentChunkRepository parentRepository;

    public List<Map<String, Object>> expand(List<Map<String, Object>> rankedChildren, int limit) {
        if (rankedChildren == null || rankedChildren.isEmpty() || limit <= 0) return List.of();
        Set<String> parentIds = new LinkedHashSet<>();
        for (Map<String, Object> hit : rankedChildren) {
            String parentId = Objects.toString(hit.get("parentChunkId"), "");
            if (!parentId.isBlank()) parentIds.add(parentId);
        }
        Map<String, KnowledgeParentChunk> parents = new LinkedHashMap<>();
        try {
            parentRepository.findAllById(parentIds).forEach(parent -> parents.put(parent.getId(), parent));
        } catch (Exception ignored) {
            return rankedChildren.stream().limit(limit).toList();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> emittedContexts = new LinkedHashSet<>();
        for (Map<String, Object> child : rankedChildren) {
            String parentId = Objects.toString(child.get("parentChunkId"), "");
            String deduplicationKey = parentId.isBlank() || !parents.containsKey(parentId)
                    ? Objects.toString(child.getOrDefault("entityId", child.get("id")), "") : parentId;
            if (!emittedContexts.add(deduplicationKey)) continue;
            Map<String, Object> expanded = new LinkedHashMap<>(child);
            KnowledgeParentChunk parent = parents.get(parentId);
            if (parent != null) {
                String childContent = Objects.toString(
                        child.getOrDefault("content", child.get("description")), "");
                expanded.put("matchedChildContent", childContent);
                expanded.put("content", parent.getContent());
                expanded.put("description", parent.getContent());
                expanded.put("sectionPath", parent.getSectionPath());
                expanded.put("_contextExpanded", true);
            }
            result.add(expanded);
            if (result.size() >= limit) break;
        }
        return List.copyOf(result);
    }
}
