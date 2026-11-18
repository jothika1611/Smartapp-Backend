package com.smartpay.fraud.service;

import com.smartpay.fraud.dto.FraudRuleRequest;
import com.smartpay.fraud.dto.FraudRuleResponse;
import com.smartpay.fraud.entity.FraudRule;
import com.smartpay.fraud.repository.FraudRuleRepository;
import com.smartpay.shared.dto.PageResponse;
import com.smartpay.shared.exception.ApiException;
import com.smartpay.shared.exception.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;

/** ADMIN CRUD for scoring rules. DELETE is a soft disable to preserve historical rule hits. */
@Service
public class FraudRuleAdminService {

    private final FraudRuleRepository ruleRepository;

    public FraudRuleAdminService(FraudRuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<FraudRuleResponse> list(int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(0, page), clampSize(size), Sort.by("id"));
        Page<FraudRuleResponse> rules = ruleRepository.findAll(pageable)
                .map(FraudRuleResponse::from);
        return PageResponse.from(rules);
    }

    @Transactional
    public FraudRuleResponse create(FraudRuleRequest request) {
        if (request == null || request.getCode() == null || request.getCode().isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Rule code is required");
        }
        String code = request.getCode().trim().toUpperCase(Locale.ROOT);
        if (code.length() > 50) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Rule code is too long");
        }
        if (ruleRepository.existsByCode(code)) {
            throw new ApiException(ErrorCode.RULE_CODE_EXISTS);
        }
        FraudRule rule = new FraudRule();
        rule.setCode(code);
        applyMutableFields(rule, request, true);
        return FraudRuleResponse.from(ruleRepository.save(rule));
    }

    @Transactional
    public FraudRuleResponse update(Long ruleId, FraudRuleRequest request) {
        FraudRule rule = load(ruleId);
        if (request == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Request body is required");
        }
        // The code is an immutable identifier used by persisted hits; it is never changed here.
        applyMutableFields(rule, request, false);
        return FraudRuleResponse.from(ruleRepository.save(rule));
    }

    @Transactional
    public FraudRuleResponse disable(Long ruleId) {
        FraudRule rule = load(ruleId);
        rule.setEnabled(false);
        return FraudRuleResponse.from(ruleRepository.save(rule));
    }

    private FraudRule load(Long ruleId) {
        return ruleRepository.findById(ruleId)
                .orElseThrow(() -> new ApiException(ErrorCode.RULE_NOT_FOUND));
    }

    private void applyMutableFields(FraudRule rule, FraudRuleRequest request, boolean creating) {
        String name = request.getName() == null ? null : request.getName().trim();
        if (name == null || name.isBlank() || name.length() > 120) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Rule name is required (max 120 chars)");
        }
        Integer maxPoints = request.getMaxPoints();
        if (maxPoints == null || maxPoints < 1 || maxPoints > 100) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "maxPoints must be between 1 and 100");
        }
        Boolean enabled = request.getEnabled() == null ? Boolean.TRUE : request.getEnabled();
        BigDecimal threshold = request.getThresholdValue();
        if (threshold != null && threshold.signum() < 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "thresholdValue cannot be negative");
        }
        Integer window = request.getTimeWindowMinutes();
        if (window != null && window <= 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "timeWindowMinutes must be positive");
        }
        String description = request.getDescription();
        if (description != null && description.length() > 500) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "description is too long");
        }
        String category = request.getCategory();
        if (category != null && category.length() > 50) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "category is too long");
        }

        rule.setName(name);
        rule.setDescription(description);
        rule.setCategory(category);
        rule.setMaxPoints(maxPoints);
        rule.setThresholdValue(threshold);
        rule.setTimeWindowMinutes(window);
        rule.setEnabled(enabled);
        if (creating && description == null) {
            rule.setDescription("ADMIN-created simulated fraud rule");
        }
    }

    private int clampSize(int size) {
        return Math.max(1, Math.min(100, size));
    }
}
