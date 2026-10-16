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

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's scheduler.
 * <p>
 * Register item <b>D11</b>. This application had <b>no scheduled work of any kind</b> before the
 * mail outbox - a repository-wide search for {@code @Scheduled}, {@code @Async}, a broker, a queue
 * or a retry mechanism found nothing, which ADR-003 recorded as an accepted cost of the approved
 * target. {@link org.trebol.mailing.outbox.MailOutboxDispatcher} is the only scheduled bean, and
 * this configuration exists solely to activate it.
 * <p>
 * Kept as its own class rather than annotating {@code BackendApp}, so that what enabling the
 * scheduler is <i>for</i> is written down next to the switch.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
