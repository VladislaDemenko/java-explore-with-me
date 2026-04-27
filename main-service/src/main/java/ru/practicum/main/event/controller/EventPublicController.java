package ru.practicum.main.event.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.main.event.dto.EventFullDto;
import ru.practicum.main.event.dto.EventShortDto;
import ru.practicum.main.event.service.EventService;
import ru.practicum.main.exception.ValidationException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
@Validated
public class EventPublicController {

    private final EventService eventService;

    @GetMapping
    public List<EventShortDto> getEvents(
            @RequestParam(required = false) String text,
            @RequestParam(required = false) List<Long> categories,
            @RequestParam(required = false) Boolean paid,
            @RequestParam(required = false) String rangeStart,
            @RequestParam(required = false) String rangeEnd,
            @RequestParam(defaultValue = "false") boolean onlyAvailable,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") @PositiveOrZero int from,
            @RequestParam(defaultValue = "10") @Positive int size,
            HttpServletRequest request) {
        log.info("GET /events - text={}, categories={}, paid={}, rangeStart={}, rangeEnd={}, onlyAvailable={}, sort={}, from={}, size={}",
                text, categories, paid, rangeStart, rangeEnd, onlyAvailable, sort, from, size);

        if (sort != null && !sort.equals("EVENT_DATE") && !sort.equals("VIEWS")) {
            throw new ValidationException("Invalid sort parameter. Allowed values: EVENT_DATE, VIEWS");
        }

        LocalDateTime startDateTime = null;
        LocalDateTime endDateTime = null;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        if (rangeStart != null) {
            try {
                startDateTime = LocalDateTime.parse(rangeStart, formatter);
            } catch (DateTimeParseException e) {
                throw new ValidationException("Invalid rangeStart format. Expected format: yyyy-MM-dd HH:mm:ss. Value: " + rangeStart);
            }
        }

        if (rangeEnd != null) {
            try {
                endDateTime = LocalDateTime.parse(rangeEnd, formatter);
            } catch (DateTimeParseException e) {
                throw new ValidationException("Invalid rangeEnd format. Expected format: yyyy-MM-dd HH:mm:ss. Value: " + rangeEnd);
            }
        }

        if (startDateTime != null && endDateTime != null && endDateTime.isBefore(startDateTime)) {
            throw new ValidationException("Range end must be after range start");
        }

        return eventService.getPublishedEvents(text, categories, paid, startDateTime, endDateTime,
                onlyAvailable, sort, from, size,
                request.getRemoteAddr(), request.getRequestURI());
    }

    @GetMapping("/{id}")
    public EventFullDto getEvent(@PathVariable Long id, HttpServletRequest request) {
        log.info("GET /events/{}", id);
        if (id <= 0) {
            throw new ValidationException("Event id must be positive");
        }
        return eventService.getPublishedEvent(id, request.getRemoteAddr(), request.getRequestURI());
    }
}