package pl.przychodnia360.appointments;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

/**
 * Współbieżność rezerwacji: o wyniku rozstrzyga ograniczenie wykluczające w bazie, nie sprawdzenie w aplikacji.
 * Bariera startowa (latch) i osobni pacjenci, żeby kolizje dotyczyły wyłącznie wykonawcy.
 */
class BookingConcurrencyTest extends AbstractAppointmentTest {

    private static final int THREADS = 20;

    private List<Integer> runConcurrently(List<java.util.concurrent.Callable<Integer>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (var task : tasks) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return task.call();
            }));
        }
        ready.await();
        go.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : futures) {
            statuses.add(f.get());
        }
        pool.shutdown();
        return statuses;
    }

    @RepeatedTest(3)
    void exactlyOneOfManyParallelBookingsOfTheSameSlotSucceeds() throws Exception {
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tokens.add(newPatientAccount(tenant, "Pacjent" + i, "Wspolbiezny").token());
        }
        List<java.util.concurrent.Callable<Integer>> tasks = new ArrayList<>();
        for (String token : tokens) {
            tasks.add(() -> call("POST", "/api/v1/appointments", token, bookBody("10:00", null)).status());
        }

        List<Integer> statuses = runConcurrently(tasks);

        assertThat(statuses.stream().filter(s -> s == 201)).as("statusy: %s", statuses).hasSize(1);
        assertThat(statuses.stream().filter(s -> s == 409)).as("statusy: %s", statuses).hasSize(THREADS - 1);
    }

    @Test
    void bookingAndReschedulingOntoTheSameSlotLeaveOneWinner() throws Exception {
        String movedId = call("POST", "/api/v1/appointments", reception, bookBody("09:00", patient.patientId().toString()))
                .json().get("id").asString();
        var other = newPatientAccount(tenant, "Konkurent", "Rezerwacji");

        List<Integer> statuses = runConcurrently(List.of(
                () -> call("POST", "/api/v1/appointments", other.token(), bookBody("11:00", null)).status(),
                () -> call("POST", "/api/v1/appointments/" + movedId + "/reschedule", reception,
                        "{\"startAt\":\"" + at("11:00") + "\"}").status()));

        assertThat(statuses).as("jedno 200/201 i jedno 409").containsExactlyInAnyOrder(
                statuses.contains(201) ? 201 : 200, 409);
    }

    @Test
    void cancellationFreesTheSlotForTheNextBooking() {
        String id = bookAsPatient("12:00").json().get("id").asString();
        var other = newPatientAccount(tenant, "Kolejny", "Pacjent");
        assertThat(call("POST", "/api/v1/appointments", other.token(), bookBody("12:00", null)).status()).isEqualTo(409);

        assertThat(call("POST", "/api/v1/appointments/" + id + "/cancel", reception, null).status()).isEqualTo(204);

        assertThat(call("POST", "/api/v1/appointments", other.token(), bookBody("12:00", null)).status()).isEqualTo(201);
    }
}
