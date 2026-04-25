package ru.practicum.main.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.main.exception.NotFoundException;
import ru.practicum.main.user.dto.NewUserRequest;
import ru.practicum.main.user.dto.UserDto;
import ru.practicum.main.user.model.User;
import ru.practicum.main.user.repository.UserRepository;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public List<UserDto> getUsers(List<Long> ids, int from, int size) {
        log.info("Getting users with ids={}, from={}, size={}", ids, from, size);
        PageRequest pageRequest = PageRequest.of(from / size, size);

        List<User> users;
        if (ids != null && !ids.isEmpty()) {
            users = userRepository.findByIdIn(ids, pageRequest);
        } else {
            users = userRepository.findAll(pageRequest).getContent();
        }

        return users.stream()
                .map(this::toUserDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public UserDto createUser(NewUserRequest newUserRequest) {
        log.info("Creating user: {}", newUserRequest);

        User user = User.builder()
                .name(newUserRequest.getName())
                .email(newUserRequest.getEmail())
                .build();

        user = userRepository.save(user);
        return toUserDto(user);
    }

    @Transactional
    public void deleteUser(Long userId) {
        log.info("Deleting user with id={}", userId);
        getUserById(userId);
        userRepository.deleteById(userId);
    }

    private User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with id=" + userId + " was not found"));
    }

    private UserDto toUserDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .build();
    }
}