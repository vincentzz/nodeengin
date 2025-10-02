package me.vincentzz.lang.Result;

import me.vincentzz.lang.exception.Rte;
import me.vincentzz.lang.functional.TFunction;

import java.util.Objects;
import java.util.Optional;

public final class Failure<T> extends Result<T> {
    private final Exception e;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Failure<?> failure = (Failure<?>) o;
        return Objects.equals(e.getMessage(), failure.e.getMessage());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(e.getMessage());
    }

    public static Failure of(Exception e) {
        return new Failure(e);
    }

    private Failure(Exception e) {
        this.e = e;
    }

    @Override
    public boolean isSuccess() {
        return false;
    }

    @Override
    public <R> Result<R> flatMap(TFunction<? super T, Result<? extends R>> mapper) {
        return (Result<R>) this;
    }

    @Override
    public Optional<T> toOptional() {
        return Optional.empty();
    }

    @Override
    public T get() {
        throw Rte.wrap(getException());
    }

    @Override
    public Exception getException() {
        return e;
    }

    @Override
    public <R> Result<R> map(TFunction<? super T, ? extends R> mapper) {
        return (Result<R>)this;
    }

    @Override
    public String toString() {
        return "Failure(" + e.getMessage() +')';
    }
}
