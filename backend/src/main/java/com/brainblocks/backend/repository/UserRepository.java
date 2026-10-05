package com.brainblocks.backend.repository;

import com.brainblocks.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    // Khóa dòng users (SELECT ... FOR UPDATE) trước khi đổi mật khẩu: 2 request đổi mật khẩu / dùng cùng 1 link đặt
    // lại chạy song song thì request sau chờ request trước xong rồi mới đọc tokenVersion mới. SQL thuần để chỉ chạm
    // bảng users: JPQL trên User (kế thừa JOINED) kéo thêm outer join tới bảng con, Postgres không cho FOR UPDATE.
    // Phải gọi TRƯỚC mọi lần đọc User khác trong transaction (Hibernate không nạp lại entity đã có).
    @Query(value = "select id from users where email = :email for update", nativeQuery = true)
    Optional<Long> lockIdByEmail(@Param("email") String email);

    @Query(value = "select id from users where id = :id for update", nativeQuery = true)
    Optional<Long> lockId(@Param("id") Long id);
}
