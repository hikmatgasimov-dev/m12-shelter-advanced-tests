package lv.bootcamp.shelter;

import jakarta.persistence.EntityManager;
import lv.bootcamp.shelter.client.NotificationClient;
import lv.bootcamp.shelter.dto.AdoptionRequest;
import lv.bootcamp.shelter.dto.AnimalCreateRequest;
import lv.bootcamp.shelter.dto.AnimalResponse;
import lv.bootcamp.shelter.model.AnimalStatus;
import lv.bootcamp.shelter.model.AnimalType;
import lv.bootcamp.shelter.repository.AnimalRepository;
import lv.bootcamp.shelter.service.AnimalNotFoundException;
import lv.bootcamp.shelter.service.AnimalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Transactional
class AdoptionIntegrationTest {

    @Autowired
    private AnimalService animalService;

    @Autowired
    private AnimalRepository animalRepository;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private NotificationClient notificationClient;

    @Test
    void adoptionFlow_shouldPersistStatusAndNotify() {
        AnimalResponse created = animalService.create(new AnimalCreateRequest(
                "Rex", AnimalType.DOG, "Labrador", 3, "Friendly", "985112000123456"));
        assertThat(created.id()).isNotNull();
        assertThat(created.status()).isEqualTo(AnimalStatus.AVAILABLE);

        AnimalResponse adopted = animalService.adopt(
                new AdoptionRequest(created.id(), "Anna", "anna@example.com"));

        assertThat(adopted.status()).isEqualTo(AnimalStatus.ADOPTED);

        // write to the real database and read it back, instead of trusting the object in memory
        entityManager.flush();
        entityManager.clear();
        assertThat(animalRepository.findById(created.id()).orElseThrow().getStatus())
                .isEqualTo(AnimalStatus.ADOPTED);

        verify(notificationClient).sendAdoptionNotification(created.id(), "Rex", "anna@example.com");
    }

    @Test
    void adoptingTheSameAnimalTwice_shouldFailAndNotifyOnlyOnce() {
        AnimalResponse created = animalService.create(new AnimalCreateRequest(
                "Luna", AnimalType.CAT, "Siamese", 2, "Calm", null));
        AdoptionRequest request = new AdoptionRequest(created.id(), "Anna", "anna@example.com");

        animalService.adopt(request);

        assertThatThrownBy(() -> animalService.adopt(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not available");
        verify(notificationClient, times(1)).sendAdoptionNotification(any(), any(), any());
    }

    @Test
    void adoptingUnknownAnimal_shouldFailAndNotNotify() {
        assertThatThrownBy(() -> animalService.adopt(new AdoptionRequest(9999L, "Anna", "anna@example.com")))
                .isInstanceOf(AnimalNotFoundException.class);
        verify(notificationClient, never()).sendAdoptionNotification(any(), any(), any());
    }
}