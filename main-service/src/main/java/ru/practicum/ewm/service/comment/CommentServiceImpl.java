package ru.practicum.ewm.service.comment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.dto.comment.CommentDto;
import ru.practicum.ewm.dto.comment.CommentParam;
import ru.practicum.ewm.event.EventRepository;
import ru.practicum.ewm.exception.AccessViolationException;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.exception.ValidationException;
import ru.practicum.ewm.mapper.CommentMapper;
import ru.practicum.ewm.model.comment.Comment;
import ru.practicum.ewm.model.event.Event;
import ru.practicum.ewm.model.user.User;
import ru.practicum.ewm.repository.UserRepository;
import ru.practicum.ewm.repository.comment.CommentRepository;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class CommentServiceImpl implements CommentService {
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;

    @Override
    public CommentDto create(CommentParam commentParam) {
        log.debug("Comment create request for eventId = {} by userId = {}: {}",
                commentParam.getEventId(), commentParam.getUserId(), commentParam.getCommentDto());

        Optional<User> maybeUser = userRepository.findById(commentParam.getUserId());

        if (maybeUser.isEmpty()) {
            log.warn("User with id = {} not found", commentParam.getUserId());
            throw new NotFoundException(String.format("User with id = %d not found", commentParam.getUserId()));
        }

        Optional<Event> maybeEvent = eventRepository.findById(commentParam.getEventId());

        if (maybeEvent.isEmpty()) {
            log.warn("Event with id = {} not found", commentParam.getEventId());
            throw new NotFoundException(String.format("Event with id = %d not found", commentParam.getEventId()));
        }

        Comment comment = commentRepository.save(CommentMapper.toNewComment(maybeUser.get(),
                maybeEvent.get(), commentParam.getCommentDto()));

        log.info("New comment added: {}", comment);
        return CommentMapper.toCommentDto(comment);
    }

    @Override
    public CommentDto update(CommentParam commentParam) {
        log.debug("CommentId = {} update request for eventId = {} by userId = {}: {}",
                commentParam.getCommentId(), commentParam.getEventId(), commentParam.getUserId(),
                commentParam.getUpdateCommentRequest());

        Optional<Comment> maybeComment = commentRepository.findById(commentParam.getCommentId());

        if (maybeComment.isEmpty()) {
            log.warn("Comment with id = {} not found", commentParam.getCommentId());
            throw new NotFoundException(String.format("Comment with id = %d not found", commentParam.getCommentId()));
        }

        Comment comment = maybeComment.get();
        log.debug("Initial comment state: {}", comment);

        if (!comment.getUser().getId().equals(commentParam.getUserId())) {
            log.warn("No access to edit comment");
            throw new AccessViolationException("No access to edit comment");
        }

        if (!commentParam.getEventId().equals(comment.getEvent().getId())) {
            log.warn("Comment with id = {} doesn't belong to event with id = {}",
                    commentParam.getCommentId(), commentParam.getEventId());
            throw new ValidationException(String.format("Comment with id = %d doesn't belong to event with id = %d",
                    commentParam.getCommentId(), commentParam.getEventId()));
        }

        CommentMapper.updateFields(comment, commentParam.getUpdateCommentRequest());
        comment = commentRepository.save(comment);

        log.debug("Comment has been updated: {}", comment);
        return CommentMapper.toCommentDto(comment);
    }

    @Override
    public void deleteByUser(CommentParam commentParam) {
        log.debug("CommentId = {} delete request for eventId = {} by userId = {}",
                commentParam.getCommentId(), commentParam.getEventId(), commentParam.getUserId());

        Optional<Comment> maybeComment = commentRepository.findById(commentParam.getCommentId());

        if (maybeComment.isEmpty()) {
            log.warn("Comment with id = {} not found", commentParam.getCommentId());
            throw new NotFoundException(String.format("Comment with id = %d not found", commentParam.getCommentId()));
        }

        Comment comment = maybeComment.get();

        if (!commentParam.getEventId().equals(comment.getEvent().getId())) {
            log.warn("Comment with id = {} doesn't belong to event with id = {}",
                    commentParam.getCommentId(), commentParam.getEventId());
            throw new ValidationException(String.format("Comment with id = %d doesn't belong to event with id = %d",
                    commentParam.getCommentId(), commentParam.getEventId()));
        }

        if (!comment.getUser().getId().equals(commentParam.getUserId())) {
            log.warn("No access to delete comment");
            throw new AccessViolationException("No access to delete comment");
        }

        commentRepository.deleteById(commentParam.getCommentId());
        log.debug("Comment has been deleted by user");
    }

    @Override
    public void deleteByAdmin(Long eventId, Long commentId) {
        log.debug("Comment id = {} delete request for eventId = {} by admin", commentId, eventId);

        Optional<Comment> maybeComment = commentRepository.findById(commentId);

        if (maybeComment.isEmpty()) {
            log.warn("Comment with id = {} not found", commentId);
            throw new NotFoundException(String.format("Comment with id = %d not found", commentId));
        }

        Comment comment = maybeComment.get();

        if (!eventId.equals(comment.getEvent().getId())) {
            log.warn("Comment with id = {} doesn't belong to event with id = {}", commentId, eventId);
            throw new ValidationException(String.format("Comment with id = %d doesn't belong to event with id = %d",
                    commentId, eventId));
        }

        commentRepository.deleteById(commentId);
        log.debug("Comment has been deleted by admin");
    }

    @Override
    public List<CommentDto> findAllByEventId(CommentParam commentParam) {
        log.debug("Comments request for eventId = {}", commentParam.getEventId());

        if (!eventRepository.existsById(commentParam.getEventId())) {
            log.warn("Event with id = {} not found", commentParam.getEventId());
            throw new NotFoundException(String.format("Event with id = %d not found", commentParam.getEventId()));
        }

        int page = commentParam.getFrom() / commentParam.getSize();
        Sort sort = Sort.by("createdOn").ascending();
        Pageable pageable = PageRequest.of(page, commentParam.getSize(), sort);
        List<Comment> comments = commentRepository.findByEventId(commentParam.getEventId(), pageable).getContent();
        log.debug("comments.size = {}", comments.size());

        return comments.stream()
                .map(CommentMapper::toCommentDto)
                .toList();
    }
}
