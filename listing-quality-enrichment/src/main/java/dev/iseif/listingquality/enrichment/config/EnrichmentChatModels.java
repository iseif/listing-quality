package dev.iseif.listingquality.enrichment.config;

import org.springframework.ai.chat.model.ChatModel;

import java.util.Objects;

record EnrichmentChatModels(ChatModel gemini, ChatModel omlx) {

  EnrichmentChatModels {
    Objects.requireNonNull(gemini);
    Objects.requireNonNull(omlx);
  }

  ChatModel require(String providerId) {
    return switch (providerId) {
      case "gemini" -> gemini;
      case "omlx" -> omlx;
      default -> throw new IllegalArgumentException("Unknown enrichment provider");
    };
  }
}
