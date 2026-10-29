

package lv.bootcamp.shelter.service;

import lv.bootcamp.shelter.audit.AuditLogger;
import lv.bootcamp.shelter.audit.RejectionReason;
import lv.bootcamp.shelter.client.NotificationClient;
import lv.bootcamp.shelter.model.Adopter;
import lv.bootcamp.shelter.model.AdoptionResult;
import lv.bootcamp.shelter.model.Animal;
import lv.bootcamp.shelter.model.AnimalStatus;
import lv.bootcamp.shelter.model.AnimalType;
import lv.bootcamp.shelter.repository.AdopterRepository;
import lv.bootcamp.shelter.repository.AnimalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdoptionEligibilityServiceTest {

    private static final Long ADOPTER_ID = 1L;
    private static final Long ANIMAL_ID = 2L;

    @Mock private AdopterRepository adopterRepository;
    @Mock private AnimalRepository animalRepository;
    @Mock private NotificationClient notificationClient;
    @Mock private AuditLogger auditLogger;

    @InjectMocks
    private AdoptionEligibilityService service;

    // ---- Round 1: one test per branch of evaluateAdoption ----

    @Test
    void shouldRejectWhenAdopterNotFound() {
        when(adopterRepository.findById(ADOPTER_ID)).thenReturn(Optional.empty());

        AdoptionResult result = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertThat(result.approved()).isFalse();
        assertThat(result.reason()).isEqualTo(RejectionReasons.ADOPTER_NOT_FOUND);
    }

    @Test
    void shouldRejectWhenAnimalNotFound() {
        when(adopterRepository.findById(ADOPTER_ID)).thenReturn(Optional.of(adopter(30, 0, 0, false, false)));
        when(animalRepository.findById(ANIMAL_ID)).thenReturn(Optional.empty());

        AdoptionResult result = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertThat(result.approved()).isFalse();
        assertThat(result.reason()).isEqualTo(RejectionReasons.ANIMAL_NOT_FOUND);
    }

    @Test
    void shouldRejectWhenAnimalNotAvailable() {
        when(adopterRepository.findById(ADOPTER_ID)).thenReturn(Optional.of(adopter(30, 0, 0, false, false)));
        when(animalRepository.findById(ANIMAL_ID)).thenReturn(Optional.of(animal(AnimalType.DOG, AnimalStatus.ADOPTED, 3)));

        AdoptionResult result = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertThat(result.approved()).isFalse();
        assertThat(result.reason()).isEqualTo(RejectionReasons.ANIMAL_NOT_AVAILABLE);
    }

    @Test
    void shouldRejectUnderageAdopter() {
        givenAdopterAndAnimal(adopter(17, 0, 0, false, false), animal(AnimalType.DOG, AnimalStatus.AVAILABLE, 3));

        AdoptionResult result = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertThat(result.approved()).isFalse();
        assertThat(result.reason()).isEqualTo(RejectionReasons.UNDERAGE);
        verify(auditLogger).logRejection(ADOPTER_ID, ANIMAL_ID, RejectionReason.UNDERAGE);
    }

    @Test
    void shouldRejectWhenPetLimitReached() {
        givenAdopterAndAnimal(adopter(30, 3, 0, false, false), animal(AnimalType.DOG, AnimalStatus.AVAILABLE, 3));

        AdoptionResult result = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertThat(result.approved()).isFalse();
        assertThat(result.reason()).isEqualTo(RejectionReasons.PET_LIMIT_REACHED);
        verify(auditLogger).logRejection(ADOPTER_ID, ANIMAL_ID, RejectionReason.PET_LIMIT_REACHED);
    }

    @Test
    void shouldRejectExoticAnimalWithoutPermit() {
        givenAdopterAndAnimal(adopter(30, 0, 0, false, false), animal(AnimalType.BIRD, AnimalStatus.AVAILABLE, 3));

        AdoptionResult result = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertThat(result.approved()).isFalse();
        assertThat(result.reason()).isEqualTo(RejectionReasons.EXOTIC_PERMIT_REQUIRED);
        verify(auditLogger).logRejection(ADOPTER_ID, ANIMAL_ID, RejectionReason.NO_EXOTIC_PERMIT);
    }

    @Test
    void shouldApproveEligibleAdopterAndNotify() {
        Adopter adopter = adopter(30, 0, 0, false, false);
        givenAdopterAndAnimal(adopter, animal(AnimalType.DOG, AnimalStatus.AVAILABLE, 3));

        AdoptionResult result = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertThat(result.approved()).isTrue();
        verify(auditLogger).logApproval(ADOPTER_ID, ANIMAL_ID, 0);
        verify(notificationClient).sendApprovalNotification(adopter.getEmail(), "Rex");
    }

    // ---- Round 2: boundaries and branches that PiTest showed were not yet covered ----

    @Test
    void shouldApproveAdopterWhoIsExactlyEighteen() {
        givenAdopterAndAnimal(adopter(18, 0, 0, false, false), animal(AnimalType.DOG, AnimalStatus.AVAILABLE, 3));

        AdoptionResult result = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertThat(result.approved()).isTrue();
    }

    @Test
    void shouldApproveWhenBelowPetLimitOnSmallProperty() {
        givenAdopterAndAnimal(adopter(30, 2, 0, false, false), animal(AnimalType.DOG, AnimalStatus.AVAILABLE, 3));

        assertThat(service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID).approved()).isTrue();
    }

    @Test
    void shouldAllowLargePropertyOwnerMorePetsThanSmallProperty() {
        // 4 pets would be rejected on a small property (limit 3), but a large one allows up to 4
        givenAdopterAndAnimal(adopter(30, 4, 0, true, false), animal(AnimalType.DOG, AnimalStatus.AVAILABLE, 3));

        assertThat(service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID).approved()).isTrue();
    }

    @Test
    void shouldRejectLargePropertyOwnerAtFivePets() {
        givenAdopterAndAnimal(adopter(30, 5, 0, true, false), animal(AnimalType.DOG, AnimalStatus.AVAILABLE, 3));

        AdoptionResult result = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertThat(result.approved()).isFalse();
        assertThat(result.reason()).isEqualTo(RejectionReasons.PET_LIMIT_REACHED);
        verify(auditLogger).logRejection(ADOPTER_ID, ANIMAL_ID, RejectionReason.PET_LIMIT_REACHED);
    }

    @Test
    void shouldRejectRabbitWithoutExoticPermit() {
        givenAdopterAndAnimal(adopter(30, 0, 0, false, false), animal(AnimalType.RABBIT, AnimalStatus.AVAILABLE, 3));

        AdoptionResult result = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertThat(result.approved()).isFalse();
        assertThat(result.reason()).isEqualTo(RejectionReasons.EXOTIC_PERMIT_REQUIRED);
        verify(auditLogger).logRejection(ADOPTER_ID, ANIMAL_ID, RejectionReason.NO_EXOTIC_PERMIT);
    }

    @ParameterizedTest
    @EnumSource(value = AnimalType.class, names = {"BIRD", "RABBIT"})
    void shouldApproveExoticAnimalWhenAdopterHasPermit(AnimalType exoticType) {
        givenAdopterAndAnimal(adopter(30, 0, 0, false, true), animal(exoticType, AnimalStatus.AVAILABLE, 3));

        assertThat(service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID).approved()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = AnimalType.class, names = {"DOG", "CAT", "OTHER"})
    void shouldNotRequirePermitForNonExoticAnimals(AnimalType commonType) {
        givenAdopterAndAnimal(adopter(30, 0, 0, false, false), animal(commonType, AnimalStatus.AVAILABLE, 3));

        assertThat(service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID).approved()).isTrue();
    }

    @Test
    void shouldReportPriorityScoreInResultAuditAndNotification() {
        // previous adoption (+10) and large property (+15), no pets: score 25
        Adopter adopter = adopter(40, 0, 1, true, false);
        givenAdopterAndAnimal(adopter, animal(AnimalType.DOG, AnimalStatus.AVAILABLE, 3));

        AdoptionResult result = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertThat(result.approved()).isTrue();
        assertThat(result.priorityScore()).isEqualTo(25);
        verify(auditLogger).logApproval(ADOPTER_ID, ANIMAL_ID, 25);
        verify(notificationClient).sendApprovalNotification("anna@example.com", "Rex");
    }

    // ---- priority score: one row per rule, so every +/- and boundary is pinned down ----

    @ParameterizedTest(name = "previous={0}, large={1}, animalAge={2}, pets={3} -> score {4}")
    @CsvSource(nullValues = "null", value = {
            // previous, large, animalAge, pets, expected
            "0, false, 3,    0, 0",    // nothing applies
            "1, false, 3,    0, 10",   // at least one previous adoption: +10
            "3, false, 3,    0, 10",   // exactly 3 is not "more than 3"
            "4, false, 3,    0, 15",   // more than 3 previous adoptions: +10 and +5
            "0, true,  3,    0, 15",   // large property: +15
            "0, false, 7,    0, 0",    // animal age 7 is not a senior
            "0, false, 8,    0, 20",   // animal age 8 is a senior: +20
            "0, false, null, 0, 0",    // unknown animal age: no senior bonus
            "4, true,  8,    2, 46",   // 10+5+15+20 = 50, minus 2 pets * 2 = 4
            "0, false, 3,    5, 0"     // -10 would be negative, so the score never goes below 0
    })
    void calculatePriorityScore_shouldFollowEachRule(int previous, boolean large, Integer animalAge,
                                                     int pets, int expected) {
        Adopter adopter = adopter(30, pets, previous, large, false);
        Animal animal = animal(AnimalType.DOG, AnimalStatus.AVAILABLE, animalAge);

        assertThat(service.calculatePriorityScore(adopter, animal)).isEqualTo(expected);
    }

    // ---- helpers ----

    private void givenAdopterAndAnimal(Adopter adopter, Animal animal) {
        when(adopterRepository.findById(ADOPTER_ID)).thenReturn(Optional.of(adopter));
        when(animalRepository.findById(ANIMAL_ID)).thenReturn(Optional.of(animal));
    }

    private Adopter adopter(int age, int currentPets, int previousAdoptions, boolean largeProperty, boolean exoticPermit) {
        Adopter adopter = new Adopter();
        adopter.setId(ADOPTER_ID);
        adopter.setName("Anna");
        adopter.setEmail("anna@example.com");
        adopter.setAge(age);
        adopter.setCurrentPetCount(currentPets);
        adopter.setPreviousAdoptions(previousAdoptions);
        adopter.setLargeProperty(largeProperty);
        adopter.setExoticPermit(exoticPermit);
        return adopter;
    }

    private Animal animal(AnimalType type, AnimalStatus status, Integer age) {
        Animal animal = new Animal();
        animal.setId(ANIMAL_ID);
        animal.setName("Rex");
        animal.setType(type);
        animal.setStatus(status);
        animal.setAge(age);
        return animal;
    }
}