package de.markusdope.stats.data.dto;

import com.merakianalytics.orianna.Orianna;
import com.merakianalytics.orianna.types.data.match.Event;
import com.merakianalytics.orianna.types.data.match.Match;
import com.merakianalytics.orianna.types.data.match.Participant;
import de.markusdope.stats.data.document.MatchDocument;
import de.markusdope.stats.data.document.MatchPlayer;
import de.markusdope.stats.data.dto.record.KDA;
import de.markusdope.stats.data.dto.record.TimeRecord;
import de.markusdope.stats.data.dto.recordTypes.PlayerRecord;
import lombok.Data;

import java.time.Duration;
import java.util.*;
import java.util.function.Function;

@Data
public class LolRecordsDTO {

    private Map<String, Set<LolRecord>> records;

    public static LolRecordsDTO ofMatchDocument(MatchDocument matchDocument, MatchPlayer matchPlayer) {
        Match match = matchDocument.getMatch();
        LolRecordsDTO lolRecordsDTO = new LolRecordsDTO();
        Map<String, Set<LolRecord>> records = new LinkedHashMap<>();

        records.put("kills", LolRecordsDTO.createParticipantRecord(participant -> participant.getStats().getKills(), match, matchPlayer, false));
        records.put("deaths", LolRecordsDTO.createParticipantRecord(participant -> participant.getStats().getDeaths(), match, matchPlayer, false));
        records.put("assists", LolRecordsDTO.createParticipantRecord(participant -> participant.getStats().getAssists(), match, matchPlayer, false));
        records.put("kda", LolRecordsDTO.createParticipantRecord(participant -> new KDA(participant.getStats().getKills(), participant.getStats().getDeaths(), participant.getStats().getAssists()), match, matchPlayer, false));
        records.put("gold", LolRecordsDTO.createParticipantRecord(participant -> participant.getStats().getGoldEarned(), match, matchPlayer, false));
        records.put("cs", LolRecordsDTO.createParticipantRecord(participant -> participant.getStats().getCreepScore(), match, matchPlayer, false));
        records.put("visionScore", LolRecordsDTO.createParticipantRecord(participant -> participant.getStats().getVisionScore(), match, matchPlayer, false));

        Optional<Event> first_champion_kill_opt = Arrays.stream(matchDocument.getTimeline()).flatMap(Collection::stream).filter(event -> event.getType().equals("CHAMPION_KILL")).min(Comparator.comparing(Event::getTimestamp));

        if (first_champion_kill_opt.isPresent()) {
            Event first_champion_kill = first_champion_kill_opt.get();
            Participant victim = getParticipant(match, first_champion_kill.getVictimId());
            Participant killer = getParticipant(match, first_champion_kill.getKillerId());


            records.put("earlyKill",
                    Collections.singleton(
                            new PlayerRecord<TimeRecord>(
                                    new TimeRecord(Duration.ofMillis(first_champion_kill.getTimestamp().getMillis())),
                                    matchPlayer.getParticipant(killer.getParticipantId()),
                                    killer.getLane(),
                                    killer.getChampionId(),
                                    Orianna.championWithId(killer.getChampionId()).get().getName(),
                                    match.getId(),
                                    true
                            )
                    )
            );

            records.put("earlyDeath",
                    Collections.singleton(
                            new PlayerRecord<TimeRecord>(
                                    new TimeRecord(Duration.ofMillis(first_champion_kill.getTimestamp().getMillis())),
                                    matchPlayer.getParticipant(victim.getParticipantId()),
                                    victim.getLane(),
                                    victim.getChampionId(),
                                    Orianna.championWithId(victim.getChampionId()).get().getName(),
                                    match.getId(),
                                    true
                            )
                    )
            );
        }

        lolRecordsDTO.setRecords(records);
        return lolRecordsDTO;
    }

    public static LolRecordsDTO combine(LolRecordsDTO e1, LolRecordsDTO e2) {
        LolRecordsDTO lolRecordsDTO = new LolRecordsDTO();
        Map<String, Set<LolRecord>> records = new LinkedHashMap<>();

        e1.getRecords().forEach(
                (key, lolRecord) -> {
                    records.put(key, LolRecordsDTO.getMaxSet(lolRecord, e2.getRecords().get(key)));
                    e2.getRecords().remove(key);
                }
        );
        //In case e2 contains record entries which e1 doesnt contain
        e2.getRecords().forEach(
                (key, lolRecord) -> {
                    records.put(key, LolRecordsDTO.getMaxSet(lolRecord, e1.getRecords().get(key)));
                }
        );

        lolRecordsDTO.setRecords(records);
        return lolRecordsDTO;
    }

    private static Participant getParticipant(Match match, int participantId) {
        return match.getParticipants().stream().filter(participant -> participant.getParticipantId() == participantId).findFirst().get();
    }

    private static <R extends LolRecord> Set<LolRecord> getMaxSet(Set<R> e1, Set<R> e2) {
        int comparison = e1.iterator().next().compareTo(e2.iterator().next());
        Set<LolRecord> r = new HashSet<>();
        if (comparison == 0) {
            r.addAll(e1);
            r.addAll(e2);
            return r;
        } else if (comparison > 0) {
            r.addAll(e1);
            return r;
        } else {
            r.addAll(e2);
            return r;
        }
    }

    private static <T extends Comparable<T>> Set<LolRecord> createParticipantRecord(Function<Participant, T> participantComparableFunction, Match match, MatchPlayer matchPlayer, boolean inverse) {
        return match.getParticipants()
                .stream()
                .map(participant ->
                        new PlayerRecord<T>(
                                participantComparableFunction.apply(participant),
                                matchPlayer.getParticipant(participant.getParticipantId()),
                                participant.getLane(),
                                participant.getChampionId(),
                                Orianna.championWithId(participant.getChampionId()).get().getName(),
                                match.getId(),
                                inverse
                        )
                )
                .map(Collections::<LolRecord>singleton)
                .reduce(LolRecordsDTO::getMaxSet)
                .orElseGet(Collections::emptySet);
    }

}
