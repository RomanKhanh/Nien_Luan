package com.brainblocks.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// profile "test" dùng H2 trong bộ nhớ (src/test/resources/application-test.properties)
@SpringBootTest
@ActiveProfiles("test")
class BrainBlocksApplicationTests {

    @Test
    void contextLoads() {
    }

}
