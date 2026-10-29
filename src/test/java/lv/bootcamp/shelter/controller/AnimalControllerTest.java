package lv.bootcamp.shelter.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lv.bootcamp.shelter.config.SecurityConfig;
import lv.bootcamp.shelter.dto.AnimalCreateRequest;
import lv.bootcamp.shelter.dto.AnimalResponse;
import lv.bootcamp.shelter.model.AnimalStatus;
import lv.bootcamp.shelter.model.AnimalType;
import lv.bootcamp.shelter.service.AnimalNotFoundException;
import lv.bootcamp.shelter.service.AnimalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
/**
 * Ticket: ANIMAL-2 (see README).
 * findById_shouldReturn404WhenNotFound is already implemented as a worked
 * example (also see AdopterControllerTest for the same pattern) — add the
 * remaining tests yourself.
 * <p>
 * @Import(SecurityConfig.class) is required: without it, this slice falls
 * back to Spring Boot's default "require auth for everything" security
 * instead of this project's actual (permissive, for these endpoints) rules.
 */
@WebMvcTest(AnimalController.class)
@Import(SecurityConfig.class)
class AnimalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AnimalService animalService;

    @Test
    void findById_shouldReturn404WhenNotFound() throws Exception {
        when(animalService.findById(99L)).thenThrow(new AnimalNotFoundException(99L));

        mockMvc.perform(get("/api/animals/99"))
                .andExpect(status().isNotFound());
    }
    // ---- ANIMAL-2: the two  tests ----

    @Test
    void findAll_shouldReturnAnimalsAsJson() throws Exception {
        when(animalService.findAll()).thenReturn(List.of(
                new AnimalResponse(1L, "Rex", AnimalType.DOG, "Labrador", 3, "Friendly", AnimalStatus.AVAILABLE),
                new AnimalResponse(2L, "Luna", AnimalType.CAT, "Siamese", 2, "Calm", AnimalStatus.RESERVED)));

        mockMvc.perform(get("/api/animals"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Rex"))
                .andExpect(jsonPath("$[0].type").value("DOG"))
                .andExpect(jsonPath("$[1].name").value("Luna"))
                .andExpect(jsonPath("$[1].status").value("RESERVED"));
    }

    @Test
    void create_shouldReturn400AndNotCallServiceWhenBodyIsInvalid() throws Exception {
        // blank name, missing type and negative age all break the validation rules
        AnimalCreateRequest invalid = new AnimalCreateRequest("", null, null, -1, null, null);

        mockMvc.perform(post("/api/animals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(3)));

        verifyNoInteractions(animalService);
    }

    // ---- Bonus: success path ----

    @Test
    void create_shouldReturn201WithCreatedAnimalWhenBodyIsValid() throws Exception {
        AnimalCreateRequest request = new AnimalCreateRequest(
                "Rex", AnimalType.DOG, "Labrador", 3, "Friendly", "985112000123456");
        when(animalService.create(any(AnimalCreateRequest.class))).thenReturn(
                new AnimalResponse(7L, "Rex", AnimalType.DOG, "Labrador", 3, "Friendly", AnimalStatus.AVAILABLE));

        mockMvc.perform(post("/api/animals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.name").value("Rex"))
                .andExpect(jsonPath("$.type").value("DOG"))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));

        // the JSON body really arrived at the service as the object we sent
        ArgumentCaptor<AnimalCreateRequest> captor = ArgumentCaptor.forClass(AnimalCreateRequest.class);
        verify(animalService).create(captor.capture());
        assertThat(captor.getValue()).isEqualTo(request);
    }
}
