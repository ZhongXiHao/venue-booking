package com.fdu.cloudnative.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.OptBoolean;
import com.fdu.cloudnative.api.entity.VenueStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.time.LocalTime;

@Getter
public class CreateVenueRequest {
    //{ "name": "新场地", "location": "某楼 1 楼", "capacity": 50,
    //  "openTime": "09:00:00", "closeTime": "18:00:00", "status": "OPEN" }
    @NotBlank(message = "场地名称不能为空")
    @Size(max = 100, message = "场地名称不能超过100个字符")
    private String name;
    @NotBlank(message = "场地位置不能为空")
    @Size(max = 200, message = "场地位置不能超过200个字符")
    private String location;
    @NotNull(message = "场地容量不能为空")
    @Min(value = 1, message = "场地容量必须大于0")
    private Integer capacity;
    @NotNull(message = "开放时间不能为空")
    @JsonFormat(pattern = "HH:mm:ss", lenient = OptBoolean.FALSE)
    private LocalTime openTime;
    @NotNull(message = "关闭时间不能为空")
    @JsonFormat(pattern = "HH:mm:ss", lenient = OptBoolean.FALSE)
    private LocalTime closeTime;
    // 可省略，默认 OPEN；非法取值在反序列化阶段就会返回 400
    private VenueStatus status;
}
