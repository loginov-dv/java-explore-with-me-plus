package ru.practicum.ewm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.dto.compilation.CompilationDto;
import ru.practicum.ewm.dto.compilation.CompilationParam;
import ru.practicum.ewm.dto.compilation.NewCompilationDto;
import ru.practicum.ewm.dto.compilation.UpdateCompilationRequest;
import ru.practicum.ewm.dto.event.EventShortDto;
import ru.practicum.ewm.event.EventMapper;
import ru.practicum.ewm.event.EventRepository;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.mapper.CompilationMapper;
import ru.practicum.ewm.model.compilation.Compilation;
import ru.practicum.ewm.model.event.Event;
import ru.practicum.ewm.repository.CompilationRepository;
import ru.practicum.ewm.repository.RequestRepository;
import ru.practicum.ewm.repository.comment.CommentRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CompilationServiceImpl implements CompilationService {
    private final CompilationRepository compilationRepository;
    private final EventRepository eventRepository;
    private final RequestRepository requestRepository;
    private final EventMapper eventMapper;
    private final CommentRepository commentRepository;

    @Override
    public CompilationDto create(NewCompilationDto request) {
        log.debug("Create compilation request: {}", request);

        Set<Event> events = new HashSet<>();
        if (request.getEvents() != null && !request.getEvents().isEmpty()) {
            events = new HashSet<>(eventRepository.findAllById(request.getEvents()));
        }

        Compilation compilation = compilationRepository.save(CompilationMapper.toNewCompilation(request, events));
        log.info("Compilation created: {}", compilation);

        Set<EventShortDto> eventShortDtos = buildEventShortDtos(events);
        return CompilationMapper.toDto(compilation, eventShortDtos);
    }

    @Override
    public void deleteById(Long compId) {
        log.debug("Delete compilation with id = {}", compId);

        if (!compilationRepository.existsById(compId)) {
            log.warn("Compilation with id = {} not found", compId);
            throw new NotFoundException(String.format("Compilation with id = %d not found", compId));
        }

        compilationRepository.deleteById(compId);
        log.info("Compilation with id = {} deleted", compId);
    }

    @Override
    public CompilationDto update(Long compId, UpdateCompilationRequest request) {
        log.debug("Update compilation with id = {}: {}", compId, request);

        Compilation compilation = compilationRepository.findById(compId)
                .orElseThrow(() -> new NotFoundException(String.format("Compilation with id = %d not found", compId)));

        Set<Event> events = compilation.getEvents();
        if (request.hasEvents()) {
            if (request.getEvents() != null && !request.getEvents().isEmpty()) {
                events = new HashSet<>(eventRepository.findAllById(request.getEvents()));
            } else {
                events = new HashSet<>();
            }
        }

        CompilationMapper.updateFields(compilation, request, events);
        compilationRepository.save(compilation);
        log.info("Compilation updated: {}", compilation);

        Set<EventShortDto> eventShortDtos = buildEventShortDtos(events);
        return CompilationMapper.toDto(compilation, eventShortDtos);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CompilationDto> getCompilations(CompilationParam param) {
        log.debug("Get compilations: {}", param);

        int page = param.from() / param.size();
        Pageable pageable = PageRequest.of(page, param.size());

        List<Compilation> compilations;
        if (param.pinned() != null) {
            compilations = compilationRepository.findByPinned(param.pinned(), pageable);
        } else {
            compilations = compilationRepository.findAll(pageable).getContent();
        }

        return compilations.stream()
                .map(compilation -> {
                    Set<EventShortDto> eventShortDtos = buildEventShortDtos(compilation.getEvents());
                    return CompilationMapper.toDto(compilation, eventShortDtos);
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CompilationDto getById(Long compId) {
        log.debug("Get compilation with id = {}", compId);

        Compilation compilation = compilationRepository.findById(compId)
                .orElseThrow(() -> new NotFoundException(String.format("Compilation with id = %d not found", compId)));

        Set<EventShortDto> eventShortDtos = buildEventShortDtos(compilation.getEvents());
        return CompilationMapper.toDto(compilation, eventShortDtos);
    }

    private Set<EventShortDto> buildEventShortDtos(Set<Event> events) {
        if (events == null || events.isEmpty()) {
            return new HashSet<>();
        }

        List<Long> eventIds = events.stream()
                .map(Event::getId)
                .toList();
        Map<Long, Long> confirmedRequestsMap = requestRepository.countConfirmedRequestsByEventIds(eventIds)
                .stream()
                .collect(Collectors.toMap(
                        result -> (Long) result[0],
                        result -> (Long) result[1]
                ));
        Map<Long, Long> commentsMap = commentRepository.countByEventIdIn(events.stream()
                        .map(Event::getId).toList()).stream()
                .collect(Collectors.toMap(
                        result -> (Long) result[0],
                        result -> (Long) result[1]
                ));

        return events.stream()
                .map(event -> {
                    Long confirmedRequests = confirmedRequestsMap.getOrDefault(event.getId(), 0L);
                    Long views = 0L;
                    Long comments = commentsMap.getOrDefault(event.getId(), 0L);
                    return eventMapper.toShortDto(event, confirmedRequests, views, comments);
                })
                .collect(Collectors.toSet());
    }
}