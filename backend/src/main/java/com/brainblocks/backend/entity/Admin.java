package com.brainblocks.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "admins")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Admin extends User {

    private LocalDateTime lastLoginAt;

    // khiếu nại admin này nhận xử lý
    @Builder.Default
    @OneToMany(mappedBy = "handledBy")
    private List<Complaint> handledComplaints = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "admin")
    private List<ChatbotConfig> chatbotConfigs = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "admin")
    private List<KnowledgeDocument> knowledgeDocuments = new ArrayList<>();
}
