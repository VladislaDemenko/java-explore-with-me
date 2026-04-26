package ru.practicum.main.event.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.main.category.dto.CategoryDto;
import ru.practicum.main.category.model.Category;
import ru.practicum.main.category.repository.CategoryRepository;
import ru.practicum.main.event.dto.*;
import ru.practicum.main.event.model.Event;
import ru.practicum.main.event.model.EventState;
import ru.practicum.main.event.model.Location;
import ru.practicum.main.event.repository.EventRepository;
import ru.practicum.main.exception.ConflictException;
import ru.practicum.main.exception.NotFoundException;
import ru.practicum.main.exception.ValidationException;
import ru.practicum.main.request.model.RequestStatus;
import ru.practicum.main.request.repository.RequestRepository;
import ru.practicum.main.user.dto.UserShortDto;
import ru.practicum.main.user.model.User;
import ru.practicum.main.user.repository.UserRepository;
import ru.practicum.stats.client.StatsClient;
import ru.practicum.stats.dto.EndpointHit;
import ru.practicum.stats.dto.ViewStats;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
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
        getUserById(userId);
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
            throw new ValidationException("Field: eventDate. Error: должно содержать дату, которая еще не наступила. Value: " + newEventDto.getEventDate());
        }

        if (newEventDto.getParticipantLimit() != null && newEventDto.getParticipantLimit() < 0) {
            throw new ValidationException("Field: participantLimit. Error: must be greater than or equal to 0. Value: " + newEventDto.getParticipantLimit());
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

        validateUpdateFields(request);

        if (request.getEventDate() != null) {
            LocalDateTime newDate = LocalDateTime.parse(request.getEventDate(), FORMATTER);
            if (newDate.isBefore(LocalDateTime.now().plusHours(2))) {
                throw new ValidationException("Field: eventDate. Error: должно содержать дату, которая еще не наступила. Value: " + request.getEventDate());
            }
            event.setEventDate(newDate);
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

        validateUpdateFields(request);

        if (request.getEventDate() != null) {
            LocalDateTime newDate = LocalDateTime.parse(request.getEventDate(), FORMATTER);
            if (newDate.isBefore(LocalDateTime.now().plusHours(1))) {
                throw new ValidationException("Field: eventDate. Error: должно содержать дату, которая еще не наступила. Value: " + request.getEventDate());
            }
            event.setEventDate(newDate);
        }

        updateEventFields(event, request);

        if (request.getStateAction() != null) {
            if (request.getStateAction().equals("PUBLISH_EVENT")) {
                if (event.getState() != EventState.PENDING) {
                    throw new ConflictException("Cannot publish the event because it's not in the right state: " + event.getState());
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

        List<Event> events = eventRepository.findAll().stream()
                .filter(event -> event.getState() == EventState.PUBLISHED)
                .filter(event -> categories == null || categories.isEmpty() || categories.contains(event.getCategory().getId()))
                .filter(event -> paid == null || event.getPaid().equals(paid))
                .filter(event -> text == null || text.isEmpty() ||
                        event.getAnnotation().toLowerCase().contains(text.toLowerCase()) ||
                        (event.getDescription() != null && event.getDescription().toLowerCase().contains(text.toLowerCase())))
                .filter(event -> rangeStart == null ||
                        event.getEventDate().isAfter(rangeStart) ||
                        event.getEventDate().isEqual(rangeStart))
                .filter(event -> rangeEnd == null ||
                        event.getEventDate().isBefore(rangeEnd) ||
                        event.getEventDate().isEqual(rangeEnd))
                .filter(event -> !onlyAvailable || event.getParticipantLimit() == 0 ||
                        requestRepository.countByEventIdAndStatus(event.getId(), RequestStatus.CONFIRMED) < event.getParticipantLimit())
                .collect(Collectors.toList());

        if ("VIEWS".equals(sort)) {
            events.sort(Comparator.comparing(this::getViews).reversed());
        } else {
            events.sort(Comparator.comparing(Event::getEventDate));
        }

        return events.stream()
                .skip(from)
                .limit(size)
                .map(this::toEventShortDto)
                .collect(Collectors.toList());
    }

    public EventFullDto getPublishedEvent(Long eventId, String remoteAddr, String requestURI) {
        EndpointHit hit = EndpointHit.builder()
                .app("ewm-main-service")
                .uri("/events/" + eventId)
                .ip(remoteAddr)
                .timestamp(LocalDateTime.now())
                .build();
        statsClient.sendHit(hit);

        Event event = getEventById(eventId);
        if (event.getState() != EventState.PUBLISHED) {
            throw new NotFoundException("Event with id=" + eventId + " was not found");
        }

        EventFullDto fullDto = toEventFullDto(event);
        fullDto.setViews(getViews(event) + 1);

        return fullDto;
    }

    private void validateUpdateFields(Object request) {
        if (request instanceof UpdateEventUserRequest) {
            UpdateEventUserRequest req = (UpdateEventUserRequest) request;
            validateStringLength(req.getAnnotation(), 20, 2000, "annotation");
            validateStringLength(req.getDescription(), 20, 7000, "description");
            validateStringLength(req.getTitle(), 3, 120, "title");
            if (req.getParticipantLimit() != null && req.getParticipantLimit() < 0) {
                throw new ValidationException("Field: participantLimit. Error: must be greater than or equal to 0. Value: " + req.getParticipantLimit());
            }
        } else if (request instanceof UpdateEventAdminRequest) {
            UpdateEventAdminRequest req = (UpdateEventAdminRequest) request;
            validateStringLength(req.getAnnotation(), 20, 2000, "annotation");
            validateStringLength(req.getDescription(), 20, 7000, "description");
            validateStringLength(req.getTitle(), 3, 120, "title");
            if (req.getParticipantLimit() != null && req.getParticipantLimit() < 0) {
                throw new ValidationException("Field: participantLimit. Error: must be greater than or equal to 0. Value: " + req.getParticipantLimit());
            }
        }
    }

    private void validateStringLength(String value, int min, int max, String fieldName) {
        if (value != null) {
            if (value.length() < min || value.length() > max) {
                throw new ValidationException("Field: " + fieldName + ". Error: length must be between " + min + " and " + max + ". Value: " + value);
            }
        }
    }

    private void updateEventFields(Event event, Object request) {
        if (request instanceof UpdateEventUserRequest) {
            UpdateEventUserRequest req = (UpdateEventUserRequest) request;
            if (req.getAnnotation() != null) event.setAnnotation(req.getAnnotation());
            if (req.getCategory() != null) event.setCategory(getCategoryById(req.getCategory()));
            if (req.getDescription() != null) event.setDescription(req.getDescription());
            if (req.getLocation() != null) event.setLocation(toLocation(req.getLocation()));
            if (req.getPaid() != null) event.setPaid(req.getPaid());
            if (req.getParticipantLimit() != null) event.setParticipantLimit(req.getParticipantLimit());
            if (req.getRequestModeration() != null) event.setRequestModeration(req.getRequestModeration());
            if (req.getTitle() != null) event.setTitle(req.getTitle());
        } else if (request instanceof UpdateEventAdminRequest) {
            UpdateEventAdminRequest req = (UpdateEventAdminRequest) request;
            if (req.getAnnotation() != null) event.setAnnotation(req.getAnnotation());
            if (req.getCategory() != null) event.setCategory(getCategoryById(req.getCategory()));
            if (req.getDescription() != null) event.setDescription(req.getDescription());
            if (req.getLocation() != null) event.setLocation(toLocation(req.getLocation()));
            if (req.getPaid() != null) event.setPaid(req.getPaid());
            if (req.getParticipantLimit() != null) event.setParticipantLimit(req.getParticipantLimit());
            if (req.getRequestModeration() != null) event.setRequestModeration(req.getRequestModeration());
            if (req.getTitle() != null) event.setTitle(req.getTitle());
        }
    }

    private EventFullDto toEventFullDto(Event event) {
        long confirmedRequests = requestRepository.countByEventIdAndStatus(event.getId(), RequestStatus.CONFIRMED);
        long views = getViews(event);

        return EventFullDto.builder()
                .id(event.getId())
                .annotation(event.getAnnotation())
                .category(new CategoryDto(event.getCategory().getId(), event.getCategory().getName()))
                .confirmedRequests(confirmedRequests)
                .createdOn(event.getCreatedOn())
                .description(event.getDescription())
                .eventDate(event.getEventDate())
                .initiator(new UserShortDto(event.getInitiator().getId(), event.getInitiator().getName()))
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
                .category(new CategoryDto(event.getCategory().getId(), event.getCategory().getName()))
                .confirmedRequests(confirmedRequests)
                .eventDate(event.getEventDate())
                .initiator(new UserShortDto(event.getInitiator().getId(), event.getInitiator().getName()))
                .paid(event.getPaid())
                .title(event.getTitle())
                .views(views)
                .build();
    }

    private long getViews(Event event) {
        List<ViewStats> stats = statsClient.getStats(
                event.getCreatedOn() != null ? event.getCreatedOn() : LocalDateTime.now().minusYears(1),
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