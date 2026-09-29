package com.company.leave.department.domain;

import com.company.leave.common.entity.BaseTimeEntity;
import com.company.leave.employee.domain.Employee;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 부서. 자기참조(parent)로 계층 구조를 이룬다.
 */
@Entity
@Table(name = "departments")
public class Department extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Department parent;

    /** 부서장(팀장). 결재 권한 판단에 사용. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id")
    private Employee lead;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    protected Department() {
    }

    public Department(String name, Department parent, int sortOrder) {
        this.name = name;
        this.parent = parent;
        this.sortOrder = sortOrder;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void moveTo(Department newParent) {
        this.parent = newParent;
    }

    public void changeSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public void assignLead(Employee lead) {
        this.lead = lead;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Department getParent() {
        return parent;
    }

    public Long getParentId() {
        return parent != null ? parent.getId() : null;
    }

    public Employee getLead() {
        return lead;
    }

    public Long getLeadId() {
        return lead != null ? lead.getId() : null;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
