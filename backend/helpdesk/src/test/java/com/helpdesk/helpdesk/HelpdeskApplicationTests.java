package com.helpdesk.helpdesk;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "app.whatsapp.base-url=http://localhost:21465")
class HelpdeskApplicationTests {

	@Test
	void contextLoads() {
	}

}
