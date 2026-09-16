package com.ktc.chungnam3.remembrall;

import org.springframework.boot.SpringApplication;

public class TestRemembrallApplication {

	public static void main(String[] args) {
		SpringApplication.from(RemembrallApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
