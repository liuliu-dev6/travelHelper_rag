package com.example.travelhelper_server.vector;

import java.util.List;

/** Cosine 检索不允许零向量、非有限值或维度不一致的向量进入链路。 */
public final class VectorValidator {
    private VectorValidator() {}

    public static void validate(List<Float> vector, int expectedDimensions) {
        if (vector == null || vector.isEmpty()
                || expectedDimensions > 0 && vector.size() != expectedDimensions) {
            throw new IllegalArgumentException("Embedding维度不匹配或向量为空");
        }
        double norm = 0;
        for (Float value : vector) {
            if (value == null || !Float.isFinite(value)) {
                throw new IllegalArgumentException("Embedding包含非法数值");
            }
            norm += (double) value * value;
        }
        if (norm <= 1e-20) throw new IllegalArgumentException("Embedding为零向量，拒绝写入或检索");
    }
}
