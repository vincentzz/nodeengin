package me.vincentzz.lang.Either;

import me.vincentzz.lang.functional.TFunction;

import java.util.Objects;
import java.util.Optional;

public final class Left<L, R> extends Either<L, R> {
    private final L value;

    public static <L, R> Left<L, R> of(L value) {
        return new Left<>(value);
    }

    private Left(L value) {
        this.value = value;
    }

    @Override
    public boolean isLeft() {
        return true;
    }

    @Override
    public L getLeft() {
        return value;
    }

    @Override
    public R getRight() {
        throw new UnsupportedOperationException("Cannot getRight() on a Left");
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R2> Either<L, R2> map(TFunction<? super R, ? extends R2> mapper) {
        return (Either<L, R2>) this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R2> Either<L, R2> flatMap(TFunction<? super R, Either<L, ? extends R2>> mapper) {
        return (Either<L, R2>) this;
    }

    @Override
    public <L2> Either<L2, R> mapLeft(TFunction<? super L, ? extends L2> mapper) {
        return Left.of(me.vincentzz.lang.exception.Rte.run(mapper, value));
    }

    @Override
    public <T> T fold(TFunction<? super L, ? extends T> leftMapper, TFunction<? super R, ? extends T> rightMapper) {
        return me.vincentzz.lang.exception.Rte.run(leftMapper, value);
    }

    @Override
    public Either<R, L> swap() {
        return Right.of(value);
    }

    @Override
    public Optional<R> toOptional() {
        return Optional.empty();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Left<?, ?> left = (Left<?, ?>) o;
        return Objects.equals(value, left.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public String toString() {
        return "Left(" + value + ')';
    }
}
