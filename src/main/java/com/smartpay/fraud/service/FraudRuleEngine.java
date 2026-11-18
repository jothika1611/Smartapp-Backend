package com.smartpay.fraud.service;

import com.smartpay.fraud.config.FraudProperties;
import com.smartpay.fraud.entity.FraudRule;
import com.smartpay.fraud.repository.FraudRuleRepository;
import com.smartpay.fraud.velocity.SqlVelocityCounterService;
import com.smartpay.fraud.velocity.VelocityCounterService;
import com.smartpay.payment.entity.Transaction;
import com.smartpay.payment.entity.enums.TransactionStatus;
import com.smartpay.payment.repository.TransactionRepository;
import com.smartpay.shared.fraud.FraudContext;
import com.smartpay.shared.fraud.FraudDecision;
import com.smartpay.shared.fraud.FraudScreener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Deterministic, explainable scoring. Rules and weights come from fraud_rules; transaction history
 * is read through repositories. The result never depends on an AI model.
 */
@Service
public class FraudRuleEngine implements FraudScreener {

    private static final String AMOUNT_DEVIATION = "AMOUNT_DEVIATION";
    private static final String VELOCITY = "VELOCITY";
    private static final String REPEATED_TRANSACTION = "REPEATED_TRANSACTION";
    private static final String UNFAMILIAR_DEVICE = "UNFAMILIAR_DEVICE";
    private static final String UNUSUAL_LOCATION = "UNUSUAL_LOCATION";
    private static final String FAILED_PATTERN = "FAILED_PATTERN";
    private static final int KNOWN_CONTEXT_LOOKBACK_MINUTES = 43200;

    private final FraudRuleRepository ruleRepository;
    private final TransactionRepository transactionRepository;
    private final FraudProperties properties;
    private final VelocityCounterService velocityCounterService;

    /** Kept for unit tests; uses the MySQL-backed counter by default. */
    public FraudRuleEngine(FraudRuleRepository ruleRepository,
                           TransactionRepository transactionRepository,
                           FraudProperties properties) {
        this(ruleRepository, transactionRepository, properties,
                new SqlVelocityCounterService(transactionRepository));
    }

    @Autowired
    public FraudRuleEngine(FraudRuleRepository ruleRepository,
                           TransactionRepository transactionRepository,
                           FraudProperties properties,
                           VelocityCounterService velocityCounterService) {
        this.ruleRepository = ruleRepository;
        this.transactionRepository = transactionRepository;
        this.properties = properties;
        this.velocityCounterService = velocityCounterService;
    }

    @Override
    @Transactional(readOnly = true)
    public FraudDecision assess(FraudContext context) {
        Map<String, FraudRule> rules = ruleRepository.findAllByEnabledTrue().stream()
                .collect(Collectors.toMap(FraudRule::getCode, Function.identity()));
        List<FraudDecision.RuleHit> hits = new ArrayList<>();

        amountDeviation(context, rules.get(AMOUNT_DEVIATION), hits);
        velocity(context, rules.get(VELOCITY), hits);
        repeatedTransaction(context, rules.get(REPEATED_TRANSACTION), hits);
        unfamiliarDevice(context, rules.get(UNFAMILIAR_DEVICE), hits);
        unusualLocation(context, rules.get(UNUSUAL_LOCATION), hits);
        failedPattern(context, rules.get(FAILED_PATTERN), hits);

        int score = Math.min(100, Math.max(0,
                hits.stream().mapToInt(FraudDecision.RuleHit::points).sum()));
        boolean capped = false;
        String level = levelFor(score);
        if ("HIGH".equals(level) && hits.size() < properties.getMinDistinctRulesForHold()) {
            score = Math.min(score, properties.getMediumMax());
            level = levelFor(score);
            capped = true;
        }

        return new FraudDecision(score, level,
                explanation(hits, score, level, capped), List.copyOf(hits));
    }

    private void amountDeviation(FraudContext context, FraudRule rule,
                                 List<FraudDecision.RuleHit> hits) {
        if (rule == null || context.amount() == null) {
            return;
        }
        int windowMinutes = rule.getTimeWindowMinutes() == null
                ? KNOWN_CONTEXT_LOOKBACK_MINUTES : rule.getTimeWindowMinutes();
        List<Transaction> history = transactionRepository.findBySenderUserIdAndStatusAndCreatedAtAfter(
                context.senderUserId(), TransactionStatus.SUCCESS,
                LocalDateTime.now().minusMinutes(windowMinutes));
        BigDecimal average = BigDecimal.ZERO;
        if (!history.isEmpty()) {
            BigDecimal total = history.stream().map(Transaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            average = total.divide(BigDecimal.valueOf(history.size()), 2, RoundingMode.HALF_UP);
        }

        BigDecimal multiplier = rule.getThresholdValue() == null
                ? BigDecimal.valueOf(3) : rule.getThresholdValue();
        boolean aboveCap = properties.getAmountCap() != null
                && context.amount().compareTo(properties.getAmountCap()) > 0;
        boolean aboveAverage = average.compareTo(BigDecimal.ZERO) > 0
                && context.amount().compareTo(average.multiply(multiplier)) > 0;
        if (!aboveCap && !aboveAverage) {
            return;
        }

        int points;
        String detail;
        if (aboveCap) {
            points = rule.getMaxPoints();
            detail = "Amount " + money(context.amount()) + " exceeds the simulated cap "
                    + money(properties.getAmountCap()) + ".";
        } else {
            BigDecimal ratio = context.amount().divide(average, 2, RoundingMode.HALF_UP);
            points = scaledPoints(rule, ratio.doubleValue(), multiplier.doubleValue());
            detail = "Amount " + money(context.amount()) + " is " + ratio.toPlainString()
                    + "x the average " + money(average) + " over "
                    + windowLabel(windowMinutes) + ".";
        }
        addHit(hits, rule, points, detail);
    }

    private void velocity(FraudContext context, FraudRule rule,
                          List<FraudDecision.RuleHit> hits) {
        if (rule == null) {
            return;
        }
        int windowMinutes = rule.getTimeWindowMinutes() == null ? 5 : rule.getTimeWindowMinutes();
        long count = velocityCounterService.countRecent(context.senderUserId(), windowMinutes);
        double limit = thresholdOf(rule, 5);
        if (count > limit) {
            int points = scaledPoints(rule, count, limit);
            addHit(hits, rule, points, "There are " + count + " simulated transactions in "
                    + windowLabel(windowMinutes) + " (limit " + formatLimit(limit) + ").");
        }
    }

    private void repeatedTransaction(FraudContext context, FraudRule rule,
                                     List<FraudDecision.RuleHit> hits) {
        if (rule == null) {
            return;
        }
        int windowMinutes = rule.getTimeWindowMinutes() == null ? 10 : rule.getTimeWindowMinutes();
        long repeats = transactionRepository.countBySenderUserIdAndReceiverUserIdAndAmountAndCreatedAtAfter(
                context.senderUserId(), context.receiverUserId(), context.amount(),
                LocalDateTime.now().minusMinutes(windowMinutes));
        double limit = thresholdOf(rule, 2);
        if (repeats >= limit) {
            int points = scaledPoints(rule, repeats + 1, limit);
            addHit(hits, rule, points, "Receiver and amount were repeated " + (repeats + 1)
                    + " times in " + windowLabel(windowMinutes) + " (limit "
                    + formatLimit(limit) + ").");
        }
    }

    private void unfamiliarDevice(FraudContext context, FraudRule rule,
                                  List<FraudDecision.RuleHit> hits) {
        if (rule == null || !hasText(context.deviceFingerprint())) {
            return;
        }
        boolean known = transactionRepository.existsBySenderUserIdAndDeviceFingerprintAndCreatedAtAfter(
                context.senderUserId(), context.deviceFingerprint(),
                LocalDateTime.now().minusMinutes(KNOWN_CONTEXT_LOOKBACK_MINUTES));
        if (!known) {
            addHit(hits, rule, rule.getMaxPoints(),
                    "Device fingerprint has not been seen for this user in recent history.");
        }
    }

    private void unusualLocation(FraudContext context, FraudRule rule,
                                 List<FraudDecision.RuleHit> hits) {
        if (rule == null || !hasText(context.locationHint())) {
            return;
        }
        boolean known = transactionRepository.existsBySenderUserIdAndLocationHintAndCreatedAtAfter(
                context.senderUserId(), context.locationHint(),
                LocalDateTime.now().minusMinutes(KNOWN_CONTEXT_LOOKBACK_MINUTES));
        if (!known) {
            addHit(hits, rule, rule.getMaxPoints(),
                    "Location hint has not been seen for this user in recent history.");
        }
    }

    private void failedPattern(FraudContext context, FraudRule rule,
                               List<FraudDecision.RuleHit> hits) {
        if (rule == null) {
            return;
        }
        int windowMinutes = rule.getTimeWindowMinutes() == null ? 15 : rule.getTimeWindowMinutes();
        long failures = transactionRepository.countBySenderUserIdAndStatusAndCreatedAtAfter(
                context.senderUserId(), TransactionStatus.FAILED,
                LocalDateTime.now().minusMinutes(windowMinutes));
        double limit = thresholdOf(rule, 3);
        if (failures >= limit) {
            int points = scaledPoints(rule, failures, limit);
            addHit(hits, rule, points, "There are " + failures + " failed simulated attempts in "
                    + windowLabel(windowMinutes) + " (limit " + formatLimit(limit) + ").");
        }
    }

    private void addHit(List<FraudDecision.RuleHit> hits, FraudRule rule, int points, String detail) {
        int safePoints = Math.max(0, Math.min(rule.getMaxPoints(), points));
        hits.add(new FraudDecision.RuleHit(rule.getCode(), safePoints, detail));
    }

    private int scaledPoints(FraudRule rule, double actual, double threshold) {
        if (threshold <= 0) {
            return rule.getMaxPoints();
        }
        double excessRatio = Math.max(0.0, actual - threshold) / threshold;
        if (excessRatio <= 0) {
            return 1;
        }
        return Math.min(rule.getMaxPoints(),
                Math.max(1, (int) Math.ceil(excessRatio * rule.getMaxPoints())));
    }

    private String levelFor(int score) {
        if (score >= properties.getHighMin()) {
            return "HIGH";
        }
        if (score > properties.getLowMax()) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String explanation(List<FraudDecision.RuleHit> hits, int score, String level,
                               boolean capped) {
        StringBuilder text = new StringBuilder("Risk ").append(score).append("/100 (")
                .append(level).append("). ");
        if (hits.isEmpty()) {
            text.append("No configured risk rule was triggered. ");
        } else {
            hits.forEach(hit -> text.append(hit.detail()).append(" (+").append(hit.points())
                    .append("). "));
        }
        if (capped) {
            text.append("Only one distinct rule fired, so the score was capped at ")
                    .append(properties.getMediumMax())
                    .append("; a single signal never causes a hold. ");
        }
        return text.append("Decision is for review only; no fraud determination made.").toString();
    }

    private double thresholdOf(FraudRule rule, double fallback) {
        return rule.getThresholdValue() == null ? fallback : rule.getThresholdValue().doubleValue();
    }

    private String formatLimit(double limit) {
        return BigDecimal.valueOf(limit).stripTrailingZeros().toPlainString();
    }

    private String windowLabel(int minutes) {
        return minutes % 1440 == 0 ? (minutes / 1440) + " days" : minutes + " minutes";
    }

    private String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
