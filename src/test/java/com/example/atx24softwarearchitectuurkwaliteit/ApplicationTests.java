package com.example.atx24softwarearchitectuurkwaliteit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;

@SpringBootTest
class ApplicationTests {

    @MockitoBean
    ConnectionFactory connectionFactory;

    @Test
    void contextLoads() {
    }

}
