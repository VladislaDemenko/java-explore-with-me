package ru.practicum.main.comment.model;

import lombok.*;
import lombok.experimental.FieldDefaults;
import ru.practicum.main.event.model.Event;
import ru.practicum.main.user.model.User;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "comments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Comment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(nullable = false, length = 2000)
    String text;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    User author;

    @Column(name = "created_on", nullable = false)
    LocalDateTime createdOn;

    @Column(name = "edited_on")
    LocalDateTime editedOn;

    @Column(name = "is_edited", nullable = false)
    Boolean isEdited;
}