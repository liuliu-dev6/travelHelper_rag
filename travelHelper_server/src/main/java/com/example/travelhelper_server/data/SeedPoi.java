package com.example.travelhelper_server.data;

import java.util.List;

/**
 * 种子数据 POI 模型，对应 resources/data/poi_seed.json 的一条记录。
 *
 * @param type        poi（景点）| food（美食）
 * @param aliases     用户常用简称/别名；同名别名必须结合城市与实体类型消歧
 * @param tags        主题标签（亲子/情侣/历史/自然/美食…），会生成 HAS_THEME 关系
 * @param description 用于向量化的介绍文本
 * @param ticket      门票/人均（字符串，兼容 "60元" / "人均150元"）
 * @param nearbyFood  POI关联美食名称列表（历史字段名保留兼容，生成 HAS_FOOD 关系）
 */
public record SeedPoi(
        String id,
        String name,
        String city,
        String type,
        List<String> aliases,
        List<String> tags,
        String description,
        String ticket,
        Double rating,
        List<String> nearbyFood
) {
}
