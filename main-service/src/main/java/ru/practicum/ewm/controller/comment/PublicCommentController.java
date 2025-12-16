package ru.practicum.ewm.controller.comment;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.practicum.ewm.dto.comment.CommentDto;
import ru.practicum.ewm.dto.comment.CommentParam;
import ru.practicum.ewm.service.comment.CommentService;

import java.util.List;

@RestController
@Slf4j
@RequiredArgsConstructor
@Validated
public class PublicCommentController {
    private final CommentService commentService;

    @GetMapping("/events/{eventId}/comments")
    public List<CommentDto> getAllByEvent(@PathVariable @Positive Long eventId,
                                          @RequestParam(required = false, defaultValue = "0") @PositiveOrZero Integer from,
                                          @RequestParam(required = false, defaultValue = "10") @Positive Integer size) {
        log.debug("GET /events/{}/comments: from = {}, size = {}", eventId, from, size);

        CommentParam commentParam = new CommentParam();
        commentParam.setEventId(eventId);
        commentParam.setFrom(from);
        commentParam.setSize(size);

        return commentService.findAllByEventId(commentParam);
    }
}
