package com.smartpay.payment.service;

import com.smartpay.payment.dto.TransactionResponse;
import com.smartpay.payment.entity.Transaction;
import com.smartpay.payment.entity.enums.TransactionStatus;
import com.smartpay.payment.entity.enums.TransactionType;
import com.smartpay.payment.mapper.TransactionMapper;
import com.smartpay.payment.repository.TransactionRepository;
import com.smartpay.shared.dto.PageResponse;
import com.smartpay.shared.exception.ApiException;
import com.smartpay.shared.exception.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

/** Paginated, filtered history. Customers see only their own sent/received transactions. */
@Service
public class TransactionQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final TransactionRepository transactionRepository;

    public TransactionQueryService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> search(Long callerUserId, boolean admin, int page, int size,
                                                    String status, String type, LocalDateTime from,
                                                    LocalDateTime to, String query) {
        Specification<Transaction> spec = Specification.where(null);

        if (!admin) {
            Long userId = callerUserId;
            spec = spec.and((root, ignored, cb) -> cb.or(
                    cb.equal(root.get("senderUserId"), userId),
                    cb.equal(root.get("receiverUserId"), userId)));
        }
        if (status != null && !status.isBlank()) {
            TransactionStatus parsed = parseEnum(TransactionStatus.class, status, "status");
            spec = spec.and((root, ignored, cb) -> cb.equal(root.get("status"), parsed));
        }
        if (type != null && !type.isBlank()) {
            TransactionType parsed = parseEnum(TransactionType.class, type, "type");
            spec = spec.and((root, ignored, cb) -> cb.equal(root.get("type"), parsed));
        }
        if (from != null) {
            spec = spec.and((root, ignored, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        if (to != null) {
            spec = spec.and((root, ignored, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to));
        }
        if (query != null && !query.isBlank()) {
            String pattern = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, ignored, cb) -> cb.or(
                    cb.like(cb.lower(root.get("reference")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern)));
        }

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));

        Page<Transaction> result = transactionRepository.findAll(spec, pageable);
        return PageResponse.from(result.map(TransactionMapper::toResponse));
    }

    private <E extends Enum<E>> E parseEnum(Class<E> enumType, String value, String field) {
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Invalid " + field);
        }
    }
}
