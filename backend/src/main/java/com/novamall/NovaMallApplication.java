package com.novamall;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@SpringBootApplication
public class NovaMallApplication {
    public static void main(String[] args) {
        SpringApplication.run(NovaMallApplication.class, args);
    }
}

@Configuration
class WebConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5173")
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*");
    }
}

@RestController
@RequestMapping("/api")
class CatalogController {
    private final ShopService shopService;

    CatalogController(ShopService shopService) {
        this.shopService = shopService;
    }

    @GetMapping("/products")
    List<Product> products(@RequestParam(required = false) String keyword,
                           @RequestParam(required = false) String category) {
        return shopService.listProducts(keyword, category);
    }

    @GetMapping("/categories")
    List<String> categories() {
        return shopService.listCategories();
    }
}

@RestController
@RequestMapping("/api/orders")
class OrderController {
    private final ShopService shopService;

    OrderController(ShopService shopService) {
        this.shopService = shopService;
    }

    @GetMapping
    List<OrderResponse> orders() {
        return shopService.listOrders();
    }

    @GetMapping("/{orderNo}")
    OrderResponse order(@PathVariable String orderNo) {
        return shopService.getOrder(orderNo);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    OrderResponse create(@Valid @RequestBody CreateOrderRequest request) {
        return shopService.createOrder(request);
    }
}

@Service
class ShopService {
    private final JdbcTemplate jdbcTemplate;

    ShopService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    List<Product> listProducts(String keyword, String category) {
        StringBuilder sql = new StringBuilder("SELECT * FROM products WHERE 1=1");
        List<Object> arguments = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (LOWER(name) LIKE ? OR LOWER(description) LIKE ?)");
            String value = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            arguments.add(value);
            arguments.add(value);
        }
        if (category != null && !category.isBlank()) {
            sql.append(" AND category = ?");
            arguments.add(category.trim());
        }
        sql.append(" ORDER BY featured DESC, id ASC");
        return jdbcTemplate.query(sql.toString(), this::mapProduct, arguments.toArray());
    }

    List<String> listCategories() {
        return jdbcTemplate.queryForList("SELECT DISTINCT category FROM products ORDER BY category", String.class);
    }

    @Transactional
    OrderResponse createOrder(CreateOrderRequest request) {
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (OrderItemRequest item : request.items()) {
            quantities.merge(item.productId(), item.quantity(), Integer::sum);
        }

        String orderNo = generateOrderNo();
        LocalDateTime createdAt = LocalDateTime.now();
        BigDecimal total = BigDecimal.ZERO;
        List<OrderItemResponse> items = new ArrayList<>();

        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            Product product = findProduct(entry.getKey());
            int quantity = entry.getValue();
            int updated = jdbcTemplate.update(
                    "UPDATE products SET stock = stock - ? WHERE id = ? AND stock >= ?",
                    quantity, product.id(), quantity
            );
            if (updated == 0) {
                throw new IllegalArgumentException(product.name() + " 库存不足");
            }
            BigDecimal subtotal = product.price().multiply(BigDecimal.valueOf(quantity));
            total = total.add(subtotal);
            items.add(new OrderItemResponse(
                    product.id(), product.name(), product.imageUrl(), product.price(), quantity, subtotal
            ));
        }

        jdbcTemplate.update(
                "INSERT INTO mall_orders(order_no, customer_name, phone, address, total_amount, status, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                orderNo, request.customerName().trim(), request.phone().trim(), request.address().trim(),
                total, "待发货", Timestamp.valueOf(createdAt)
        );
        for (OrderItemResponse item : items) {
            jdbcTemplate.update(
                    "INSERT INTO order_items(order_no, product_id, product_name, image_url, unit_price, quantity, subtotal) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    orderNo, item.productId(), item.productName(), item.imageUrl(), item.unitPrice(), item.quantity(), item.subtotal()
            );
        }
        return new OrderResponse(
                orderNo, request.customerName().trim(), request.phone().trim(), request.address().trim(),
                total, "待发货", createdAt, items
        );
    }

    List<OrderResponse> listOrders() {
        return jdbcTemplate.query(
                "SELECT * FROM mall_orders ORDER BY created_at DESC",
                (rs, rowNum) -> mapOrder(rs.getString("order_no"), rs.getString("customer_name"),
                        rs.getString("phone"), rs.getString("address"), rs.getBigDecimal("total_amount"),
                        rs.getString("status"), rs.getTimestamp("created_at").toLocalDateTime())
        );
    }

    OrderResponse getOrder(String orderNo) {
        List<OrderResponse> orders = jdbcTemplate.query(
                "SELECT * FROM mall_orders WHERE order_no = ?",
                (rs, rowNum) -> mapOrder(rs.getString("order_no"), rs.getString("customer_name"),
                        rs.getString("phone"), rs.getString("address"), rs.getBigDecimal("total_amount"),
                        rs.getString("status"), rs.getTimestamp("created_at").toLocalDateTime()),
                orderNo
        );
        if (orders.isEmpty()) {
            throw new IllegalArgumentException("订单不存在：" + orderNo);
        }
        return orders.get(0);
    }

    private Product findProduct(Long id) {
        List<Product> products = jdbcTemplate.query("SELECT * FROM products WHERE id = ?", this::mapProduct, id);
        if (products.isEmpty()) {
            throw new IllegalArgumentException("商品不存在：" + id);
        }
        return products.get(0);
    }

    private Product mapProduct(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new Product(
                rs.getLong("id"), rs.getString("name"), rs.getString("description"),
                rs.getString("category"), rs.getBigDecimal("price"), rs.getInt("stock"),
                rs.getString("image_url"), rs.getBoolean("featured")
        );
    }

    private OrderResponse mapOrder(String orderNo, String customerName, String phone, String address,
                                   BigDecimal totalAmount, String status, LocalDateTime createdAt) {
        List<OrderItemResponse> items = jdbcTemplate.query(
                "SELECT * FROM order_items WHERE order_no = ? ORDER BY id",
                (rs, rowNum) -> new OrderItemResponse(
                        rs.getLong("product_id"), rs.getString("product_name"), rs.getString("image_url"),
                        rs.getBigDecimal("unit_price"), rs.getInt("quantity"), rs.getBigDecimal("subtotal")
                ),
                orderNo
        );
        return new OrderResponse(orderNo, customerName, phone, address, totalAmount, status, createdAt, items);
    }

    private String generateOrderNo() {
        return "NM" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().substring(0, 4).toUpperCase(Locale.ROOT);
    }
}

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, String> businessError(IllegalArgumentException exception) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, String> validationError(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst().map(error -> error.getDefaultMessage()).orElse("请求参数不正确");
        return Map.of("message", message);
    }
}

record Product(Long id, String name, String description, String category, BigDecimal price,
               Integer stock, String imageUrl, boolean featured) {
}

record OrderItemRequest(
        @NotNull(message = "商品 ID 不能为空") Long productId,
        @NotNull(message = "购买数量不能为空") @Min(value = 1, message = "购买数量至少为 1") Integer quantity
) {
}

record CreateOrderRequest(
        @NotBlank(message = "收货人不能为空") String customerName,
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^1\\d{10}$", message = "请输入正确的 11 位手机号") String phone,
        @NotBlank(message = "收货地址不能为空") String address,
        @NotEmpty(message = "订单商品不能为空") List<@Valid OrderItemRequest> items
) {
}

record OrderItemResponse(Long productId, String productName, String imageUrl, BigDecimal unitPrice,
                         Integer quantity, BigDecimal subtotal) {
}

record OrderResponse(String orderNo, String customerName, String phone, String address,
                     BigDecimal totalAmount, String status, LocalDateTime createdAt,
                     List<OrderItemResponse> items) {
}
