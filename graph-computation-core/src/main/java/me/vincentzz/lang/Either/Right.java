package me.vincentzz.lang.Either;

import me.vincentzz.lang.exception.Rte;
import me.vincentzz.lang.functional.TFunction;

import java.util.Objects;
import java.util.Optional;

public final class Right<L, R> extends Either<L, R> {
    private final R value;

    public static <L, R> Right<L, R> of(R value) {
        return new Right<>(value);
    }

    private Right(R value) {
        this.value = value;
    }

    @Override
    public boolean isLeft() {
        return false;
    }

    @Override
    public L getLeft() {
        throw new UnsupportedOperationException("Cannot getLeft() on a Right");
    }

    @Override
    public R getRight() {
        return value;
    }

    @Override
    public <R2> Either<L, R2> map(TFunction<? super R, ? extends R2> mapper) {
        return Right.of(Rte.run(mapper, value));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R2> Either<L, R2> flatMap(TFunction<? super R, Either<L, ? extends R2>> mapper) {
        return (Either<L, R2>) Rte.run(mapper, value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <L2> Either<L2, R> mapLeft(TFunction<? super L, ? extends L2> mapper) {
        return (Either<L2, R>) this;
    }

    @Override
    public <T> T fold(TFunction<? super L, ? extends T> leftMapper, TFunction<? super R, ? extends T> rightMapper) {
        return Rte.run(rightMapper, value);
    }

    @Override
    public Either<R, L> swap() {
        return Left.of(value);
    }

    @Override
    public Optional<R> toOptional() {
        return Optional.ofNullable(value);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Right<?, ?> right = (Right<?, ?>) o;
        return Objects.equals(value, right.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public String toString() {
        return "Right(" + value + ')';
    }
}
