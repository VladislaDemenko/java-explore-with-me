package ru.practicum.main.comment.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import jakarta.validation.constraints.Size;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateCommentDto {
    @Size(min = 1, max = 2000)
    String text;
}