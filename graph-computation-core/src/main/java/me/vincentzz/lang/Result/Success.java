package me.vincentzz.lang.Result;

import me.vincentzz.lang.functional.TFunction;

import java.util.Objects;
import java.util.Optional;

public final class Success<T> extends Result<T>{
    private T data;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Success<?> success = (Success<?>) o;
        return Objects.equals(data, success.data);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(data);
    }

    public static <T> Success<T> of(T data) {
        return new Success<>(data);
    }


    private Success(T data) {
        this.data = data;
    }

    @Override
    public boolean isSuccess() {
        return true;
    }

    @Override
    public <R> Result<R> map(TFunction<? super T, ? extends R> mapper) {
        return Try(() -> mapper.apply(data));
    }

    @Override
    public <R> Result<R> flatMap(TFunction<? super T, Result<? extends R>> mapper) {
        return (Result<R>) Result.flatten(Result.Try(() -> mapper.apply(data)));
    }

    @Override
    public Optional<T> toOptional() {
        return Optional.ofNullable(data);
    }

    @Override
    public T get() {
        return data;
    }

    @Override
    public Exception getException() {
        return null;
    }

    @Override
    public String toString() {
        return "Success(" + data +')';
    }
}
