package ru.practicum.main.compilation.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.practicum.main.compilation.dto.CompilationDto;
import ru.practicum.main.compilation.dto.NewCompilationDto;
import ru.practicum.main.compilation.dto.UpdateCompilationRequest;
import ru.practicum.main.compilation.service.CompilationService;

import jakarta.validation.Valid;

@Slf4j
@RestController
@RequestMapping(CompilationAdminController.BASE_PATH)
@RequiredArgsConstructor
public class CompilationAdminController {

    public static final String BASE_PATH = "/admin/compilations";
    public static final String COMP_ID_PATH = "/{compId}";

    private final CompilationService compilationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CompilationDto createCompilation(@Valid @RequestBody NewCompilationDto newCompilationDto) {
        log.info("POST {} - {}", BASE_PATH, newCompilationDto);
        return compilationService.createCompilation(newCompilationDto);
    }

    @DeleteMapping(COMP_ID_PATH)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCompilation(@PathVariable Long compId) {
        log.info("DELETE {}/{}", BASE_PATH, compId);
        compilationService.deleteCompilation(compId);
    }

    @PatchMapping(COMP_ID_PATH)
    public CompilationDto updateCompilation(@PathVariable Long compId,
                                            @Valid @RequestBody UpdateCompilationRequest request) {
        log.info("PATCH {}/{} - {}", BASE_PATH, compId, request);
        return compilationService.updateCompilation(compId, request);
    }
}