package dev.makris.lexicon.answer;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class ChatClientAnswerGenerator implements AnswerGenerator {

    private final ChatClient chatClient;

    public ChatClientAnswerGenerator(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public String generate(String systemPrompt, String userPrompt) {
        // The prompts are passed as template *parameters* so that curly braces inside document
        // text are never interpreted as template placeholders.
        String content = chatClient
            .prompt()
            .system(s -> s.text("{text}").param("text", systemPrompt))
            .user(u -> u.text("{text}").param("text", userPrompt))
            .call()
            .content();
        return content == null ? "" : content;
    }
}
