package com.ecommerce.analytics.service;

import com.ecommerce.analytics.dto.OrderItemRequest;
import com.ecommerce.analytics.dto.OrderRequest;
import com.ecommerce.analytics.dto.OrderResponse;
import com.ecommerce.analytics.entity.Order;
import com.ecommerce.analytics.entity.OrderItem;
import com.ecommerce.analytics.entity.Product;
import com.ecommerce.analytics.entity.User;
import com.ecommerce.analytics.exception.OrderNotFoundException;
import com.ecommerce.analytics.exception.ProductNotFoundException;
import com.ecommerce.analytics.exception.UserNotFoundException;
import com.ecommerce.analytics.repository.OrderItemRepository;
import com.ecommerce.analytics.repository.OrderRepository;
import com.ecommerce.analytics.repository.PaymentRepository;
import com.ecommerce.analytics.repository.ProductRepository;
import com.ecommerce.analytics.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private OrderService orderService;


    // ============================================================
    // CREATE ORDER
    // ============================================================

    @Test
    void createOrder_shouldCreateOrderSuccessfully() {

        User user = mock(User.class);
        Product product = mock(Product.class);
        Order savedOrder = mock(Order.class);
        OrderItem savedItem = mock(OrderItem.class);

        OrderRequest request = new OrderRequest(
                1L,
                List.of(
                        new OrderItemRequest(10L, 2)
                )
        );

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        when(productRepository.findById(10L))
                .thenReturn(Optional.of(product));

        when(product.getPrice())
                .thenReturn(500.0);

        when(orderRepository.save(any(Order.class)))
                .thenReturn(savedOrder);

        when(savedOrder.getItems())
                .thenReturn(List.of(savedItem));

        when(savedOrder.getId())
                .thenReturn(1L);

        when(savedOrder.getUser())
                .thenReturn(user);

        when(user.getId())
                .thenReturn(1L);

        when(savedOrder.getStatus())
                .thenReturn("PENDING");

        when(savedOrder.getPaymentStatus())
                .thenReturn("UNPAID");

        // Entity returns Double
        when(savedOrder.getTotalAmount())
                .thenReturn(1000.0);

        when(savedOrder.getCreatedAt())
                .thenReturn(null);

        when(savedItem.getId())
                .thenReturn(1L);

        when(savedItem.getProduct())
                .thenReturn(product);

        when(product.getId())
                .thenReturn(10L);

        when(product.getName())
                .thenReturn("Test Product");

        when(savedItem.getQuantity())
                .thenReturn(2);

        when(savedItem.getUnitPrice())
                .thenReturn(500.0);


        OrderResponse response =
                orderService.createOrder(request);


        assertNotNull(response);

        assertEquals(1L, response.getId());
        assertEquals(1L, response.getUserId());
        assertEquals("PENDING", response.getStatus());
        assertEquals("UNPAID", response.getPaymentStatus());

        // DTO returns BigDecimal
        assertEquals(
                BigDecimal.valueOf(1000.0),
                response.getTotalAmount()
        );

        assertNotNull(response.getItems());
        assertEquals(1, response.getItems().size());

        assertEquals(
                10L,
                response.getItems().get(0).getProductId()
        );

        assertEquals(
                "Test Product",
                response.getItems().get(0).getProductName()
        );

        assertEquals(
                2,
                response.getItems().get(0).getQuantity()
        );

        assertEquals(
                500.0,
                response.getItems().get(0).getUnitPrice()
        );


        verify(userRepository)
                .findById(1L);

        verify(productRepository)
                .findById(10L);

        verify(orderRepository)
                .save(any(Order.class));
    }


    @Test
    void createOrder_shouldThrowUserNotFoundException() {

        OrderRequest request = new OrderRequest(
                999L,
                List.of(
                        new OrderItemRequest(10L, 1)
                )
        );

        when(userRepository.findById(999L))
                .thenReturn(Optional.empty());


        UserNotFoundException exception =
                assertThrows(
                        UserNotFoundException.class,
                        () -> orderService.createOrder(request)
                );


        assertEquals(
                "User not found with id: 999",
                exception.getMessage()
        );


        verify(userRepository)
                .findById(999L);

        verify(productRepository, never())
                .findById(anyLong());

        verify(orderRepository, never())
                .save(any(Order.class));
    }


    @Test
    void createOrder_shouldThrowProductNotFoundException() {

        User user = mock(User.class);

        OrderRequest request = new OrderRequest(
                1L,
                List.of(
                        new OrderItemRequest(999L, 1)
                )
        );

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        when(productRepository.findById(999L))
                .thenReturn(Optional.empty());


        ProductNotFoundException exception =
                assertThrows(
                        ProductNotFoundException.class,
                        () -> orderService.createOrder(request)
                );


        assertEquals(
                "Product not found with id: 999",
                exception.getMessage()
        );


        verify(userRepository)
                .findById(1L);

        verify(productRepository)
                .findById(999L);

        verify(orderRepository, never())
                .save(any(Order.class));
    }


    @Test
    void createOrder_shouldCalculateTotalForMultipleItems() {

        User user = mock(User.class);
        Product product1 = mock(Product.class);
        Product product2 = mock(Product.class);
        Order savedOrder = mock(Order.class);

        OrderRequest request = new OrderRequest(
                1L,
                List.of(
                        new OrderItemRequest(10L, 2),
                        new OrderItemRequest(20L, 3)
                )
        );

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        when(productRepository.findById(10L))
                .thenReturn(Optional.of(product1));

        when(productRepository.findById(20L))
                .thenReturn(Optional.of(product2));

        when(product1.getPrice())
                .thenReturn(100.0);

        when(product2.getPrice())
                .thenReturn(200.0);

        when(orderRepository.save(any(Order.class)))
                .thenReturn(savedOrder);

        when(savedOrder.getItems())
                .thenReturn(List.of());

        when(savedOrder.getId())
                .thenReturn(1L);

        when(savedOrder.getUser())
                .thenReturn(user);

        when(user.getId())
                .thenReturn(1L);

        when(savedOrder.getStatus())
                .thenReturn("PENDING");

        when(savedOrder.getPaymentStatus())
                .thenReturn("UNPAID");

        // Entity returns Double
        when(savedOrder.getTotalAmount())
                .thenReturn(800.0);

        when(savedOrder.getCreatedAt())
                .thenReturn(null);


        OrderResponse response =
                orderService.createOrder(request);


        assertNotNull(response);

        // DTO returns BigDecimal
        assertEquals(
                BigDecimal.valueOf(800.0),
                response.getTotalAmount()
        );


        verify(productRepository)
                .findById(10L);

        verify(productRepository)
                .findById(20L);

        verify(orderRepository)
                .save(any(Order.class));
    }


    // ============================================================
    // GET ORDER BY ID
    // ============================================================

    @Test
    void getOrderById_shouldReturnOrderSuccessfully() {

        Order order = mock(Order.class);
        User user = mock(User.class);

        when(orderRepository.findById(1L))
                .thenReturn(Optional.of(order));

        when(order.getItems())
                .thenReturn(List.of());

        when(order.getId())
                .thenReturn(1L);

        when(order.getUser())
                .thenReturn(user);

        when(user.getId())
                .thenReturn(10L);

        when(order.getStatus())
                .thenReturn("PENDING");

        when(order.getPaymentStatus())
                .thenReturn("UNPAID");

        // Entity returns Double
        when(order.getTotalAmount())
                .thenReturn(1099.99);

        when(order.getCreatedAt())
                .thenReturn(null);


        OrderResponse response =
                orderService.getOrderById(1L);


        assertNotNull(response);

        assertEquals(1L, response.getId());
        assertEquals(10L, response.getUserId());
        assertEquals("PENDING", response.getStatus());
        assertEquals("UNPAID", response.getPaymentStatus());

        // DTO returns BigDecimal
        assertEquals(
                BigDecimal.valueOf(1099.99),
                response.getTotalAmount()
        );

        assertNotNull(response.getItems());
        assertTrue(response.getItems().isEmpty());


        verify(orderRepository)
                .findById(1L);
    }


    @Test
    void getOrderById_shouldThrowOrderNotFoundException() {

        when(orderRepository.findById(999L))
                .thenReturn(Optional.empty());


        OrderNotFoundException exception =
                assertThrows(
                        OrderNotFoundException.class,
                        () -> orderService.getOrderById(999L)
                );


        assertEquals(
                "Order not found with id: 999",
                exception.getMessage()
        );


        verify(orderRepository)
                .findById(999L);
    }


    // ============================================================
    // GET ALL ORDERS
    // ============================================================

    @Test
    void getAllOrders_shouldReturnAllOrders() {

        Order order1 = mock(Order.class);
        Order order2 = mock(Order.class);

        User user1 = mock(User.class);
        User user2 = mock(User.class);


        when(order1.getItems())
                .thenReturn(List.of());

        when(order1.getId())
                .thenReturn(1L);

        when(order1.getUser())
                .thenReturn(user1);

        when(user1.getId())
                .thenReturn(10L);

        when(order1.getStatus())
                .thenReturn("PENDING");

        when(order1.getPaymentStatus())
                .thenReturn("UNPAID");

        when(order1.getTotalAmount())
                .thenReturn(500.0);

        when(order1.getCreatedAt())
                .thenReturn(null);


        when(order2.getItems())
                .thenReturn(List.of());

        when(order2.getId())
                .thenReturn(2L);

        when(order2.getUser())
                .thenReturn(user2);

        when(user2.getId())
                .thenReturn(20L);

        when(order2.getStatus())
                .thenReturn("SHIPPED");

        when(order2.getPaymentStatus())
                .thenReturn("PAID");

        when(order2.getTotalAmount())
                .thenReturn(1000.0);

        when(order2.getCreatedAt())
                .thenReturn(null);


        when(orderRepository.findAll())
                .thenReturn(List.of(order1, order2));


        List<OrderResponse> responses =
                orderService.getAllOrders();


        assertNotNull(responses);

        assertEquals(2, responses.size());

        assertEquals(1L, responses.get(0).getId());
        assertEquals(2L, responses.get(1).getId());


        verify(orderRepository)
                .findAll();
    }


    // ============================================================
    // UPDATE ORDER STATUS
    // ============================================================

    @Test
    void updateOrderStatus_shouldUpdateStatusSuccessfully() {

        Order order = mock(Order.class);
        User user = mock(User.class);

        when(orderRepository.findById(1L))
                .thenReturn(
                        Optional.of(order),
                        Optional.of(order)
                );

        when(order.getItems())
                .thenReturn(List.of());

        when(order.getId())
                .thenReturn(1L);

        when(order.getUser())
                .thenReturn(user);

        when(user.getId())
                .thenReturn(10L);

        when(order.getStatus())
                .thenReturn("SHIPPED");

        when(order.getPaymentStatus())
                .thenReturn("PAID");

        when(order.getTotalAmount())
                .thenReturn(1099.99);

        when(order.getCreatedAt())
                .thenReturn(null);


        OrderResponse response =
                orderService.updateOrderStatus(
                        1L,
                        "SHIPPED"
                );


        assertNotNull(response);

        assertEquals(
                "SHIPPED",
                response.getStatus()
        );


        verify(orderRepository, times(2))
                .findById(1L);

        verify(order)
                .setStatus("SHIPPED");

        verify(orderRepository)
                .save(order);
    }


    @Test
    void updateOrderStatus_shouldThrowOrderNotFoundException() {

        when(orderRepository.findById(999L))
                .thenReturn(Optional.empty());


        OrderNotFoundException exception =
                assertThrows(
                        OrderNotFoundException.class,
                        () -> orderService.updateOrderStatus(
                                999L,
                                "SHIPPED"
                        )
                );


        assertEquals(
                "Order not found with id: 999",
                exception.getMessage()
        );


        verify(orderRepository)
                .findById(999L);

        verify(orderRepository, never())
                .save(any(Order.class));
    }


    // ============================================================
    // DELETE ORDER
    // ============================================================

    @Test
    void deleteOrder_shouldDeletePaymentItemsAndOrder() {

        Order order = mock(Order.class);

        when(orderRepository.findById(1L))
                .thenReturn(Optional.of(order));


        orderService.deleteOrder(1L);


        verify(orderRepository)
                .findById(1L);

        verify(paymentRepository)
                .deleteByOrderId(1L);

        verify(orderItemRepository)
                .deleteByOrderId(1L);

        verify(orderRepository)
                .delete(order);
    }


    @Test
    void deleteOrder_shouldThrowOrderNotFoundException() {

        when(orderRepository.findById(999L))
                .thenReturn(Optional.empty());


        OrderNotFoundException exception =
                assertThrows(
                        OrderNotFoundException.class,
                        () -> orderService.deleteOrder(999L)
                );


        assertEquals(
                "Order not found with id: 999",
                exception.getMessage()
        );


        verify(orderRepository)
                .findById(999L);

        verify(paymentRepository, never())
                .deleteByOrderId(anyLong());

        verify(orderItemRepository, never())
                .deleteByOrderId(anyLong());

        verify(orderRepository, never())
                .delete(any(Order.class));
    }
}
