package com.company.leave.report;

import com.company.leave.calendar.domain.Holiday;
import com.company.leave.calendar.repository.HolidayRepository;
import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import com.company.leave.department.domain.Department;
import com.company.leave.employee.domain.Employee;
import com.company.leave.leave.LeaveBalanceService;
import com.company.leave.leave.accrual.WorkdayCalculator;
import com.company.leave.leave.domain.AnnualDeductionMode;
import com.company.leave.leave.domain.DayPortion;
import com.company.leave.leave.domain.LeaveBalance;
import com.company.leave.leave.domain.LeaveRequest;
import com.company.leave.leave.repository.LeaveRequestRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 연차 사용 현황 리포트 (엑셀). 연구소 연차현황표 양식: 직원마다 한 줄, 부서별로 팀 칸을 묶고 사용한 날을 한 칸씩 적는다.
 * <ul>
 *   <li>사용일: 연차처럼 차감하는 종류(연차·반차·시간차 등)의 승인된 휴가만. 경조사·병가·공가, 결재 대기·취소 요청 중은 넣지 않는다</li>
 *   <li>종일은 날짜, 반차는 "날짜(*)", 시간차는 "날짜(2h)". 여러 날 휴가는 주말·공휴일을 뺀 날마다 한 칸</li>
 *   <li>사용 = 사용일 칸 합계, 연차 = 부여, 추가일은 아직 정하지 않아 빈칸, 남은 연차 = 시스템 잔여(이월·소멸 반영)</li>
 * </ul>
 */
@Service
public class LeaveReportService {

    /** 사용일 기본 칸 수. 더 많이 쓴 직원이 있으면 그만큼 늘린다. */
    static final int MIN_DAY_COLUMNS = 50;
    static final String[] HEADERS =
            {"번호", "이름", "팀", "사용", "입사일", "연차", "추가일", "전체 연차", "남은 연차", "사용 기간", "사용일"};
    static final String LEGEND = "반차 : (*) · 시간차 : (시간, 예: 2h)";
    static final String NO_DEPARTMENT = "부서 없음";

    /** 받은 양식처럼 A열과 1행은 비워 두고 B2 부터 쓴다. */
    private static final int FIRST_COL = 1;
    private static final int TEAM_COL = FIRST_COL + 2;
    private static final int FIRST_DAY_COL = FIRST_COL + HEADERS.length - 1;
    /** 1: 제목, 2: 범례, 3: 사용일 번호, 4: 머리줄, 5~: 직원 (0행은 비움) */
    private static final int TITLE_ROW = 1;
    private static final int NUMBER_ROW = 3;
    private static final int HEADER_ROW = 4;
    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final String PATH_SEPARATOR = " › ";

    private final LeaveBalanceService balanceService;
    private final LeaveRequestRepository requestRepository;
    private final HolidayRepository holidayRepository;
    private final WorkdayCalculator workdayCalculator;

    public LeaveReportService(LeaveBalanceService balanceService, LeaveRequestRepository requestRepository,
                              HolidayRepository holidayRepository, WorkdayCalculator workdayCalculator) {
        this.balanceService = balanceService;
        this.requestRepository = requestRepository;
        this.holidayRepository = holidayRepository;
        this.workdayCalculator = workdayCalculator;
    }

    /** 사용일 한 칸: 날짜, 표시 기호(종일은 null), 차감 일수. */
    record UsedDay(LocalDate date, String mark, BigDecimal days) {
    }

    /**
     * year 의 연차 현황. 입사일 기준이면 직원마다 연차 기간이 달라, 기준일(올해는 오늘, 지난해는 12월 31일)에
     * 각 직원이 쓰고 있던 기간과 그 기간 안에 쓴 날을 보여 준다. 관리 전용 계정은 빼고 퇴사자는 넣는다.
     */
    @Transactional(readOnly = true)
    public byte[] exportUsage(int year) {
        LocalDate today = LocalDate.now();
        LocalDate asOf = year == today.getYear() ? today : LocalDate.of(year, 12, 31);
        List<LeaveBalanceService.PeriodBalance> rows = new ArrayList<>(balanceService.balancesAsOf(asOf, false, false));
        rows.sort(Comparator.comparing((LeaveBalanceService.PeriodBalance pb) -> pb.employee().getDepartment(),
                        LeaveReportService::compareDepartments)
                .thenComparing(pb -> pb.employee().getName()));
        Map<Long, List<UsedDay>> usedDays = usedDays(rows);

        int dayColumns = Math.max(MIN_DAY_COLUMNS,
                usedDays.values().stream().mapToInt(List::size).max().orElse(0));

        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles styles = new Styles(wb);
            Sheet sheet = wb.createSheet(String.valueOf(year));
            Cell title = sheet.createRow(TITLE_ROW).createCell(FIRST_COL);
            title.setCellValue(year + " 연차현황 (" + asOf + " 기준)");
            title.setCellStyle(styles.title);
            sheet.createRow(TITLE_ROW + 1).createCell(FIRST_COL).setCellValue(LEGEND);

            Row numbers = sheet.createRow(NUMBER_ROW);
            for (int i = 0; i < dayColumns; i++) {
                styled(numbers.createCell(FIRST_DAY_COL + i), styles.header).setCellValue(i + 1);
            }
            Row header = sheet.createRow(HEADER_ROW);
            for (int i = 0; i < HEADERS.length - 1 + dayColumns; i++) {
                styled(header.createCell(FIRST_COL + i), styles.header)
                        .setCellValue(i < HEADERS.length ? HEADERS[i] : "");
            }
            sheet.addMergedRegion(new CellRangeAddress(HEADER_ROW, HEADER_ROW,
                    FIRST_DAY_COL, FIRST_DAY_COL + dayColumns - 1));

            int r = HEADER_ROW + 1;
            int groupStart = r;
            String groupTeam = null;
            for (int n = 0; n < rows.size(); n++) {
                LeaveBalanceService.PeriodBalance pb = rows.get(n);
                Employee e = pb.employee();
                LeaveBalance b = pb.balance();
                List<UsedDay> days = usedDays.getOrDefault(e.getId(), List.of());
                String team = departmentPath(e.getDepartment());
                if (!team.equals(groupTeam)) {
                    mergeTeam(sheet, groupStart, r - 1);
                    groupStart = r;
                    groupTeam = team;
                }

                Row row = sheet.createRow(r++);
                styled(row.createCell(FIRST_COL), styles.center).setCellValue(n + 1);
                styled(row.createCell(FIRST_COL + 1), styles.center).setCellValue(e.getName());
                styled(row.createCell(TEAM_COL), styles.team).setCellValue(team);
                styled(row.createCell(FIRST_COL + 3), styles.number).setCellValue(
                        days.stream().map(UsedDay::days).reduce(BigDecimal.ZERO, BigDecimal::add).doubleValue());
                Cell hire = styled(row.createCell(FIRST_COL + 4), styles.date);
                if (e.getHireDate() != null) {
                    hire.setCellValue(e.getHireDate());
                }
                styled(row.createCell(FIRST_COL + 5), styles.number).setCellValue(b.getGranted().doubleValue());
                styled(row.createCell(FIRST_COL + 6), styles.number); // 추가일: 아직 정하지 않아 빈칸
                styled(row.createCell(FIRST_COL + 7), styles.number).setCellValue(b.getGranted().doubleValue());
                styled(row.createCell(FIRST_COL + 8), styles.number).setCellValue(b.remaining().doubleValue());
                styled(row.createCell(FIRST_COL + 9), styles.center).setCellValue(
                        pb.period().start().format(PERIOD_FORMAT) + " ~ " + pb.period().end().format(PERIOD_FORMAT));
                for (int i = 0; i < dayColumns; i++) {
                    Cell cell = row.createCell(FIRST_DAY_COL + i);
                    if (i >= days.size()) {
                        styled(cell, styles.center);
                    } else if (days.get(i).mark() == null) {
                        styled(cell, styles.date).setCellValue(days.get(i).date());
                    } else {
                        styled(cell, styles.center).setCellValue(days.get(i).date() + days.get(i).mark());
                    }
                }
            }
            mergeTeam(sheet, groupStart, r - 1);

            sheet.setColumnWidth(0, 2 * 256);
            int[] widths = {6, 12, 30, 7, 12, 7, 7, 9, 9, 24};
            for (int i = 0; i < widths.length; i++) {
                sheet.setColumnWidth(FIRST_COL + i, widths[i] * 256);
            }
            for (int i = 0; i < dayColumns; i++) {
                sheet.setColumnWidth(FIRST_DAY_COL + i, 15 * 256);
            }
            sheet.createFreezePane(TEAM_COL + 1, HEADER_ROW + 1);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "리포트 생성 실패: " + ex.getMessage());
        }
    }

    /**
     * 직원별 사용일: 연차처럼 차감하는 종류의 승인된 휴가를 근무일마다 한 칸으로 펼치고, 그 직원의 연차 기간 안의 날만 남긴다.
     * 칸마다 차감 일수는 휴가 차감 일수를 칸 수로 나눈 값(종일 1, 반차 0.5, 시간차 시간×0.125).
     */
    private Map<Long, List<UsedDay>> usedDays(List<LeaveBalanceService.PeriodBalance> rows) {
        if (rows.isEmpty()) {
            return Map.of();
        }
        Map<Long, LeaveBalanceService.PeriodBalance> byEmployee = rows.stream()
                .collect(Collectors.toMap(pb -> pb.employee().getId(), pb -> pb, (a, b) -> a));
        LocalDate from = rows.stream().map(pb -> pb.period().start()).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate to = rows.stream().map(pb -> pb.period().end()).max(Comparator.naturalOrder()).orElseThrow();
        Set<LocalDate> holidays = holidayRepository.findByDateBetweenOrderByDateAsc(from, to).stream()
                .map(Holiday::getDate).collect(Collectors.toSet());

        Map<Long, List<UsedDay>> result = new HashMap<>();
        for (LeaveRequest req : requestRepository.findApprovedBetween(from, to)) {
            LeaveBalanceService.PeriodBalance pb = byEmployee.get(req.getEmployee().getId());
            if (pb == null || req.getLeaveType().getAnnualDeductionMode() != AnnualDeductionMode.DEDUCT) {
                continue;
            }
            DayPortion portion = req.getPortion();
            List<LocalDate> dates = new ArrayList<>();
            if (portion == DayPortion.FULL) {
                for (LocalDate d = req.getStartDate(); !d.isAfter(req.getEndDate()); d = d.plusDays(1)) {
                    if (workdayCalculator.isWorkday(d, holidays)) {
                        dates.add(d);
                    }
                }
            } else {
                dates.add(req.getStartDate());
            }
            if (dates.isEmpty()) {
                continue;
            }
            String mark = switch (portion) {
                case FULL -> null;
                case HALF -> "(*)";
                case HOURLY -> "(" + WorkdayCalculator.hoursOf(req.getDays()) + "h)";
            };
            BigDecimal perDay = req.getDeductedDays().divide(BigDecimal.valueOf(dates.size()), 3, RoundingMode.HALF_UP);
            for (LocalDate d : dates) {
                if (!d.isBefore(pb.period().start()) && !d.isAfter(pb.period().end())) {
                    result.computeIfAbsent(req.getEmployee().getId(), k -> new ArrayList<>())
                            .add(new UsedDay(d, mark, perDay));
                }
            }
        }
        result.values().forEach(list -> list.sort(Comparator.comparing(UsedDay::date)));
        return result;
    }

    /** 같은 부서가 두 줄 이상이면 팀 칸을 세로로 합친다. */
    private static void mergeTeam(Sheet sheet, int first, int last) {
        if (last > first) {
            sheet.addMergedRegion(new CellRangeAddress(first, last, TEAM_COL, TEAM_COL));
        }
    }

    /** 부서 경로: "MLsoft › 연구소 › 개발팀". 부서가 없으면 "부서 없음". */
    static String departmentPath(Department d) {
        if (d == null) {
            return NO_DEPARTMENT;
        }
        return ancestry(d).stream().map(Department::getName).collect(Collectors.joining(PATH_SEPARATOR));
    }

    /** 부서 트리 순서(형제끼리는 정렬 순서, 이름 순). 상위 부서가 하위 부서보다 먼저, 부서 없는 직원은 맨 뒤. */
    static int compareDepartments(Department a, Department b) {
        if (a == null || b == null) {
            return a == b ? 0 : a == null ? 1 : -1;
        }
        List<Department> pa = ancestry(a);
        List<Department> pb = ancestry(b);
        for (int i = 0; i < Math.min(pa.size(), pb.size()); i++) {
            Department x = pa.get(i);
            Department y = pb.get(i);
            if (same(x, y)) {
                continue;
            }
            return Comparator.comparingInt(Department::getSortOrder)
                    .thenComparing(Department::getName)
                    .thenComparing(Department::getId, Comparator.nullsLast(Comparator.naturalOrder()))
                    .compare(x, y);
        }
        return Integer.compare(pa.size(), pb.size());
    }

    /** 같은 부서인지(지연 로딩 프록시와 실제 객체가 섞일 수 있어 id 로도 비교). */
    private static boolean same(Department x, Department y) {
        return x == y || (x.getId() != null && x.getId().equals(y.getId()));
    }

    /** 최상위 부서부터 자신까지. */
    private static List<Department> ancestry(Department d) {
        List<Department> path = new ArrayList<>();
        for (Department cur = d; cur != null; cur = cur.getParent()) {
            path.addFirst(cur);
        }
        return path;
    }

    private static Cell styled(Cell cell, CellStyle style) {
        cell.setCellStyle(style);
        return cell;
    }

    /** 칸 서식: 테두리, 가운데 정렬, 날짜 yyyy-mm-dd. */
    private static final class Styles {
        final CellStyle title;
        final CellStyle header;
        final CellStyle center;
        final CellStyle team;
        final CellStyle number;
        final CellStyle date;

        Styles(Workbook wb) {
            Font bold = wb.createFont();
            bold.setBold(true);
            Font big = wb.createFont();
            big.setBold(true);
            big.setFontHeightInPoints((short) 14);

            title = wb.createCellStyle();
            title.setFont(big);
            header = bordered(wb);
            header.setFont(bold);
            center = bordered(wb);
            team = bordered(wb);
            team.setWrapText(true);
            number = bordered(wb);
            date = bordered(wb);
            date.setDataFormat(wb.createDataFormat().getFormat("yyyy-mm-dd"));
        }

        private static CellStyle bordered(Workbook wb) {
            CellStyle s = wb.createCellStyle();
            s.setBorderTop(BorderStyle.THIN);
            s.setBorderBottom(BorderStyle.THIN);
            s.setBorderLeft(BorderStyle.THIN);
            s.setBorderRight(BorderStyle.THIN);
            s.setAlignment(HorizontalAlignment.CENTER);
            s.setVerticalAlignment(VerticalAlignment.CENTER);
            return s;
        }
    }
}
