package ru.practicum.ewm.controller.comment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.ewm.dto.comment.CommentDto;
import ru.practicum.ewm.dto.comment.CommentParam;
import ru.practicum.ewm.dto.comment.UpdateCommentRequest;
import ru.practicum.ewm.service.comment.CommentService;

@RestController
@Slf4j
@RequiredArgsConstructor
@Validated
public class PrivateCommentController {
    private static final String HEADER = "X-Ewm-User-Id";
    private final CommentService commentService;

    @PostMapping("/events/{eventId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentDto create(@RequestHeader(HEADER) @Positive Long userId,
                             @PathVariable @Positive Long eventId,
                             @RequestBody @Valid CommentDto commentDto) {
        log.debug("POST /events/{}/comments by userId = {}: {}", eventId, userId, commentDto);

        CommentParam commentParam = new CommentParam();
        commentParam.setEventId(eventId);
        commentParam.setUserId(userId);
        commentParam.setCommentDto(commentDto);

        return commentService.create(commentParam);
    }

    @PatchMapping("/events/{eventId}/comments/{commentId}")
    public CommentDto edit(@RequestHeader(HEADER) @Positive Long userId,
                           @PathVariable @Positive Long eventId,
                           @PathVariable @Positive Long commentId,
                           @RequestBody @Valid UpdateCommentRequest updateCommentRequest) {
        log.debug("PATCH /events/{}/comments/{} by userId = {}: {}", eventId, commentId, userId, updateCommentRequest);

        CommentParam commentParam = new CommentParam();
        commentParam.setEventId(eventId);
        commentParam.setCommentId(commentId);
        commentParam.setUserId(userId);
        commentParam.setUpdateCommentRequest(updateCommentRequest);

        return commentService.update(commentParam);
    }

    @DeleteMapping("/events/{eventId}/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader(HEADER) @Positive Long userId,
                       @PathVariable @Positive Long eventId,
                       @PathVariable @Positive Long commentId) {
        log.debug("DELETE /events/{}/comments/{} by userId = {}", eventId, commentId, userId);

        CommentParam commentParam = new CommentParam();
        commentParam.setEventId(eventId);
        commentParam.setCommentId(commentId);
        commentParam.setUserId(userId);

        commentService.deleteByUser(commentParam);
    }
}
