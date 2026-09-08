package com.example.travelhelper_server.service;

import com.example.travelhelper_server.dto.ConversationTurnDTO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ConversationContextService {

    public ResolvedConversation resolve(String message, List<ConversationTurnDTO> suppliedContext) {
        List<ConversationTurnDTO> context = suppliedContext == null ? List.of() : suppliedContext.stream()
                .filter(turn -> turn != null && turn.role() != null && turn.content() != null)
                .filter(turn -> ("user".equals(turn.role()) || "assistant".equals(turn.role()))
                        && !turn.content().isBlank())
                .skip(Math.max(0, suppliedContext.size() - 8L))
                .map(turn -> new ConversationTurnDTO(turn.role(), limit(turn.content().strip(), 1200)))
                .toList();

        String current = message == null ? "" : message.strip();
        if (context.size() >= 2) {
            ConversationTurnDTO last = context.get(context.size() - 1);
            if ("assistant".equals(last.role()) && asksForMissingSlot(last.content())) {
                for (int index = context.size() - 2; index >= 0; index--) {
                    ConversationTurnDTO previous = context.get(index);
                    if ("user".equals(previous.role())) {
                        return new ResolvedConversation(
                                previous.content() + "；用户补充：" + current, context, true);
                    }
                }
            }
        }
        if (isReferentialFollowUp(current)) {
            for (int index = context.size() - 1; index >= 0; index--) {
                ConversationTurnDTO previous = context.get(index);
                if ("user".equals(previous.role())) {
                    return new ResolvedConversation(
                            previous.content() + "；用户继续问：" + current, context, true);
                }
            }
        }
        return new ResolvedConversation(current, context, false);
    }

    public String formatForPrompt(List<ConversationTurnDTO> context) {
        if (context == null || context.isEmpty()) return "无";
        StringBuilder result = new StringBuilder();
        for (ConversationTurnDTO turn : context) {
            result.append("user".equals(turn.role()) ? "用户：" : "助手：")
                    .append(turn.content()).append('\n');
        }
        return result.toString();
    }

    private boolean asksForMissingSlot(String content) {
        return content.contains("哪个城市") || content.contains("目的地")
                || content.contains("城市") && (content.contains("告诉") || content.contains("提供")
                || content.contains("补充") || content.contains("需要查询"))
                || content.contains("哪一天") || content.contains("几天")
                || content.contains("预算") && content.contains("告诉");
    }

    private boolean isReferentialFollowUp(String content) {
        return content.matches(".*(附近|周边|那里|那边|这个地方|它|继续|接着|刚才|上述|前面).*?");
    }

    private String limit(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public record ResolvedConversation(
            String effectiveQuery,
            List<ConversationTurnDTO> context,
            boolean inheritedPendingRequest
    ) {
    }
}
