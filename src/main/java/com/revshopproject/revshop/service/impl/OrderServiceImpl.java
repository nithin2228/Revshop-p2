package com.revshopproject.revshop.service.impl;

import java.math.BigDecimal;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.revshopproject.revshop.dto.OrderRequestDTO;
import com.revshopproject.revshop.dto.OrderResponseDTO;
import com.revshopproject.revshop.entity.*;
import com.revshopproject.revshop.repository.*;
import com.revshopproject.revshop.service.NotificationService;
import com.revshopproject.revshop.service.OrderService;
import com.revshopproject.revshop.service.UserService;

@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger logger = LoggerFactory.getLogger(OrderServiceImpl.class);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartItemRepository cartItemRepository;
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final NotificationService notificationService;
    private final UserService userService;

    public OrderServiceImpl(OrderRepository orderRepository, OrderItemRepository orderItemRepository,
                            CartItemRepository cartItemRepository, CartRepository cartRepository, 
                            ProductRepository productRepository, NotificationService notificationService,
                            UserService userService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.cartItemRepository = cartItemRepository;
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
        this.notificationService = notificationService;
        this.userService = userService;
    }

    @Override
    @Transactional
    public OrderResponseDTO placeOrder(OrderRequestDTO dto) {
        // Validate payment options
        if (dto.getPaymentMethod() == null) {
            throw new RuntimeException("Payment method cannot be empty!");
        }
        
        String normalizedPayment = dto.getPaymentMethod().trim().toUpperCase();
        if (!normalizedPayment.equals("CREDIT_CARD") && !normalizedPayment.equals("DEBIT_CARD") && 
            !normalizedPayment.equals("UPI") && !normalizedPayment.equals("COD") && 
            !normalizedPayment.equals("NET_BANKING")) {
            throw new RuntimeException("Invalid payment method. Only CREDIT_CARD, DEBIT_CARD, UPI, COD, or NET_BANKING are accepted.");
        }

        User currentUser = userService.getCurrentUser();
        Cart cart = cartRepository.findByUser_UserId(currentUser.getUserId()).orElseThrow(() -> new RuntimeException("Cart not found"));
        List<CartItem> cartItems = cartItemRepository.findByCart_CartId(cart.getCartId());
        
        if (cartItems.isEmpty()) throw new RuntimeException("Cart is empty!");

        Order order = new Order();
        order.setUser(cart.getUser());
        order.setShippingAddress(dto.getShippingAddress());
        order.setPaymentMethod(normalizedPayment);
        order.setStatus("PENDING");
        order = orderRepository.save(order);

        BigDecimal total = BigDecimal.ZERO;
        List<OrderItem> savedItems = new ArrayList<>();

        for (CartItem item : cartItems) {
            Product product = item.getProduct();
            
            if (product.getStock() < item.getQuantity()) {
                throw new RuntimeException("Product " + product.getName() + " is out of stock!");
            }

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setQuantity(item.getQuantity());
            orderItem.setPrice(product.getPrice());
            savedItems.add(orderItemRepository.save(orderItem));

            total = total.add(product.getPrice().multiply(new BigDecimal(item.getQuantity())));
            
            // TRIGGER 1: Notify Seller of new order
            notificationService.sendNotification(product.getSeller(), 
                "[PLACED] New Order Received: " + product.getName() + " (Qty: " + item.getQuantity() + ")");
        }

        order.setTotalAmount(total);
        cartItemRepository.deleteAll(cartItems);
        order = orderRepository.save(order);

        logger.info("[ORDER] Order #{} placed by user: {}. Amount: {}. Items: {}", 
            order.getOrderId(), order.getUser().getEmail(), total, buildProductSummary(savedItems));

        // Notify Buyer
        String summary = buildProductSummary(savedItems);
        notificationService.sendNotification(order.getUser(), 
            "[PLACED] Order Placed Successfully! Your order #" + order.getOrderId() + " containing " + summary + " is being processed.");

        logger.info("Order #{} placed successfully for user: {} with total amount: {}", order.getOrderId(), order.getUser().getEmail(), total);
        return OrderResponseDTO.fromEntity(order, savedItems);
    }

    @Override
    @Transactional
    public OrderResponseDTO acceptOrder(Long orderId) {
        User currentSeller = userService.getCurrentUser();
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found"));

        if (!"PENDING".equals(order.getStatus())) {
            throw new RuntimeException("Cannot accept. Status is: " + order.getStatus());
        }

        List<OrderItem> items = orderItemRepository.findByOrder_OrderId(orderId);
        
        // Verify that the current seller owns AT LEAST ONE product in this order to be allowed to accept it
        // In a multi-seller system, this is complex, but for now we follow the logic that a seller accepts the whole order
        boolean ownsProduct = items.stream().anyMatch(item -> item.getProduct().getSeller().getUserId().equals(currentSeller.getUserId()));
        if (!ownsProduct) {
            throw new RuntimeException("Unauthorized: You do not have products in this order.");
        }

        for (OrderItem item : items) {
            Product product = item.getProduct();
            product.setStock(product.getStock() - item.getQuantity());
            
            int threshold = (product.getInventoryThreshold() != null) ? product.getInventoryThreshold() : 5;
            if (product.getStock() <= threshold) {
                notificationService.sendNotification(product.getSeller(), 
                    "[LOW STOCK] Alert: " + product.getName() + " has only " + product.getStock() + " left.");
            }
            productRepository.save(product);
        }

        order.setStatus("ACCEPTED");
        String summary = buildProductSummary(items);
        notificationService.sendNotification(order.getUser(), 
            "[ACCEPTED] Your order #" + order.getOrderId() + " containing " + summary + " has been ACCEPTED by the seller.");
        notificationService.sendNotification(currentSeller, 
            "[ACCEPTED] You have ACCEPTED order #" + order.getOrderId() + " containing " + summary + ".");
        
        order = orderRepository.save(order);
        logger.info("[ORDER] Order #{} ACCEPTED by seller: {}. Items: {}", 
            order.getOrderId(), currentSeller.getEmail(), summary);
        return OrderResponseDTO.fromEntity(order, items);
    }

    @Override
    @Transactional
    public OrderResponseDTO updateOrderStatus(Long orderId, String newStatus) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found"));
        User currentUser = userService.getCurrentUser();
        String status = newStatus.toUpperCase();

        // 1. Ownership & Permission Check
        boolean isBuyer = order.getUser().getUserId().equals(currentUser.getUserId());
        List<OrderItem> items = orderItemRepository.findByOrder_OrderId(orderId);
        boolean isSeller = items.stream().anyMatch(item -> item.getProduct().getSeller().getUserId().equals(currentUser.getUserId()));

        if (!isBuyer && !isSeller) {
            throw new RuntimeException("Unauthorized: You do not have access to this order.");
        }

        // 2. Cancellation Rules (Buyer restricted to PENDING/ACCEPTED)
        if ("CANCELLED".equals(status) && isBuyer) {
            if (!"PENDING".equals(order.getStatus()) && !"ACCEPTED".equals(order.getStatus())) {
                throw new RuntimeException("Cannot cancel order in current state: " + order.getStatus());
            }
        }

        // 3. Stock Reversal (If it was previously accepted/deducted)
        if (("CANCELLED".equals(status) || "REJECTED".equals(status)) && "ACCEPTED".equals(order.getStatus())) {
            reverseInventory(orderId);
        }

        order.setStatus(status);
        String summary = buildProductSummary(items);

        if ("REJECTED".equals(status)) {
            notificationService.sendNotification(order.getUser(), 
                "[REJECTED] Order #" + orderId + " containing " + summary + " was unfortunately REJECTED by the seller.");
            notificationService.sendNotification(currentUser, 
                "[REJECTED] You have REJECTED order #" + orderId + " containing " + summary + ".");
            logger.info("[ORDER] Order #{} REJECTED by seller: {}. Items: {}", orderId, currentUser.getEmail(), summary);
        } else if ("CANCELLED".equals(status)) {
            // Notify Sellers that the buyer cancelled
            items.forEach(item -> notificationService.sendNotification(item.getProduct().getSeller(), 
                "[CANCELLED] Order #" + orderId + " has been CANCELLED by the customer."));
            // Notify Buyer
            notificationService.sendNotification(order.getUser(), "[CANCELLED] You have successfully cancelled your order #" + orderId + ".");
            logger.info("[ORDER] Order #{} CANCELLED by {}: {}. Items: {}", 
                orderId, isBuyer ? "Buyer" : "Seller", currentUser.getEmail(), summary);
        } else {
            notificationService.sendNotification(order.getUser(), "[" + status + "] Order #" + orderId + " containing " + summary + " status updated to: " + status);
            logger.info("[ORDER] Order #{} status updated to {} by user: {}", orderId, status, currentUser.getEmail());
        }

        logger.info("Order #{} status updated to {} by user {}", orderId, status, currentUser.getEmail());
        order = orderRepository.save(order);
        return OrderResponseDTO.fromEntity(order, orderItemRepository.findByOrder_OrderId(orderId));
    }

    @Override
    @Transactional
    public OrderResponseDTO markAsDelivered(Long orderId) {
        User currentSeller = userService.getCurrentUser();
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found"));
        
        // Basic Ownership Check
        List<OrderItem> items = orderItemRepository.findByOrder_OrderId(orderId);
        boolean ownsProduct = items.stream().anyMatch(item -> item.getProduct().getSeller().getUserId().equals(currentSeller.getUserId()));
        if (!ownsProduct) {
            throw new RuntimeException("Unauthorized: You do not have products in this order.");
        }

        order.setStatus("DELIVERED");
        String summary = buildProductSummary(items);
        notificationService.sendNotification(order.getUser(), 
            "[DELIVERED] Great news! Your order #" + orderId + " containing " + summary + " has been DELIVERED.");
        notificationService.sendNotification(currentSeller, 
            "[DELIVERED] Order #" + orderId + " containing " + summary + " has been successfully marked as DELIVERED.");
            
        order = orderRepository.save(order);
        logger.info("[ORDER] Order #{} marked as DELIVERED by seller: {}. Items: {}", 
            orderId, currentSeller.getEmail(), summary);
        return OrderResponseDTO.fromEntity(order, items);
    }

    private String buildProductSummary(List<OrderItem> items) {
        if (items == null || items.isEmpty()) return "items";
        return items.stream()
            .map(item -> item.getProduct().getName() + " (Qty: " + item.getQuantity() + ")")
            .collect(Collectors.joining(", "));
    }

    private void reverseInventory(Long orderId) {
        List<OrderItem> items = orderItemRepository.findByOrder_OrderId(orderId);
        for (OrderItem item : items) {
            Product product = item.getProduct();
            int newStock = product.getStock() + item.getQuantity();
            logger.info("Reversing inventory for order #{}: Product {} stock updated from {} to {}", orderId, product.getName(), product.getStock(), newStock);
            product.setStock(newStock);
            productRepository.save(product);
        }
    }

    @Override
    public List<OrderResponseDTO> getOrdersByUserId() { 
        User currentUser = userService.getCurrentUser();
        return orderRepository.findByUser_UserIdOrderByOrderIdDesc(currentUser.getUserId()).stream()
            .map(order -> OrderResponseDTO.fromEntity(order, orderItemRepository.findByOrder_OrderId(order.getOrderId())))
            .collect(Collectors.toList()); 
    }

    @Override
    public OrderResponseDTO getOrderById(Long orderId) { 
        User currentUser = userService.getCurrentUser();
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found")); 
        
        // Ownership Check: Only the buyer who placed the order or the involved seller can view it
        boolean isBuyer = order.getUser().getUserId().equals(currentUser.getUserId());
        List<OrderItem> items = orderItemRepository.findByOrder_OrderId(orderId);
        boolean isSeller = items.stream().anyMatch(item -> item.getProduct().getSeller().getUserId().equals(currentUser.getUserId()));
        
        if (!isBuyer && !isSeller) {
            throw new RuntimeException("Unauthorized: You do not have access to this order.");
        }

        return OrderResponseDTO.fromEntity(order, items);
    }
}