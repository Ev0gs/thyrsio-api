package com.pierr.thyrsio_api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class ThyrsioApiApplicationTests {

	@Test
	void contextLoads() {
	}

}