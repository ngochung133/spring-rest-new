package vn.hoidanit.springrestwithai.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import vn.hoidanit.springrestwithai.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class UserControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    // =========================================================
    // NESTED GROUP 1: Happy Path
    // =========================================================
    @Nested
    @DisplayName("Happy Path - Tạo user thành công")
    class HappyPath {

        private static final String VALID_EMAIL = "happypath@example.com";
        private static final String RAW_PASSWORD = "secret123";

        private String validJson() {
            return """
                    {
                        "name": "Nguyen Van A",
                        "email": "%s",
                        "password": "%s"
                    }
                    """.formatted(VALID_EMAIL, RAW_PASSWORD);
        }

        @Test
        @DisplayName("Trả về 201 Created khi data hợp lệ")
        void createUser_withValidData_shouldReturn201() throws Exception {
            mockMvc.perform(post("/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validJson())
                    .with(jwt()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.statusCode").value(201));
        }

        @Test
        @DisplayName("Response body có đủ fields bắt buộc (id, name, email)")
        void createUser_shouldReturnRequiredFieldsInResponse() throws Exception {
            mockMvc.perform(post("/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validJson())
                    .with(jwt()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.id").exists())
                    .andExpect(jsonPath("$.data.name").value("Nguyen Van A"))
                    .andExpect(jsonPath("$.data.email").value(VALID_EMAIL));
        }

        @Test
        @DisplayName("Password KHÔNG được trả về trong response (security)")
        void createUser_shouldNotReturnPasswordInResponse() throws Exception {
            // NOTE: Test này đang kiểm tra yêu cầu bảo mật.
            // Cần thêm @JsonIgnore vào field password trong User entity
            // để test này pass. Nếu test FAIL, chứng tỏ password đang bị lộ.
            mockMvc.perform(post("/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validJson())
                    .with(jwt()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.password").doesNotExist());
        }

        @Test
        @DisplayName("Data được lưu vào database đúng sau khi tạo")
        void createUser_shouldSaveCorrectDataToDatabase() throws Exception {
            mockMvc.perform(post("/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validJson())
                    .with(jwt()))
                    .andExpect(status().isCreated());

            var savedUser = userRepository.findByEmail(VALID_EMAIL);
            assertThat(savedUser).isNotNull();
            assertThat(savedUser.getName()).isEqualTo("Nguyen Van A");
            assertThat(savedUser.getEmail()).isEqualTo(VALID_EMAIL);
        }

        @Test
        @DisplayName("Password trong database đã được encode, không phải plain text")
        void createUser_shouldEncodePasswordInDatabase() throws Exception {
            mockMvc.perform(post("/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validJson())
                    .with(jwt()))
                    .andExpect(status().isCreated());

            var savedUser = userRepository.findByEmail(VALID_EMAIL);
            assertThat(savedUser).isNotNull();
            assertThat(savedUser.getPassword())
                    .isNotEqualTo(RAW_PASSWORD) // không phải plain text
                    .startsWith("$2a$"); // BCrypt hash prefix
        }
    }

    // =========================================================
    // NESTED GROUP 2: Validation Errors
    // =========================================================
    @Nested
    @DisplayName("Validation Errors - Lỗi 400 khi data không hợp lệ")
    class ValidationErrors {

        @Test
        @DisplayName("Trả về 400 khi name để trống")
        void createUser_withBlankName_shouldReturn400() throws Exception {
            String json = """
                    {
                        "name": "",
                        "email": "valid@example.com",
                        "password": "secret123"
                    }
                    """;

            mockMvc.perform(post("/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json)
                    .with(jwt()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.statusCode").value(400));
        }

        @Test
        @DisplayName("Trả về 400 khi email để trống")
        void createUser_withBlankEmail_shouldReturn400() throws Exception {
            String json = """
                    {
                        "name": "Nguyen Van A",
                        "email": "",
                        "password": "secret123"
                    }
                    """;

            mockMvc.perform(post("/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json)
                    .with(jwt()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.statusCode").value(400));
        }

        @Test
        @DisplayName("Trả về 400 khi email không đúng định dạng")
        void createUser_withInvalidEmailFormat_shouldReturn400() throws Exception {
            String json = """
                    {
                        "name": "Nguyen Van A",
                        "email": "not-an-email",
                        "password": "secret123"
                    }
                    """;

            mockMvc.perform(post("/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json)
                    .with(jwt()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.statusCode").value(400))
                    .andExpect(jsonPath("$.details").isArray());
        }

        @Test
        @DisplayName("Trả về 400 khi password ít hơn 6 ký tự")
        void createUser_withShortPassword_shouldReturn400() throws Exception {
            String json = """
                    {
                        "name": "Nguyen Van A",
                        "email": "valid@example.com",
                        "password": "123"
                    }
                    """;

            mockMvc.perform(post("/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json)
                    .with(jwt()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.statusCode").value(400))
                    .andExpect(jsonPath("$.details").isArray());
        }

        @Test
        @DisplayName("KHÔNG lưu data vào database khi validation fail")
        void createUser_whenValidationFails_shouldNotSaveToDatabase() throws Exception {
            String uniqueEmail = "no-save-validation@example.com";
            String json = """
                    {
                        "name": "",
                        "email": "%s",
                        "password": "secret123"
                    }
                    """.formatted(uniqueEmail);

            mockMvc.perform(post("/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json)
                    .with(jwt()))
                    .andExpect(status().isBadRequest());

            assertThat(userRepository.existsByEmail(uniqueEmail)).isFalse();
        }
    }

    // =========================================================
    // NESTED GROUP 3: Business Errors
    // =========================================================
    @Nested
    @DisplayName("Business Errors - Lỗi business logic")
    class BusinessErrors {

        private static final String DUPLICATE_EMAIL = "duplicate@example.com";

        private String jsonWithEmail(String email) {
            return """
                    {
                        "name": "Nguyen Van A",
                        "email": "%s",
                        "password": "secret123"
                    }
                    """.formatted(email);
        }

        @Test
        @DisplayName("Trả về 409 Conflict khi email đã tồn tại trong database")
        void createUser_withDuplicateEmail_shouldReturn409() throws Exception {
            // Tạo user đầu tiên thành công
            mockMvc.perform(post("/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonWithEmail(DUPLICATE_EMAIL))
                    .with(jwt()))
                    .andExpect(status().isCreated());

            // Tạo user thứ hai với cùng email → 409
            mockMvc.perform(post("/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonWithEmail(DUPLICATE_EMAIL))
                    .with(jwt()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.statusCode").value(409))
                    .andExpect(jsonPath("$.error").value("Conflict"));
        }

        @Test
        @DisplayName("KHÔNG tạo user mới khi email đã tồn tại")
        void createUser_whenEmailDuplicate_shouldNotCreateNewUser() throws Exception {
            // Tạo user đầu tiên thành công
            mockMvc.perform(post("/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonWithEmail(DUPLICATE_EMAIL))
                    .with(jwt()))
                    .andExpect(status().isCreated());

            long countAfterFirst = userRepository.count();

            // Cố tạo user thứ hai với email trùng → thất bại
            mockMvc.perform(post("/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonWithEmail(DUPLICATE_EMAIL))
                    .with(jwt()))
                    .andExpect(status().isConflict());

            // Tổng số user không tăng thêm
            assertThat(userRepository.count()).isEqualTo(countAfterFirst);
        }
    }
}
