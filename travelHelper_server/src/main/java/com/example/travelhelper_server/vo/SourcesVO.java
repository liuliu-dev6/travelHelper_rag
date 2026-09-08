package com.example.travelhelper_server.vo;

import java.util.List;

public record SourcesVO(String type, List<SourceItemVO> items) {
    public static SourcesVO of(List<SourceItemVO> items) {
        return new SourcesVO("sources", items);
    }
}
