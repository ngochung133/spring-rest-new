package vn.hoidanit.springrestwithai.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import vn.hoidanit.springrestwithai.exception.DuplicateResourceException;
import vn.hoidanit.springrestwithai.exception.ResourceNotFoundException;
import vn.hoidanit.springrestwithai.model.User;
import vn.hoidanit.springrestwithai.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("Lấy danh sách users")

    void testGetAllUsers_ShouldReturnList(){
        User user = new User();
        user.setEmail("test@example.com");
        user.setId(1L);
        when(userRepository.findAll()).thenReturn(List.of(user));
        List<User> result = userService.getAllUsers();
        assertEquals(1,result.size());
        assertEquals("test@example.com",result.get(0).getEmail());
        verify(userRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("Lấy người dùng theo Id")
    void testGetUserById_ShouldReturnUser(){
        User user = new User();
        user.setId(1L);
        user.setEmail("test@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        User result = userService.getUserById(1L);
        assertEquals("test@example.com",result.getEmail());
        verify(userRepository,times(1)).findById(1L);
    }

    @Test
    @DisplayName("Lấy người dùng theo Id không thành công")
    void testGetUserById_SholdThrowsException(){
        User user = new User();
        user.setId(1L);
        user.setEmail("test@example.com");
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        //   assertThatThrownBy(() -> userService.createUser(input))
        //             .isInstanceOf(DuplicateResourceException.class);
       assertThatThrownBy(() -> userService.getUserById(2L))
       .isInstanceOf(ResourceNotFoundException.class);
        verify(userRepository,times(1)).findById(2L);
    }

    // =========================================================
    // Nested group 1: Các trường hợp thành công
    // =========================================================
    @Nested
    @DisplayName("Khi tạo user thành công")
    class WhenCreateUserSucceeds {

        @Test
        @DisplayName("Happy path: tạo user thành công và trả về user đã được lưu")
        void createUser_shouldReturnSavedUser() {
            // Arrange
            User input = new User();
            input.setEmail("test@example.com");
            input.setName("Nguyen Van A");
            input.setPassword("rawPassword123");

            String encodedPassword = "encoded_rawPassword123";

            User savedUser = new User();
            savedUser.setId(1L);
            savedUser.setEmail("test@example.com");
            savedUser.setName("Nguyen Van A");
            savedUser.setPassword(encodedPassword);

            when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
            when(passwordEncoder.encode("rawPassword123")).thenReturn(encodedPassword);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            // Act
            User result = userService.createUser(input);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getEmail()).isEqualTo("test@example.com");
            assertThat(result.getPassword()).isEqualTo(encodedPassword);
        }

        @Test
        @DisplayName("Password được encode trước khi save: user được lưu có password đã encode (dùng argThat)")
        void createUser_shouldEncodePasswordBeforeSaving() {
            // Arrange
        String rawPassword = "rawPassword123";
        String encodedPassword = "encoded_rawPassword123";

        User input = new User();
        input.setEmail("test@example.com");
        input.setPassword(rawPassword);
        
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode(rawPassword)).thenReturn(encodedPassword);

        //Act
        userService.createUser(input);

        //Assert

        //kiểm tra đối tượng truyền vào function save(user) -> kiểm tra user
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User userSaved = userCaptor.getValue();
        assertThat(userSaved.getPassword()).isEqualTo(encodedPassword);
        }

        @Test
        @DisplayName("Thứ tự gọi: existsByEmail() phải được gọi TRƯỚC save()")
        void createUser_shouldCallExistsByEmailBeforeSave() {
            // Arrange
            User input = new User();
            input.setEmail("test@example.com");
            input.setPassword("rawPassword123");

            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("encoded");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            // Act
            userService.createUser(input);

            // Assert: verify thứ tự nghiêm ngặt bằng InOrder
            InOrder inOrder = inOrder(userRepository);
            inOrder.verify(userRepository).existsByEmail("test@example.com");
            inOrder.verify(userRepository).save(any(User.class));
        }
    }

    // =========================================================
    // Nested group 2: Các trường hợp thất bại
    // =========================================================
    @Nested
    @DisplayName("Khi tạo user thất bại")
    class WhenCreateUserFails {

        @Test
        @DisplayName("Email đã tồn tại: ném DuplicateResourceException với message chứa email")
        void createUser_whenEmailExists_shouldThrowDuplicateResourceException() {
            // Arrange
            String duplicateEmail = "duplicate@example.com";

            User input = new User();
            input.setEmail(duplicateEmail);
            input.setPassword("anyPassword");

            when(userRepository.existsByEmail(duplicateEmail)).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> userService.createUser(input))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining(duplicateEmail);
        }

        @Test
        @DisplayName("Email trùng lặp: save() KHÔNG được gọi")
        void createUser_whenEmailDuplicate_shouldNotCallSave() {
            // Arrange
            User input = new User();
            input.setEmail("duplicate@example.com");
            input.setPassword("anyPassword");

            when(userRepository.existsByEmail(anyString())).thenReturn(true);

            // Act
            assertThatThrownBy(() -> userService.createUser(input))
                    .isInstanceOf(DuplicateResourceException.class);

            // Assert: save() không bao giờ được gọi
            verify(userRepository, never()).save(any(User.class));
        }
    }
}
