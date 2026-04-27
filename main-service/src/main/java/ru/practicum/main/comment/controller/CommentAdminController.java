package ru.practicum.main.comment.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.practicum.main.comment.service.CommentService;

@Slf4j
@RestController
@RequestMapping(CommentAdminController.BASE_PATH)
@RequiredArgsConstructor
public class CommentAdminController {

    public static final String BASE_PATH = "/admin/comments";
    public static final String COMMENT_ID_PATH = "/{commentId}";

    private final CommentService commentService;

    @DeleteMapping(COMMENT_ID_PATH)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@PathVariable Long commentId) {
        log.info("DELETE {}/{}", BASE_PATH, commentId);
        commentService.deleteCommentByAdmin(commentId);
    }
}