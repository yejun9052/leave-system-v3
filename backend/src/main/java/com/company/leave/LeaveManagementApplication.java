package com.company.leave;

import com.company.leave.calendar.holiday.HolidayApiProperties;
import com.company.leave.mail.AccountMailProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EnableAsync // 메일 발송 등 커밋 후 비동기 처리(@Async)
@EnableJpaAuditing
@EnableConfigurationProperties({AccountMailProperties.class, HolidayApiProperties.class})
@SpringBootApplication
public class LeaveManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(LeaveManagementApplication.class, args);
    }
}
