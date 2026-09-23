package com.ecommerce.analytics.service;

import com.ecommerce.analytics.dto.OrderItemRequest;
import com.ecommerce.analytics.dto.OrderItemResponse;
import com.ecommerce.analytics.dto.OrderRequest;
import com.ecommerce.analytics.dto.OrderResponse;
import com.ecommerce.analytics.repository.OrderItemRepository;
import com.ecommerce.analytics.entity.Order;
import com.ecommerce.analytics.entity.OrderItem;
import com.ecommerce.analytics.entity.Product;
import com.ecommerce.analytics.entity.User;
import com.ecommerce.analytics.exception.OrderNotFoundException;
import com.ecommerce.analytics.exception.ProductNotFoundException;
import com.ecommerce.analytics.exception.UserNotFoundException;
import com.ecommerce.analytics.repository.OrderRepository;
import com.ecommerce.analytics.repository.ProductRepository;
import com.ecommerce.analytics.repository.UserRepository;
import com.ecommerce.analytics.repository.PaymentRepository;
import com.ecommerce.analytics.event.EventEnvelope;
import com.ecommerce.analytics.event.EventEnvelopeFactory;
import com.ecommerce.analytics.event.EventType;
import com.ecommerce.analytics.event.payload.order.OrderCreatedEvent;
import com.ecommerce.analytics.event.payload.order.OrderItemEvent;
import com.ecommerce.analytics.event.payload.order.OrderStatusChangedEvent;
import com.ecommerce.analytics.event.payload.funnel.CheckoutStartedEvent;
import com.ecommerce.analytics.event.producer.DomainEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Objects;
import java.util.List;
import java.math.BigDecimal;

/**
 * Service class for managing orders.
 */
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final EventEnvelopeFactory eventEnvelopeFactory;
    private final DomainEventPublisher domainEventPublisher;

    public OrderService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            PaymentRepository paymentRepository,
            UserRepository userRepository,
            ProductRepository productRepository,
            EventEnvelopeFactory eventEnvelopeFactory,
            DomainEventPublisher domainEventPublisher) {

        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.eventEnvelopeFactory = eventEnvelopeFactory;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Transactional
    public OrderResponse createOrder(OrderRequest request) {

        if (eventEnvelopeFactory != null && domainEventPublisher != null) {
            int itemCount = request.getItems() != null ? request.getItems().size() : 0;

            CheckoutStartedEvent checkoutPayload =
                    new CheckoutStartedEvent(request.getUserId(), itemCount);

            EventEnvelope<CheckoutStartedEvent> checkoutEvent =
                    eventEnvelopeFactory.create(
                            EventType.CHECKOUT_STARTED,
                            "User",
                            request.getUserId(),
                            checkoutPayload
                    );

            // publishNow: sent immediately, ignoring the surrounding @Transactional,
            // so a checkout that later fails validation still counts as a funnel start.
            domainEventPublisher.publishNow(checkoutEvent);
        }

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() ->
                        new UserNotFoundException(
                                "User not found with id: " + request.getUserId()
                        )
                );

        Order order = new Order(
                user,
                0.0,
                "PENDING",
                "UNPAID"
        );

        double totalAmount = 0.0;

        for (OrderItemRequest itemRequest : request.getItems()) {

            Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(() ->
                            new ProductNotFoundException(
                                    "Product not found with id: "
                                            + itemRequest.getProductId()
                            )
                    );

            OrderItem item = new OrderItem(
                    product,
                    itemRequest.getQuantity(),
                    product.getPrice()
            );

            order.addItem(item);

            totalAmount +=
                    itemRequest.getQuantity() * product.getPrice();
        }

        order.setTotalAmount(totalAmount);

        Order saved = orderRepository.save(order);

        if (eventEnvelopeFactory != null && domainEventPublisher != null) {

            List<OrderItemEvent> itemEvents = new ArrayList<>();

            for (OrderItem item : saved.getItems()) {
                itemEvents.add(
                        new OrderItemEvent(
                                item.getId(),
                                item.getProduct().getId(),
                                item.getProduct().getName(),
                                item.getQuantity(),
                                item.getUnitPrice()
                        )
                );
            }

            OrderCreatedEvent payload = new OrderCreatedEvent(
                    saved.getId(),
                    saved.getUser().getId(),
                    saved.getStatus(),
                    saved.getPaymentStatus(),
                    BigDecimal.valueOf(saved.getTotalAmount()),
                    toInstant(saved.getCreatedAt()),
                    itemEvents
            );

            EventEnvelope<OrderCreatedEvent> event =
                    eventEnvelopeFactory.create(
                            EventType.ORDER_CREATED,
                            "Order",
                            saved.getId(),
                            payload
                    );

            domainEventPublisher.publish(event);
        }

        return mapToResponse(saved);
    }

    public OrderResponse getOrderById(Long id) {

        Order order = orderRepository.findById(id)
                .orElseThrow(() ->
                        new OrderNotFoundException(
                                "Order not found with id: " + id
                        )
                );

        return mapToResponse(order);
    }

    public List<OrderResponse> getAllOrders() {

        List<Order> orders = orderRepository.findAll();

        List<OrderResponse> responses = new ArrayList<>();

        for (Order order : orders) {
            responses.add(mapToResponse(order));
        }

        return responses;
    }

    @Transactional
    public OrderResponse updateOrderStatus(Long id, String status) {

        Order order = orderRepository.findById(id)
                .orElseThrow(() ->
                        new OrderNotFoundException(
                                "Order not found with id: " + id
                        )
                );

        String previousStatus = order.getStatus();

        order.setStatus(status);
        orderRepository.save(order);

        if (eventEnvelopeFactory != null
                && domainEventPublisher != null
                && !Objects.equals(previousStatus, status)) {

            OrderStatusChangedEvent payload =
                    new OrderStatusChangedEvent(id, previousStatus, status);

            EventEnvelope<OrderStatusChangedEvent> event =
                    eventEnvelopeFactory.create(
                            EventType.ORDER_STATUS_CHANGED,
                            "Order",
                            id,
                            payload
                    );

            domainEventPublisher.publish(event);
        }

        Order updated = orderRepository.findById(id)
                .orElseThrow(() ->
                        new OrderNotFoundException(
                                "Order not found with id: " + id
                        )
                );

        return mapToResponse(updated);
    }

    /**
     * Deletes an order by its ID.
     *
     * @param id the ID of the order to delete
     */
    @Transactional
    public void deleteOrder(Long id) {

        Order order = orderRepository.findById(id)
                .orElseThrow(() ->
                        new OrderNotFoundException(
                                "Order not found with id: " + id
                        )
                );

        // Delete payment first because payments.order_id
        // references orders.id
        paymentRepository.deleteByOrderId(id);

        // Delete order items because order_items.order_id
        // references orders.id
        orderItemRepository.deleteByOrderId(id);

        // Now it is safe to delete the order
        orderRepository.delete(order);
    }

    private static Instant toInstant(LocalDateTime value) {
        return value == null
                ? Instant.now()
                : value.atZone(ZoneId.systemDefault()).toInstant();
    }

    private OrderResponse mapToResponse(Order order) {

        List<OrderItemResponse> items = new ArrayList<>();

        for (OrderItem item : order.getItems()) {

            items.add(
                    new OrderItemResponse(
                            item.getId(),
                            item.getProduct().getId(),
                            item.getProduct().getName(),
                            item.getQuantity(),
                            item.getUnitPrice()
                    )
            );
        }

        return new OrderResponse(
                order.getId(),
                order.getUser().getId(),
                order.getStatus(),
                order.getPaymentStatus(),
                BigDecimal.valueOf(order.getTotalAmount()),
                order.getCreatedAt(),
                items
        );
    }
}