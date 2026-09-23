package dev.bryrich.credapp.ssn;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SsnAccessLogRepository extends JpaRepository<SsnAccessLog, Long> {

    List<SsnAccessLog> findTop10BySubjectTypeAndSubjectIdOrderByAccessedAtDesc(
            SsnSubjectType subjectType, Long subjectId);

    Page<SsnAccessLog> findBySubjectTypeAndSubjectId(
            SsnSubjectType subjectType, Long subjectId, Pageable pageable);

    Page<SsnAccessLog> findByUserId(Long userId, Pageable pageable);
}
