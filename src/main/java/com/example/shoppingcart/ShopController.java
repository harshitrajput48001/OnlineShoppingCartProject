package com.example.shoppingcart;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@RequestMapping("/api")
@CrossOrigin
public class ShopController {
 private final ShopService service;
 public ShopController(ShopService service) { this.service = service; }
 @GetMapping("/products") public List<Map<String,Object>> products(@RequestParam(required=false) String search) { return service.getProducts(search); }
 @PostMapping("/products") @ResponseStatus(HttpStatus.CREATED) public Map<String,Object> addProduct(@RequestBody ProductRequest p) { return service.addProduct(p); }
 @PutMapping("/products/{id}") public Map<String,Object> updateProduct(@PathVariable int id,@RequestBody ProductRequest p) { return service.updateProduct(id,p); }
 @DeleteMapping("/products/{id}") public Map<String,Object> deleteProduct(@PathVariable int id) { service.deleteProduct(id); return Map.of("message","Product deleted"); }
 @PostMapping("/customers") @ResponseStatus(HttpStatus.CREATED) public Map<String,Object> addCustomer(@RequestBody CustomerRequest c) { return service.addCustomer(c); }
 @GetMapping("/customers") public List<Map<String,Object>> customers() { return service.getCustomers(); }
 @PostMapping("/cart") public Map<String,Object> addCart(@RequestBody CartRequest c) { return service.addToCart(c); }
 @GetMapping("/cart/{customerId}") public List<Map<String,Object>> cart(@PathVariable int customerId) { return service.getCart(customerId); }
 @DeleteMapping("/cart/{customerId}/{productId}") public Map<String,Object> remove(@PathVariable int customerId,@PathVariable int productId) { service.removeFromCart(customerId,productId); return Map.of("message","Item removed"); }
 @PostMapping("/checkout") public Map<String,Object> checkout(@RequestBody CheckoutRequest c) { return service.checkout(c); }
 @GetMapping("/orders") public List<Map<String,Object>> orders() { return service.getOrders(); }
 @GetMapping("/orders/{id}/items") public List<Map<String,Object>> orderItems(@PathVariable int id) { return service.getOrderItems(id); }
 @GetMapping("/demo/traditional-jdbc/products") public List<Map<String,Object>> traditionalJdbc() { return service.traditionalJdbcProducts(); }
}
record ProductRequest(String name,String description,double price,int stock) {}
record CustomerRequest(String name,String email,String phone) {}
record CartRequest(int customerId,int productId,int quantity) {}
record CheckoutRequest(int customerId,double discountPercent) {}