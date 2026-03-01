package me.vincentzz.graph;

import me.vincentzz.graph.node.ConnectionPoint;
import me.vincentzz.graph.scope.Exclude;
import me.vincentzz.graph.scope.Include;
import me.vincentzz.graph.scope.RegExMatch;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class RegExMatchTest {

    private final BasicResourceIdentifier appleRid = new BasicResourceIdentifier("APPLE_ASK", String.class);
    private final BasicResourceIdentifier googleRid = new BasicResourceIdentifier("GOOGLE_BID", String.class);
    private final BasicResourceIdentifier msftRid = new BasicResourceIdentifier("MSFT_ASK", String.class);

    private final ConnectionPoint appleCp = ConnectionPoint.of(Path.of("ProviderA"), appleRid);
    private final ConnectionPoint googleCp = ConnectionPoint.of(Path.of("ProviderB"), googleRid);
    private final ConnectionPoint msftCp = ConnectionPoint.of(Path.of("ProviderA"), msftRid);

    @Test
    void matchesSingleField() {
        RegExMatch<ConnectionPoint> scope = new RegExMatch<>(Map.of(
                "nodePath", "ProviderA"
        ));

        assertThat(scope.isInScope(appleCp)).isTrue();
        assertThat(scope.isInScope(msftCp)).isTrue();
        assertThat(scope.isInScope(googleCp)).isFalse();
    }

    @Test
    void matchesMultipleFields() {
        RegExMatch<ConnectionPoint> scope = new RegExMatch<>(Map.of(
                "nodePath", "ProviderA",
                "rid", ".*APPLE.*"
        ));

        assertThat(scope.isInScope(appleCp)).isTrue();
        assertThat(scope.isInScope(msftCp)).isFalse();  // right path, wrong rid
        assertThat(scope.isInScope(googleCp)).isFalse(); // wrong path
    }

    @Test
    void emptyMatcherMatchesEverything() {
        RegExMatch<ConnectionPoint> scope = new RegExMatch<>(Map.of());

        assertThat(scope.isInScope(appleCp)).isTrue();
        assertThat(scope.isInScope(googleCp)).isTrue();
        assertThat(scope.isInScope(msftCp)).isTrue();
    }

    @Test
    void regexPatternSupport() {
        RegExMatch<ConnectionPoint> scope = new RegExMatch<>(Map.of(
                "rid", ".*_ASK.*"
        ));

        assertThat(scope.isInScope(appleCp)).isTrue();   // APPLE_ASK
        assertThat(scope.isInScope(msftCp)).isTrue();     // MSFT_ASK
        assertThat(scope.isInScope(googleCp)).isFalse();  // GOOGLE_BID
    }

    @Test
    void worksWithIncludeScope() {
        RegExMatch<ConnectionPoint> regEx = new RegExMatch<>(Map.of(
                "nodePath", "ProviderA"
        ));
        Include<ConnectionPoint> include = Include.of(regEx);

        assertThat(include.isInScope(appleCp)).isTrue();
        assertThat(include.isInScope(msftCp)).isTrue();
        assertThat(include.isInScope(googleCp)).isFalse();
    }

    @Test
    void worksWithExcludeScope() {
        RegExMatch<ConnectionPoint> regEx = new RegExMatch<>(Map.of(
                "nodePath", "ProviderA"
        ));
        Exclude<ConnectionPoint> exclude = Exclude.of(regEx);

        // Exclude inverts: things "in scope" of the regex are excluded
        assertThat(exclude.isInScope(appleCp)).isFalse();
        assertThat(exclude.isInScope(msftCp)).isFalse();
        assertThat(exclude.isInScope(googleCp)).isTrue();
    }

    @Test
    void nonExistentFieldThrows() {
        RegExMatch<ConnectionPoint> scope = new RegExMatch<>(Map.of(
                "nonExistentField", ".*"
        ));

        assertThatThrownBy(() -> scope.isInScope(appleCp))
                .isInstanceOf(RuntimeException.class);
    }
}
