package me.vincentzz.lang.Result;


import me.vincentzz.lang.functional.TFunction;
import me.vincentzz.lang.functional.TRunnable;
import me.vincentzz.lang.functional.TSupplier;

import java.util.Optional;

public sealed abstract class Result<T> permits Success, Failure {
    public abstract boolean isSuccess();

    public boolean isFailure() {
        return !isSuccess();
    }

    abstract public <R> Result<R> map(TFunction<? super T, ? extends R> mapper);

    abstract public <R> Result<R> flatMap(TFunction<? super T, Result<? extends R>> mapper);

    public static <R> Result<R> flatten(Result<Result<R>> nestedResult) {
        if (nestedResult.isFailure()) {
            return (Failure<R>) nestedResult;
        } else {
            return nestedResult.get();
        }
    }

    abstract public Optional<T> toOptional();

    abstract public T get();

    abstract public Exception getException();


    public static Result<Void> Try(TRunnable runnable) {
        try {
            runnable.run();
            return Success.of(null);
        } catch (Exception e) {
            return (Result<Void>) Failure.of(e);
        }
    }
    public static <T> Result<T> Try(TSupplier<? extends T>  supplier) {
        try {
            return Success.of(supplier.get());
        } catch (Exception e) {
            return (Result<T>) Failure.of(e);
        }
    }
    
}
