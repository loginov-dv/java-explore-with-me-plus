package ru.practicum.ewm.repository.comment;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.practicum.ewm.model.comment.Comment;

import java.util.Collection;
import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByEventId(Long eventId);

    List<Comment> findByEventIdIn(Collection<Long> ids);
}
