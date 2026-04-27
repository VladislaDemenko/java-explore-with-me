package ru.practicum.main.category.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.main.category.dto.CategoryDto;
import ru.practicum.main.category.dto.NewCategoryDto;
import ru.practicum.main.category.service.CategoryService;

import jakarta.validation.Valid;

@Slf4j
@RestController
@RequestMapping(CategoryAdminController.BASE_PATH)
@RequiredArgsConstructor
@Validated
public class CategoryAdminController {

    public static final String BASE_PATH = "/admin/categories";
    public static final String CAT_ID_PATH = "/{catId}";

    private final CategoryService categoryService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryDto createCategory(@Valid @RequestBody NewCategoryDto newCategoryDto) {
        log.info("POST {} - {}", BASE_PATH, newCategoryDto);
        return categoryService.createCategory(newCategoryDto);
    }

    @PatchMapping(CAT_ID_PATH)
    public CategoryDto updateCategory(@PathVariable Long catId,
                                      @Valid @RequestBody CategoryDto categoryDto) {
        log.info("PATCH {}/{} - {}", BASE_PATH, catId, categoryDto);
        return categoryService.updateCategory(catId, categoryDto);
    }

    @DeleteMapping(CAT_ID_PATH)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable Long catId) {
        log.info("DELETE {}/{}", BASE_PATH, catId);
        categoryService.deleteCategory(catId);
    }
}