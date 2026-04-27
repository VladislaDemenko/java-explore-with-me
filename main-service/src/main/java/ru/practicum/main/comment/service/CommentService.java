package ru.practicum.main.comment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.main.comment.dto.CommentDto;
import ru.practicum.main.comment.dto.NewCommentDto;
import ru.practicum.main.comment.dto.UpdateCommentDto;
import ru.practicum.main.comment.model.Comment;
import ru.practicum.main.comment.repository.CommentRepository;
import ru.practicum.main.event.model.Event;
import ru.practicum.main.event.model.EventState;
import ru.practicum.main.event.repository.EventRepository;
import ru.practicum.main.exception.ConflictException;
import ru.practicum.main.exception.NotFoundException;
import ru.practicum.main.user.model.User;
import ru.practicum.main.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    private final CommentRepository commentRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;

    public List<CommentDto> getEventComments(Long eventId, int from, int size) {
        log.info("Getting comments for event {}", eventId);
        getEventById(eventId);

        PageRequest pageRequest = PageRequest.of(from / size, size);
        return commentRepository.findByEventIdOrderByCreatedOnDesc(eventId, pageRequest)
                .stream()
                .map(this::toCommentDto)
                .collect(Collectors.toList());
    }

    public CommentDto getComment(Long commentId) {
        log.info("Getting comment {}", commentId);
        Comment comment = getCommentById(commentId);
        return toCommentDto(comment);
    }

    public List<CommentDto> getUserComments(Long userId, int from, int size) {
        log.info("Getting comments for user {}", userId);
        getUserById(userId);

        PageRequest pageRequest = PageRequest.of(from / size, size);
        return commentRepository.findByAuthorIdOrderByCreatedOnDesc(userId, pageRequest)
                .stream()
                .map(this::toCommentDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public CommentDto createComment(Long userId, Long eventId, NewCommentDto newCommentDto) {
        log.info("Creating comment for event {} by user {}", eventId, userId);

        User author = getUserById(userId);
        Event event = getEventById(eventId);

        if (event.getState() != EventState.PUBLISHED) {
            throw new ConflictException("Cannot comment on unpublished event");
        }

        Comment comment = Comment.builder()
                .text(newCommentDto.getText())
                .event(event)
                .author(author)
                .createdOn(LocalDateTime.now())
                .isEdited(false)
                .build();

        comment = commentRepository.save(comment);
        return toCommentDto(comment);
    }

    @Transactional
    public CommentDto updateComment(Long userId, Long commentId, UpdateCommentDto updateCommentDto) {
        log.info("Updating comment {} by user {}", commentId, userId);

        Comment comment = getCommentById(commentId);

        if (!comment.getAuthor().getId().equals(userId)) {
            throw new ConflictException("Only author can edit comment");
        }

        if (updateCommentDto.getText() != null) {
            comment.setText(updateCommentDto.getText());
            comment.setEditedOn(LocalDateTime.now());
            comment.setIsEdited(true);
        }

        comment = commentRepository.save(comment);
        return toCommentDto(comment);
    }

    @Transactional
    public void deleteComment(Long userId, Long commentId) {
        log.info("Deleting comment {} by user {}", commentId, userId);

        Comment comment = getCommentById(commentId);

        if (!comment.getAuthor().getId().equals(userId)) {
            throw new ConflictException("Only author can delete comment");
        }

        commentRepository.delete(comment);
    }

    @Transactional
    public void deleteCommentByAdmin(Long commentId) {
        log.info("Deleting comment {} by admin", commentId);
        getCommentById(commentId);
        commentRepository.deleteById(commentId);
    }

    private CommentDto toCommentDto(Comment comment) {
        return CommentDto.builder()
                .id(comment.getId())
                .text(comment.getText())
                .eventId(comment.getEvent().getId())
                .authorId(comment.getAuthor().getId())
                .authorName(comment.getAuthor().getName())
                .createdOn(comment.getCreatedOn())
                .editedOn(comment.getEditedOn())
                .isEdited(comment.getIsEdited())
                .build();
    }

    private Comment getCommentById(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("Comment with id=" + commentId + " was not found"));
    }

    private Event getEventById(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));
    }

    private User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with id=" + userId + " was not found"));
    }
}