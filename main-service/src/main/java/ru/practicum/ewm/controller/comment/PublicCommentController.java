package ru.practicum.ewm.controller.comment;

import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import ru.practicum.ewm.dto.comment.CommentDto;
import ru.practicum.ewm.service.comment.CommentService;

import java.util.List;

@RestController
@Slf4j
@RequiredArgsConstructor
@Validated
public class PublicCommentController {
    private final CommentService commentService;

    @GetMapping("/events/{eventId}/comments")
    public List<CommentDto> getAllByEvent(@PathVariable @Positive Long eventId) {
        log.debug("GET /events/{}/comments", eventId);

        return commentService.findAllByEventId(eventId);
    }
}
