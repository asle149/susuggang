package com.susuggang.controller;

import com.susuggang.dto.CommonResponse;
import com.susuggang.dto.ProductCreateRequest;
import com.susuggang.dto.ProductResponse;
import com.susuggang.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    public CommonResponse<Long> create(@RequestBody ProductCreateRequest request){
        return CommonResponse.success(
                productService.create(request)
        );
    }

    @GetMapping
    public CommonResponse<List<ProductResponse>> list(){
        return CommonResponse.success(
                productService.findAll()
        );
    }

    @GetMapping("/{id}")
    public CommonResponse<ProductResponse> detail(@PathVariable Long id){
        return CommonResponse.success(
                productService.findOne(id)
        );
    }
}
