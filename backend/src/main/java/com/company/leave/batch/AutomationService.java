package com.company.leave.batch;

import com.company.leave.backup.BackupSettings;
import com.company.leave.backup.BackupSettingsService;
import com.company.leave.calendar.holiday.HolidaySyncService;
import com.company.leave.leave.LeavePromotionService;
import com.company.leave.policy.PolicyService;
import com.company.leave.policy.domain.LeavePolicy;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 정책 → 자동화 탭: 자동 작업(스케줄러) 목록과 마지막 실행 결과, 연차 촉진 자동 발송 설정.
 */
@Service
public class AutomationService {

    private final PolicyService policyService;
    private final LeavePromotionService promotionService;
    private final HolidaySyncService holidaySyncService;
    private final JobRunRecorder recorder;
    private final BackupSettingsService backupSettingsService;

    public AutomationService(PolicyService policyService, LeavePromotionService promotionService,
                             HolidaySyncService holidaySyncService, JobRunRecorder recorder,
                             BackupSettingsService backupSettingsService) {
        this.backupSettingsService = backupSettingsService;
        this.policyService = policyService;
        this.promotionService = promotionService;
        this.holidaySyncService = holidaySyncService;
        this.recorder = recorder;
    }

    /** 작업 상태: 항상 실행, 켜짐, 꺼짐, 설정 없음(실행해도 건너뜀). */
    public enum State { ALWAYS, ON, OFF, NOT_CONFIGURED }

    /**
     * 자동 작업 한 개.
     *
     * @param lastSuccess 마지막 실행 성공 true, 실패 false, 건너뜀 null(실행 기록이 없으면 lastStartedAt 도 null)
     */
    public record Job(String key, String name, String description, String schedule, State state,
                      Instant lastStartedAt, Instant lastFinishedAt, Boolean lastSuccess, String lastMessage) {
    }

    public record Overview(boolean promotionEnabled, List<Integer> promotionMonths,
                           List<LeavePromotionService.AutoTarget> promotionPreview, List<Job> jobs) {
    }

    @Transactional(readOnly = true)
    public Overview overview() {
        LeavePolicy policy = policyService.getActivePolicy();
        Map<String, JobRunRecorder.Run> runs = recorder.lastRuns();
        BackupSettings backup = backupSettingsService.current();
        List<Job> jobs = List.of(
                job(runs, JobRunRecorder.HOLIDAY_SYNC, "공휴일 동기화", "매일 00:10",
                        "올해·내년 공휴일을 공공데이터 API에서 받아 저장합니다. 새로 생긴 공휴일이 걸친 휴가는 일수를 다시 "
                                + "계산하고, 근무일이 없어지면 자동 취소합니다. 서버를 시작할 때도 한 번 받습니다.",
                        holidaySyncService.isConfigured() ? State.ALWAYS : State.NOT_CONFIGURED),
                job(runs, JobRunRecorder.LEAVE_GRANT, "연차 부여·소멸", "매일 01:00",
                        "직원마다 지금 연차 기간(입사 기념일부터 1년)의 연차를 부여·재계산합니다. 입사 첫해는 월차가 "
                                + "쌓이고, 기념일이 지나 새 기간이 시작되면 지난 기간 남은 연차를 이월하거나 소멸합니다.",
                        State.ALWAYS),
                job(runs, JobRunRecorder.PROMOTION_AUTO, "연차 촉진 자동 발송", "매일 09:00",
                        "사용 기한이 정한 시기(" + monthsLabel(policy.getPromotionMonths()) + ")에 들어온 직원에게 남은 연차 "
                                + "안내 메일과 앱 알림을 보냅니다.",
                        policy.isPromotionEnabled() ? State.ON : State.OFF),
                job(runs, JobRunRecorder.BACKUP_AUTO, "자동 백업", backup.schedule().label(),
                        "DB 전체를 서버 백업 폴더에 파일로 저장하고, 보관 개수를 넘은 오래된 자동 백업을 지웁니다. 실패하면 "
                                + "시스템 관리자·인사관리자에게 알림과 메일을 보냅니다. 설정은 백업 탭에서 바꿉니다.",
                        backup.isEnabled() ? State.ON : State.OFF));
        return new Overview(policy.isPromotionEnabled(), policy.getPromotionMonths(),
                promotionService.autoPreview(), jobs);
    }

    /** 연차 촉진 자동 발송 ON/OFF·발송 시기 저장. */
    @Transactional
    public Overview updatePromotion(boolean enabled, List<Integer> months) {
        policyService.updateAutomation(enabled, months);
        return overview();
    }

    private static Job job(Map<String, JobRunRecorder.Run> runs, String key, String name, String schedule,
                           String description, State state) {
        JobRunRecorder.Run run = runs.get(key);
        return new Job(key, name, description, schedule, state,
                run != null ? run.startedAt() : null, run != null ? run.finishedAt() : null,
                run != null ? run.success() : null, run != null ? run.message() : null);
    }

    /** [6, 2] → "기한 6개월 전·2개월 전" */
    static String monthsLabel(List<Integer> months) {
        return "기한 " + String.join("·", months.stream().map(m -> m + "개월 전").toList());
    }
}
