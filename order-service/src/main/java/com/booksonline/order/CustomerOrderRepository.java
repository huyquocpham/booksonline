package com.booksonline.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {

    Optional<CustomerOrder> findByCartId(String cartId);

    List<CustomerOrder> findByCustomerIdOrderByCreatedAtDesc(String customerId);
}
