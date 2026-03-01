package me.vincentzz.lang.Either;

import me.vincentzz.lang.Result.Result;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

class EitherTest {

    @Nested
    class RightTest {

        @Test
        void shouldCreateRightWithValue() {
            // Given
            String value = "test data";

            // When
            Right<String, String> either = Right.of(value);

            // Then
            assertThat(either.isRight()).isTrue();
            assertThat(either.isLeft()).isFalse();
            assertThat(either.getRight()).isEqualTo(value);
        }

        @Test
        void shouldThrowOnGetLeft() {
            // Given
            Right<String, Integer> either = Right.of(42);

            // When & Then
            assertThatThrownBy(either::getLeft)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("Cannot getLeft() on a Right");
        }

        @Test
        void shouldMapSuccessfully() {
            // Given
            Right<String, String> either = Right.of("hello");

            // When
            Either<String, Integer> mapped = either.map(String::length);

            // Then
            assertThat(mapped.isRight()).isTrue();
            assertThat(mapped.getRight()).isEqualTo(5);
        }

        @Test
        void shouldPropagateExceptionFromMap() {
            // Given
            Right<String, String> either = Right.of("test");

            // When & Then
            assertThatThrownBy(() -> either.map(s -> {
                throw new RuntimeException("Map error");
            })).isInstanceOf(RuntimeException.class)
               .hasMessage("Map error");
        }

        @Test
        void shouldFlatMapSuccessfully() {
            // Given
            Right<String, String> either = Right.of("hello");

            // When
            Either<String, Integer> flatMapped = either.flatMap(s -> Right.of(s.length()));

            // Then
            assertThat(flatMapped.isRight()).isTrue();
            assertThat(flatMapped.getRight()).isEqualTo(5);
        }

        @Test
        void shouldFlatMapToLeft() {
            // Given
            Right<String, String> either = Right.of("test");

            // When
            Either<String, Integer> flatMapped = either.flatMap(s -> Left.of("error"));

            // Then
            assertThat(flatMapped.isLeft()).isTrue();
            assertThat(flatMapped.getLeft()).isEqualTo("error");
        }

        @Test
        void shouldPassThroughOnMapLeft() {
            // Given
            Right<String, Integer> either = Right.of(42);

            // When
            Either<Integer, Integer> mapped = either.mapLeft(String::length);

            // Then
            assertThat(mapped.isRight()).isTrue();
            assertThat(mapped.getRight()).isEqualTo(42);
            assertThat(mapped).isSameAs(either);
        }

        @Test
        void shouldFoldWithRightMapper() {
            // Given
            Right<String, Integer> either = Right.of(42);

            // When
            String result = either.fold(l -> "left: " + l, r -> "right: " + r);

            // Then
            assertThat(result).isEqualTo("right: 42");
        }

        @Test
        void shouldSwapToLeft() {
            // Given
            Right<String, Integer> either = Right.of(42);

            // When
            Either<Integer, String> swapped = either.swap();

            // Then
            assertThat(swapped.isLeft()).isTrue();
            assertThat(swapped.getLeft()).isEqualTo(42);
        }

        @Test
        void shouldReturnOptionalWithValue() {
            // Given
            Right<String, String> either = Right.of("data");

            // When
            Optional<String> optional = either.toOptional();

            // Then
            assertThat(optional).isPresent();
            assertThat(optional.get()).isEqualTo("data");
        }

        @Test
        void shouldReturnEmptyOptionalForNullValue() {
            // Given
            Right<String, String> either = Right.of(null);

            // When
            Optional<String> optional = either.toOptional();

            // Then
            assertThat(optional).isEmpty();
        }

        @Test
        void shouldImplementEqualsAndHashCode() {
            // Given
            Right<String, Integer> right1 = Right.of(42);
            Right<String, Integer> right2 = Right.of(42);
            Right<String, Integer> right3 = Right.of(99);
            Right<String, Integer> right4 = Right.of(null);
            Right<String, Integer> right5 = Right.of(null);

            // Then
            assertThat(right1).isEqualTo(right2);
            assertThat(right1).isNotEqualTo(right3);
            assertThat(right1).isNotEqualTo(right4);
            assertThat(right4).isEqualTo(right5);
            assertThat(right1).isNotEqualTo(null);
            assertThat(right1).isNotEqualTo("not an either");

            assertThat(right1.hashCode()).isEqualTo(right2.hashCode());
            assertThat(right4.hashCode()).isEqualTo(right5.hashCode());
        }
    }

    @Nested
    class LeftTest {

        @Test
        void shouldCreateLeftWithValue() {
            // Given
            String value = "error";

            // When
            Left<String, Integer> either = Left.of(value);

            // Then
            assertThat(either.isLeft()).isTrue();
            assertThat(either.isRight()).isFalse();
            assertThat(either.getLeft()).isEqualTo(value);
        }

        @Test
        void shouldThrowOnGetRight() {
            // Given
            Left<String, Integer> either = Left.of("error");

            // When & Then
            assertThatThrownBy(either::getRight)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("Cannot getRight() on a Left");
        }

        @Test
        void shouldShortCircuitOnMap() {
            // Given
            Left<String, String> either = Left.of("error");

            // When
            Either<String, Integer> mapped = either.map(String::length);

            // Then
            assertThat(mapped.isLeft()).isTrue();
            assertThat(mapped.getLeft()).isEqualTo("error");
            assertThat(mapped).isSameAs(either);
        }

        @Test
        void shouldShortCircuitOnFlatMap() {
            // Given
            Left<String, String> either = Left.of("error");

            // When
            Either<String, Integer> flatMapped = either.flatMap(s -> Right.of(s.length()));

            // Then
            assertThat(flatMapped.isLeft()).isTrue();
            assertThat(flatMapped.getLeft()).isEqualTo("error");
            assertThat(flatMapped).isSameAs(either);
        }

        @Test
        void shouldMapLeft() {
            // Given
            Left<String, Integer> either = Left.of("error");

            // When
            Either<Integer, Integer> mapped = either.mapLeft(String::length);

            // Then
            assertThat(mapped.isLeft()).isTrue();
            assertThat(mapped.getLeft()).isEqualTo(5);
        }

        @Test
        void shouldFoldWithLeftMapper() {
            // Given
            Left<String, Integer> either = Left.of("error");

            // When
            String result = either.fold(l -> "left: " + l, r -> "right: " + r);

            // Then
            assertThat(result).isEqualTo("left: error");
        }

        @Test
        void shouldSwapToRight() {
            // Given
            Left<String, Integer> either = Left.of("error");

            // When
            Either<Integer, String> swapped = either.swap();

            // Then
            assertThat(swapped.isRight()).isTrue();
            assertThat(swapped.getRight()).isEqualTo("error");
        }

        @Test
        void shouldReturnEmptyOptional() {
            // Given
            Left<String, Integer> either = Left.of("error");

            // When
            Optional<Integer> optional = either.toOptional();

            // Then
            assertThat(optional).isEmpty();
        }

        @Test
        void shouldImplementEqualsAndHashCode() {
            // Given
            Left<String, Integer> left1 = Left.of("error");
            Left<String, Integer> left2 = Left.of("error");
            Left<String, Integer> left3 = Left.of("different");
            Left<String, Integer> left4 = Left.of(null);
            Left<String, Integer> left5 = Left.of(null);

            // Then
            assertThat(left1).isEqualTo(left2);
            assertThat(left1).isNotEqualTo(left3);
            assertThat(left1).isNotEqualTo(left4);
            assertThat(left4).isEqualTo(left5);
            assertThat(left1).isNotEqualTo(null);
            assertThat(left1).isNotEqualTo("not an either");

            assertThat(left1.hashCode()).isEqualTo(left2.hashCode());
            assertThat(left4.hashCode()).isEqualTo(left5.hashCode());
        }
    }

    @Nested
    class StaticMethodsTest {

        @Test
        void shouldFlattenRightOfRight() {
            // Given
            Either<String, Either<String, Integer>> nested = Right.of(Right.of(42));

            // When
            Either<String, Integer> flattened = Either.flatten(nested);

            // Then
            assertThat(flattened.isRight()).isTrue();
            assertThat(flattened.getRight()).isEqualTo(42);
        }

        @Test
        void shouldFlattenLeftOuter() {
            // Given
            Either<String, Either<String, Integer>> nested = Left.of("error");

            // When
            Either<String, Integer> flattened = Either.flatten(nested);

            // Then
            assertThat(flattened.isLeft()).isTrue();
            assertThat(flattened.getLeft()).isEqualTo("error");
        }

        @Test
        void shouldFlattenRightOfLeft() {
            // Given
            Either<String, Either<String, Integer>> nested = Right.of(Left.of("inner error"));

            // When
            Either<String, Integer> flattened = Either.flatten(nested);

            // Then
            assertThat(flattened.isLeft()).isTrue();
            assertThat(flattened.getLeft()).isEqualTo("inner error");
        }

        @Test
        void shouldConvertRightToSuccess() {
            // Given
            Either<Exception, String> either = Right.of("value");

            // When
            Result<String> result = Either.toResult(either);

            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.get()).isEqualTo("value");
        }

        @Test
        void shouldConvertLeftToFailure() {
            // Given
            RuntimeException exception = new RuntimeException("test error");
            Either<Exception, String> either = Left.of(exception);

            // When
            Result<String> result = Either.toResult(either);

            // Then
            assertThat(result.isFailure()).isTrue();
            assertThat(result.getException()).isEqualTo(exception);
        }
    }

    @Nested
    class IntegrationTest {

        @Test
        void shouldChainOperationsSuccessfully() {
            // Given
            String input = "hello world";

            // When
            Either<String, String> result = Right.<String, String>of(input)
                .map(String::toUpperCase)
                .map(s -> s.replace(" ", "_"))
                .flatMap(s -> Right.of(s + "!"));

            // Then
            assertThat(result.isRight()).isTrue();
            assertThat(result.getRight()).isEqualTo("HELLO_WORLD!");
        }

        @Test
        void shouldShortCircuitOnLeftInChain() {
            // Given
            Either<String, String> result = Right.<String, String>of("hello")
                .map(String::toUpperCase)
                .flatMap(s -> Left.<String, String>of("error occurred"))
                .map(s -> s + "!"); // Should not execute

            // Then
            assertThat(result.isLeft()).isTrue();
            assertThat(result.getLeft()).isEqualTo("error occurred");
        }

        @Test
        void shouldSwapThenMap() {
            // Given
            Left<String, Integer> either = Left.of("error");

            // When
            Either<Integer, Integer> result = either.swap().map(String::length);

            // Then
            assertThat(result.isRight()).isTrue();
            assertThat(result.getRight()).isEqualTo(5);
        }

        @Test
        void shouldUnifyWithFold() {
            // Given
            Either<Integer, String> left = Left.of(42);
            Either<Integer, String> right = Right.of("hello");

            // When
            String leftResult = left.fold(i -> "number: " + i, s -> "string: " + s);
            String rightResult = right.fold(i -> "number: " + i, s -> "string: " + s);

            // Then
            assertThat(leftResult).isEqualTo("number: 42");
            assertThat(rightResult).isEqualTo("string: hello");
        }
    }
}
