package com.pm.patientservice.service;

import com.pm.patientservice.dto.PatientRequestDTO;
import com.pm.patientservice.dto.PatientResponseDTO;
import com.pm.patientservice.exception.EmailAlreadyExistsException;
import com.pm.patientservice.exception.PatientNotFoundException;
import com.pm.patientservice.grpc.BillingServiceGrpcClient;
import com.pm.patientservice.mapper.PatientMapper;
import com.pm.patientservice.model.Patient;
import com.pm.patientservice.repository.PatientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class PatientService {
    private static final Logger log = LoggerFactory.getLogger(PatientService.class);
    private final PatientRepository patientRepository;
    private final BillingServiceGrpcClient billingServiceGrpcClient;

    public PatientService(PatientRepository patientRepository, BillingServiceGrpcClient billingServiceGrpcClient) {
        this.patientRepository = patientRepository;
        this.billingServiceGrpcClient = billingServiceGrpcClient;
    }

    public List<PatientResponseDTO> getPatients() {
        List<Patient> patients = patientRepository.findAll();

        return patients.stream().map(
                PatientMapper::toDTO).toList();
    }

    public PatientResponseDTO createPatient(PatientRequestDTO patientRequestDTO) {
        if (patientRepository.existsByEmail(patientRequestDTO.getEmail())) {
            throw new EmailAlreadyExistsException(
                    "A patient with this email: " + patientRequestDTO.getEmail() + " already exists");
        }

        Patient newPatient = patientRepository.save(PatientMapper.toEntity(patientRequestDTO));
        log.info("📌 Сохранили пациента. Сейчас будем вызывать BillingService.");
        billingServiceGrpcClient.createBillingAccount(
                newPatient.getId().toString(),
                newPatient.getName(),
                newPatient.getEmail());

        return PatientMapper.toDTO(newPatient);
    }

    public PatientResponseDTO updatePatient(UUID patientId, PatientRequestDTO patientRequestDTO) {
        Patient patient = patientRepository.findById(patientId).orElseThrow(() -> new PatientNotFoundException("Patient not found with ID: " + patientId));

        if (patientRepository.existsByEmailAndIdNot(patientRequestDTO.getEmail(), patientId)) {
            throw new EmailAlreadyExistsException("A patient with this email: " + patientRequestDTO.getEmail() + " already exists");
        }

        patient.setName(patientRequestDTO.getName());
        patient.setAddress(patientRequestDTO.getAddress());
        patient.setEmail(patientRequestDTO.getEmail());
        patient.setBirthDate(LocalDate.parse(patientRequestDTO.getDateOfBirth()));
        if (patientRequestDTO.getRegisteredDate() != null) {
            patient.setRegisteredDate(LocalDate.parse(patientRequestDTO.getRegisteredDate()));
        }
        patientRepository.save(patient);
        return PatientMapper.toDTO(patient);
    }

    public void deletePatient(UUID patientId) {
        Patient patient = patientRepository.findById(patientId).orElseThrow(() -> new PatientNotFoundException("Patient not found with ID: " + patientId));
        patientRepository.delete(patient);
    }
}
