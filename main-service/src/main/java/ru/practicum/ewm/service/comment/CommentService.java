package ru.practicum.ewm.service.comment;

import ru.practicum.ewm.dto.comment.CommentDto;
import ru.practicum.ewm.dto.comment.CommentParam;

import java.util.List;

public interface CommentService {

    CommentDto create(CommentParam commentParam);

    CommentDto update(CommentParam commentParam);

    void deleteByUser(CommentParam commentParam);

    void deleteByAdmin(Long eventId, Long commentId);

    List<CommentDto> findAllByEventId(CommentParam commentParam);
}
