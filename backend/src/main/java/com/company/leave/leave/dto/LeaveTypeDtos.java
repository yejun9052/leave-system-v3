package com.company.leave.leave.dto;

import com.company.leave.leave.domain.LeaveType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public final class LeaveTypeDtos {

    private LeaveTypeDtos() {
    }

    public record Response(
            Long id,
            String code,
            String name,
            BigDecimal deductDays,
            boolean paid,
            boolean halfDay,
            boolean deductFromAnnual,
            String colorHex,
            int sortOrder,
            boolean active) {

        public static Response from(LeaveType t) {
            return new Response(t.getId(), t.getCode(), t.getName(), t.getDeductDays(),
                    t.isPaid(), t.isHalfDay(), t.isDeductFromAnnual(), t.getColorHex(),
                    t.getSortOrder(), t.isActive());
        }
    }

    public record Create(
            @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 60) String name,
            @NotNull BigDecimal deductDays,
            boolean paid,
            boolean halfDay,
            boolean deductFromAnnual,
            @NotBlank @Size(max = 7) String colorHex,
            Integer sortOrder) {
    }

    public record Update(
            @NotBlank @Size(max = 60) String name,
            @NotNull BigDecimal deductDays,
            boolean paid,
            boolean halfDay,
            boolean deductFromAnnual,
            @NotBlank @Size(max = 7) String colorHex,
            Integer sortOrder,
            boolean active) {
    }
}
