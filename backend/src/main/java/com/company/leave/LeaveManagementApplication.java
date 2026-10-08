package com.company.leave;

import com.company.leave.backup.BackupProperties;
import com.company.leave.calendar.holiday.HolidayApiProperties;
import com.company.leave.mail.AccountMailProperties;
import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EnableAsync // 메일 발송 등 커밋 후 비동기 처리(@Async)
@EnableJpaAuditing
@EnableConfigurationProperties({AccountMailProperties.class, HolidayApiProperties.class, BackupProperties.class})
@SpringBootApplication
public class LeaveManagementApplication {

    public static void main(String[] args) {
        // "오늘"(LocalDate.now 등)이 한국 날짜가 되도록 고정한다. Docker 밖(UTC 서버)에서 띄워도 새벽 0~9시에 하루 밀리지 않게
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
        SpringApplication.run(LeaveManagementApplication.class, args);
    }
}
