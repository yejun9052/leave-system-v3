package com.company.leave.policy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * 장기근속 포상 규칙: 근속 {@code years}년 도달 시 해당 연도에 {@code bonusDays} 만큼 연차를 가산 부여.
 */
@Entity
@Table(name = "service_award_rules")
public class ServiceAwardRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private int years;

    @Column(name = "bonus_days", nullable = false)
    private BigDecimal bonusDays;

    @Column(length = 60)
    private String name;

    protected ServiceAwardRule() {
    }

    public ServiceAwardRule(int years, BigDecimal bonusDays, String name) {
        this.years = years;
        this.bonusDays = bonusDays;
        this.name = name;
    }

    public void update(int years, BigDecimal bonusDays, String name) {
        this.years = years;
        this.bonusDays = bonusDays;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public int getYears() {
        return years;
    }

    public BigDecimal getBonusDays() {
        return bonusDays;
    }

    public String getName() {
        return name;
    }
}
