package me.vincentzz.lang.Result;

import me.vincentzz.lang.functional.TFunction;
import me.vincentzz.lang.functional.TRunnable;
import me.vincentzz.lang.functional.TSupplier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

class ResultTest {

    @Nested
    class SuccessTest {

        @Test
        void shouldCreateSuccessWithData() {
            // Given
            String data = "test data";
            
            // When
            Success<String> result = Success.of(data);
            
            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.isFailure()).isFalse();
            assertThat(result.get()).isEqualTo(data);
        }

        @Test
        void shouldCreateSuccessWithNullData() {
            // When
            Success<String> result = Success.of(null);
            
            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.get()).isNull();
        }

        @Test
        void shouldReturnNullException() {
            // Given
            Success<String> result = Success.of("test");
            
            // When & Then
            assertThat(result.getException()).isNull();
        }

        @Test
        void shouldReturnOptionalWithValue() {
            // Given
            String data = "test data";
            Success<String> result = Success.of(data);
            
            // When
            Optional<String> optional = result.toOptional();
            
            // Then
            assertThat(optional).isPresent();
            assertThat(optional.get()).isEqualTo(data);
        }

        @Test
        void shouldReturnEmptyOptionalForNullData() {
            // Given
            Success<String> result = Success.of(null);
            
            // When
            Optional<String> optional = result.toOptional();
            
            // Then
            assertThat(optional).isEmpty();
        }

        @Test
        void shouldMapSuccessfully() {
            // Given
            Success<String> result = Success.of("hello");
            TFunction<String, Integer> mapper = String::length;
            
            // When
            Result<Integer> mapped = result.map(mapper);
            
            // Then
            assertThat(mapped.isSuccess()).isTrue();
            assertThat(mapped.get()).isEqualTo(5);
        }

        @Test
        void shouldReturnFailureWhenMapThrows() {
            // Given
            Success<String> result = Success.of("test");
            TFunction<String, Integer> mapper = s -> {
                throw new RuntimeException("Map error");
            };
            
            // When
            Result<Integer> mapped = result.map(mapper);
            
            // Then
            assertThat(mapped.isFailure()).isTrue();
            assertThat(mapped.getException()).hasMessage("Map error");
        }

        @Test
        void shouldFlatMapSuccessfully() {
            // Given
            Success<String> result = Success.of("hello");
            
            // When
            Result<Integer> flatMapped = result.flatMap(s -> Success.of(s.length()));
            
            // Then
            assertThat(flatMapped.isSuccess()).isTrue();
            assertThat(flatMapped.get()).isEqualTo(5);
        }

        @Test
        void shouldFlatMapToFailure() {
            // Given
            Success<String> result = Success.of("test");
            
            // When
            Result<Integer> flatMapped = result.flatMap(s -> Failure.of(new RuntimeException("Flat map error")));
            
            // Then
            assertThat(flatMapped.isFailure()).isTrue();
            assertThat(flatMapped.getException()).hasMessage("Flat map error");
        }

        @Test
        void shouldReturnFailureWhenFlatMapThrows() {
            // Given
            Success<String> result = Success.of("test");
            
            // When
            Result<Integer> flatMapped = result.flatMap(s -> {
                throw new RuntimeException("Flat map throws");
            });
            
            // Then
            assertThat(flatMapped.isFailure()).isTrue();
            assertThat(flatMapped.getException()).hasMessage("Flat map throws");
        }

        @Test
        void shouldImplementEqualsAndHashCode() {
            // Given
            Success<String> result1 = Success.of("test");
            Success<String> result2 = Success.of("test");
            Success<String> result3 = Success.of("different");
            Success<String> result4 = Success.of(null);
            Success<String> result5 = Success.of(null);
            
            // Then
            assertThat(result1).isEqualTo(result2);
            assertThat(result1).isNotEqualTo(result3);
            assertThat(result1).isNotEqualTo(result4);
            assertThat(result4).isEqualTo(result5);
            assertThat(result1).isNotEqualTo(null);
            assertThat(result1).isNotEqualTo("not a result");
            
            assertThat(result1.hashCode()).isEqualTo(result2.hashCode());
            assertThat(result4.hashCode()).isEqualTo(result5.hashCode());
        }
    }

    @Nested
    class FailureTest {

        @Test
        void shouldCreateFailureWithException() {
            // Given
            Exception exception = new RuntimeException("test error");
            
            // When
            Failure<String> result = Failure.of(exception);
            
            // Then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.isFailure()).isTrue();
            assertThat(result.getException()).isEqualTo(exception);
        }

        @Test
        void shouldThrowWhenGettingValue() {
            // Given
            Exception exception = new RuntimeException("test error");
            Failure<String> result = Failure.of(exception);
            
            // When & Then
            assertThatThrownBy(result::get)
                .isInstanceOf(RuntimeException.class)
                .hasMessage("test error");
        }

        @Test
        void shouldReturnEmptyOptional() {
            // Given
            Failure<String> result = Failure.of(new RuntimeException("error"));
            
            // When
            Optional<String> optional = result.toOptional();
            
            // Then
            assertThat(optional).isEmpty();
        }

        @Test
        void shouldReturnSelfOnMap() {
            // Given
            Failure<String> result = Failure.of(new RuntimeException("error"));
            TFunction<String, Integer> mapper = String::length;
            
            // When
            Result<Integer> mapped = result.map(mapper);
            
            // Then
            assertThat(mapped.isFailure()).isTrue();
            assertThat(mapped.getException()).hasMessage("error");
            assertThat(mapped).isSameAs(result);
        }

        @Test
        void shouldReturnSelfOnFlatMap() {
            // Given
            Failure<String> result = Failure.of(new RuntimeException("error"));
            
            // When
            Result<Integer> flatMapped = result.flatMap(s -> Success.of(s.length()));
            
            // Then
            assertThat(flatMapped.isFailure()).isTrue();
            assertThat(flatMapped.getException()).hasMessage("error");
            assertThat(flatMapped).isSameAs(result);
        }

        @Test
        void shouldImplementEqualsAndHashCode() {
            // Given
            Exception exception1 = new RuntimeException("error1");
            Exception exception2 = new RuntimeException("error2");
            Failure<String> result1 = Failure.of(exception1);
            Failure<String> result2 = Failure.of(exception1);
            Failure<String> result3 = Failure.of(exception2);
            
            // Then
            assertThat(result1).isEqualTo(result2);
            assertThat(result1).isNotEqualTo(result3);
            assertThat(result1).isNotEqualTo(null);
            assertThat(result1).isNotEqualTo("not a result");
            
            assertThat(result1.hashCode()).isEqualTo(result2.hashCode());
        }
    }

    @Nested
    class StaticMethodsTest {

        @Test
        void shouldTryRunnableSuccessfully() {
            // Given
            TRunnable successRunnable = () -> {
                // Do nothing - success case
            };
            
            // When
            Result<Void> result = Result.Try(successRunnable);
            
            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.get()).isNull();
        }

        @Test
        void shouldTryRunnableWithFailure() {
            // Given
            TRunnable failingRunnable = () -> {
                throw new RuntimeException("Runnable failed");
            };
            
            // When
            Result<Void> result = Result.Try(failingRunnable);
            
            // Then
            assertThat(result.isFailure()).isTrue();
            assertThat(result.getException()).hasMessage("Runnable failed");
        }

        @Test
        void shouldTrySupplierSuccessfully() {
            // Given
            TSupplier<String> successSupplier = () -> "success value";
            
            // When
            Result<String> result = Result.Try(successSupplier);
            
            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.get()).isEqualTo("success value");
        }

        @Test
        void shouldTrySupplierWithFailure() {
            // Given
            TSupplier<String> failingSupplier = () -> {
                throw new RuntimeException("Supplier failed");
            };
            
            // When
            Result<String> result = Result.Try(failingSupplier);
            
            // Then
            assertThat(result.isFailure()).isTrue();
            assertThat(result.getException()).hasMessage("Supplier failed");
        }

        @Test
        void shouldFlattenSuccessfulNestedResult() {
            // Given
            Result<Result<String>> nestedSuccess = Success.of(Success.of("inner value"));
            
            // When
            Result<String> flattened = Result.flatten(nestedSuccess);
            
            // Then
            assertThat(flattened.isSuccess()).isTrue();
            assertThat(flattened.get()).isEqualTo("inner value");
        }

        @Test
        void shouldFlattenFailedOuterResult() {
            // Given
            Result<Result<String>> nestedFailure = Failure.of(new RuntimeException("Outer failure"));
            
            // When
            Result<String> flattened = Result.flatten(nestedFailure);
            
            // Then
            assertThat(flattened.isFailure()).isTrue();
            assertThat(flattened.getException()).hasMessage("Outer failure");
        }

        @Test
        void shouldFlattenSuccessfulOuterWithFailedInner() {
            // Given
            Result<Result<String>> nestedWithInnerFailure = Success.of(Failure.of(new RuntimeException("Inner failure")));
            
            // When
            Result<String> flattened = Result.flatten(nestedWithInnerFailure);
            
            // Then
            assertThat(flattened.isFailure()).isTrue();
            assertThat(flattened.getException()).hasMessage("Inner failure");
        }
    }

    @Nested
    class IntegrationTest {

        @Test
        void shouldChainOperationsSuccessfully() {
            // Given
            String input = "hello world";
            
            // When
            Result<String> result = Success.of(input)
                .map(s -> s.toUpperCase())
                .map(s -> s.replace(" ", "_"))
                .flatMap(s -> Success.of(s + "!"));
            
            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.get()).isEqualTo("HELLO_WORLD!");
        }

        @Test
        void shouldStopChainOnFirstFailure() {
            // Given
            String input = "hello";
            
            // When
            Result<String> result = Success.of(input)
                .map(s -> s.toUpperCase())
                .map(s -> {
                    throw new RuntimeException("Chain failure");
                })
                .map(s -> s + "!"); // This should not execute
            
            // Then
            assertThat(result.isFailure()).isTrue();
            assertThat(result.getException()).hasMessage("Chain failure");
        }

        @Test
        void shouldHandleNullValues() {
            // When
            Result<String> result = Success.of((String) null)
                .map(s -> s == null ? "was null" : s.toUpperCase());
            
            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.get()).isEqualTo("was null");
        }
    }
}
