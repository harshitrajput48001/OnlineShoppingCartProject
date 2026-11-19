package com.example.shoppingcart;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.sql.*;
import java.util.*;

@Service
public class ShopService {
 private final JdbcTemplate jdbc;
 @Value("${spring.datasource.url}") private String dbUrl;
 @Value("${spring.datasource.username}") private String dbUser;
 @Value("${spring.datasource.password}") private String dbPassword;
 public ShopService(JdbcTemplate jdbc) { this.jdbc=jdbc; }

 public List<Map<String,Object>> getProducts(String search) {
  if(search==null || search.isBlank()) return jdbc.queryForList("SELECT * FROM products ORDER BY product_id DESC");
  return jdbc.queryForList("SELECT * FROM products WHERE name LIKE ? OR description LIKE ? ORDER BY name","%"+search+"%","%"+search+"%");
 }
 public Map<String,Object> addProduct(ProductRequest p) {
  validateProduct(p); KeyHolder k=new GeneratedKeyHolder();
  jdbc.update(con->{PreparedStatement s=con.prepareStatement("INSERT INTO products(name,description,price,stock) VALUES(?,?,?,?)",Statement.RETURN_GENERATED_KEYS); s.setString(1,p.name()); s.setString(2,p.description()); s.setDouble(3,p.price()); s.setInt(4,p.stock()); return s;},k);
  return jdbc.queryForMap("SELECT * FROM products WHERE product_id=?",k.getKey().intValue());
 }
 public Map<String,Object> updateProduct(int id,ProductRequest p) {
  validateProduct(p); int n=jdbc.update("UPDATE products SET name=?,description=?,price=?,stock=? WHERE product_id=?",p.name(),p.description(),p.price(),p.stock(),id);
  if(n==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Product not found");
  return jdbc.queryForMap("SELECT * FROM products WHERE product_id=?",id);
 }
 public void deleteProduct(int id) {
  try { if(jdbc.update("DELETE FROM products WHERE product_id=?",id)==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Product not found"); }
  catch(org.springframework.dao.DataIntegrityViolationException e) { throw new ResponseStatusException(HttpStatus.CONFLICT,"Product is referenced by a cart or order"); }
 }
 public Map<String,Object> addCustomer(CustomerRequest c) {
  if(c.name()==null||c.name().isBlank()||c.email()==null||c.email().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Name and email are required");
  try {
   KeyHolder k=new GeneratedKeyHolder();
   jdbc.update(con->{PreparedStatement s=con.prepareStatement("INSERT INTO customers(name,email,phone) VALUES(?,?,?)",Statement.RETURN_GENERATED_KEYS); s.setString(1,c.name()); s.setString(2,c.email()); s.setString(3,c.phone()); return s;},k);
   return jdbc.queryForMap("SELECT * FROM customers WHERE customer_id=?",k.getKey().intValue());
  } catch(org.springframework.dao.DuplicateKeyException e) { throw new ResponseStatusException(HttpStatus.CONFLICT,"Email already registered"); }
 }
 public List<Map<String,Object>> getCustomers(){return jdbc.queryForList("SELECT * FROM customers ORDER BY customer_id DESC");}
 public Map<String,Object> addToCart(CartRequest c) {
  if(c.quantity()<1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Quantity must be positive");
  exists("SELECT COUNT(*) FROM customers WHERE customer_id=?",c.customerId(),"Customer not found");
  Map<String,Object> p=product(c.productId());
  if(((Number)p.get("stock")).intValue()<c.quantity()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Insufficient stock");
  jdbc.update("INSERT INTO cart_items(customer_id,product_id,quantity) VALUES(?,?,?) ON DUPLICATE KEY UPDATE quantity=quantity+VALUES(quantity)",c.customerId(),c.productId(),c.quantity());
  return Map.of("message","Added to cart");
 }
 public List<Map<String,Object>> getCart(int id) {
  return jdbc.queryForList("SELECT ci.customer_id,ci.product_id,p.name,p.price,ci.quantity,(p.price*ci.quantity) AS line_total FROM cart_items ci JOIN products p ON ci.product_id=p.product_id WHERE ci.customer_id=? ORDER BY p.name",id);
 }
 public void removeFromCart(int c,int p){jdbc.update("DELETE FROM cart_items WHERE customer_id=? AND product_id=?",c,p);}

 @Transactional
 public Map<String,Object> checkout(CheckoutRequest c) {
  if(c.discountPercent()<0||c.discountPercent()>50) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Discount must be 0 to 50 percent");
  exists("SELECT COUNT(*) FROM customers WHERE customer_id=?",c.customerId(),"Customer not found");
  List<Map<String,Object>> items=getCart(c.customerId());
  if(items.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Cart is empty");
  double subtotal=0;
  for(Map<String,Object> item:items) {
   Map<String,Object> p=product(((Number)item.get("product_id")).intValue());
   if(((Number)p.get("stock")).intValue()<((Number)item.get("quantity")).intValue()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Insufficient stock for "+p.get("name"));
   subtotal+=((Number)item.get("line_total")).doubleValue();
  }
  double discount=subtotal*c.discountPercent()/100.0, total=subtotal-discount;
  final double finalSubtotal=subtotal, finalDiscount=discount, finalTotal=total;
  KeyHolder k=new GeneratedKeyHolder();
  jdbc.update(con->{PreparedStatement s=con.prepareStatement("INSERT INTO orders(customer_id,subtotal,discount,total_amount,status) VALUES(?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS); s.setInt(1,c.customerId()); s.setDouble(2,finalSubtotal); s.setDouble(3,finalDiscount); s.setDouble(4,finalTotal); s.setString(5,"PLACED"); return s;},k);
  int orderId=k.getKey().intValue();
  for(Map<String,Object> item:items) {
   int pid=((Number)item.get("product_id")).intValue(), qty=((Number)item.get("quantity")).intValue();
   jdbc.update("INSERT INTO order_items(order_id,product_id,quantity,unit_price) VALUES(?,?,?,?)",orderId,pid,qty,item.get("price"));
   if(jdbc.update("UPDATE products SET stock=stock-? WHERE product_id=? AND stock>=?",qty,pid,qty)==0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Stock changed; try again");
  }
  jdbc.update("DELETE FROM cart_items WHERE customer_id=?",c.customerId());
  return Map.of("orderId",orderId,"subtotal",subtotal,"discount",discount,"total",total,"status","PLACED");
 }
 public List<Map<String,Object>> getOrders(){return jdbc.queryForList("SELECT o.*,c.name AS customer_name,c.email FROM orders o JOIN customers c ON o.customer_id=c.customer_id ORDER BY o.order_id DESC");}
 public List<Map<String,Object>> getOrderItems(int id){return jdbc.queryForList("SELECT oi.*,p.name AS product_name FROM order_items oi JOIN products p ON oi.product_id=p.product_id WHERE oi.order_id=?",id);}

 // Traditional JDBC: manually uses DriverManager, Connection, PreparedStatement and ResultSet.
 public List<Map<String,Object>> traditionalJdbcProducts() {
  List<Map<String,Object>> out=new ArrayList<>();
  try(Connection con=DriverManager.getConnection(dbUrl,dbUser,dbPassword);
      PreparedStatement ps=con.prepareStatement("SELECT product_id,name,price,stock FROM products");
      ResultSet rs=ps.executeQuery()) {
   while(rs.next()) {Map<String,Object> row=new LinkedHashMap<>(); row.put("product_id",rs.getInt("product_id")); row.put("name",rs.getString("name")); row.put("price",rs.getBigDecimal("price")); row.put("stock",rs.getInt("stock")); out.add(row);}
   return out;
  } catch(SQLException e) {throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"Traditional JDBC error: "+e.getMessage());}
 }
 private Map<String,Object> product(int id) {
  try{return jdbc.queryForMap("SELECT * FROM products WHERE product_id=?",id);}
  catch(org.springframework.dao.EmptyResultDataAccessException e){throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Product not found");}
 }
 private void exists(String sql,int id,String message){Integer n=jdbc.queryForObject(sql,Integer.class,id); if(n==null||n==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,message);}
 private void validateProduct(ProductRequest p){if(p.name()==null||p.name().isBlank()||p.price()<0||p.stock()<0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter valid product name, price and stock");}
}