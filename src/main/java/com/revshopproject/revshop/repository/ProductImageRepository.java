
package com.revshopproject.revshop.repository;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.revshopproject.revshop.entity.ProductImage;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
	
	//Fetches images belonging to a product.
    List<ProductImage> findByProduct_ProductId(Long productId);
}