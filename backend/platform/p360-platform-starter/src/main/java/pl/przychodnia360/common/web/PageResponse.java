package pl.przychodnia360.common.web;

import java.util.List;
import java.util.function.Function;

/** Strona wyników listy. page liczone od 0. */
public record PageResponse<T>(List<T> items, int page, int size, long total) {

    public <R> PageResponse<R> map(Function<T, R> mapper) {
        return new PageResponse<>(items.stream().map(mapper).toList(), page, size, total);
    }
}
