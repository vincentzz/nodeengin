package me.vincentzz.lang.exception;

import me.vincentzz.lang.functional.TFunction;
import me.vincentzz.lang.functional.TRunnable;
import me.vincentzz.lang.functional.TSupplier;

public final class Rte {
    private Rte() {}

    public static <T extends Exception> RuntimeException wrap(T e) {
        if (e instanceof RuntimeException rte) {
            return rte;
        } else {
            return new RuntimeException(e);
        }
    }

    public static void run(TRunnable act) {
        try {
            act.run();
        } catch (Exception e) {
            throw wrap(e);
        }
    }

    public static <T> T run(TSupplier<T> sup) {
        try {
            return sup.get();
        } catch (Exception e) {
            throw wrap(e);
        }
    }


    public static <P, R> R run(TFunction<P, R> func, P param) {
        try {
            return func.apply(param);
        } catch (Exception e) {
            throw wrap(e);
        }
    }
}
