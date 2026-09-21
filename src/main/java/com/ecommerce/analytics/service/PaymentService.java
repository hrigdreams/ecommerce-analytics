package com.ecommerce.analytics.service;

import com.ecommerce.analytics.dto.PaymentRequest;
import com.ecommerce.analytics.dto.PaymentResponse;
import com.ecommerce.analytics.entity.Order;
import com.ecommerce.analytics.entity.Payment;
import com.ecommerce.analytics.exception.OrderNotFoundException;
import com.ecommerce.analytics.exception.PaymentNotFoundException;
import com.ecommerce.analytics.repository.OrderRepository;
import com.ecommerce.analytics.repository.PaymentRepository;
import com.ecommerce.analytics.event.EventEnvelope;
import com.ecommerce.analytics.event.EventEnvelopeFactory;
import com.ecommerce.analytics.event.EventType;
import com.ecommerce.analytics.event.payload.payment.PaymentCreatedEvent;
import com.ecommerce.analytics.event.producer.DomainEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final EventEnvelopeFactory eventEnvelopeFactory;
    private final DomainEventPublisher domainEventPublisher;

    public PaymentService(
            PaymentRepository paymentRepository,
            OrderRepository orderRepository,
            EventEnvelopeFactory eventEnvelopeFactory,
            DomainEventPublisher domainEventPublisher
    ) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.eventEnvelopeFactory = eventEnvelopeFactory;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Transactional
    public PaymentResponse createPayment(PaymentRequest request) {

        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() ->
                        new OrderNotFoundException(
                                "Order not found with id: " + request.getOrderId()
                        )
                );

        if (paymentRepository.findByOrderId(request.getOrderId()).isPresent()) {
            throw new IllegalArgumentException(
                    "Payment already exists for order id: "
                            + request.getOrderId()
            );
        }

        if (Double.compare(
                request.getAmount(),
                order.getTotalAmount()
        ) != 0) {
            throw new IllegalArgumentException(
                    "Payment amount must match order total amount: "
                            + order.getTotalAmount()
            );
        }

        Payment payment = new Payment(
                order,
                request.getPaymentMethod(),
                request.getStatus(),
                request.getAmount(),
                request.getTransactionId() != null
                        ? request.getTransactionId()
                        : "txn-" + System.currentTimeMillis()
        );

        order.setPaymentStatus(request.getStatus());

        Payment saved = paymentRepository.save(payment);

        orderRepository.save(order);

        if (eventEnvelopeFactory != null && domainEventPublisher != null) {

            PaymentCreatedEvent payload = new PaymentCreatedEvent(
                    saved.getId(),
                    order.getId(),
                    saved.getPaymentMethod(),
                    saved.getStatus(),
                    saved.getAmount(),
                    saved.getTransactionId(),
                    toInstant(saved.getCreatedAt())
            );

            EventEnvelope<PaymentCreatedEvent> event =
                    eventEnvelopeFactory.create(
                            EventType.PAYMENT_CREATED,
                            "Payment",
                            saved.getId(),
                            payload
                    );

            // Key by ORDER id so ORDER_CREATED, ORDER_STATUS_CHANGED and
            // PAYMENT_CREATED for one order land on the same partition (in order).
            domainEventPublisher.publish(event, String.valueOf(order.getId()));
        }

        return mapToResponse(saved);
    }

    public PaymentResponse getPaymentById(Long id) {

        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() ->
                        new PaymentNotFoundException(
                                "Payment not found with id: " + id
                        )
                );

        return mapToResponse(payment);
    }

    public List<PaymentResponse> getAllPayments() {

        return paymentRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public void deletePayment(Long id) {

        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() ->
                        new PaymentNotFoundException(
                                "Payment not found with id: " + id
                        )
                );

        Order order = payment.getOrder();

        paymentRepository.delete(payment);

        order.setPaymentStatus("UNPAID");

        orderRepository.save(order);
    }

    private static Instant toInstant(LocalDateTime value) {
        return value == null
                ? Instant.now()
                : value.atZone(ZoneId.systemDefault()).toInstant();
    }

    private PaymentResponse mapToResponse(Payment payment) {

        return new PaymentResponse(
                payment.getId(),
                payment.getOrder().getId(),
                payment.getPaymentMethod(),
                payment.getStatus(),
                payment.getAmount(),
                payment.getTransactionId(),
                payment.getCreatedAt()
        );
    }
}