
package lv.bootcamp.shelter.repository;

import lv.bootcamp.shelter.model.AdoptionRecord;
import lv.bootcamp.shelter.model.Animal;
import lv.bootcamp.shelter.model.AnimalStatus;
import lv.bootcamp.shelter.model.AnimalType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class AdoptionRecordRepositoryTest {

    @Autowired
    private AdoptionRecordRepository adoptionRecordRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findRecentAdoptionsByAnimalType_shouldReturnOnlyMatchingTypeAndRecentDate() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime since = now.minusDays(30);

        Animal recentDog = persistAnimal("Rex", AnimalType.DOG);
        Animal oldDog = persistAnimal("Max", AnimalType.DOG);
        Animal recentCat = persistAnimal("Mia", AnimalType.CAT);

        persistRecord(recentDog, "anna@example.com", now.minusDays(2));   // matches: dog, recent
        persistRecord(oldDog, "bob@example.com", now.minusDays(90));      // too old
        persistRecord(recentCat, "carl@example.com", now.minusDays(2));   // wrong type
        entityManager.flush();
        entityManager.clear();

        List<AdoptionRecord> result =
                adoptionRecordRepository.findRecentAdoptionsByAnimalType(AnimalType.DOG, since);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getAdopterEmail()).isEqualTo("anna@example.com");
        assertThat(result.getFirst().getAnimal().getName()).isEqualTo("Rex");
    }

    @Test
    void findRecentAdoptionsByAnimalType_shouldIncludeAdoptionExactlyAtCutoff() {
        // whole seconds only: the database stores a rounded timestamp, so nanoseconds would make this flaky
        LocalDateTime since = LocalDateTime.now().minusDays(30).truncatedTo(ChronoUnit.SECONDS);

        Animal dog = persistAnimal("Rex", AnimalType.DOG);
        persistRecord(dog, "anna@example.com", since);
        entityManager.flush();
        entityManager.clear();

        List<AdoptionRecord> result =
                adoptionRecordRepository.findRecentAdoptionsByAnimalType(AnimalType.DOG, since);

        assertThat(result).hasSize(1);
    }

    @Test
    void findRecentAdoptionsByAnimalType_shouldReturnEmptyListWhenNothingMatches() {
        Animal cat = persistAnimal("Mia", AnimalType.CAT);
        persistRecord(cat, "carl@example.com", LocalDateTime.now().minusDays(1));
        entityManager.flush();
        entityManager.clear();

        List<AdoptionRecord> result = adoptionRecordRepository
                .findRecentAdoptionsByAnimalType(AnimalType.DOG, LocalDateTime.now().minusDays(30));

        assertThat(result).isEmpty();
    }

    // The animal must be persisted first: the record points to it (many-to-one).
    private Animal persistAnimal(String name, AnimalType type) {
        Animal animal = new Animal();
        animal.setName(name);
        animal.setType(type);
        animal.setStatus(AnimalStatus.ADOPTED);
        return entityManager.persist(animal);
    }

    private AdoptionRecord persistRecord(Animal animal, String adopterEmail, LocalDateTime adoptedAt) {
        AdoptionRecord record = new AdoptionRecord();
        record.setAnimal(animal);
        record.setAdopterEmail(adopterEmail);
        record.setAdoptedAt(adoptedAt);
        return entityManager.persist(record);
    }
}