package de.vothography.postpilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Startpunkt der Anwendung. Spring Boot bringt seinen eigenen Webserver mit —
 * es läuft also alles in diesem einen Programm, ohne zusätzliche Installation.
 */
@SpringBootApplication
public class PostPilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(PostPilotApplication.class, args);
    }
}
