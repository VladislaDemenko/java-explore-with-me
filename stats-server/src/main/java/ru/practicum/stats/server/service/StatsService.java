package ru.practicum.stats.server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.stats.dto.EndpointHit;
import ru.practicum.stats.dto.ViewStats;
import ru.practicum.stats.server.model.Hit;
import ru.practicum.stats.server.repository.HitRepository;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatsService {

    private final HitRepository hitRepository;

    @Transactional
    public void saveHit(EndpointHit endpointHit) {
        log.info("Saving hit: app={}, uri={}, ip={}",
                endpointHit.getApp(), endpointHit.getUri(), endpointHit.getIp());

        Hit hit = Hit.builder()
                .app(endpointHit.getApp())
                .uri(endpointHit.getUri())
                .ip(endpointHit.getIp())
                .timestamp(endpointHit.getTimestamp())
                .build();

        hitRepository.save(hit);
    }

    @Transactional(readOnly = true)
    public List<ViewStats> getStats(LocalDateTime start, LocalDateTime end,
                                    List<String> uris, boolean unique) {
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("Start date must be before end date");
        }

        log.info("Getting stats: start={}, end={}, uris={}, unique={}",
                start, end, uris, unique);

        if (unique) {
            return hitRepository.getStatsUnique(start, end, uris);
        } else {
            return hitRepository.getStats(start, end, uris);
        }
    }
}