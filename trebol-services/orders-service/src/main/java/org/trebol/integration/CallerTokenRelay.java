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

package org.trebol.integration;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Relays the caller's bearer token to a sibling service.
 * <p>
 * <b>Phase 3.</b> Per {@code ADR-002} every service verifies tokens locally against the shared
 * signing key, so a token minted by Identity is accepted by the Catalogue and by Identity itself.
 * Relaying the caller's own token therefore preserves the caller's identity and authorities across
 * the hop, rather than escalating to a service account - which means a sibling service applies the
 * <em>same</em> authorisation decision it would have applied to a direct call.
 * <p>
 * <b>That is the safe default, and it has one consequence that needs a decision.</b> A guest
 * completing a checkout holds only the {@code checkout} authority. In the monolith,
 * {@code resolveOrCreateCustomer} was an in-process service call and so bypassed authorisation
 * entirely - no authority was ever checked for the customer row it created. Across a service
 * boundary the same provisioning becomes an HTTP call that Identity <em>will</em> authorise, and a
 * guest's token does not carry {@code customers:create}.
 * <p>
 * <b>Recorded as an open decision in {@code PHASE-3-EXIT.md}</b>, because the three ways to resolve
 * it are materially different in security terms and the choice is not the conversion's to make:
 * <ol>
 *   <li>grant {@code customers:create} to the guest role - simple, and widens what a guest token
 *       can do everywhere, including against Identity's public data routes;</li>
 *   <li>have Identity expose a narrow internal provisioning route reachable only from inside the
 *       deployment boundary - keeps the guest token narrow, but adds an unauthenticated surface
 *       whose protection becomes a deployment concern rather than an application one;</li>
 *   <li>provision the customer <em>before</em> checkout, as part of the guest-token issue that
 *       Identity already performs - the cleanest, and the largest change.</li>
 * </ol>
 * No option is selected here. The relay below is the mechanism; which principal it relays is the
 * decision.
 */
final class CallerTokenRelay {
    private CallerTokenRelay() {
    }

    /**
     * @return the inbound {@code Authorization} header, or {@code null} when there is no request in
     *         scope - which is the case for the scheduled outbox dispatcher
     */
    static String currentAuthorizationHeader() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        return request==null ? null:request.getHeader("Authorization");
    }
}
