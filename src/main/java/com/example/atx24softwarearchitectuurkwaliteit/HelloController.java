package com.example.atx24softwarearchitectuurkwaliteit;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/")
    public String hello() {
        return "Hello from ATx2.4 Softwarearchitectuur Kwaliteit! Docker is working! 🚀";
    }
}
