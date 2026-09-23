package dev.bryrich.credapp.usergroup;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserGroupRepository extends JpaRepository<UserGroup, Long> {
    Optional<UserGroup> findByJoinCode(String joinCode);
}
