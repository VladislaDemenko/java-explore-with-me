package ru.practicum.main.comment.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.main.comment.dto.CommentDto;
import ru.practicum.main.comment.service.CommentService;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/events/{eventId}/comments")
@RequiredArgsConstructor
@Validated
public class CommentPublicController {

    private final CommentService commentService;

    @GetMapping
    public List<CommentDto> getEventComments(
            @PathVariable Long eventId,
            @RequestParam(defaultValue = "0") @PositiveOrZero int from,
            @RequestParam(defaultValue = "10") @Positive int size) {
        log.info("GET /events/{}/comments - from={}, size={}", eventId, from, size);
        return commentService.getEventComments(eventId, from, size);
    }

    @GetMapping("/{commentId}")
    public CommentDto getComment(@PathVariable Long eventId,
                                 @PathVariable Long commentId) {
        log.info("GET /events/{}/comments/{}", eventId, commentId);
        return commentService.getComment(commentId);
    }
}