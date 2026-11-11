package com.rallit.domain.payment.repository;

import com.rallit.domain.payment.entity.PaymentRefund;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, Long> {
}
