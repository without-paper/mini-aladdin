package com.minialaddin;

import com.minialaddin.config.TestDatabaseConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabaseConfiguration.class)
class MiniAladdinApplicationTests {

	@Test
	void contextLoads() {
	}

}
