package dev.bryrich.credapp;

import org.springframework.boot.SpringApplication;

public class TestCredappApplication {

	public static void main(String[] args) {
		SpringApplication.from(CredappApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
