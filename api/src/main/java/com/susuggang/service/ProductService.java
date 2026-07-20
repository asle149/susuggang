package com.susuggang.service;

import com.susuggang.domain.Product;
import com.susuggang.domain.ProductStatus;
import com.susuggang.domain.Stock;
import com.susuggang.dto.ProductCreateRequest;
import com.susuggang.dto.ProductResponse;
import com.susuggang.exception.BusinessException;
import com.susuggang.exception.ErrorCode;
import com.susuggang.repository.ProductRepository;
import com.susuggang.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final StockRepository stockRepository;

    @Transactional
    public Long create(ProductCreateRequest request) {
        Product product = productRepository.save(Product.builder()
                .title(request.title())
                .price(request.price())
                .sellerId(request.sellerId())
                .status(ProductStatus.ON_SALE)
                .build());
        stockRepository.save(Stock.builder()
                .productId(product.getId())
                .quantity(request.quantity())
                .build());
        return product.getId();
    }

    public List<ProductResponse> findAll() {
        return productRepository.findAll().stream()
                .map(p -> ProductResponse.from(p, stockRepository.findByProductId(p.getId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND))))
                .toList();
    }

    public ProductResponse findOne(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        Stock stock = stockRepository.findByProductId(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        return ProductResponse.from(product, stock);
    }
}
