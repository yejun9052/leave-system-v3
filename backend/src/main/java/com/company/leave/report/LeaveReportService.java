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
import com.company.leave.leave.domain.HalfDayPart;
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
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.RegionUtil;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 연차 사용 현황 리포트 (엑셀). 연구소 연차현황표 양식: 직원마다 한 줄, 부서별로 팀 칸을 묶고(부서 안은 입사일 순) 사용한 날을 한 칸씩 적는다.
 * <ul>
 *   <li>사용일: 연차처럼 차감하는 종류(연차·반차·시간차 등)의 승인된 휴가만. 경조사·병가·공가, 결재 대기·취소 요청 중은 넣지 않는다</li>
 *   <li>종일은 날짜, 반차는 "날짜(*)", 시간차는 "날짜(2h)". 여러 날 휴가는 주말·공휴일을 뺀 날마다 한 칸</li>
 *   <li>사용 = 사용일 칸 합계, 연차 = 부여, 추가일은 아직 정하지 않아 빈칸, 남은 연차 = 시스템 잔여(이월·소멸 반영)</li>
 *   <li>아래에 "경조사 사용 내역": 그해에 시작한 승인된 경조사 규정 휴가(생일 반차 포함)</li>
 * </ul>
 */
@Service
public class LeaveReportService {

    /** 사용일 기본 칸 수. 더 많이 쓴 직원이 있으면 그만큼 늘린다. */
    static final int MIN_DAY_COLUMNS = 50;
    static final String[] HEADERS =
            {"번호", "이름", "팀", "사용", "입사일", "연차", "추가일", "전체 연차", "남은 연차", "사용 기간", "사용일"};
    /** 받은 양식 맨 위 안내 문구(그대로 옮김). */
    static final String NOTICE = "근속이 1년 미만인 경우 연차 관리 유념해주기 바랍니다.";
    static final String NOTE_AWARD = "* 3년이상 근무한 사람은 2년당 1개의 추가 연차가 발생함 (3년 => 16개, 5년 => 17개)";
    static final String NOTE_2019 =
            "* 2019년 변경사항 : 신입의 경우 1개월 만근시 1개의 연차가 주어지고, 1년 만근하면 15개가 추가로 주어집니다. ";
    static final String CHECK_NEEDED = "확인필요";
    static final String LEGEND_HALF = "반차 : (*)";
    static final String LEGEND_HOURLY = "시간차 : (2h)";
    static final String NO_DEPARTMENT = "부서 없음";
    static final String CONDOLENCE_TITLE = "경조사 사용 내역";
    static final String CONDOLENCE_NONE = "해당 연도에 승인된 경조사 휴가가 없습니다.";
    /** 연차 현황표 마지막 줄 바로 다음 줄부터 빈 줄 3개를 두고 경조사 표 제목 */
    private static final int CONDOLENCE_GAP = 3;

    /** 받은 양식처럼 A열과 1행은 비워 두고 B2 부터 쓴다. */
    private static final int FIRST_COL = 1;
    private static final int TEAM_COL = FIRST_COL + 2;
    private static final int FIRST_DAY_COL = FIRST_COL + HEADERS.length - 1;
    /** 받은 양식의 행: 2 안내, 3 분홍 칸, 4~5 안내(5행 오른쪽은 사용일 번호), 6 기준일·확인필요·범례, 7 머리줄, 8~ 직원 */
    private static final int NOTICE_ROW = 1;
    private static final int NUMBER_ROW = 4;
    private static final int INFO_ROW = 5;
    private static final int HEADER_ROW = 6;
    /** 안내 문구를 합치는 범위: B~J열 */
    private static final int NOTICE_LAST_COL = FIRST_COL + 8;

    /** 받은 양식의 열 너비(글자 수): A 여백, B 번호 … K 사용 기간(양식 20.75보다 10px 넓게), L~U 사용일 앞 10칸. 그 뒤는 기본 너비 */
    private static final double[] TEMPLATE_WIDTHS = {7.25, 14.0, 14.13, 7.88, 9.0, 10.38, 10.38, 8.63, 10.25, 11.25,
            22.14, 14.5, 15.0, 15.0, 13.25, 12.63, 12.63, 13.75, 13.75, 13.75, 13.75};
    private static final double TEMPLATE_DEFAULT_WIDTH = 12.63;
    /** 받은 양식의 색 */
    private static final int PINK_BAR = 0xF4CCCC;
    private static final int HEADER_BLUE = 0x6FA8DC;
    private static final int WHITE = 0xFFFFFF;
    private static final int USED_PINK = 0xEAD1DC;
    private static final int REMAINING_CYAN = 0x00FFFF;
    private static final int PERIOD_GRAY = 0xF3F3F3;
    private static final int DAY_BLUE = 0xCFE2F3;
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
        return exportUsage(year, List.of(), List.of());
    }

    /**
     * 고른 대상만 출력. 고른 부서에 소속된 직원과 고른 사용자를 합친다. 둘 다 비면 전체.
     * 부서는 화면에서 체크한 부서 그대로다(하위 부서는 화면이 체크해서 보내며, 여기서 따로 펼치지 않는다).
     */
    @Transactional(readOnly = true)
    public byte[] exportUsage(int year, Collection<Long> departmentIds, Collection<Long> employeeIds) {
        LocalDate today = LocalDate.now();
        LocalDate asOf = year == today.getYear() ? today : LocalDate.of(year, 12, 31);
        Predicate<Employee> selected = selection(departmentIds, employeeIds);
        List<LeaveBalanceService.PeriodBalance> rows = balanceService.balancesAsOf(asOf, false, false).stream()
                .filter(pb -> selected.test(pb.employee()))
                .collect(Collectors.toCollection(ArrayList::new));
        // 부서별로 묶고, 부서 안에서는 입사일이 빠른 순(입사일 없으면 뒤), 같으면 이름순
        rows.sort(Comparator.comparing((LeaveBalanceService.PeriodBalance pb) -> pb.employee().getDepartment(),
                        LeaveReportService::compareDepartments)
                .thenComparing(pb -> pb.employee().getHireDate(), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(pb -> pb.employee().getName()));
        Map<Long, List<UsedDay>> usedDays = usedDays(rows);

        int dayColumns = Math.max(MIN_DAY_COLUMNS,
                usedDays.values().stream().mapToInt(List::size).max().orElse(0));

        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles styles = new Styles(wb);
            Sheet sheet = wb.createSheet(String.valueOf(year));
            writeNotices(sheet, styles, asOf);
            Row numbers = sheet.getRow(NUMBER_ROW);
            for (int i = 0; i < dayColumns; i++) {
                styled(numbers.createCell(FIRST_DAY_COL + i), styles.dayNumber).setCellValue(i + 1);
            }
            Row header = sheet.createRow(HEADER_ROW);
            for (int i = 0; i < HEADERS.length - 1 + dayColumns; i++) {
                styled(header.createCell(FIRST_COL + i), styles.header)
                        .setCellValue(i < HEADERS.length ? HEADERS[i] : "");
            }
            CellRangeAddress dayHeader = new CellRangeAddress(HEADER_ROW, HEADER_ROW,
                    FIRST_DAY_COL, FIRST_DAY_COL + dayColumns - 1);
            sheet.addMergedRegion(dayHeader);
            outline(dayHeader, sheet);

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

                // 칸 색은 받은 양식대로: 사용 분홍, 남은 연차 하늘, 사용 기간 회색, 사용일 연파랑, 나머지 흰색
                Row row = sheet.createRow(r++);
                styled(row.createCell(FIRST_COL), styles.cell).setCellValue(n + 1);
                styled(row.createCell(FIRST_COL + 1), styles.cell).setCellValue(e.getName());
                styled(row.createCell(TEAM_COL), styles.team).setCellValue(team);
                styled(row.createCell(FIRST_COL + 3), styles.used).setCellValue(
                        days.stream().map(UsedDay::days).reduce(BigDecimal.ZERO, BigDecimal::add).doubleValue());
                // 날짜는 글자로 쓴다(날짜 값 + 서식이면 서식을 모르는 미리보기·뷰어에서 43241.0 처럼 보임).
                // yyyy.MM.dd 라 글자로 정렬해도 날짜 순이다
                Cell hire = styled(row.createCell(FIRST_COL + 4), styles.cell);
                if (e.getHireDate() != null) {
                    hire.setCellValue(e.getHireDate().format(PERIOD_FORMAT));
                }
                styled(row.createCell(FIRST_COL + 5), styles.cell).setCellValue(b.getGranted().doubleValue());
                styled(row.createCell(FIRST_COL + 6), styles.cell); // 추가일: 아직 정하지 않아 빈칸
                styled(row.createCell(FIRST_COL + 7), styles.cell).setCellValue(b.getGranted().doubleValue());
                styled(row.createCell(FIRST_COL + 8), styles.remaining).setCellValue(b.remaining().doubleValue());
                styled(row.createCell(FIRST_COL + 9), styles.period).setCellValue(
                        pb.period().start().format(PERIOD_FORMAT) + " ~ " + pb.period().end().format(PERIOD_FORMAT));
                for (int i = 0; i < dayColumns; i++) {
                    Cell cell = row.createCell(FIRST_DAY_COL + i);
                    if (i >= days.size()) {
                        styled(cell, styles.day);
                    } else if (days.get(i).mark() == null) {
                        styled(cell, styles.day).setCellValue(days.get(i).date().toString());
                    } else {
                        styled(cell, styles.day).setCellValue(days.get(i).date() + days.get(i).mark());
                    }
                }
            }
            mergeTeam(sheet, groupStart, r - 1);
            writeCondolences(sheet, styles, r + CONDOLENCE_GAP, condolences(year, selected));
            applyTemplateSize(sheet, dayColumns, rows.stream()
                    .map(pb -> departmentPath(pb.employee().getDepartment())).toList());
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

    /**
     * 받은 양식의 맨 위(2~6행)를 그대로: 굵은 안내, 분홍 칸, 안내 두 줄, 기준일·확인필요·범례.
     * 양식의 =TODAY() 자리에는 보고서 기준일(올해는 오늘)을 쓴다.
     */
    private static void writeNotices(Sheet sheet, Styles styles, LocalDate asOf) {
        styled(sheet.createRow(NOTICE_ROW).createCell(FIRST_COL), styles.notice).setCellValue(NOTICE);
        sheet.addMergedRegion(new CellRangeAddress(NOTICE_ROW, NOTICE_ROW, FIRST_COL, NOTICE_LAST_COL));

        Row pink = sheet.createRow(NOTICE_ROW + 1);
        for (int c = FIRST_COL; c <= NOTICE_LAST_COL; c++) {
            styled(pink.createCell(c), styles.pinkBar);
        }
        CellRangeAddress pinkRange = new CellRangeAddress(NOTICE_ROW + 1, NOTICE_ROW + 1, FIRST_COL, NOTICE_LAST_COL);
        sheet.addMergedRegion(pinkRange);
        outline(pinkRange, sheet);

        sheet.createRow(NOTICE_ROW + 2).createCell(FIRST_COL).setCellValue(NOTE_AWARD);
        sheet.addMergedRegion(new CellRangeAddress(NOTICE_ROW + 2, NOTICE_ROW + 2, FIRST_COL, NOTICE_LAST_COL));
        sheet.createRow(NUMBER_ROW).createCell(FIRST_COL).setCellValue(NOTE_2019);

        Row info = sheet.createRow(INFO_ROW);
        info.createCell(FIRST_COL).setCellValue(asOf.toString());
        styled(info.createCell(FIRST_COL + 7), styles.red).setCellValue(CHECK_NEEDED);
        info.createCell(FIRST_DAY_COL).setCellValue(LEGEND_HALF);
        info.createCell(FIRST_DAY_COL + 1).setCellValue(LEGEND_HOURLY);
    }

    /**
     * 경조사 사용 내역: 그해(1~12월)에 시작한 승인된 경조사 규정 휴가. 고른 대상만, 관리 전용 계정 제외.
     * 시작일 순, 같으면 이름순.
     */
    private List<LeaveRequest> condolences(int year, Predicate<Employee> selected) {
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);
        return requestRepository.findApprovedBetween(from, to).stream()
                .filter(r -> r.getSpecialRuleName() != null)
                .filter(r -> !r.getStartDate().isBefore(from) && !r.getStartDate().isAfter(to))
                .filter(r -> !r.getEmployee().isSystemAccount() && selected.test(r.getEmployee()))
                .sorted(Comparator.comparing(LeaveRequest::getStartDate)
                        .thenComparing(r -> r.getEmployee().getName()))
                .toList();
    }

    /**
     * 연차 현황표 아래의 "경조사 사용 내역" 표. 열: 번호 · 이름 · 부서 · 경조사 규정 · 사용일 · 일수.
     * 규정과 사용일은 위 표의 좁은 열을 세 칸씩 합쳐 쓴다(위 표 너비를 바꾸지 않으려고).
     */
    private static void writeCondolences(Sheet sheet, Styles styles, int top, List<LeaveRequest> requests) {
        styled(sheet.createRow(top).createCell(FIRST_COL), styles.sectionTitle).setCellValue(CONDOLENCE_TITLE);
        Row header = sheet.createRow(top + 1);
        String[] labels = {"번호", "이름", "부서", "경조사 규정", "", "", "사용일", "", "", "일수"};
        for (int i = 0; i < labels.length; i++) {
            styled(header.createCell(FIRST_COL + i), styles.header).setCellValue(labels[i]);
        }
        mergeCondolenceRow(sheet, top + 1);

        int r = top + 2;
        if (requests.isEmpty()) {
            Row row = sheet.createRow(r);
            for (int i = 0; i < labels.length; i++) {
                styled(row.createCell(FIRST_COL + i), styles.cell);
            }
            row.getCell(FIRST_COL).setCellValue(CONDOLENCE_NONE);
            sheet.addMergedRegion(new CellRangeAddress(r, r, FIRST_COL, FIRST_COL + labels.length - 1));
            return;
        }
        for (int n = 0; n < requests.size(); n++, r++) {
            LeaveRequest req = requests.get(n);
            Row row = sheet.createRow(r);
            for (int i = 0; i < labels.length; i++) {
                styled(row.createCell(FIRST_COL + i), styles.cell);
            }
            row.getCell(FIRST_COL).setCellValue(n + 1);
            row.getCell(FIRST_COL + 1).setCellValue(req.getEmployee().getName());
            row.getCell(FIRST_COL + 2).setCellValue(departmentPath(req.getEmployee().getDepartment()));
            row.getCell(FIRST_COL + 3).setCellValue(req.getSpecialRuleName()
                    + (req.getHalfDayPart() != null ? " · " + (req.getHalfDayPart() == HalfDayPart.AM ? "오전" : "오후") + " 반차" : ""));
            row.getCell(FIRST_COL + 6).setCellValue(req.getStartDate().equals(req.getEndDate())
                    ? req.getStartDate().toString() : req.getStartDate() + " ~ " + req.getEndDate());
            row.getCell(FIRST_COL + 9).setCellValue(req.getDays().doubleValue());
            mergeCondolenceRow(sheet, r);
        }
    }

    /** 경조사 표 한 줄: 규정(E~G), 사용일(H~J)을 세 칸씩 합친다. */
    private static void mergeCondolenceRow(Sheet sheet, int row) {
        sheet.addMergedRegion(new CellRangeAddress(row, row, FIRST_COL + 3, FIRST_COL + 5));
        sheet.addMergedRegion(new CellRangeAddress(row, row, FIRST_COL + 6, FIRST_COL + 8));
    }

    /** 고른 부서 소속이거나 고른 사용자. 아무것도 고르지 않으면 모두. */
    private static Predicate<Employee> selection(Collection<Long> departmentIds, Collection<Long> employeeIds) {
        Set<Long> departments = departmentIds == null ? Set.of() : Set.copyOf(departmentIds);
        Set<Long> employees = employeeIds == null ? Set.of() : Set.copyOf(employeeIds);
        if (departments.isEmpty() && employees.isEmpty()) {
            return e -> true;
        }
        return e -> employees.contains(e.getId())
                || (e.getDepartmentId() != null && departments.contains(e.getDepartmentId()));
    }

    /** 합친 칸 바깥 테두리(가는 선). */
    private static void outline(CellRangeAddress range, Sheet sheet) {
        RegionUtil.setBorderTop(BorderStyle.THIN, range, sheet);
        RegionUtil.setBorderBottom(BorderStyle.THIN, range, sheet);
        RegionUtil.setBorderLeft(BorderStyle.THIN, range, sheet);
        RegionUtil.setBorderRight(BorderStyle.THIN, range, sheet);
    }

    /**
     * 받은 양식의 열 너비·행 높이(15.75). 사용일은 양식에 있는 25칸까지 양식 너비, 그 뒤는 양식 기본 너비(12.63).
     * 팀 칸만 부서 경로가 들어가 양식(7.88)보다 넓어질 수 있다: 가장 긴 경로에 맞춘다.
     */
    private static void applyTemplateSize(Sheet sheet, int dayColumns, List<String> teams) {
        sheet.setDefaultRowHeightInPoints(15.75f);
        for (int c = 0; c < TEMPLATE_WIDTHS.length; c++) {
            width(sheet, c, TEMPLATE_WIDTHS[c]);
        }
        for (int c = TEMPLATE_WIDTHS.length; c < FIRST_DAY_COL + dayColumns; c++) {
            width(sheet, c, TEMPLATE_DEFAULT_WIDTH);
        }
        double team = teams.stream().mapToDouble(LeaveReportService::displayWidth).max().orElse(0) + 2;
        width(sheet, TEAM_COL, Math.max(TEMPLATE_WIDTHS[TEAM_COL], team));
    }

    private static void width(Sheet sheet, int col, double chars) {
        sheet.setColumnWidth(col, (int) Math.round(chars * 256));
    }

    /** 엑셀 글자 폭 어림: 한글 등 넓은 글자 2, 그 외 1. */
    private static double displayWidth(String s) {
        return s.codePoints().mapToDouble(cp -> cp > 0x2E80 ? 2 : 1).sum();
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

    /**
     * 칸 서식: 받은 양식과 같은 글꼴(Arial 10)·색. 머리줄 파랑, 표 칸은 흰색에 가는 테두리·가운데 정렬,
     * 사용 분홍, 남은 연차 하늘, 사용 기간 회색, 사용일 연파랑.
     */
    private static final class Styles {
        final CellStyle notice;
        final CellStyle sectionTitle;
        final CellStyle pinkBar;
        final CellStyle red;
        final CellStyle dayNumber;
        final CellStyle header;
        final CellStyle cell;
        final CellStyle team;
        final CellStyle used;
        final CellStyle remaining;
        final CellStyle period;
        final CellStyle day;

        Styles(Workbook wb) {
            Font base = wb.getFontAt(0);
            base.setFontName("Arial");
            base.setFontHeightInPoints((short) 10);
            Font big = font(wb, true, 14, null);
            Font redFont = font(wb, false, 10, IndexedColors.RED);

            notice = wb.createCellStyle();
            notice.setFont(big);
            sectionTitle = wb.createCellStyle();
            sectionTitle.setFont(font(wb, true, 12, null));
            pinkBar = filled(wb.createCellStyle(), PINK_BAR);
            red = wb.createCellStyle();
            red.setFont(redFont);
            dayNumber = wb.createCellStyle();
            dayNumber.setAlignment(HorizontalAlignment.CENTER);

            header = filled(bordered(wb), HEADER_BLUE);
            cell = filled(bordered(wb), WHITE);
            team = filled(bordered(wb), WHITE);
            team.setWrapText(true);
            used = filled(bordered(wb), USED_PINK);
            remaining = filled(bordered(wb), REMAINING_CYAN);
            period = filled(bordered(wb), PERIOD_GRAY);
            day = filled(bordered(wb), DAY_BLUE);
        }

        private static CellStyle filled(CellStyle s, int rgb) {
            ((XSSFCellStyle) s).setFillForegroundColor(new XSSFColor(
                    new byte[] {(byte) (rgb >> 16), (byte) (rgb >> 8), (byte) rgb}, null));
            s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            return s;
        }

        private static Font font(Workbook wb, boolean bold, int points, IndexedColors color) {
            Font f = wb.createFont();
            f.setFontName("Arial");
            f.setFontHeightInPoints((short) points);
            f.setBold(bold);
            if (color != null) {
                f.setColor(color.getIndex());
            }
            return f;
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
