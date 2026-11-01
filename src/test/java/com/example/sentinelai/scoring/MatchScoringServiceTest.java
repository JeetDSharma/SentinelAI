package com.example.sentinelai.scoring;

import static com.example.sentinelai.domain.EntityType.ORGANIZATION;
import static com.example.sentinelai.domain.EntityType.PERSON;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.sentinelai.domain.EntityType;
import com.example.sentinelai.domain.ScreeningQuery;
import com.example.sentinelai.domain.WatchlistRecord;
import com.example.sentinelai.scoring.MatchResult.Band;
import com.example.sentinelai.scoring.MatchSignal.Type;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Plain JUnit, no Spring context: the scorer is a pure function. Records mirror entries in watchlist.json. */
class MatchScoringServiceTest {

    private final MatchScoringService scorer = new MatchScoringService();

    private static final WatchlistRecord PETROV = new WatchlistRecord("WL-0001", "Viktor Alekseevich Petrov",
            List.of("Viktor Petrov", "V. A. Petrov"), PERSON, "RU", List.of("P-RU-7701234"), "SYN-SANCTIONS", null);
    private static final WatchlistRecord GARCIA = new WatchlistRecord("WL-0009", "José Antonio García Ruiz",
            List.of("Jose Garcia Ruiz"), PERSON, "MX", List.of("P-MX-G7823410"), "SYN-SANCTIONS", null);
    private static final WatchlistRecord ORION = new WatchlistRecord("WL-0103", "Orion Global Trading Corporation",
            List.of("OGT Corp"), ORGANIZATION, "AE", List.of("REG-AE-DMCC-30412"), "SYN-SANCTIONS", null);
    private static final WatchlistRecord JOHN_SMITH = new WatchlistRecord("WL-0006", "John Smith",
            List.of(), PERSON, "GB", List.of("P-GB-533190876"), "SYN-PEP", null);
    private static final WatchlistRecord PETROVA_LLC = new WatchlistRecord("WL-0002", "Petrova Consulting LLC",
            List.of("Petrova Consulting"), ORGANIZATION, "CY", List.of("REG-CY-HE412233"), "SYN-ADVERSE-MEDIA", null);

    private static ScreeningQuery query(String name, EntityType type, String country, String id) {
        return ScreeningQuery.of(name, type, country, id);
    }

    private static boolean hasSignal(MatchResult result, Type type) {
        return result.signals().stream().anyMatch(s -> s.type() == type);
    }

    @ParameterizedTest(name = "{0} is an exact name match")
    @CsvSource({
            "Viktor Petrov",              // alias, verbatim
            "petrov VIKTOR",              // word order + case
            "Viktor Alekseevich Petrov",  // primary name
    })
    void exactNameVariantsScoreHigh(String name) {
        MatchResult result = scorer.score(query(name, PERSON, "RU", null), PETROV);

        assertThat(hasSignal(result, Type.NAME_EXACT)).isTrue();
        assertThat(result.score()).isEqualTo(80); // 70 name + 5 country + 5 type
        assertThat(result.band()).isEqualTo(Band.HIGH);
    }

    @Test
    void accentsAreIgnored() {
        MatchResult result = scorer.score(query("Jose Antonio Garcia Ruiz", PERSON, null, null), GARCIA);
        assertThat(hasSignal(result, Type.NAME_EXACT)).isTrue();
        assertThat(result.matchedName()).isEqualTo("José Antonio García Ruiz");
    }

    @Test
    void abbreviationsAreExpanded() {
        MatchResult result = scorer.score(query("Orion Global Trading Corp.", ORGANIZATION, "AE", null), ORION);
        assertThat(hasSignal(result, Type.NAME_EXACT)).isTrue();
        assertThat(result.band()).isEqualTo(Band.HIGH);
    }

    @Test
    void misspellingIsSimilarButBelowExact() {
        MatchResult exact = scorer.score(query("Viktor Petrov", PERSON, "RU", null), PETROV);
        MatchResult typo = scorer.score(query("Viktor Petrof", PERSON, "RU", null), PETROV);

        assertThat(hasSignal(typo, Type.NAME_SIMILAR)).isTrue();
        assertThat(typo.score()).isLessThan(exact.score()).isGreaterThanOrEqualTo(70);
        assertThat(typo.matchedName()).isEqualTo("Viktor Petrov");
    }

    @Test
    void partialNameScoresBelowFullNameButIsStillShown() {
        MatchResult full = scorer.score(query("Jose Garcia Ruiz", PERSON, null, null), GARCIA);
        MatchResult partial = scorer.score(query("Jose Garcia", PERSON, null, null), GARCIA);

        assertThat(partial.score()).isLessThan(full.score()).isGreaterThanOrEqualTo(50);
    }

    @Test
    void differentSurnameScoresLow() {
        MatchResult result = scorer.score(query("John Doe", PERSON, null, null), JOHN_SMITH);
        assertThat(result.score()).isLessThan(50);
        assertThat(result.band()).isEqualTo(Band.LOW);
    }

    @Test
    void matchingIdentifierAddsPointsAndIgnoresFormatting() {
        MatchResult result = scorer.score(query("Viktor Petrov", PERSON, "RU", "p ru 7701234"), PETROV);

        assertThat(hasSignal(result, Type.IDENTIFIER_MATCH)).isTrue();
        assertThat(result.score()).isEqualTo(100);
    }

    @Test
    void identifierRescuesAWeakNameMatch() {
        MatchResult withoutId = scorer.score(query("V Petrovic", PERSON, "RU", null), PETROV);
        MatchResult withId = scorer.score(query("V Petrovic", PERSON, "RU", "P-RU-7701234"), PETROV);

        assertThat(withId.score() - withoutId.score()).isEqualTo(MatchScoringService.IDENTIFIER_MATCH);
    }

    @Test
    void conflictingIdentifierCountryAndTypeArePenalised() {
        MatchResult result = scorer.score(query("Petrova Consulting", PERSON, "RU", "P-RU-0000000"), PETROVA_LLC);

        assertThat(result.signals()).extracting(MatchSignal::type)
                .contains(Type.NAME_EXACT, Type.IDENTIFIER_MISMATCH, Type.COUNTRY_MISMATCH, Type.ENTITY_TYPE_MISMATCH);
        assertThat(result.score()).isEqualTo(70 - 25 - 10 - 15);
        assertThat(result.band()).isEqualTo(Band.LOW);
    }

    @Test
    void missingOptionalFieldsProduceNoSignal() {
        MatchResult result = scorer.score(query("Viktor Petrov", PERSON, null, null), PETROV);

        assertThat(result.signals()).extracting(MatchSignal::type)
                .containsExactly(Type.NAME_EXACT, Type.ENTITY_TYPE_MATCH);
    }

    @Test
    void scoreIsTheSumOfSignalPoints() {
        MatchResult result = scorer.score(query("Viktor Petrof", PERSON, "RU", null), PETROV);
        assertThat(result.score()).isEqualTo(result.signals().stream().mapToInt(MatchSignal::points).sum());
    }

    @Test
    void reversedNameOrderIsIndistinguishableWithoutOtherEvidence() {
        WatchlistRecord liWei = new WatchlistRecord("WL-0007", "Li Wei", List.of(), PERSON, "CN",
                List.of("P-CN-E44120931"), null, null);
        WatchlistRecord weiLi = new WatchlistRecord("WL-0008", "Wei Li", List.of(), PERSON, "SG",
                List.of("P-SG-K7712098"), null, null);

        ScreeningQuery nameOnly = query("Li Wei", PERSON, null, null);
        assertThat(scorer.score(nameOnly, liWei).score()).isEqualTo(scorer.score(nameOnly, weiLi).score());

        ScreeningQuery withId = query("Li Wei", PERSON, null, "P-SG-K7712098");
        assertThat(scorer.score(withId, weiLi).score()).isGreaterThan(scorer.score(withId, liWei).score());
    }
}
