package sdjwtwrapper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FlowCycle {
    @Test
    fun test_assembly_sd_full_recursive() {
        assertNotFail {
            val userClaims = """
                {
                    "sub": "6c5c0a49-b589-431d-bae7-219122a9ec2c",
                    "iss": "https://example.com/issuer",
                    "iat": 1683000000,
                    "exp": 1883000000,
                    "address": {
                        "street_address": "Schulstr. 12",
                        "locality": "Schulpforta",
                        "region": "Sachsen-Anhalt",
                        "country": "DE"
                    }
                }
            """.replace("\n", "")
            val key = EncodingKeyValue.fromEcPem(FlowCycle.PRIVATE_ISSUER_PEM)
            val issuer = SdjwtIssuerWrapper(key, null)
            val sdjwt = issuer.issueSdJwtAllLevel(userClaims, null, false, SdjwtSerializationFormat.COMPACT)
            println("sdjwt: $sdjwt")
        }
    }

    @Test
    fun create_full_presentation() {
        assertNotFail {
            val userClaims = """
                {
                    "sub": "6c5c0a49-b589-431d-bae7-219122a9ec2c",
                    "iss": "https://example.com/issuer",
                    "iat": 1683000000,
                    "exp": 1883000000,
                    "address": {
                        "street_address": "Schulstr. 12",
                        "locality": "Schulpforta",
                        "region": "Sachsen-Anhalt",
                        "country": "DE"
                    }
                }
            """.replace("\n", "")
            val key = EncodingKeyValue.fromEcPem(FlowCycle.PRIVATE_ISSUER_PEM)
            val issuer = SdjwtIssuerWrapper(key, null)
            val sdjwt = issuer.issueSdJwtAllLevel(userClaims, null, false, SdjwtSerializationFormat.COMPACT)
            println("sdjwt: $sdjwt")
            val claims_to_disclose = """
                {
                    "sub": "6c5c0a49-b589-431d-bae7-219122a9ec2c",
                    "iss": "https://example.com/issuer",
                    "iat": 1683000000,
                    "exp": 1883000000,
                    "address": {
                        "street_address": "Schulstr. 12",
                        "locality": "Schulpforta",
                        "region": "Sachsen-Anhalt",
                        "country": "DE"
                    }
                }
            """.replace("\n", "")
            val holder = SdjwtHolderWrapper(sdjwt, SdjwtSerializationFormat.COMPACT)
            val presentation = holder.createPresentation(claims_to_disclose, null, null, null, null)
            val presentationSplit = presentation.split("~")
            val sdjwtSplit = sdjwt.split("~")

            assertEquals(sdjwtSplit.size, presentationSplit.size)

            presentationSplit.forEach {
                assertTrue(sdjwtSplit.contains(it))
            }

            println("presentation: $presentation")
            // Sometimes the order of items after the ~ is different from the generated SD-JWT,
            // that is something that I need to investigate further. But, with the new changes it should work as expected.
            // assertEquals(sdjwt, presentation)
        }
    }

    @Test
    fun testVerifier() {
        assertNotFail {
            val userClaims = """
                {
                    "sub": "6c5c0a49-b589-431d-bae7-219122a9ec2c",
                    "iss": "https://example.com/issuer",
                    "iat": 1683000000,
                    "exp": 1883000000,
                    "address": {
                        "street_address": "Schulstr. 12",
                        "locality": "Schulpforta",
                        "region": "Sachsen-Anhalt",
                        "country": "DE"
                    }
                }
            """.replace("\n", "")
            val key = EncodingKeyValue.fromEcPem(FlowCycle.PRIVATE_ISSUER_PEM)
            val issuer = SdjwtIssuerWrapper(key, null)
            val sdjwt = issuer.issueSdJwtAllLevel(userClaims, null, false, SdjwtSerializationFormat.COMPACT)
            println("sdjwt: $sdjwt")

            val claims_to_disclose = """
                {
                    "sub": "6c5c0a49-b589-431d-bae7-219122a9ec2c",
                    "iss": "https://example.com/issuer",
                    "iat": 1683000000,
                    "exp": 1883000000,
                    "address": {
                        "street_address": "Schulstr. 12",
                        "locality": "Schulpforta",
                        "region": "Sachsen-Anhalt",
                        "country": "DE"
                    }
                }
            """.replace("\n", "")
            val holder = SdjwtHolderWrapper(sdjwt, SdjwtSerializationFormat.COMPACT)
            val presentation = holder.createPresentation(claims_to_disclose, null, null, null, null)
            val presentationSplit = presentation.split("~")
            val sdjwtSplit = sdjwt.split("~")

            assertEquals(sdjwtSplit.size, presentationSplit.size)

            presentationSplit.forEach {
                assertTrue(sdjwtSplit.contains(it))
            }

            println("presentation: $presentation")

            val verifier = SdjwtVerifierWrapper(
                presentation,
                PUBLIC_ISSUER_PEM,
                null,
                null,
                SdjwtSerializationFormat.COMPACT
            )
            val result: Boolean = verifier.verify(userClaims)
            assertTrue(result)
            println("verify: $result")
        }
    }

    /**
     * Executes the given block of code and asserts that it does not throw any exception.
     *
     * @param block the block of code to execute
     * @throws AssertionError if the block of code throws an exception
     */
    private fun assertNotFail(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            throw AssertionError("Expected operation to not fail, but it failed with an exception", e)
        }
    }

    companion object {
        /**
         * Private key in PEM format used by the SdjwtIssuerWrapper for signing tokens.
         */
        val PRIVATE_ISSUER_PEM = "-----BEGIN PRIVATE KEY-----\nMIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgUr2bNKuBPOrAaxsR\nnbSH6hIhmNTxSGXshDSUD1a1y7ihRANCAARvbx3gzBkyPDz7TQIbjF+ef1IsxUwz\nX1KWpmlVv+421F7+c1sLqGk4HUuoVeN8iOoAcE547pJhUEJyf5Asc6pP\n-----END PRIVATE KEY-----\n"

        /**
         * The public issuer PEM key used for verifying signatures.
         *
         * The key is in PEM format and is stored as a string. It is used to verify the signatures of JSON Web Tokens (JWTs).
         * The key is used in conjunction with the `SdjwtVerifierWrapper` class to verify the authenticity and integrity of the JWT.
         */
        val PUBLIC_ISSUER_PEM = "-----BEGIN PUBLIC KEY-----\nMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEb28d4MwZMjw8+00CG4xfnn9SLMVM\nM19SlqZpVb/uNtRe/nNbC6hpOB1LqFXjfIjqAHBOeO6SYVBCcn+QLHOqTw==\n-----END PUBLIC KEY-----\n"
    }
}
