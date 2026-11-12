package dev.makris.lexicon.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** API keys accepted in the X-Api-Key header. Empty means authentication is disabled. */
@ConfigurationProperties(prefix = "lexicon.security")
public record SecurityProperties(@DefaultValue List<String> apiKeys) {
    public SecurityProperties {
        apiKeys = apiKeys == null ? List.of() : List.copyOf(apiKeys);
    }
}
