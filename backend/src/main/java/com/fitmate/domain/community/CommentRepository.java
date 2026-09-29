package com.fitmate.domain.community;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    boolean existsByParentId(Long parentId);

    /** 지워진 댓글(자리만 남은 것) 아래 답글이 모두 지워졌는지 확인할 때 쓴다 */
    long countByParentIdAndDeletedFalse(Long parentId);
}
