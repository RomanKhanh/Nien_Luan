package com.brainblocks.backend.repository;

import com.brainblocks.backend.entity.Admin;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminRepository extends JpaRepository<Admin, Long> {}
