package ru.practicum.ewm.repository.comment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.practicum.ewm.model.comment.Comment;

import java.util.Collection;
import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    Page<Comment> findByEventId(Long eventId, Pageable pageable);

    Long countByEventId(Long eventId);

    @Query("SELECT c.event.id, COUNT(c) FROM Comment c " +
            "WHERE c.event.id IN :ids " +
            "GROUP BY c.event.id")
    List<Object[]> countByEventIdIn(Collection<Long> ids);
}
