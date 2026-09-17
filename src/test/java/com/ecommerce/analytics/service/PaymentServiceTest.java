package com.ecommerce.analytics.service;

import com.ecommerce.analytics.dto.PaymentRequest;
import com.ecommerce.analytics.dto.PaymentResponse;
import com.ecommerce.analytics.entity.Order;
import com.ecommerce.analytics.entity.Payment;
import com.ecommerce.analytics.exception.OrderNotFoundException;
import com.ecommerce.analytics.exception.PaymentNotFoundException;
import com.ecommerce.analytics.repository.OrderRepository;
import com.ecommerce.analytics.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private Order order;

    @Mock
    private Payment payment;

    @InjectMocks
    private PaymentService paymentService;



    /**
     * Test case for creating a payment.
     */
    // CREATE PAYMENT

    @Test
    void createPayment_shouldCreatePaymentSuccessfully() {

        PaymentRequest request = new PaymentRequest(
                1L,
                "CARD",
                "PAID",
                1099.99,
                "txn-test-001"
        );

        when(orderRepository.findById(1L))
                .thenReturn(Optional.of(order));

        when(order.getId())
                .thenReturn(1L);

        when(order.getTotalAmount())
                .thenReturn(1099.99);

        when(paymentRepository.findByOrderId(1L))
                .thenReturn(Optional.empty());

        Payment savedPayment = mock(Payment.class);

        when(savedPayment.getId())
                .thenReturn(1L);

        when(savedPayment.getOrder())
                .thenReturn(order);

        when(savedPayment.getPaymentMethod())
                .thenReturn("CARD");

        when(savedPayment.getStatus())
                .thenReturn("PAID");

        when(savedPayment.getAmount())
                .thenReturn(1099.99);

        when(savedPayment.getTransactionId())
                .thenReturn("txn-test-001");

        when(savedPayment.getCreatedAt())
                .thenReturn(null);

        when(paymentRepository.save(any(Payment.class)))
                .thenReturn(savedPayment);


        PaymentResponse response =
                paymentService.createPayment(request);


        assertNotNull(response);

        assertEquals(1L, response.getId());
        assertEquals(1L, response.getOrderId());
        assertEquals("CARD", response.getPaymentMethod());
        assertEquals("PAID", response.getStatus());
        assertEquals(1099.99, response.getAmount());
        assertEquals("txn-test-001", response.getTransactionId());


        verify(orderRepository)
                .findById(1L);

        verify(paymentRepository)
                .findByOrderId(1L);

        verify(paymentRepository)
                .save(any(Payment.class));

        verify(orderRepository)
                .save(order);

        verify(order)
                .setPaymentStatus("PAID");
    }


    @Test
    void createPayment_shouldThrowOrderNotFoundException() {

        PaymentRequest request = new PaymentRequest(
                999L,
                "CARD",
                "PAID",
                1099.99,
                "txn-test-002"
        );

        when(orderRepository.findById(999L))
                .thenReturn(Optional.empty());


        OrderNotFoundException exception =
                assertThrows(
                        OrderNotFoundException.class,
                        () -> paymentService.createPayment(request)
                );


        assertEquals(
                "Order not found with id: 999",
                exception.getMessage()
        );


        verify(orderRepository)
                .findById(999L);

        verify(paymentRepository, never())
                .findByOrderId(anyLong());

        verify(paymentRepository, never())
                .save(any(Payment.class));
    }


    @Test
    void createPayment_shouldThrowExceptionWhenPaymentAlreadyExists() {

        PaymentRequest request = new PaymentRequest(
                1L,
                "CARD",
                "PAID",
                1099.99,
                "txn-test-003"
        );

        when(orderRepository.findById(1L))
                .thenReturn(Optional.of(order));

        when(paymentRepository.findByOrderId(1L))
                .thenReturn(Optional.of(payment));


        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> paymentService.createPayment(request)
                );


        assertEquals(
                "Payment already exists for order id: 1",
                exception.getMessage()
        );


        verify(orderRepository)
                .findById(1L);

        verify(paymentRepository)
                .findByOrderId(1L);

        verify(paymentRepository, never())
                .save(any(Payment.class));

        verify(orderRepository, never())
                .save(any(Order.class));
    }


    @Test
    void createPayment_shouldThrowExceptionWhenAmountDoesNotMatch() {

        PaymentRequest request = new PaymentRequest(
                1L,
                "CARD",
                "PAID",
                999.99,
                "txn-test-004"
        );

        when(orderRepository.findById(1L))
                .thenReturn(Optional.of(order));

        when(paymentRepository.findByOrderId(1L))
                .thenReturn(Optional.empty());

        when(order.getTotalAmount())
                .thenReturn(1099.99);


        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> paymentService.createPayment(request)
                );


        assertEquals(
                "Payment amount must match order total amount: 1099.99",
                exception.getMessage()
        );


        verify(orderRepository)
                .findById(1L);

        verify(paymentRepository)
                .findByOrderId(1L);

        verify(order, times(2))
                .getTotalAmount();

        verify(paymentRepository, never())
                .save(any(Payment.class));

        verify(orderRepository, never())
                .save(any(Order.class));
    }


    @Test
    void createPayment_shouldGenerateTransactionIdWhenNotProvided() {

        PaymentRequest request = new PaymentRequest(
                1L,
                "CARD",
                "PAID",
                1099.99,
                null
        );

        when(orderRepository.findById(1L))
                .thenReturn(Optional.of(order));

        when(paymentRepository.findByOrderId(1L))
                .thenReturn(Optional.empty());

        when(order.getTotalAmount())
                .thenReturn(1099.99);

        when(order.getId())
                .thenReturn(1L);


        Payment savedPayment = mock(Payment.class);

        when(savedPayment.getId())
                .thenReturn(2L);

        when(savedPayment.getOrder())
                .thenReturn(order);

        when(savedPayment.getPaymentMethod())
                .thenReturn("CARD");

        when(savedPayment.getStatus())
                .thenReturn("PAID");

        when(savedPayment.getAmount())
                .thenReturn(1099.99);

        when(savedPayment.getTransactionId())
                .thenReturn("txn-generated");

        when(savedPayment.getCreatedAt())
                .thenReturn(null);

        when(paymentRepository.save(any(Payment.class)))
                .thenReturn(savedPayment);


        PaymentResponse response =
                paymentService.createPayment(request);


        assertNotNull(response);

        assertNotNull(response.getTransactionId());

        verify(paymentRepository)
                .save(any(Payment.class));

        verify(orderRepository)
                .save(order);
    }


    // ============================================================
    // GET PAYMENT BY ID
    // ============================================================

    @Test
    void getPaymentById_shouldReturnPaymentSuccessfully() {

        when(paymentRepository.findById(1L))
                .thenReturn(Optional.of(payment));

        when(payment.getId())
                .thenReturn(1L);

        when(payment.getOrder())
                .thenReturn(order);

        when(order.getId())
                .thenReturn(1L);

        when(payment.getPaymentMethod())
                .thenReturn("CARD");

        when(payment.getStatus())
                .thenReturn("PAID");

        when(payment.getAmount())
                .thenReturn(1099.99);

        when(payment.getTransactionId())
                .thenReturn("txn-test-005");

        when(payment.getCreatedAt())
                .thenReturn(null);


        PaymentResponse response =
                paymentService.getPaymentById(1L);


        assertNotNull(response);

        assertEquals(1L, response.getId());
        assertEquals(1L, response.getOrderId());
        assertEquals("CARD", response.getPaymentMethod());
        assertEquals("PAID", response.getStatus());
        assertEquals(1099.99, response.getAmount());
        assertEquals("txn-test-005", response.getTransactionId());


        verify(paymentRepository)
                .findById(1L);
    }


    @Test
    void getPaymentById_shouldThrowPaymentNotFoundException() {

        when(paymentRepository.findById(999L))
                .thenReturn(Optional.empty());


        PaymentNotFoundException exception =
                assertThrows(
                        PaymentNotFoundException.class,
                        () -> paymentService.getPaymentById(999L)
                );


        assertEquals(
                "Payment not found with id: 999",
                exception.getMessage()
        );


        verify(paymentRepository)
                .findById(999L);
    }


    // ============================================================
    // GET ALL PAYMENTS
    // ============================================================

    @Test
    void getAllPayments_shouldReturnAllPayments() {

        Payment payment1 = mock(Payment.class);
        Payment payment2 = mock(Payment.class);


        when(payment1.getId())
                .thenReturn(1L);

        when(payment1.getOrder())
                .thenReturn(order);

        when(payment1.getPaymentMethod())
                .thenReturn("CARD");

        when(payment1.getStatus())
                .thenReturn("PAID");

        when(payment1.getAmount())
                .thenReturn(1099.99);

        when(payment1.getTransactionId())
                .thenReturn("txn-001");

        when(payment1.getCreatedAt())
                .thenReturn(null);


        when(payment2.getId())
                .thenReturn(2L);

        when(payment2.getOrder())
                .thenReturn(order);

        when(payment2.getPaymentMethod())
                .thenReturn("CASH");

        when(payment2.getStatus())
                .thenReturn("PAID");

        when(payment2.getAmount())
                .thenReturn(500.00);

        when(payment2.getTransactionId())
                .thenReturn("txn-002");

        when(payment2.getCreatedAt())
                .thenReturn(null);


        when(paymentRepository.findAll())
                .thenReturn(List.of(payment1, payment2));


        var responses =
                paymentService.getAllPayments();


        assertNotNull(responses);

        assertEquals(2, responses.size());


        verify(paymentRepository)
                .findAll();
    }


    // ============================================================
    // DELETE PAYMENT
    // ============================================================

    @Test
    void deletePayment_shouldDeletePaymentAndMarkOrderUnpaid() {

        when(paymentRepository.findById(1L))
                .thenReturn(Optional.of(payment));

        when(payment.getOrder())
                .thenReturn(order);


        paymentService.deletePayment(1L);


        verify(paymentRepository)
                .findById(1L);

        verify(paymentRepository)
                .delete(payment);

        verify(order)
                .setPaymentStatus("UNPAID");

        verify(orderRepository)
                .save(order);
    }


    @Test
    void deletePayment_shouldThrowPaymentNotFoundException() {

        when(paymentRepository.findById(999L))
                .thenReturn(Optional.empty());


        PaymentNotFoundException exception =
                assertThrows(
                        PaymentNotFoundException.class,
                        () -> paymentService.deletePayment(999L)
                );


        assertEquals(
                "Payment not found with id: 999",
                exception.getMessage()
        );


        verify(paymentRepository)
                .findById(999L);

        verify(paymentRepository, never())
                .delete(any(Payment.class));

        verify(orderRepository, never())
                .save(any(Order.class));
    }
}