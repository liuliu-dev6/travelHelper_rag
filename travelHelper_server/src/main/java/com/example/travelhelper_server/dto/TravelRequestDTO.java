package com.example.travelhelper_server.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TravelRequestDTO {
    @NotNull(message = "城市不能为空")
    private String city;

    @NotNull(message = "天数不能为空")
    @Min(value = 1,message = "天数不能小于1")
    @Max(value = 30,message = "天数不能大于30")
    private Integer days;

    @NotNull(message = "预算不能为空")
    @DecimalMin(value = "100",message = "预算不能小于100")
    private Double budget;
}
