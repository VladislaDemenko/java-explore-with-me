package ru.practicum.main.event.model;

import lombok.*;
import lombok.experimental.FieldDefaults;

import jakarta.persistence.Embeddable;

@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Location {
    Float lat;
    Float lon;
}