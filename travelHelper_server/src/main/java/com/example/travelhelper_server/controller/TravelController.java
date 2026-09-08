package com.example.travelhelper_server.controller;

import com.example.travelhelper_server.dto.ChatRequestDTO;
import com.example.travelhelper_server.dto.TravelRequestDTO;
import com.example.travelhelper_server.service.TravelService;
import com.example.travelhelper_server.service.ConversationService;
import com.example.travelhelper_server.vo.Result;
import com.example.travelhelper_server.vo.TravelRecommendVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.security.Principal;


@RequiredArgsConstructor
@RestController
@RequestMapping("/api/travel")
public class TravelController {
    private final TravelService travelService;
    private final ConversationService conversationService;

    @PostMapping("/recommend")
    public Result<TravelRecommendVO> recommend(@Valid @RequestBody TravelRequestDTO travelRequestDTO){
        TravelRecommendVO travelRecommendVO = travelService.recommend(travelRequestDTO.getCity(),travelRequestDTO.getDays(),travelRequestDTO.getBudget());
        return Result.ok(travelRecommendVO);
    }

    @PostMapping(value = "/chat",produces = "text/event-stream")
    public SseEmitter chat(@Valid @RequestBody ChatRequestDTO chatRequestDTO, Principal principal){
        ConversationService.PreparedTurn turn = conversationService.prepareTurn(
                principal.getName(), chatRequestDTO.getConversationId(), chatRequestDTO.getMessage());
        return travelService.chat(chatRequestDTO.getMessage(), turn.context(), principal.getName(),
                turn.conversationId());
    }
}
