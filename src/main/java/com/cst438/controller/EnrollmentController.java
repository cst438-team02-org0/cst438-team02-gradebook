package com.cst438.controller;

import com.cst438.domain.*;
import com.cst438.dto.EnrollmentDTO;
import com.cst438.service.RegistrarServiceProxy;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

import java.util.List;

@RestController
public class EnrollmentController {

    private final EnrollmentRepository enrollmentRepository;
    private final SectionRepository sectionRepository;
    private final RegistrarServiceProxy registrar;

    public EnrollmentController (
            EnrollmentRepository enrollmentRepository,
            SectionRepository sectionRepository,
            RegistrarServiceProxy registrar
    ) {
        this.enrollmentRepository = enrollmentRepository;
        this.sectionRepository = sectionRepository;
        this.registrar = registrar;
    }


    // instructor gets student enrollments with grades for a section
    @PreAuthorize("hasAuthority('SCOPE_ROLE_INSTRUCTOR')")
    @GetMapping("/sections/{sectionNo}/enrollments")
    public List<EnrollmentDTO> getEnrollments(
            @PathVariable("sectionNo") int sectionNo, Principal principal ) {

        Section section = sectionRepository.findById(sectionNo).orElse(null);
        if (section == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "section not found");
        }

        if (!section.getInstructorEmail().equals(principal.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "User is not the instructor for this section");
        }

        List<Enrollment> enrollments =
                enrollmentRepository.findEnrollmentsBySectionNoOrderByStudentName(sectionNo);

        return enrollments.stream().map(e -> new EnrollmentDTO(
                e.getEnrollmentId(),
                e.getGrade(),
                e.getStudent().getId(),
                e.getStudent().getName(),
                e.getStudent().getEmail(),
                e.getSection().getCourse().getCourseId(),
                e.getSection().getCourse().getTitle(),
                e.getSection().getSectionId(),
                e.getSection().getSectionNo(),
                e.getSection().getBuilding(),
                e.getSection().getRoom(),
                e.getSection().getTimes(),
                e.getSection().getCourse().getCredits(),
                e.getSection().getTerm().getYear(),
                e.getSection().getTerm().getSemester()
        )).toList();
    }

    // instructor updates enrollment grades
    @PreAuthorize("hasAuthority('SCOPE_ROLE_INSTRUCTOR')")
    @PutMapping("/enrollments")
    public void updateEnrollmentGrade(@Valid @RequestBody List<EnrollmentDTO> dtoList, Principal principal) {

        for (EnrollmentDTO dto : dtoList) {

            Enrollment enrollment = enrollmentRepository.findById(dto.enrollmentId()).orElse(null);
            if (enrollment == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "enrollment not found, id=" + dto.enrollmentId());
            }

            if (!enrollment.getSection().getInstructorEmail().equals(principal.getName())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "logged in user is not the instructor for this section");
            }

            enrollment.setGrade(dto.grade());
            enrollmentRepository.save(enrollment);

            EnrollmentDTO updated = new EnrollmentDTO(
                    enrollment.getEnrollmentId(),
                    enrollment.getGrade(),
                    enrollment.getStudent().getId(),
                    enrollment.getStudent().getName(),
                    enrollment.getStudent().getEmail(),
                    enrollment.getSection().getCourse().getCourseId(),
                    enrollment.getSection().getCourse().getTitle(),
                    enrollment.getSection().getSectionId(),
                    enrollment.getSection().getSectionNo(),
                    enrollment.getSection().getBuilding(),
                    enrollment.getSection().getRoom(),
                    enrollment.getSection().getTimes(),
                    enrollment.getSection().getCourse().getCredits(),
                    enrollment.getSection().getTerm().getYear(),
                    enrollment.getSection().getTerm().getSemester()
            );

            registrar.sendMessage("updateEnrollment", updated);
        }
    }
}
