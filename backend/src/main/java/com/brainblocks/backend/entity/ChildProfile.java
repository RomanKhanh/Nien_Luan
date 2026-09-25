package com.brainblocks.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "child_profiles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChildProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private LocalDate birthDate;

    @Column(length = 10)
    private String gender;

    @Column(columnDefinition = "TEXT")
    private String note;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    // phụ huynh sở hữu hồ sơ trẻ này
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    // thành phần: mỗi hồ sơ trẻ có đúng một hồ sơ kỹ năng
    @OneToOne(mappedBy = "childProfile", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private SkillProfile skillProfile;

    // lớp kết hợp ChildProfile - Product, cascade từ phía hồ sơ trẻ
    @Builder.Default
    @OneToMany(mappedBy = "childProfile", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChildProduct> childProducts = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "childProfile")
    private List<ChatSession> chatSessions = new ArrayList<>();

    // các nhóm kỹ năng phụ huynh muốn tập trung cho bé; dùng Set để JOIN FETCH cùng collection khác không bị MultipleBagFetchException
    @Builder.Default
    @ManyToMany
    @JoinTable(
            name = "child_profile_skills",
            joinColumns = @JoinColumn(name = "child_profile_id"),
            inverseJoinColumns = @JoinColumn(name = "skill_id")
    )
    private Set<Skill> interestedSkills = new HashSet<>();

    // tuổi tròn theo năm tính đến hôm nay; bé dưới 1 tuổi trả 0.
    // không có field age nên Hibernate (field access) không map method này thành cột
    public int getAge() {
        return Period.between(birthDate, LocalDate.now()).getYears();
    }
}
