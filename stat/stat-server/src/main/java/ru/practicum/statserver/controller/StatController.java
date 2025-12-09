package ru.practicum.statserver.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.practicum.dto.EndpointHitDto;
import ru.practicum.dto.ResponseDto;
import ru.practicum.dto.ViewStatsDto;
import ru.practicum.statserver.exception.BadRequestException;
import ru.practicum.statserver.server.StatService;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
        return new ResponseDto(201, "Информация сохранена");
    }

    @GetMapping("/stats")
    public List<ViewStatsDto> getStat(@RequestParam(name = "start") String start,
                                      @RequestParam(name = "end") String end,
                                      @RequestParam(name = "uris", required = false) List<String> uris,
                                      @RequestParam(name = "unique", defaultValue = "false", required = false) Boolean unique) {
        LocalDateTime startDate = parseDateTimeFlexibly(start);
        LocalDateTime endDate = parseDateTimeFlexibly(end);

        log.info("Getting stats from {} to {}, uris: {}, unique: {}", startDate, endDate, uris, unique);

        if (startDate.isAfter(endDate)) {
            throw new BadRequestException("Start не может быть позже End.");
        }

        List<ViewStatsDto> stats = statService.getStat(startDate, endDate, uris, unique);

        if (stats != null && stats.size() > 1) {
            List<ViewStatsDto> sortedStats = new ArrayList<>(stats);
            sortedStats.sort((s1, s2) -> Long.compare(s2.getHits(), s1.getHits()));
            return sortedStats;
        }

        return stats;
    }

    private LocalDateTime parseDateTimeFlexibly(String dateTimeStr) {
        try {
            String decoded = URLDecoder.decode(dateTimeStr, StandardCharsets.UTF_8);
            String cleaned = decoded.trim();

            if (cleaned.contains("T")) {
                return LocalDateTime.parse(cleaned, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } else {
                return LocalDateTime.parse(cleaned, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            }
        } catch (Exception e) {
            log.error("Failed to parse date: {}", dateTimeStr, e);
            throw new IllegalArgumentException("Invalid date format: " + dateTimeStr, e);
        }
    }
}