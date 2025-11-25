package ru.practicum.statserver.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.practicum.dto.EndpointHitDto;
import ru.practicum.dto.ResponseDto;
import ru.practicum.dto.ViewStatsDto;
import ru.practicum.statserver.exception.IllegalArgumentException;
import ru.practicum.statserver.server.StatService;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@RestController
public class StatController {
    private final StatService statService;

    @PostMapping("/hit")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseDto create(@Valid @RequestBody EndpointHitDto endpointHitDto) {
        statService.create(endpointHitDto);
        return new ResponseDto(
                201,
                "Информация сохранена"
        );
    }

    @GetMapping("/stats")
    public Collection<ViewStatsDto> getStat(@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
                                            @RequestParam(name = "start")
                                            LocalDateTime start,
                                            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
                                            @RequestParam(name = "end")
                                            LocalDateTime end,
                                            @RequestParam(name = "uris", required = false)
                                            List<String> uris,
                                            @RequestParam(name = "unique", defaultValue = "false", required = false)
                                            Boolean unique) {
        log.info("Запрос статистики: start={}, end={}, uris={}, unique={}",
                start, end, uris, unique);
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("Start не может быть позже End.");
        }
        return statService.getStat(start, end, uris, unique);
    }
}


