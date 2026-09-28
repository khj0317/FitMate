package com.fitmate.domain.sport;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "운동 종목")
@RestController
@RequestMapping("/api/sports")
@RequiredArgsConstructor
public class SportController {

    private final SportRepository sportRepository;

    @Operation(summary = "운동 종목 목록")
    @GetMapping
    public List<SportResponse> getSports() {
        return sportRepository.findAll(Sort.by("id")).stream()
                .map(SportResponse::from)
                .toList();
    }

    public record SportResponse(Short id, String code, String name) {
        static SportResponse from(Sport sport) {
            return new SportResponse(sport.getId(), sport.getCode(), sport.getName());
        }
    }
}
