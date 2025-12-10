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

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    private Map<Long, Long> getViews(List<Event> events) {
        if (events == null || events.isEmpty()) {
            return Map.of();
        }

        List<String> uriList = events.stream()
                .map(e -> "/events/" + e.getId())
                .toList();

        StatsParamDto statsParamDto = new StatsParamDto();
        // Используем широкий диапазон от начала времени до будущего
        statsParamDto.setStart(LocalDateTime.of(2000, 1, 1, 0, 0));
        statsParamDto.setEnd(LocalDateTime.now().plusYears(10));
        statsParamDto.setUris(uriList);
        statsParamDto.setIsUnique(false);

        try {
            List<ViewStatsDto> viewStatsDtoList = statClient.getStats(statsParamDto);
            return viewStatsDtoList.stream()
                    .collect(Collectors.toMap(
                            dto -> Long.parseLong(dto.getUri().substring(dto.getUri().lastIndexOf('/') + 1)),
                            ViewStatsDto::getHits
                    ));
        } catch (Exception e) {
            log.error("Ошибка при получении статистики просмотров: {}", e.getMessage());
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

    @Transactional
    public EventFullDto create(Long userId, NewEventDto newEventDto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(String.format("Пользователь с id=%s не найден", userId)));
        if (newEventDto.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
            throw new ValidationException("Дата события должна быть минимум через 2 часа");
        }
        Event savedEvent = eventRepository.save(eventMapper.toEvent(newEventDto, user));
        return eventMapper.toFullDto(savedEvent, getRequestCount(savedEvent), getViewCount(savedEvent));
    }

    @Transactional(readOnly = true)
    public Collection<EventShortDto> getEventByUserId(EventInitiatorIdFilter eventInitiatorIdFilter,
                                                      Pageable pageable) {
        Specification<Event> spec = EventSpecification.withInitiatorId(eventInitiatorIdFilter);
        Page<Event> events = eventRepository.findAll(spec, pageable);

        Map<Long, Long> viewsMap = getViews(events.getContent());
        Map<Long, Long> requestsMap = getRequests(events.getContent());

        return events.getContent().stream()
                .map(event -> eventMapper.toShortDto(
                        event,
                        requestsMap.getOrDefault(event.getId(), 0L),
                        viewsMap.getOrDefault(event.getId(), 0L)
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public EventFullDto getEventFullDescription(Long userId, Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException(String.format("Событие с id=%s не найдено", eventId)));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(String.format("Пользователь с id=%s не найден", userId)));
        if (!Objects.equals(event.getInitiator().getId(), userId)) {
            throw new AccessViolationException(String.format("Доступ ограничен! Пользователь userId=%s не является создателем события " +
                    "eventId=%s", userId, eventId));
        }
        return eventMapper.toFullDto(event, getRequestCount(event), getViewCount(event));
    }

    @Transactional
    public EventFullDto updateEventByCreator(Long userId, Long eventId, UpdateEventRequest updateEventRequest) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException(String.format("Событие с id=%s не найдено", eventId)));
        if (event.getState() != EventState.PENDING && event.getState() != EventState.CANCELED) {
            throw new ConflictException("Можно редактировать события только в состоянии Ожидания модерации и Отмены");
        }
        if (!event.getEventDate().isAfter(LocalDateTime.now().plusHours(2))) {
            throw new ValidationException("Разрешается редактировать события не позже, чем за 2 час до начала");
        }
        if (updateEventRequest.getEventDate() != null && updateEventRequest.getEventDate().isBefore(LocalDateTime.now())) {
            throw new ValidationException("Запрещено редактировать прошедшие события");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(String.format("Пользователь с id=%s не найден", userId)));
        if (!Objects.equals(event.getInitiator().getId(), userId)) {
            throw new AccessViolationException(String.format("Доступ ограничен! Пользователь userId=%s не является создателем события " +
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
                    .orElseThrow(() -> new NotFoundException("Категория не найдена"));
        }

        Location newLocation = null;
        if (updateEventRequest.hasLocationDto()) {
            newLocation = eventMapper.toLocation(updateEventRequest.getLocationDto());
        }
        updateEventRequest.applyTo(event, category, newLocation, newState);
        eventRepository.save(event);
        return eventMapper.toFullDto(event, getRequestCount(event), getViewCount(event));
    }

    public List<ParticipationRequestDto> checkUserEventParticipation(Long userId, Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException(String.format("Событие с id=%s не найдено", eventId)));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(String.format("Пользователь с id=%s не найден", userId)));

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
                .orElseThrow(() -> new NotFoundException(String.format("Событие с id=%s не найдено", eventId)));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(String.format("Пользователь с id=%s не найден", userId)));
        if (!Objects.equals(event.getInitiator().getId(), userId)) {
            throw new AccessViolationException(String.format("Доступ ограничен! Пользователь userId=%s не является создателем события " +
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
                .orElseThrow(() -> new NotFoundException(String.format("Событие с id=%s не найдено", eventId)));

        if (event.getState() != EventState.PENDING && event.getState() != EventState.CANCELED) {
            throw new ConflictException("Можно редактировать события только в состоянии Ожидания модерации и Отмены");
        }
        if (!event.getEventDate().isAfter(LocalDateTime.now().plusHours(1))) {
            throw new ValidationException("Разрешается редактировать события не позже, чем за 1 час до начала");
        }
        if (updateEventRequest.getEventDate() != null && updateEventRequest.getEventDate().isBefore(LocalDateTime.now())) {
            throw new ValidationException("Запрещено редактировать прошедшие события");
        }

        EventState newState = event.getState();
        if (updateEventRequest.getStateAction() != null) {
            newState =
                    StateTransitionValidator.changeState(event.getState(), updateEventRequest.getStateAction(), true);
        }
        Category category = null;
        if (updateEventRequest.hasCategory()) {
            category = categoryRepository.findById(updateEventRequest.getCategory())
                    .orElseThrow(() -> new NotFoundException("Категория не найдена"));
        }

        Location newLocation = null;
        if (updateEventRequest.hasLocationDto()) {
            newLocation = eventMapper.toLocation(updateEventRequest.getLocationDto());
        }

        updateEventRequest.applyTo(event, category, newLocation, newState);
        Event savedEvent = eventRepository.save(event);
        return eventMapper.toFullDto(event, getRequestCount(event), getViewCount(event));
    }

    @Transactional(readOnly = true)
    public List<EventFullDto> adminSearchEvents(EventAdminFilter eventAdminFilter, Pageable pageable) {
        Specification<Event> spec = EventSpecification.withAdminFilter(eventAdminFilter);
        List<Event> events = eventRepository.findAll(spec, pageable).getContent();

        Map<Long, Long> requestsMap = getRequests(events);
        log.debug("requestsMap: {}", requestsMap);
        Map<Long, Long> viewsMap = getViews(events);

        return events.stream()
                .map(event -> eventMapper.toFullDto(
                        event,
                        requestsMap.getOrDefault(event.getId(), 0L),
                        viewsMap.getOrDefault(event.getId(), 0L)
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EventFullDto> publicSearchEvents(EventPublicFilter eventPublicFilter, Pageable pageable) {
        Specification<Event> spec = EventSpecification.withPublicFilter(eventPublicFilter);
        List<Event> events = eventRepository.findAll(spec, pageable).getContent();

        if (events.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> requestsMap = getRequests(events);
        Map<Long, Long> viewsMap = getViews(events);

        return events.stream()
                .map(event -> eventMapper.toFullDto(
                        event,
                        requestsMap.getOrDefault(event.getId(), 0L),
                        viewsMap.getOrDefault(event.getId(), 0L)
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public EventFullDto getEvent(Long eventId) {
        Event event = eventRepository.findByIdAndState(eventId, EventState.PUBLISHED)
                .orElseThrow(() -> new NotFoundException(String.format("Событие с id=%s не найдено", eventId)));
        return eventMapper.toFullDto(event, getRequestCount(event), getViewCount(event));
    }
}