package ru.practicum.main.category.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.main.category.dto.CategoryDto;
import ru.practicum.main.category.dto.NewCategoryDto;
import ru.practicum.main.category.model.Category;
import ru.practicum.main.category.repository.CategoryRepository;
import ru.practicum.main.event.repository.EventRepository;
import ru.practicum.main.exception.ConflictException;
import ru.practicum.main.exception.NotFoundException;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final EventRepository eventRepository;

    public List<CategoryDto> getCategories(int from, int size) {
        log.info("Getting categories from={}, size={}", from, size);
        PageRequest pageRequest = PageRequest.of(from / size, size);

        return categoryRepository.findAll(pageRequest).getContent().stream()
                .map(this::toCategoryDto)
                .collect(Collectors.toList());
    }

    public CategoryDto getCategory(Long catId) {
        log.info("Getting category with id={}", catId);
        Category category = getCategoryById(catId);
        return toCategoryDto(category);
    }

    @Transactional
    public CategoryDto createCategory(NewCategoryDto newCategoryDto) {
        log.info("Creating category: {}", newCategoryDto);

        if (categoryRepository.existsByName(newCategoryDto.getName())) {
            throw new ConflictException("Category name must be unique");
        }

        Category category = Category.builder()
                .name(newCategoryDto.getName())
                .build();

        category = categoryRepository.save(category);
        return toCategoryDto(category);
    }

    @Transactional
    public CategoryDto updateCategory(Long catId, CategoryDto categoryDto) {
        log.info("Updating category with id={}", catId);

        Category category = getCategoryById(catId);

        if (!category.getName().equals(categoryDto.getName()) &&
                categoryRepository.existsByName(categoryDto.getName())) {
            throw new ConflictException("Category name must be unique");
        }

        category.setName(categoryDto.getName());
        category = categoryRepository.save(category);
        return toCategoryDto(category);
    }

    @Transactional
    public void deleteCategory(Long catId) {
        log.info("Deleting category with id={}", catId);

        Category category = getCategoryById(catId);

        if (!eventRepository.findByCategoryId(catId).isEmpty()) {
            throw new ConflictException("The category is not empty");
        }

        categoryRepository.delete(category);
    }

    private Category getCategoryById(Long catId) {
        return categoryRepository.findById(catId)
                .orElseThrow(() -> new NotFoundException("Category with id=" + catId + " was not found"));
    }

    private CategoryDto toCategoryDto(Category category) {
        return CategoryDto.builder()
                .id(category.getId())
                .name(category.getName())
                .build();
    }
}