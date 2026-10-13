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

package org.trebol.contracts.identity;

/**
 * A natural person as submitted across a service boundary.
 * <p>
 * Mirrors the monolith's person transport model exactly, including its most consequential
 * property: <b>there is no identifier field</b>. The surrogate key was never exposed
 * externally, so {@link #idNumber()} - the national identification number - is the only
 * identity a caller can express.
 *
 * @param idNumber  the national identification number. The resolution key. Must not be
 *                  blank. Matched exactly and case-sensitively. Maximum 20 characters.
 * @param firstName legal first name. Maximum 200 characters.
 * @param lastName  legal last name. Maximum 200 characters.
 * @param email     e-mail address. Length-constrained to 5-100 characters but <b>never
 *                  format-validated</b> anywhere in the monolith.
 * @param phone1    primary phone. Optional. Stored unvalidated; defaults to the empty
 *                  string rather than null, because the column is NOT NULL with no
 *                  database default.
 * @param phone2    secondary phone. Same treatment as {@code phone1}.
 */
public record PersonRef(
    String idNumber,
    String firstName,
    String lastName,
    String email,
    String phone1,
    String phone2
) {

    /**
     * Normalises the optional phone fields to the empty string, reproducing the monolith's
     * entity-builder defaults. Without this, a null phone reaches a NOT NULL column and
     * fails at flush rather than with a readable validation error.
     *
     * @return a copy with null phone values replaced by the empty string
     */
    public PersonRef withNormalisedPhones() {
        return new PersonRef(
            idNumber,
            firstName,
            lastName,
            email,
            phone1==null ? "":phone1,
            phone2==null ? "":phone2
        );
    }
}
