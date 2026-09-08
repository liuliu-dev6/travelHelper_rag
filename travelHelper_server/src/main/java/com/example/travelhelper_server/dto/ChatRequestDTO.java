package com.example.travelhelper_server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ChatRequestDTO {
    @NotBlank(message ="消息不能为空" )
    @Size(max = 2000, message = "消息不能超过2000字")
    private String message;

    @Size(max = 36, message = "会话ID格式错误")
    private String conversationId;

    @Valid
    @Size(max = 10, message = "最多携带10条上下文消息")
    private List<ConversationTurnDTO> context = new ArrayList<>();
}
