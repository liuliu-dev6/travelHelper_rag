package com.example.travelhelper_server.service;

import com.example.travelhelper_server.entity.KnowledgeSource;
import com.example.travelhelper_server.repository.KnowledgeSourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/** 将问题中明确出现的景区/场馆名称解析为订阅 sourceId，供召回前置过滤。 */
@Service
@RequiredArgsConstructor
public class KnowledgeSourceMatcher {
    private final KnowledgeSourceRepository repository;

    public Optional<SourceConstraint> match(String query, String city) {
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isBlank()) return Optional.empty();
        return repository.findAllByOrderByPriorityDescNameAsc().stream()
                .filter(KnowledgeSource::isEnabled)
                .filter(source -> source.getCurrentDocumentId() != null)
                .filter(source -> cityMatches(city, source.getCity()))
                .flatMap(source -> terms(source).filter(term -> normalizedQuery.contains(normalize(term)))
                        .map(term -> new Match(source, term, normalize(term).length())))
                .filter(match -> match.length() >= 3)
                .max(Comparator.comparingInt(Match::length)
                        .thenComparingInt(match -> match.source().getPriority()))
                .map(match -> new SourceConstraint(match.source().getId(), match.source().getName(),
                        match.term()));
    }

    private Stream<String> terms(KnowledgeSource source) {
        Stream<String> entityTerms = Arrays.stream(Objects.toString(source.getEntityName(), "").split("\\s+"));
        return Stream.concat(Stream.of(source.getName()), entityTerms)
                .map(String::strip).filter(term -> !term.isBlank()).distinct();
    }

    private boolean cityMatches(String expected, String actual) {
        if (expected == null || expected.isBlank()) return true;
        String left = normalize(expected), right = normalize(actual);
        return left.equals(right) || left.contains(right) || right.contains(left);
    }

    private String normalize(String value) {
        return Normalizer.normalize(Objects.toString(value, ""), Normalizer.Form.NFKC)
                .replaceAll("[\\s·•・—–_,，。.!！?？()（）]+", "")
                .toLowerCase(Locale.ROOT);
    }

    public record SourceConstraint(String sourceId, String sourceName, String matchedTerm) {}
    private record Match(KnowledgeSource source, String term, int length) {}
}
