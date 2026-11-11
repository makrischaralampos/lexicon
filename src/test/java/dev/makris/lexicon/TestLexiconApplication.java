package dev.makris.lexicon;

import org.springframework.boot.SpringApplication;

public class TestLexiconApplication {

	public static void main(String[] args) {
		SpringApplication.from(LexiconApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
