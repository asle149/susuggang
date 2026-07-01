package com.susuggang.controller;

import com.susuggang.dto.ProductCreateRequest;
import com.susuggang.dto.ProductResponse;
import com.susuggang.service.ProductService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    public Long create(@RequestBody ProductCreateRequest request) {
        return productService.create(request);
    }

    @GetMapping
    public List<ProductResponse> list() {
        return productService.findAll();
    }

    @GetMapping("/{id}")
    public ProductResponse detail(@PathVariable Long id) {
        return productService.findOne(id);
    }
}
