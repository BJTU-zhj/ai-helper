package com.zhj.learn.aisuperhost.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * DTO for a unified recall result in dual-path retrieval.
 * This is not a DB entity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecallHitDTO {

    /**
     * Unique chunk identifier used for de-dup and merge.
     */
    private String chunkId;

    /**
     * Source document identifier.
     */
    private String docId;

    /**
     * Chunk content returned by recall.
     */
    private String content;

    /**
     * Additional metadata (title, source, tags, etc).
     */
    private Map<String, Object> metadata;

    /**
     * Score from keyword recall route, nullable.
     */
    private Double keywordScore;

    /**
     * Score from vector recall route, nullable.
     */
    private Double vectorScore;

    /**
     * Final fused score (e.g. RRF), nullable.
     */
    private Double fusedScore;

    /**
     * Route marker: keyword / vector / fused.
     */
    private String route;
}

