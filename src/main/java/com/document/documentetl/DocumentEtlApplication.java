package com.document.documentetl;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(excludeName = "org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration")
@EnableAsync
@EnableScheduling
public class DocumentEtlApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocumentEtlApplication.class, args);
    }

}
