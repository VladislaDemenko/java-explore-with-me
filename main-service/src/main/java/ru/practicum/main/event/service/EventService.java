package ru.practicum.main.event.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.main.category.model.Category;
import ru.practicum.main.category.repository.CategoryRepository;
import ru.practicum.main.event.dto.*;
import ru.practicum.main.event.model.Event;
import ru.practicum.main.event.model.EventState;
import ru.practicum.main.event.model.Location;
import ru.practicum.main.event.repository.EventRepository;
import ru.practicum.main.exception.ConflictException;
import ru.practicum.main.exception.NotFoundException;
import ru.practicum.main.request.model.ParticipationRequest;
import ru.practicum.main.request.model.RequestStatus;
import ru.practicum.main.request.repository.RequestRepository;
import ru.practicum.main.user.model.User;
import ru.practicum.main.user.repository.UserRepository;
import ru.practicum.stats.client.StatsClient;
import ru.practicum.stats.dto.EndpointHit;
import ru.practicum.stats.dto.ViewStats;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventService {

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final RequestRepository requestRepository;
    private final StatsClient statsClient;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public List<EventShortDto> getEventsByUser(Long userId, int from, int size) {
        log.info("Getting events by user {}", userId);
        PageRequest pageRequest = PageRequest.of(from / size, size);

        return eventRepository.findByInitiatorId(userId, pageRequest).stream()
                .map(this::toEventShortDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public EventFullDto createEvent(Long userId, NewEventDto newEventDto) {
        log.info("Creating event by user {}: {}", userId, newEventDto);

        User initiator = getUserById(userId);
        Category category = getCategoryById(newEventDto.getCategory());

        LocalDateTime eventDate = LocalDateTime.parse(newEventDto.getEventDate(), FORMATTER);
        if (eventDate.isBefore(LocalDateTime.now().plusHours(2))) {
            throw new ConflictException("Field: eventDate. Error: должно содержать дату, которая еще не наступила.");
        }

        Event event = Event.builder()
                .annotation(newEventDto.getAnnotation())
                .category(category)
                .description(newEventDto.getDescription())
                .eventDate(eventDate)
                .initiator(initiator)
                .location(toLocation(newEventDto.getLocation()))
                .paid(newEventDto.getPaid() != null ? newEventDto.getPaid() : false)
                .participantLimit(newEventDto.getParticipantLimit() != null ? newEventDto.getParticipantLimit() : 0)
                .requestModeration(newEventDto.getRequestModeration() != null ? newEventDto.getRequestModeration() : true)
                .title(newEventDto.getTitle())
                .createdOn(LocalDateTime.now())
                .state(EventState.PENDING)
                .build();

        event = eventRepository.save(event);
        return toEventFullDto(event);
    }

    public EventFullDto getEventByUser(Long userId, Long eventId) {
        log.info("Getting event {} by user {}", eventId, userId);
        Event event = getEventByIdAndInitiator(eventId, userId);
        return toEventFullDto(event);
    }

    @Transactional
    public EventFullDto updateEventByUser(Long userId, Long eventId, UpdateEventUserRequest request) {
        log.info("Updating event {} by user {}", eventId, userId);

        Event event = getEventByIdAndInitiator(eventId, userId);

        if (event.getState() == EventState.PUBLISHED) {
            throw new ConflictException("Only pending or canceled events can be changed");
        }

        updateEventFields(event, request);

        if (request.getStateAction() != null) {
            if (request.getStateAction().equals("SEND_TO_REVIEW")) {
                event.setState(EventState.PENDING);
            } else if (request.getStateAction().equals("CANCEL_REVIEW")) {
                event.setState(EventState.CANCELED);
            }
        }

        event = eventRepository.save(event);
        return toEventFullDto(event);
    }

    public List<EventFullDto> getEventsByAdmin(List<Long> users, List<EventState> states,
                                               List<Long> categories, LocalDateTime rangeStart,
                                               LocalDateTime rangeEnd, int from, int size) {
        log.info("Getting events by admin");
        PageRequest pageRequest = PageRequest.of(from / size, size);

        return eventRepository.findEventsByAdmin(users, states, categories, rangeStart, rangeEnd, pageRequest)
                .stream()
                .map(this::toEventFullDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public EventFullDto updateEventByAdmin(Long eventId, UpdateEventAdminRequest request) {
        log.info("Updating event {} by admin", eventId);

        Event event = getEventById(eventId);

        updateEventFields(event, request);

        if (request.getStateAction() != null) {
            if (request.getStateAction().equals("PUBLISH_EVENT")) {
                if (event.getState() != EventState.PENDING) {
                    throw new ConflictException("Cannot publish the event because it's not in the right state: " + event.getState());
                }
                if (event.getEventDate().isBefore(LocalDateTime.now().plusHours(1))) {
                    throw new ConflictException("The event date must be at least 1 hour from now");
                }
                event.setState(EventState.PUBLISHED);
                event.setPublishedOn(LocalDateTime.now());
            } else if (request.getStateAction().equals("REJECT_EVENT")) {
                if (event.getState() == EventState.PUBLISHED) {
                    throw new ConflictException("Cannot reject published event");
                }
                event.setState(EventState.CANCELED);
            }
        }

        event = eventRepository.save(event);
        return toEventFullDto(event);
    }

    public List<EventShortDto> getPublishedEvents(String text, List<Long> categories, Boolean paid,
                                                  LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                                  boolean onlyAvailable, String sort, int from, int size,
                                                  String remoteAddr, String requestURI) {
        EndpointHit hit = EndpointHit.builder()
                .app("ewm-main-service")
                .uri(requestURI)
                .ip(remoteAddr)
                .timestamp(LocalDateTime.now())
                .build();
        statsClient.sendHit(hit);

        PageRequest pageRequest = PageRequest.of(from / size, size);
        return List.of();
    }

    public EventFullDto getPublishedEvent(Long eventId, String remoteAddr, String requestURI) {
        EndpointHit hit = EndpointHit.builder()
                .app("ewm-main-service")
                .uri(requestURI)
                .ip(remoteAddr)
                .timestamp(LocalDateTime.now())
                .build();
        statsClient.sendHit(hit);

        Event event = getEventById(eventId);
        if (event.getState() != EventState.PUBLISHED) {
            throw new NotFoundException("Event with id=" + eventId + " was not found");
        }

        return toEventFullDto(event);
    }

    private void updateEventFields(Event event, Object request) {
        if (request instanceof UpdateEventUserRequest) {
            UpdateEventUserRequest userRequest = (UpdateEventUserRequest) request;
            if (userRequest.getAnnotation() != null) event.setAnnotation(userRequest.getAnnotation());
            if (userRequest.getCategory() != null) event.setCategory(getCategoryById(userRequest.getCategory()));
            if (userRequest.getDescription() != null) event.setDescription(userRequest.getDescription());
            if (userRequest.getEventDate() != null) {
                LocalDateTime newDate = LocalDateTime.parse(userRequest.getEventDate(), FORMATTER);
                if (newDate.isBefore(LocalDateTime.now().plusHours(2))) {
                    throw new ConflictException("Event date must be at least 2 hours from now");
                }
                event.setEventDate(newDate);
            }
            if (userRequest.getLocation() != null) event.setLocation(toLocation(userRequest.getLocation()));
            if (userRequest.getPaid() != null) event.setPaid(userRequest.getPaid());
            if (userRequest.getParticipantLimit() != null) event.setParticipantLimit(userRequest.getParticipantLimit());
            if (userRequest.getRequestModeration() != null) event.setRequestModeration(userRequest.getRequestModeration());
            if (userRequest.getTitle() != null) event.setTitle(userRequest.getTitle());
        } else if (request instanceof UpdateEventAdminRequest) {
            UpdateEventAdminRequest adminRequest = (UpdateEventAdminRequest) request;
            if (adminRequest.getAnnotation() != null) event.setAnnotation(adminRequest.getAnnotation());
            if (adminRequest.getCategory() != null) event.setCategory(getCategoryById(adminRequest.getCategory()));
            if (adminRequest.getDescription() != null) event.setDescription(adminRequest.getDescription());
            if (adminRequest.getEventDate() != null) {
                event.setEventDate(LocalDateTime.parse(adminRequest.getEventDate(), FORMATTER));
            }
            if (adminRequest.getLocation() != null) event.setLocation(toLocation(adminRequest.getLocation()));
            if (adminRequest.getPaid() != null) event.setPaid(adminRequest.getPaid());
            if (adminRequest.getParticipantLimit() != null) event.setParticipantLimit(adminRequest.getParticipantLimit());
            if (adminRequest.getRequestModeration() != null) event.setRequestModeration(adminRequest.getRequestModeration());
            if (adminRequest.getTitle() != null) event.setTitle(adminRequest.getTitle());
        }
    }

    private EventFullDto toEventFullDto(Event event) {
        long confirmedRequests = requestRepository.countByEventIdAndStatus(event.getId(), RequestStatus.CONFIRMED);
        long views = getViews(event);

        return EventFullDto.builder()
                .id(event.getId())
                .annotation(event.getAnnotation())
                .category(new ru.practicum.main.category.dto.CategoryDto(event.getCategory().getId(), event.getCategory().getName()))
                .confirmedRequests(confirmedRequests)
                .createdOn(event.getCreatedOn())
                .description(event.getDescription())
                .eventDate(event.getEventDate())
                .initiator(new ru.practicum.main.user.dto.UserShortDto(event.getInitiator().getId(), event.getInitiator().getName()))
                .location(new LocationDto(event.getLocation().getLat(), event.getLocation().getLon()))
                .paid(event.getPaid())
                .participantLimit(event.getParticipantLimit())
                .publishedOn(event.getPublishedOn())
                .requestModeration(event.getRequestModeration())
                .state(event.getState().name())
                .title(event.getTitle())
                .views(views)
                .build();
    }

    private EventShortDto toEventShortDto(Event event) {
        long confirmedRequests = requestRepository.countByEventIdAndStatus(event.getId(), RequestStatus.CONFIRMED);
        long views = getViews(event);

        return EventShortDto.builder()
                .id(event.getId())
                .annotation(event.getAnnotation())
                .category(new ru.practicum.main.category.dto.CategoryDto(event.getCategory().getId(), event.getCategory().getName()))
                .confirmedRequests(confirmedRequests)
                .eventDate(event.getEventDate())
                .initiator(new ru.practicum.main.user.dto.UserShortDto(event.getInitiator().getId(), event.getInitiator().getName()))
                .paid(event.getPaid())
                .title(event.getTitle())
                .views(views)
                .build();
    }

    private long getViews(Event event) {
        List<ViewStats> stats = statsClient.getStats(
                event.getCreatedOn(),
                LocalDateTime.now(),
                List.of("/events/" + event.getId()),
                true
        );
        return stats.isEmpty() ? 0 : stats.get(0).getHits();
    }

    private Location toLocation(LocationDto dto) {
        return Location.builder()
                .lat(dto.getLat())
                .lon(dto.getLon())
                .build();
    }

    private User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with id=" + userId + " was not found"));
    }

    private Category getCategoryById(Long catId) {
        return categoryRepository.findById(catId)
                .orElseThrow(() -> new NotFoundException("Category with id=" + catId + " was not found"));
    }

    private Event getEventById(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));
    }

    private Event getEventByIdAndInitiator(Long eventId, Long userId) {
        Event event = getEventById(eventId);
        if (!event.getInitiator().getId().equals(userId)) {
            throw new NotFoundException("Event with id=" + eventId + " was not found");
        }
        return event;
    }
}