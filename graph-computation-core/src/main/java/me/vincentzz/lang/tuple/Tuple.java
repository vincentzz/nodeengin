package me.vincentzz.lang.tuple;

public record Tuple<T, P>(T _1, P _2) {
    public static <T,P> Tuple<T, P> of(T _1, P _2) {
        return new Tuple<>(_1, _2);
    }
}
