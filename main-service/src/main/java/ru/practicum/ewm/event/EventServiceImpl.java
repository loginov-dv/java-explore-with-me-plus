package ru.practicum.ewm.event;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import ru.practicum.client.StatClient;
import ru.practicum.dto.StatsParamDto;
import ru.practicum.dto.ViewStatsDto;
import ru.practicum.ewm.dto.event.*;
import ru.practicum.ewm.exception.AccessViolationException;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.exception.ValidationException;
import ru.practicum.ewm.mapper.RequestMapper;
import ru.practicum.ewm.model.category.Category;
import ru.practicum.ewm.model.event.Event;
import ru.practicum.ewm.model.event.EventState;
import ru.practicum.ewm.model.event.Location;
import ru.practicum.ewm.model.request.Request;
import ru.practicum.ewm.model.request.RequestStatus;
import ru.practicum.ewm.model.user.User;
import ru.practicum.ewm.repository.CategoryRepository;
import ru.practicum.ewm.repository.RequestRepository;
import ru.practicum.ewm.repository.UserRepository;
import ru.practicum.ewm.repository.comment.CommentRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@AllArgsConstructor
@Service
public class EventServiceImpl implements EventService {
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final EventMapper eventMapper;
    private final StatClient statClient;
    private final CategoryRepository categoryRepository;
    private final RequestRepository requestRepository;
    private final CommentRepository commentRepository;

    private Map<Long, Long> getViews(List<Event> events) {
        if (events == null || events.isEmpty()) {
            return Map.of();
        }

        List<String> uriList = events.stream()
                .map(e -> "/events/" + e.getId())
                .toList();

        StatsParamDto statsParamDto = new StatsParamDto();
        // Используем более узкий временной диапазон (не забыть)
        statsParamDto.setStart(LocalDateTime.now().minusHours(1));
        statsParamDto.setEnd(LocalDateTime.now().plusHours(1));
        statsParamDto.setUris(uriList);
        statsParamDto.setIsUnique(true);

        try {
            List<ViewStatsDto> viewStatsDtoList = statClient.getStats(statsParamDto);
            return viewStatsDtoList.stream()
                    .collect(Collectors.toMap(
                            dto -> Long.parseLong(dto.getUri().substring(dto.getUri().lastIndexOf('/') + 1)),
                            ViewStatsDto::getHits
                    ));
        } catch (Exception e) {
            log.error("Error retrieving view statistics: {}", e.getMessage());
            return Map.of();
        }
    }

    private long getViewCount(Event event) {
        Map<Long, Long> map = getViews(List.of(event));
        return map.getOrDefault(event.getId(), 0L);
    }

    private long getRequestCount(Event event) {
        Map<Long, Long> map = getRequests(List.of(event));
        return map.getOrDefault(event.getId(), 0L);
    }

    private Map<Long, Long> getRequests(List<Event> events) {
        if (events == null || events.isEmpty()) {
            return Map.of();
        }

        List<Object[]> raw = requestRepository.countConfirmedRequestsByEventIds(
                events.stream().map(Event::getId).toList()
        );
        return raw.stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]
                ));
    }

    private Map<Long, Boolean> checkAvailable(List<Event> events, Map<Long, Long> requestMap) {
        Map<Long, Boolean> availableMap = new HashMap<>();
        for (Event event : events) {
            if (event.getParticipantLimit() > 0) {
                if (requestMap.getOrDefault(event.getId(), 0L) < event.getParticipantLimit()) {
                    availableMap.put(event.getId(), true);
                } else {
                    availableMap.put(event.getId(), false);
                }
            } else {
                availableMap.put(event.getId(), true);
            }
        }
        return availableMap;
    }

    @Transactional
    public EventFullDto create(Long userId, NewEventDto newEventDto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(String.format("User id=%s not found", userId)));
        if (newEventDto.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
            throw new ValidationException("The event date must be at least 2 hours from now");
        }
        Event savedEvent = eventRepository.save(eventMapper.toEvent(newEventDto, user));
        return eventMapper.toFullDto(savedEvent, getRequestCount(savedEvent), getViewCount(savedEvent), 0L);
    }

    @Transactional(readOnly = true)
    public Collection<EventShortDto> getEventByUserId(EventInitiatorIdFilter eventInitiatorIdFilter,
                                                      Pageable pageable) {
        Specification<Event> spec = EventSpecification.withInitiatorId(eventInitiatorIdFilter);
        Page<Event> events = eventRepository.findAll(spec, pageable);

        Map<Long, Long> viewsMap = getViews(events.getContent());
        Map<Long, Long> requestsMap = getRequests(events.getContent());
        Map<Long, Long> commentsMap = commentRepository.countByEventIdIn(events.stream()
                        .map(Event::getId).toList()).stream()
                .collect(Collectors.toMap(
                        result -> (Long) result[0],
                        result -> (Long) result[1]
                ));

        return events.getContent().stream()
                .map(event -> eventMapper.toShortDto(
                        event,
                        requestsMap.getOrDefault(event.getId(), 0L),
                        viewsMap.getOrDefault(event.getId(), 0L),
                        commentsMap.getOrDefault(event.getId(), 0L)
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public EventFullDto getEventFullDescription(Long userId, Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException(String.format("Event id=%s not found", eventId)));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(String.format("User id=%s not found", userId)));
        if (!Objects.equals(event.getInitiator().getId(), userId)) {
            throw new AccessViolationException(String.format("Access denied! User userId=%s is not the creator of the event " +
                    "eventId=%s", userId, eventId));
        }

        return eventMapper.toFullDto(event, getRequestCount(event), getViewCount(event),
                commentRepository.countByEventId(eventId));
    }

    @Transactional
    public EventFullDto updateEventByCreator(Long userId, Long eventId, UpdateEventRequest updateEventRequest) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException(String.format("Event id=%s not found", eventId)));
        if (event.getState() != EventState.PENDING && event.getState() != EventState.CANCELED) {
            throw new ConflictException("Events can only be edited when in Pending Moderation or Cancelled status");
        }
        if (!event.getEventDate().isAfter(LocalDateTime.now().plusHours(2))) {
            throw new ValidationException("Editing events is allowed no later than 2 hours before they start.");
        }
        if (updateEventRequest.getEventDate() != null && updateEventRequest.getEventDate().isBefore(LocalDateTime.now())) {
            throw new ValidationException("Editing past events is prohibited");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(String.format("User id=%s not found", userId)));
        if (!Objects.equals(event.getInitiator().getId(), userId)) {
            throw new AccessViolationException(String.format("Access denied! User userId=%s is not the creator of the event " +
                    "eventId=%s", userId, eventId));
        }

        EventState newState = event.getState();
        if (updateEventRequest.getStateAction() != null) {
            newState =
                    StateTransitionValidator.changeState(event.getState(), updateEventRequest.getStateAction(), false);
        }

        Category category = null;
        if (updateEventRequest.hasCategory()) {
            category = categoryRepository.findById(updateEventRequest.getCategory())
                    .orElseThrow(() -> new NotFoundException("Category not found"));
        }

        Location newLocation = null;
        if (updateEventRequest.hasLocationDto()) {
            newLocation = eventMapper.toLocation(updateEventRequest.getLocationDto());
        }
        updateEventRequest.applyTo(event, category, newLocation, newState);
        eventRepository.save(event);

        return eventMapper.toFullDto(event, getRequestCount(event), getViewCount(event),
                commentRepository.countByEventId(eventId));
    }

    public List<ParticipationRequestDto> checkUserEventParticipation(Long userId, Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException(String.format("Event id=%s not found", eventId)));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(String.format("User id=%s not found", userId)));

        List<Request> requests = requestRepository.findByEventIdAndRequesterId(eventId, userId);
        return requests.stream()
                .map(RequestMapper::toDto)
                .toList();
    }

    public EventRequestStatusUpdateResult changeStatusRequest(@PathVariable Long userId,
                                                              @PathVariable Long eventId,
                                                              @Valid @RequestBody EventRequestStatusUpdateRequest eventRequestStatusUpdateRequest) {
        EventRequestStatusUpdateResult eventRequestStatusUpdateResult = new EventRequestStatusUpdateResult();
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException(String.format("Event id=%s not found", eventId)));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(String.format("User id=%s not found", userId)));
        if (!Objects.equals(event.getInitiator().getId(), userId)) {
            throw new AccessViolationException(String.format("Access denied! User userId=%s is not the creator of the event " +
                    "eventId=%s", userId, eventId));
        }
        List<Request> requestList = requestRepository.findAllByIdInOrderByCreated(eventRequestStatusUpdateRequest.getRequestIds());
        RuntimeException ex = null;
        for (Request request : requestList) {
            if (request.getStatus() != RequestStatus.PENDING) {
                ex = new ValidationException("Request must have status PENDING");
            }
            Long confirmedRequest = getRequestCount(event);
            if (confirmedRequest < event.getParticipantLimit()) {
                switch (eventRequestStatusUpdateRequest.getStatus()) {
                    case CONFIRMED -> request.setStatus(RequestStatus.CONFIRMED);
                    case REJECTED -> request.setStatus(RequestStatus.REJECTED);
                    default -> throw new ValidationException(
                            "Unexpected status: " + eventRequestStatusUpdateRequest.getStatus()
                    );
                }
                eventRequestStatusUpdateResult.getConfirmedRequests().add(RequestMapper.toDto(request));
            } else {
                request.setStatus(RequestStatus.CANCELED);
                eventRequestStatusUpdateResult.getRejectedRequests().add(RequestMapper.toDto(request));
                ex = (ex == null) ? new ConflictException("The participant limit has been reached") : ex;
            }
            requestRepository.save(request);
        }
        if (ex != null) throw ex;
        return eventRequestStatusUpdateResult;
    }

    @Transactional
    public EventFullDto updateEventByAdmin(Long eventId, UpdateEventRequest updateEventRequest) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException(String.format("Event id=%s not found", eventId)));

        if (event.getState() != EventState.PENDING && event.getState() != EventState.CANCELED) {
            throw new ConflictException("Events can only be edited when in Pending Moderation or Cancelled state");
        }
        if (!event.getEventDate().isAfter(LocalDateTime.now().plusHours(1))) {
            throw new ValidationException("Editing events is allowed no later than 1 hour before they start");
        }
        if (updateEventRequest.getEventDate() != null && updateEventRequest.getEventDate().isBefore(LocalDateTime.now())) {
            throw new ValidationException("Editing past events is prohibited");
        }

        EventState state = event.getState();
        if (updateEventRequest.getStateAction() != null) {
            state = StateTransitionValidator.changeState(event.getState(), updateEventRequest.getStateAction(), true);
        }

        if (event.getState() == EventState.PENDING &&  // <-- старое состояние
                state == EventState.PUBLISHED) {       // <-- новое состояние
            event.setPublishedOn(LocalDateTime.now());
        }

        Category category = null;
        if (updateEventRequest.hasCategory()) {
            category = categoryRepository.findById(updateEventRequest.getCategory())
                    .orElseThrow(() -> new NotFoundException("Category not found"));
        }

        Location newLocation = null;
        if (updateEventRequest.hasLocationDto()) {
            newLocation = eventMapper.toLocation(updateEventRequest.getLocationDto());
        }

        updateEventRequest.applyTo(event, category, newLocation, state);
        eventRepository.save(event);

        return eventMapper.toFullDto(event, getRequestCount(event), getViewCount(event),
                commentRepository.countByEventId(eventId));
    }

    @Transactional(readOnly = true)
    public List<EventFullDto> adminSearchEvents(EventAdminFilter eventAdminFilter, PageRequestDto pageRequestDto) {
        Pageable pageable = pageRequestDto.toPageable();
        Specification<Event> spec = EventSpecification.withAdminFilter(eventAdminFilter);
        List<Event> events = eventRepository.findAll(spec, pageable).getContent();

        Map<Long, Long> requestsMap = getRequests(events);
        log.debug("requestsMap: {}", requestsMap);
        Map<Long, Long> viewsMap = getViews(events);
        Map<Long, Long> commentsMap = commentRepository.countByEventIdIn(events.stream()
                        .map(Event::getId).toList()).stream()
                .collect(Collectors.toMap(
                        result -> (Long) result[0],
                        result -> (Long) result[1]
                ));

        return events.stream()
                .map(event -> eventMapper.toFullDto(
                        event,
                        requestsMap.getOrDefault(event.getId(), 0L),
                        viewsMap.getOrDefault(event.getId(), 0L),
                        commentsMap.getOrDefault(event.getId(), 0L)
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EventShortDto> publicSearchEvents(EventPublicFilter eventPublicFilter, PageRequestDto pageRequestDto) {

        Pageable pageable = pageRequestDto.toPageable();
        EventSort sort = pageRequestDto.getSort();

        boolean sorByDate = sort == EventSort.EVENT_DATE;
        boolean sortByViews = sort == EventSort.VIEWS;
        boolean noSort = sort == null;

        Specification<Event> spec = EventSpecification.withPublicFilter(eventPublicFilter);
        List<Event> events = eventRepository.findAll(spec, pageable).getContent();

        if (events.isEmpty()) {
            return List.of();
        }

        Map<Long, Long> requestsMap = getRequests(events);
        Map<Long, Long> viewsMap = getViews(events);
        Map<Long, Boolean> availableMap = checkAvailable(events, requestsMap);
        Map<Long, Long> commentsMap = commentRepository.countByEventIdIn(events.stream()
                        .map(Event::getId).toList()).stream()
                .collect(Collectors.toMap(
                        result -> (Long) result[0],
                        result -> (Long) result[1]
                ));

        if (eventPublicFilter.getOnlyAvailable() == true) {
            events = events.stream()
                    .filter(e -> availableMap.getOrDefault(e.getId(), false)).toList();
        }
        if (sortByViews) {
            events = events.stream()
                    .sorted(Comparator.comparingLong(
                            e -> viewsMap.getOrDefault(e.getId(), 0L)))
                    .toList().reversed();
        }
        return events.stream()
                .map(event -> eventMapper.toShortDto(
                        event,
                        requestsMap.getOrDefault(event.getId(), 0L),
                        viewsMap.getOrDefault(event.getId(), 0L),
                        commentsMap.getOrDefault(event.getId(), 0L)
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public EventFullDto getEvent(Long eventId) {
        Event event = eventRepository.findByIdAndState(eventId, EventState.PUBLISHED)
                .orElseThrow(() -> new NotFoundException(String.format("Event id=%s not found", eventId)));

        return eventMapper.toFullDto(event, getRequestCount(event), getViewCount(event),
                commentRepository.countByEventId(eventId));
    }
}