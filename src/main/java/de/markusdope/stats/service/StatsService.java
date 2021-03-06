package de.markusdope.stats.service;

import de.markusdope.stats.data.document.MatchDocument;
import de.markusdope.stats.data.dto.LolRecordsDTO;
import de.markusdope.stats.data.dto.PlayerStats;
import de.markusdope.stats.data.repository.MatchPlayerRepository;
import de.markusdope.stats.data.repository.MatchRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple4;
import reactor.util.function.Tuples;

import java.util.Collections;
import java.util.stream.Collectors;

@Service
public class StatsService {

    @Autowired
    private MatchPlayerRepository matchPlayerRepository;

    @Autowired
    private MatchRepository matchRepository;

    public Flux<PlayerStats> getPlayerStats(Integer season) {
        return
                matchPlayerRepository
                        .findAll()
                        .flatMap(
                                matchPlayer ->
                                        matchRepository
                                                .findById(matchPlayer.getId())
                                                //Handle season == 0 as all season combined
                                                .filter(matchDocument -> season == 0 || matchDocument.getSeason().equals(season))
                                                .map(MatchDocument::getMatch)
                                                .flatMapIterable(match -> match.getParticipants().stream().map(participant -> Tuples.of(matchPlayer.getParticipant(participant.getParticipantId()), participant, participant.getTeam() == match.getBlueTeam().getTeamId() ? match.getBlueTeam() : match.getRedTeam(), match)).collect(Collectors.toSet()))
                        )
                        .groupBy(Tuple4::getT1, tuple4 -> Tuples.of(tuple4.getT2(), tuple4.getT3(), tuple4.getT4()))
                        .parallel()
                        .runOn(Schedulers.parallel())
                        .flatMap(
                                groupedFlux ->
                                        groupedFlux
                                                .map(PlayerStats::new)
                                                .reduce(PlayerStats::combine)
                                                .doOnNext(
                                                        playerStats ->
                                                                playerStats.setPlayerName(groupedFlux.key())
                                                )
                        )
                        .sequential();
    }

    public Mono<LolRecordsDTO> getRecords(Integer season) {
        return matchPlayerRepository
                .findAll()
                .flatMap(
                        matchPlayer ->
                                matchRepository
                                        .findById(matchPlayer.getId())
                                        //Handle season == 0 as all season combined
                                        .filter(matchDocument -> season == 0 || matchDocument.getSeason().equals(season))
                                        .map(match -> Tuples.of(match, matchPlayer))
                )
                .parallel()
                .runOn(Schedulers.parallel())
                .map(matchTuple -> LolRecordsDTO.ofMatchDocument(matchTuple.getT1(), matchTuple.getT2()))
                .reduce(LolRecordsDTO::combine)
                .switchIfEmpty(Mono.defer(() -> {
                    LolRecordsDTO lolRecordsDTO = new LolRecordsDTO();
                    lolRecordsDTO.setRecords(Collections.emptyMap());
                    return Mono.just(lolRecordsDTO);
                }));
    }

}
