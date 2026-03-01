package me.vincentzz.lang.Either;

import me.vincentzz.lang.Result.Failure;
import me.vincentzz.lang.Result.Result;
import me.vincentzz.lang.Result.Success;
import me.vincentzz.lang.functional.TFunction;

import java.util.Optional;

public sealed abstract class Either<L, R> permits Left, Right {

    public abstract boolean isLeft();

    public boolean isRight() {
        return !isLeft();
    }

    abstract public L getLeft();

    abstract public R getRight();

    abstract public <R2> Either<L, R2> map(TFunction<? super R, ? extends R2> mapper);

    abstract public <R2> Either<L, R2> flatMap(TFunction<? super R, Either<L, ? extends R2>> mapper);

    abstract public <L2> Either<L2, R> mapLeft(TFunction<? super L, ? extends L2> mapper);

    public abstract <T> T fold(TFunction<? super L, ? extends T> leftMapper, TFunction<? super R, ? extends T> rightMapper);

    public abstract Either<R, L> swap();

    abstract public Optional<R> toOptional();

    @SuppressWarnings("unchecked")
    public static <L, R> Either<L, R> flatten(Either<L, Either<L, R>> nested) {
        if (nested.isLeft()) {
            return (Either<L, R>) nested;
        } else {
            return nested.getRight();
        }
    }

    public static <R> Result<R> toResult(Either<Exception, R> either) {
        if (either.isLeft()) {
            return Failure.of(either.getLeft());
        } else {
            return Success.of(either.getRight());
        }
    }
}
