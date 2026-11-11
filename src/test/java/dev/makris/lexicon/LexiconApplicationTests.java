package dev.makris.lexicon;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class LexiconApplicationTests {

	@Test
	void contextLoads() {
	}

}
