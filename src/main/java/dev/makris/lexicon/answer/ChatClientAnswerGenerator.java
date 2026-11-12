package dev.makris.lexicon.answer;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class ChatClientAnswerGenerator implements AnswerGenerator {

    private final ChatClient chatClient;

    public ChatClientAnswerGenerator(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public String generate(String systemPrompt, String userPrompt) {
        String content = request(systemPrompt, userPrompt).call().content();
        return content == null ? "" : content;
    }

    @Override
    public Flux<String> stream(String systemPrompt, String userPrompt) {
        return request(systemPrompt, userPrompt).stream().content();
    }

    // The prompts are passed as template *parameters* so that curly braces inside document
    // text are never interpreted as template placeholders.
    private ChatClient.ChatClientRequestSpec request(
        String systemPrompt,
        String userPrompt
    ) {
        return chatClient
            .prompt()
            .system(s -> s.text("{text}").param("text", systemPrompt))
            .user(u -> u.text("{text}").param("text", userPrompt));
    }
}
