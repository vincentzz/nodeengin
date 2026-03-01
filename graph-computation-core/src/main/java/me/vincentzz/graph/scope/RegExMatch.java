package me.vincentzz.graph.scope;

import me.vincentzz.lang.exception.Rte;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class RegExMatch<T> implements ScopeSet<T> {

    private final Map<String, String> fieldMatcher;
    private final Map<String, Pattern> compiledPatterns;

    public RegExMatch(Map<String, String> fieldMatcher) {
        this.fieldMatcher = Map.copyOf(fieldMatcher);
        this.compiledPatterns = fieldMatcher.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> Pattern.compile(e.getValue())));
    }

    public Map<String, String> fieldMatcher() {
        return fieldMatcher;
    }

    @Override
    public boolean isInScope(T t) {
        Optional<Boolean> firstMismatch = compiledPatterns.entrySet().stream().map(entry -> {
            String fieldName = entry.getKey();
            Pattern pattern = entry.getValue();
            Method f = Rte.run(() -> t.getClass().getMethod(fieldName));

            String fieldValueStr = Rte.run(() -> f.invoke(t).toString());
            return pattern.matcher(fieldValueStr).matches();
        }).filter(m -> !m).findFirst();
        return firstMismatch.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RegExMatch<?> that)) return false;
        return fieldMatcher.equals(that.fieldMatcher);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fieldMatcher);
    }

    @Override
    public String toString() {
        return "RegExMatch[fieldMatcher=" + fieldMatcher + "]";
    }
}
