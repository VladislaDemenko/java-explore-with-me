package ru.practicum.main.exception;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ApiError {
    List<String> errors;
    String message;
    String reason;
    String status;
    String timestamp;
}