import sd_jwt_rs.*

val ISSUER_KEY_PEM =
  """-----BEGIN PRIVATE KEY-----
    |MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgUr2bNKuBPOrAaxsR
    |nbSH6hIhmNTxSGXshDSUD1a1y7ihRANCAARvbx3gzBkyPDz7TQIbjF+ef1IsxUwz
    |X1KWpmlVv+421F7+c1sLqGk4HUuoVeN8iOoAcE547pJhUEJyf5Asc6pP
    |-----END PRIVATE KEY-----
    |""".stripMargin

val ISSUER_KEY_PEM_PUBLIC =
  """-----BEGIN PUBLIC KEY-----
  MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEb28d4MwZMjw8+00CG4xfnn9SLMVM
  M19SlqZpVb/uNtRe/nNbC6hpOB1LqFXjfIjqAHBOeO6SYVBCcn+QLHOqTw==
    -----END PUBLIC KEY-----
    |""".stripMargin

val CLAIMS =
  """{
    |  "sub": "6c5c0a49-b589-431d-bae7-219122a9ec2c",
    |  "iss": "https://example.com/issuer",
    |  "iat": 1683000000,
    |  "exp": 1883000000,
    |  "address": {
    |    "street_address": "Schulstr. 12",
    |    "locality": "Schulpforta",
    |    "region": "Sachsen-Anhalt",
    |    "country": "DE"
    |  }
    |}""".stripMargin

val CLAIMS_PRESENTED =
  """{
    |  "sub": "6c5c0a49-b589-431d-bae7-219122a9ec2c",
    |  "iss": "https://example.com/issuer",
    |  "iat": 1683000000,
    |  "exp": 1883000000,
    |  "address": {
    |    "region": "Sachsen-Anhalt",
    |    "country": "DE"
    |  }
    |}""".stripMargin

    // MAIN
@main def simpleFlowIssuePresentVerify(): Unit = {
  val key = new sd_jwt_rs.EncodingKeyValue(ISSUER_KEY_PEM)
  val issuer = new SdjwtIssuerWrapper(key, null)
  val sdjwt = issuer.issueSdJwtAllLevel(
    CLAIMS, // user_claims
    null, // holder_key
    false, // add_decoy_claims
    SdjwtSerializationFormat.JSON // COMPACT // serialization_format
  )
  println("#####  sdjwt:  ######")
  println(sdjwt)

  val holder = SdjwtHolderWrapper(sdjwt, SdjwtSerializationFormat.JSON)
  val presentation =
    holder.createPresentation(CLAIMS_PRESENTED, null, null, null, null)

  println("#####  presentation:  ######")
  println(presentation)

  val verifier = SdjwtVerifierWrapper(
    presentation, // sd_jwt_presentation
    ISSUER_KEY_PEM_PUBLIC, // public_key
    null, // expected_aud
    null, // expected_nonce
    SdjwtSerializationFormat.JSON // serialization_format
  )
  val result: Boolean = verifier.verify(CLAIMS_PRESENTED)
  println("#####  verify:  ######")
  println(result)
}
