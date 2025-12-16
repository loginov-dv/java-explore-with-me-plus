package ru.practicum.ewm.controller.comment;

import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.practicum.ewm.service.comment.CommentService;

@RestController
@Slf4j
@RequiredArgsConstructor
@Validated
public class AdminCommentController {
    private final CommentService commentService;

    @DeleteMapping("/admin/events/{eventId}/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable @Positive Long eventId,
                       @PathVariable @Positive Long commentId) {
        log.debug("DELETE /admin/events/{}/comments/{} by admin", eventId, commentId);

        commentService.deleteByAdmin(eventId, commentId);
    }
}
