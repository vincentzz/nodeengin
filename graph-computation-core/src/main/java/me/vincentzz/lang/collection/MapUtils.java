package me.vincentzz.lang.collection;

import me.vincentzz.lang.exception.Rte;
import me.vincentzz.lang.tuple.Tuple;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class MapUtils {

    public static <K, V, V2> Map<K, V2> mapValue(Map<K,V> map, BiFunction<K, V, V2> mapping) {
        return map.entrySet().stream().map(e -> Rte.run(() -> {
            K k = e.getKey();
            V v = e.getValue();
            V2 newValue = mapping.apply(k, v);
            return Tuple.of(k, newValue);
        })).collect(Collectors.toUnmodifiableMap(Tuple::_1, Tuple::_2));
    }

    public static <K, V> Map<K, V> union(Map<K, V> map1, Map<K, V> map2) {
        Map<K, V> result = new HashMap<>(map1);
        result.putAll(map2); // overwrites duplicates from map1
        return Collections.unmodifiableMap(result);
    }
}
