package dev.monitoring.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MonitoringWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(MonitoringWorkerApplication.class, args);
    }
}
