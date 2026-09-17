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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
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
    public OrderService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            PaymentRepository paymentRepository,
            UserRepository userRepository,
            ProductRepository productRepository) {

        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
    }

    public OrderResponse createOrder(OrderRequest request) {

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

    public OrderResponse updateOrderStatus(Long id, String status) {

        Order order = orderRepository.findById(id)
                .orElseThrow(() ->
                        new OrderNotFoundException(
                                "Order not found with id: " + id
                        )
                );

        order.setStatus(status);
        orderRepository.save(order);

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