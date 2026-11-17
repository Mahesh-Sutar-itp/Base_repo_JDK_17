/*
 * Copyright (c) 2020-2024 The Trebol eCommerce Project
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software
 * and associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished
 * to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package org.trebol.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI description used by Swagger UI (only served when springdoc is enabled; off by default in
 * prod, see SWAGGER_ENABLED).
 * <ul>
 *   <li>Relative server URL: on AWS the app sits behind CloudFront and an internal load balancer,
 *   so the host it sees is the load balancer's private name. With "/" Swagger UI sends its
 *   "Try it out" requests to the address the page was opened from.</li>
 *   <li>Bearer token scheme: the Authorize button takes the token returned by /public/login (with or
 *   without the "Bearer " prefix) and sends it on every request.</li>
 * </ul>
 */
@Configuration
public class OpenApiConfig {
    static final String BEARER_SCHEME = "bearerToken";

    @Bean
    public OpenAPI trebolOpenApi() {
        return new OpenAPI()
            .servers(List.of(new Server().url("/").description("The address this page was opened from")))
            .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Token from POST /public/login (response body or Authorization header)")))
            .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
