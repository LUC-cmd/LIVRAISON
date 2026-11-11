package tg.smartfood;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SmartFoodApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartFoodApplication.class, args);
    }
}
