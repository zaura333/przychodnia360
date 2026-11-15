-- Fala 1.6: ślad (W3C traceparent) zdarzenia, żeby jeden traceId łączył żądanie, zdarzenie i jego obsługę.
-- Zmiana zgodna wstecz (kolumna opcjonalna); stare wiersze zostają bez śladu.
ALTER TABLE outbox ADD COLUMN traceparent text;
