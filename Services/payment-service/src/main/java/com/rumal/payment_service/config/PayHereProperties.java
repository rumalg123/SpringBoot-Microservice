package com.rumal.payment_service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payhere")
@Getter
@Setter
public class PayHereProperties {
    private String merchantId;
    private String merchantSecret;
    private String appId;
    private String appSecret;
    private String baseUrl;
    private String checkoutUrl;
    private String returnUrl;
    private String cancelUrl;
    private String notifyUrl;
}
