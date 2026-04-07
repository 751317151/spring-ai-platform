package com.huah.ai.platform.common.web;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RequestOrigin {
    String clientIp;
    String country;
    String province;
    String city;
}
