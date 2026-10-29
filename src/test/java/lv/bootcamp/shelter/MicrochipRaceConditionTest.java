
package lv.bootcamp.shelter;

import lv.bootcamp.shelter.dto.AnimalCreateRequest;
import lv.bootcamp.shelter.model.AnimalType;
import lv.bootcamp.shelter.repository.AnimalRepository;
import lv.bootcamp.shelter.service.AnimalService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataIntegrityViolationException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest
class MicrochipRaceConditionTest {

    private static final String CHIP = "985112000777777";

    /** Runs right after the real existsByMicrochipId has returned. Set by the test. */
    private static final AtomicReference<Consumer<Boolean>> AFTER_EXISTS_CHECK = new AtomicReference<>();

    @TestConfiguration
    static class RepositoryHookConfig {

        @Bean
        static BeanPostProcessor animalRepositoryHook() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (!(bean instanceof AnimalRepository real)) {
                        return bean;
                    }
                    return Proxy.newProxyInstance(
                            AnimalRepository.class.getClassLoader(),
                            new Class<?>[]{AnimalRepository.class},
                            (proxy, method, args) -> {
                                Object result;
                                try {
                                    result = method.invoke(real, args);
                                } catch (InvocationTargetException e) {
                                    throw e.getCause();
                                }
                                Consumer<Boolean> hook = AFTER_EXISTS_CHECK.get();
                                if (hook != null && method.getName().equals("existsByMicrochipId")) {
                                    hook.accept((Boolean) result);
                                }
                                return result;
                            });
                }
            };
        }
    }

    @Autowired
    private AnimalService animalService;

    @Autowired
    private AnimalRepository animalRepository;

    @AfterEach
    void cleanUp() {
        AFTER_EXISTS_CHECK.set(null);
        animalRepository.deleteAll();   // no @Transactional rollback here, so clean up by hand
    }

    @Test
    void twoOverlappingCreates_bothPassTheServiceCheck_butOnlyOneRowIsSaved() throws Exception {
        CyclicBarrier bothHaveChecked = new CyclicBarrier(2);
        List<Boolean> whatEachCallSaw = new CopyOnWriteArrayList<>();

        // After its existsByMicrochipId check, each call waits until the OTHER call has also

        AFTER_EXISTS_CHECK.set(exists -> {
            whatEachCallSaw.add(exists);
            try {
                bothHaveChecked.await(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IllegalStateException("the two calls did not overlap", e);
            }
        });

        Callable<Throwable> createWithSameChip = () -> {
            try {
                animalService.create(new AnimalCreateRequest(
                        "Rex", AnimalType.DOG, "Labrador", 3, "Friendly", CHIP));
                return null;                      // success
            } catch (Throwable failure) {
                return failure;                   // what went wrong
            }
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Throwable> failures = new ArrayList<>();
        int successes = 0;
        try {
            Future<Throwable> first = executor.submit(createWithSameChip);
            Future<Throwable> second = executor.submit(createWithSameChip);
            for (Future<Throwable> result : List.of(first, second)) {
                Throwable failure = result.get(20, TimeUnit.SECONDS);
                if (failure == null) {
                    successes++;
                } else {
                    failures.add(failure);
                }
            }
        } finally {
            executor.shutdownNow();
        }

        // 1) The service-level check is NOT enough: both calls saw "chip not registered yet".
        assertThat(whatEachCallSaw).containsExactly(false, false);

        // 2) The database constraint is what stops the duplicate: one call wins, one is rejected.
        assertThat(successes).isEqualTo(1);
        assertThat(failures).hasSize(1);
        assertThat(failures.getFirst()).isInstanceOf(DataIntegrityViolationException.class);

        // 3) And exactly one row with that chip exists afterwards.
        long rowsWithChip = animalRepository.findAll().stream()
                .filter(animal -> CHIP.equals(animal.getMicrochipId()))
                .count();
        assertThat(rowsWithChip).isEqualTo(1);
    }
}