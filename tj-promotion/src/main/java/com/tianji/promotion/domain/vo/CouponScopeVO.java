package com.tianji.promotion.domain.vo;

import com.tianji.common.domain.dto.IdNameDTO;
import io.swagger.annotations.ApiModel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ApiModel(description = "优惠券作用范围")
public class CouponScopeVO extends IdNameDTO {

    public CouponScopeVO(Long id, String name) {
        super(id, name);
    }
}