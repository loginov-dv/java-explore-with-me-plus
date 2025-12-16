package ru.practicum.ewm.dto.comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateCommentRequest {

    @NotBlank(message = "Текст комментария не может быть пустым")
    @Size(min = 1, max = 2000)
    private String text;
}
