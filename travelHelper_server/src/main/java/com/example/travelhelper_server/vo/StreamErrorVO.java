package com.example.travelhelper_server.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class StreamErrorVO {
    private String type="error";
    private String error;
    public static StreamErrorVO of(String error) {
        return new StreamErrorVO("error", error);
    }
}
