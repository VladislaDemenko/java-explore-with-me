package ru.practicum.main.user.dto;

import lombok.*;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class NewUserRequest {
    @NotBlank
    @Size(min = 2, max = 250)
    String name;

    @NotBlank
    @Email
    @Size(min = 6, max = 254)
    String email;
}