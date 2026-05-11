package ru.practicum.main.comment.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.practicum.main.comment.model.Comment;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findByEventIdOrderByCreatedOnDesc(Long eventId, Pageable pageable);

    List<Comment> findByAuthorIdOrderByCreatedOnDesc(Long authorId, Pageable pageable);

    long countByEventId(Long eventId);
}