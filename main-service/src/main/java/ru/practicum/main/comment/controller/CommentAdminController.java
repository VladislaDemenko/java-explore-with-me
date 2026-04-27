package ru.practicum.main.comment.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.practicum.main.comment.service.CommentService;

@Slf4j
@RestController
@RequestMapping("/admin/comments")
@RequiredArgsConstructor
public class CommentAdminController {

    private final CommentService commentService;

    @DeleteMapping("/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@PathVariable Long commentId) {
        log.info("DELETE /admin/comments/{}", commentId);
        commentService.deleteCommentByAdmin(commentId);
    }
}